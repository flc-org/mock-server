/*
 * Copyright (c) 2026 TestSuites
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.testsuites.mockserver.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testsuites.mockserver.dao.RecordedResponseDao;
import com.testsuites.mockserver.dto.MockServerConfig;
import com.testsuites.mockserver.dto.RecordedResponse;
import com.testsuites.mockserver.proxy.ProxyRequest;
import com.testsuites.mockserver.proxy.ProxyResponse;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProxyService {

  private static final List<String> HOP_BY_HOP_HEADERS = List.of(
    "connection",
    "keep-alive",
    "proxy-authenticate",
    "proxy-authorization",
    "te",
    "trailers",
    "transfer-encoding",
    "upgrade"
  );
  private final MockServerConfigMatcher mockServerConfigMatcher;
  private final HashGenerationService hashGenerationService;
  private final RecordedResponseDao recordedResponseDao;
  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;
  private final MockServerConfigService mockServerConfigService;

  public ProxyResponse proxyRequest(ProxyRequest proxyRequest) {
    MockServerConfig config = mockServerConfigMatcher.findConfig(
      proxyRequest.getPath()
    );
    log.debug(
      "Matched config proxySalt={} endpointDesc={} for request {} {}",
      config.getProxySalt(),
      config.getEndpointDesc(),
      proxyRequest.getMethod(),
      proxyRequest.getPath()
    );
    String downstreamHost = mockServerConfigService.resolveDownstreamHost(
      config.getHostKey()
    );
    String targetUrl = buildTargetUrl(
      downstreamHost,
      proxyRequest.getPath(),
      proxyRequest.getQueryString(),
      config.getProxySalt()
    );
    String requestHash = hashGenerationService.generateHash(
      config.getHashGenerationFunction(),
      proxyRequest
    );
    Optional<RecordedResponse> existing =
      recordedResponseDao.findFirstByMockServerConfigProxySaltAndRequestHash(
        config.getProxySalt(),
        requestHash
      );
    if (existing.isPresent()) {
      RecordedResponse recordedResponse = existing.get();
      log.info(
        "Cache HIT proxySalt={} method={} targetUrl={} hash={}",
        config.getProxySalt(),
        proxyRequest.getMethod(),
        targetUrl,
        requestHash
      );
      return toProxyResponse(recordedResponse);
    }
    log.info(
      "Cache MISS proxySalt={} method={} targetUrl={} hash={}",
      config.getProxySalt(),
      proxyRequest.getMethod(),
      targetUrl,
      requestHash
    );
    HttpHeaders outboundHeaders = new HttpHeaders();
    proxyRequest
      .getHeaders()
      .forEach((name, values) -> {
        if (
          !isHopByHop(name) &&
          !"host".equalsIgnoreCase(name) &&
          !"content-length".equalsIgnoreCase(name)
        ) {
          outboundHeaders.put(name, values);
        }
      });
    HttpEntity<byte[]> requestEntity = new HttpEntity<>(
      proxyRequest.getBody(),
      outboundHeaders
    );
    ResponseEntity<byte[]> downstreamResponse = restTemplate.exchange(
      URI.create(targetUrl),
      HttpMethod.valueOf(proxyRequest.getMethod()),
      requestEntity,
      byte[].class
    );
    log.info(
      "Downstream response recorded proxySalt={} status={} targetUrl={}",
      config.getProxySalt(),
      downstreamResponse.getStatusCode().value(),
      targetUrl
    );
    RecordedResponse toSave = new RecordedResponse();
    toSave.setHttpMethod(proxyRequest.getMethod());
    toSave.setTargetUrl(targetUrl);
    toSave.setMockServerConfigProxySalt(config.getProxySalt());
    toSave.setRequestHash(requestHash);
    toSave.setResponseStatus(downstreamResponse.getStatusCode().value());
    toSave.setResponseBody(
      downstreamResponse.getBody() == null
        ? new byte[0]
        : downstreamResponse.getBody()
    );
    toSave.setResponseContentType(
      downstreamResponse.getHeaders().getContentType() == null
        ? null
        : downstreamResponse.getHeaders().getContentType().toString()
    );
    toSave.setResponseHeadersJson(
      serializeHeaders(filterResponseHeaders(downstreamResponse.getHeaders()))
    );
    recordedResponseDao.save(toSave);
    return new ProxyResponse(
      downstreamResponse.getStatusCode().value(),
      filterResponseHeaders(downstreamResponse.getHeaders()),
      downstreamResponse.getBody() == null
        ? new byte[0]
        : downstreamResponse.getBody()
    );
  }

  private ProxyResponse toProxyResponse(RecordedResponse recordedResponse) {
    HttpHeaders headers = deserializeHeaders(
      recordedResponse.getResponseHeadersJson()
    );
    if (StringUtils.hasText(recordedResponse.getResponseContentType())) {
      headers.set(
        HttpHeaders.CONTENT_TYPE,
        recordedResponse.getResponseContentType()
      );
    }
    return new ProxyResponse(
      recordedResponse.getResponseStatus(),
      headers,
      recordedResponse.getResponseBody()
    );
  }

  private String buildTargetUrl(
    String downstreamHost,
    String path,
    String queryString,
    String proxySalt
  ) {
    if (!StringUtils.hasText(downstreamHost)) {
      throw new IllegalStateException("Downstream host must be configured");
    }
    String sanitizedPath = stripProxySalt(path, proxySalt);
    String normalizedBase = downstreamHost.endsWith("/")
      ? downstreamHost.substring(0, downstreamHost.length() - 1)
      : downstreamHost;
    String url = normalizedBase + sanitizedPath;
    if (StringUtils.hasText(queryString)) {
      url += "?" + queryString;
    }
    return url;
  }

  private String stripProxySalt(String path, String proxySalt) {
    if (!StringUtils.hasText(path) || !StringUtils.hasText(proxySalt)) {
      return normalizePath(path);
    }
    String normalized = normalizePath(path);
    String withoutLeadingSlash = normalized.substring(1);
    int nextSlash = withoutLeadingSlash.indexOf('/');
    String firstSegment = nextSlash >= 0
      ? withoutLeadingSlash.substring(0, nextSlash)
      : withoutLeadingSlash;
    if (!firstSegment.equals(proxySalt)) {
      return normalized;
    }
    if (nextSlash < 0) {
      return "/";
    }
    String stripped = withoutLeadingSlash.substring(nextSlash);
    return stripped;
  }

  private String normalizePath(String path) {
    if (!StringUtils.hasText(path)) {
      return "/";
    }
    return path.startsWith("/") ? path : "/" + path;
  }

  private boolean isHopByHop(String headerName) {
    return HOP_BY_HOP_HEADERS.stream()
      .anyMatch(h -> h.equalsIgnoreCase(headerName));
  }

  private HttpHeaders filterResponseHeaders(HttpHeaders sourceHeaders) {
    HttpHeaders filtered = new HttpHeaders();
    sourceHeaders.forEach((name, values) -> {
      if (!isHopByHop(name)) {
        filtered.put(name, values);
      }
    });
    filtered.remove(HttpHeaders.CONTENT_LENGTH);
    return filtered;
  }

  private String serializeHeaders(HttpHeaders headers) {
    try {
      Map<String, List<String>> asMap = new LinkedHashMap<>();
      headers.forEach((name, values) -> asMap.put(name, new ArrayList<>(values))
      );
      return objectMapper.writeValueAsString(asMap);
    } catch (Exception e) {
      throw new IllegalStateException(
        "Failed to serialize response headers",
        e
      );
    }
  }

  private HttpHeaders deserializeHeaders(String headersJson) {
    try {
      Map<String, List<String>> raw = objectMapper.readValue(
        headersJson,
        new TypeReference<>() {}
      );
      HttpHeaders headers = new HttpHeaders();
      raw.forEach(headers::put);
      return headers;
    } catch (Exception e) {
      throw new IllegalStateException(
        "Failed to deserialize response headers",
        e
      );
    }
  }
}
