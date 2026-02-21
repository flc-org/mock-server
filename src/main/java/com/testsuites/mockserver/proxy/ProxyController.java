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
package com.testsuites.mockserver.proxy;

import com.testsuites.mockserver.service.ProxyService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/proxy")
@RequiredArgsConstructor
public class ProxyController {

  private final ProxyService proxyService;

  @RequestMapping(
    value = { "", "/", "/**" },
    method = {
      RequestMethod.GET,
      RequestMethod.POST,
      RequestMethod.PUT,
      RequestMethod.PATCH,
      RequestMethod.DELETE,
      RequestMethod.OPTIONS,
      RequestMethod.HEAD,
    }
  )
  public ResponseEntity<byte[]> proxy(
    HttpServletRequest request,
    @RequestHeader HttpHeaders headers,
    @RequestBody(required = false) byte[] body
  ) {
    String proxyPath = extractProxyPath(request);
    log.debug(
      "Incoming proxy request method={} uri={} query={}",
      request.getMethod(),
      proxyPath,
      request.getQueryString()
    );
    ProxyRequest proxyRequest = new ProxyRequest(
      request.getMethod(),
      proxyPath,
      request.getQueryString(),
      headers,
      body == null ? new byte[0] : body,
      extractQueryParams(request)
    );
    ProxyResponse response = proxyService.proxyRequest(proxyRequest);
    return new ResponseEntity<>(
      response.body(),
      response.headers(),
      HttpStatusCode.valueOf(response.status())
    );
  }

  private String extractProxyPath(HttpServletRequest request) {
    String fullPath = request.getRequestURI();
    String contextPath = request.getContextPath();
    String withoutContext = contextPath == null || contextPath.isBlank()
      ? fullPath
      : fullPath.substring(contextPath.length());
    String prefix = "/proxy";
    if (!withoutContext.startsWith(prefix)) {
      return "/";
    }
    String extracted = withoutContext.substring(prefix.length());
    return extracted.isBlank() ? "/" : extracted;
  }

  private Map<String, List<String>> extractQueryParams(
    HttpServletRequest request
  ) {
    Map<String, List<String>> queryParams = new LinkedHashMap<>();
    request
      .getParameterMap()
      .forEach((key, value) -> queryParams.put(key, Arrays.asList(value)));
    return queryParams;
  }
}
