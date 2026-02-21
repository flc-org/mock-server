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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testsuites.mockserver.dao.RecordedResponseDao;
import com.testsuites.mockserver.dto.MockServerConfig;
import com.testsuites.mockserver.dto.RecordedResponse;
import com.testsuites.mockserver.proxy.ProxyRequest;
import com.testsuites.mockserver.proxy.ProxyResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class ProxyServiceTest {

  @Mock
  private MockServerConfigMatcher mockServerConfigMatcher;

  @Mock
  private HashGenerationService hashGenerationService;

  @Mock
  private RecordedResponseDao recordedResponseDao;

  @Mock
  private RestTemplate restTemplate;

  @Mock
  private MockServerConfigService mockServerConfigService;

  @Test
  void proxyRequestShouldReturnRecordedResponseWithContentTypeWhenCacheHit()
    throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    ProxyService service = newService(objectMapper);
    ProxyRequest request = proxyRequest(
      "GET",
      "/proxy-1/orders",
      "q=1",
      new HttpHeaders(),
      new byte[0]
    );
    MockServerConfig config = config("proxy-1", "ueqsHost", "spel:#request.path");
    RecordedResponse recorded = new RecordedResponse();
    recorded.setResponseStatus(200);
    recorded.setResponseBody("cached".getBytes(StandardCharsets.UTF_8));
    recorded.setResponseHeadersJson(
      objectMapper.writeValueAsString(Map.of("X-From-Cache", List.of("yes")))
    );
    recorded.setResponseContentType("application/json");

    when(mockServerConfigMatcher.findConfig("/proxy-1/orders")).thenReturn(config);
    when(mockServerConfigService.resolveDownstreamHost("ueqsHost"))
      .thenReturn("https://downstream/");
    when(hashGenerationService.generateHash("spel:#request.path", request))
      .thenReturn("hash-1");
    when(
      recordedResponseDao.findFirstByMockServerConfigProxySaltAndRequestHash(
        "proxy-1",
        "hash-1"
      )
    )
      .thenReturn(Optional.of(recorded));

    ProxyResponse response = service.proxyRequest(request);

    assertEquals(200, response.status());
    assertArrayEquals("cached".getBytes(StandardCharsets.UTF_8), response.body());
    assertEquals("yes", response.headers().getFirst("X-From-Cache"));
    assertEquals("application/json", response.headers().getFirst(HttpHeaders.CONTENT_TYPE));
    verifyNoInteractions(restTemplate);
    verify(recordedResponseDao, never()).save(any(RecordedResponse.class));
  }

  @Test
  void proxyRequestShouldNotSetContentTypeWhenRecordedValueIsBlank()
    throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    ProxyService service = newService(objectMapper);
    ProxyRequest request = proxyRequest(
      "GET",
      "/proxy-1/orders",
      null,
      new HttpHeaders(),
      new byte[0]
    );
    MockServerConfig config = config("proxy-1", "ueqsHost", "spel:#request.path");
    RecordedResponse recorded = new RecordedResponse();
    recorded.setResponseStatus(200);
    recorded.setResponseBody(new byte[0]);
    recorded.setResponseHeadersJson(objectMapper.writeValueAsString(Map.of()));
    recorded.setResponseContentType("  ");

    when(mockServerConfigMatcher.findConfig("/proxy-1/orders")).thenReturn(config);
    when(mockServerConfigService.resolveDownstreamHost("ueqsHost"))
      .thenReturn("https://downstream/");
    when(hashGenerationService.generateHash("spel:#request.path", request))
      .thenReturn("hash-1");
    when(
      recordedResponseDao.findFirstByMockServerConfigProxySaltAndRequestHash(
        "proxy-1",
        "hash-1"
      )
    )
      .thenReturn(Optional.of(recorded));

    ProxyResponse response = service.proxyRequest(request);

    assertFalse(response.headers().containsKey(HttpHeaders.CONTENT_TYPE));
  }

  @Test
  void proxyRequestShouldForwardRequestAndStoreResponseOnCacheMiss()
    throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    ProxyService service = newService(objectMapper);
    HttpHeaders incomingHeaders = new HttpHeaders();
    incomingHeaders.add("Connection", "keep-alive");
    incomingHeaders.add("Host", "original-host");
    incomingHeaders.add("Content-Length", "999");
    incomingHeaders.add("X-Req", "abc");
    ProxyRequest request = proxyRequest(
      "POST",
      "/proxy-1",
      null,
      incomingHeaders,
      "request-body".getBytes(StandardCharsets.UTF_8)
    );
    MockServerConfig config = config("proxy-1", "ueqsHost", "spel:#request.path");

    HttpHeaders downstreamHeaders = new HttpHeaders();
    downstreamHeaders.add("Connection", "keep-alive");
    downstreamHeaders.setContentLength(123);
    downstreamHeaders.add("X-Resp", "ok");
    downstreamHeaders.setContentType(MediaType.TEXT_PLAIN);
    ResponseEntity<byte[]> downstreamResponse = ResponseEntity
      .status(201)
      .headers(downstreamHeaders)
      .body(null);

    when(mockServerConfigMatcher.findConfig("/proxy-1")).thenReturn(config);
    when(mockServerConfigService.resolveDownstreamHost("ueqsHost"))
      .thenReturn("https://downstream/");
    when(hashGenerationService.generateHash("spel:#request.path", request))
      .thenReturn("hash-1");
    when(
      recordedResponseDao.findFirstByMockServerConfigProxySaltAndRequestHash(
        "proxy-1",
        "hash-1"
      )
    )
      .thenReturn(Optional.empty());
    when(
      restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(), eq(byte[].class))
    )
      .thenReturn(downstreamResponse);
    when(recordedResponseDao.save(any(RecordedResponse.class)))
      .thenAnswer(invocation -> invocation.getArgument(0));

    ProxyResponse response = service.proxyRequest(request);

    assertEquals(201, response.status());
    assertEquals(0, response.body().length);
    assertEquals("ok", response.headers().getFirst("X-Resp"));
    assertEquals("text/plain", response.headers().getFirst(HttpHeaders.CONTENT_TYPE));
    assertFalse(response.headers().containsKey("Connection"));
    assertFalse(response.headers().containsKey(HttpHeaders.CONTENT_LENGTH));

    ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<HttpEntity<byte[]>> entityCaptor = ArgumentCaptor.forClass(
      (Class<HttpEntity<byte[]>>) (Class<?>) HttpEntity.class
    );
    verify(restTemplate).exchange(
      uriCaptor.capture(),
      eq(HttpMethod.POST),
      entityCaptor.capture(),
      eq(byte[].class)
    );
    assertEquals("https://downstream/", uriCaptor.getValue().toString());
    assertEquals("abc", entityCaptor.getValue().getHeaders().getFirst("X-Req"));
    assertFalse(entityCaptor.getValue().getHeaders().containsKey("Connection"));
    assertFalse(entityCaptor.getValue().getHeaders().containsKey("Host"));
    assertFalse(entityCaptor.getValue().getHeaders().containsKey("Content-Length"));

    ArgumentCaptor<RecordedResponse> savedCaptor = ArgumentCaptor.forClass(
      RecordedResponse.class
    );
    verify(recordedResponseDao).save(savedCaptor.capture());
    RecordedResponse saved = savedCaptor.getValue();
    assertEquals("POST", saved.getHttpMethod());
    assertEquals("https://downstream/", saved.getTargetUrl());
    assertEquals("proxy-1", saved.getMockServerConfigProxySalt());
    assertEquals("hash-1", saved.getRequestHash());
    assertEquals(201, saved.getResponseStatus());
    assertEquals("text/plain", saved.getResponseContentType());
    assertEquals(0, saved.getResponseBody().length);
    Map<String, List<String>> savedHeaders = objectMapper.readValue(
      saved.getResponseHeadersJson(),
      new TypeReference<>() {}
    );
    assertNotNull(savedHeaders.get("X-Resp"));
    assertFalse(savedHeaders.containsKey(HttpHeaders.CONTENT_LENGTH));
    assertFalse(savedHeaders.containsKey("Connection"));
  }

  @Test
  void proxyRequestShouldKeepOriginalPathWhenFirstSegmentDoesNotMatchProxySalt() {
    ObjectMapper objectMapper = new ObjectMapper();
    ProxyService service = newService(objectMapper);
    ProxyRequest request = proxyRequest(
      "GET",
      "other/api",
      "q=1",
      new HttpHeaders(),
      new byte[0]
    );
    MockServerConfig config = config("proxy-1", "ueqsHost", "spel:#request.path");

    when(mockServerConfigMatcher.findConfig("other/api")).thenReturn(config);
    when(mockServerConfigService.resolveDownstreamHost("ueqsHost"))
      .thenReturn("https://downstream");
    when(hashGenerationService.generateHash("spel:#request.path", request))
      .thenReturn("hash-1");
    when(
      recordedResponseDao.findFirstByMockServerConfigProxySaltAndRequestHash(
        "proxy-1",
        "hash-1"
      )
    )
      .thenReturn(Optional.empty());
    when(
      restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(byte[].class))
    )
      .thenReturn(ResponseEntity.ok("ok".getBytes(StandardCharsets.UTF_8)));
    when(recordedResponseDao.save(any(RecordedResponse.class)))
      .thenAnswer(invocation -> invocation.getArgument(0));

    ProxyResponse response = service.proxyRequest(request);

    ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
    verify(restTemplate).exchange(
      uriCaptor.capture(),
      eq(HttpMethod.GET),
      any(),
      eq(byte[].class)
    );
    assertEquals("https://downstream/other/api?q=1", uriCaptor.getValue().toString());
    assertArrayEquals("ok".getBytes(StandardCharsets.UTF_8), response.body());
  }

  @Test
  void proxyRequestShouldFailWhenDownstreamHostIsBlank() {
    ProxyService service = newService(new ObjectMapper());
    ProxyRequest request = proxyRequest(
      "GET",
      "/proxy-1/orders",
      null,
      new HttpHeaders(),
      new byte[0]
    );
    MockServerConfig config = config("proxy-1", "ueqsHost", "spel:#request.path");

    when(mockServerConfigMatcher.findConfig("/proxy-1/orders")).thenReturn(config);
    when(mockServerConfigService.resolveDownstreamHost("ueqsHost")).thenReturn("   ");

    IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
      service.proxyRequest(request)
    );

    assertEquals("Downstream host must be configured", ex.getMessage());
    verifyNoInteractions(hashGenerationService);
  }

  @Test
  void proxyRequestShouldWrapHeaderSerializationFailure() throws Exception {
    ObjectMapper objectMapper = org.mockito.Mockito.mock(ObjectMapper.class);
    ProxyService service = newService(objectMapper);
    ProxyRequest request = proxyRequest(
      "GET",
      "/proxy-1/orders",
      null,
      new HttpHeaders(),
      new byte[0]
    );
    MockServerConfig config = config("proxy-1", "ueqsHost", "spel:#request.path");

    when(mockServerConfigMatcher.findConfig("/proxy-1/orders")).thenReturn(config);
    when(mockServerConfigService.resolveDownstreamHost("ueqsHost"))
      .thenReturn("https://downstream");
    when(hashGenerationService.generateHash("spel:#request.path", request))
      .thenReturn("hash-1");
    when(
      recordedResponseDao.findFirstByMockServerConfigProxySaltAndRequestHash(
        "proxy-1",
        "hash-1"
      )
    )
      .thenReturn(Optional.empty());
    when(
      restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(byte[].class))
    )
      .thenReturn(ResponseEntity.ok("ok".getBytes(StandardCharsets.UTF_8)));
    doThrow(new RuntimeException("boom"))
      .when(objectMapper)
      .writeValueAsString(any(Map.class));

    IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
      service.proxyRequest(request)
    );

    assertTrue(ex.getMessage().contains("Failed to serialize response headers"));
  }

  @Test
  void proxyRequestShouldWrapHeaderDeserializationFailure() throws Exception {
    ObjectMapper objectMapper = org.mockito.Mockito.mock(ObjectMapper.class);
    ProxyService service = newService(objectMapper);
    ProxyRequest request = proxyRequest(
      "GET",
      "/proxy-1/orders",
      null,
      new HttpHeaders(),
      new byte[0]
    );
    MockServerConfig config = config("proxy-1", "ueqsHost", "spel:#request.path");
    RecordedResponse recorded = new RecordedResponse();
    recorded.setResponseStatus(200);
    recorded.setResponseBody(new byte[0]);
    recorded.setResponseHeadersJson("{}");
    recorded.setResponseContentType("application/json");

    when(mockServerConfigMatcher.findConfig("/proxy-1/orders")).thenReturn(config);
    when(mockServerConfigService.resolveDownstreamHost("ueqsHost"))
      .thenReturn("https://downstream");
    when(hashGenerationService.generateHash("spel:#request.path", request))
      .thenReturn("hash-1");
    when(
      recordedResponseDao.findFirstByMockServerConfigProxySaltAndRequestHash(
        "proxy-1",
        "hash-1"
      )
    )
      .thenReturn(Optional.of(recorded));
    doThrow(new RuntimeException("boom"))
      .when(objectMapper)
      .readValue(anyString(), any(TypeReference.class));

    IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
      service.proxyRequest(request)
    );

    assertTrue(ex.getMessage().contains("Failed to deserialize response headers"));
  }

  @Test
  void proxyRequestShouldNormalizeBlankPathWhenProxySaltMissing() {
    ObjectMapper objectMapper = new ObjectMapper();
    ProxyService service = newService(objectMapper);
    ProxyRequest request = proxyRequest(
      "GET",
      "",
      null,
      new HttpHeaders(),
      new byte[0]
    );
    MockServerConfig config = config("", "ueqsHost", "spel:#request.path");

    when(mockServerConfigMatcher.findConfig("")).thenReturn(config);
    when(mockServerConfigService.resolveDownstreamHost("ueqsHost"))
      .thenReturn("https://downstream");
    when(hashGenerationService.generateHash("spel:#request.path", request))
      .thenReturn("hash-1");
    when(
      recordedResponseDao.findFirstByMockServerConfigProxySaltAndRequestHash(
        "",
        "hash-1"
      )
    )
      .thenReturn(Optional.empty());
    when(
      restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(byte[].class))
    )
      .thenReturn(ResponseEntity.ok(new byte[] { 1 }));
    when(recordedResponseDao.save(any(RecordedResponse.class)))
      .thenAnswer(invocation -> invocation.getArgument(0));

    service.proxyRequest(request);

    ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
    verify(restTemplate).exchange(
      uriCaptor.capture(),
      eq(HttpMethod.GET),
      any(),
      eq(byte[].class)
    );
    assertEquals("https://downstream/", uriCaptor.getValue().toString());
  }

  private ProxyService newService(ObjectMapper objectMapper) {
    return new ProxyService(
      mockServerConfigMatcher,
      hashGenerationService,
      recordedResponseDao,
      restTemplate,
      objectMapper,
      mockServerConfigService
    );
  }

  private MockServerConfig config(
    String proxySalt,
    String hostKey,
    String hashGenerationFunction
  ) {
    MockServerConfig config = new MockServerConfig();
    config.setProxySalt(proxySalt);
    config.setHostKey(hostKey);
    config.setEndpointDesc("endpoint");
    config.setHashGenerationFunction(hashGenerationFunction);
    return config;
  }

  private ProxyRequest proxyRequest(
    String method,
    String path,
    String queryString,
    HttpHeaders headers,
    byte[] body
  ) {
    return new ProxyRequest(
      method,
      path,
      queryString,
      headers,
      body,
      new LinkedHashMap<>()
    );
  }
}
