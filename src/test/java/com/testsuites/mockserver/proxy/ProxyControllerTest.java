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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.testsuites.mockserver.service.ProxyService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class ProxyControllerTest {

  @Mock
  private ProxyService proxyService;

  @InjectMocks
  private ProxyController controller;

  @Test
  void proxyShouldForwardRequestDetails() {
    MockHttpServletRequest request = new MockHttpServletRequest(
      "POST",
      "/ctx/proxy/proxy-1/orders"
    );
    request.setContextPath("/ctx");
    request.setQueryString("include=1&limit=10&limit=20");
    request.addParameter("include", "1");
    request.addParameter("limit", "10", "20");

    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Req", "abc");
    byte[] body = "payload".getBytes(StandardCharsets.UTF_8);

    HttpHeaders responseHeaders = new HttpHeaders();
    responseHeaders.add("X-Resp", "ok");
    when(proxyService.proxyRequest(any()))
      .thenReturn(
        new ProxyResponse(
          201,
          responseHeaders,
          "response".getBytes(StandardCharsets.UTF_8)
        )
      );

    ResponseEntity<byte[]> response = controller.proxy(request, headers, body);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertArrayEquals(
      "response".getBytes(StandardCharsets.UTF_8),
      response.getBody()
    );
    assertEquals("ok", response.getHeaders().getFirst("X-Resp"));

    ArgumentCaptor<ProxyRequest> requestCaptor = ArgumentCaptor.forClass(
      ProxyRequest.class
    );
    verify(proxyService).proxyRequest(requestCaptor.capture());
    ProxyRequest captured = requestCaptor.getValue();
    assertEquals("POST", captured.getMethod());
    assertEquals("/proxy-1/orders", captured.getPath());
    assertEquals("include=1&limit=10&limit=20", captured.getQueryString());
    assertArrayEquals(body, captured.getBody());
    assertEquals(List.of("1"), captured.getQueryParams().get("include"));
    assertEquals(List.of("10", "20"), captured.getQueryParams().get("limit"));
  }

  @Test
  void proxyShouldUseRootWhenOnlyProxyPrefixIsPresent() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/proxy");
    HttpHeaders headers = new HttpHeaders();
    when(proxyService.proxyRequest(any()))
      .thenReturn(new ProxyResponse(200, new HttpHeaders(), new byte[0]));

    controller.proxy(request, headers, null);

    ArgumentCaptor<ProxyRequest> requestCaptor = ArgumentCaptor.forClass(
      ProxyRequest.class
    );
    verify(proxyService).proxyRequest(requestCaptor.capture());
    ProxyRequest captured = requestCaptor.getValue();
    assertEquals("/", captured.getPath());
    assertEquals(0, captured.getBody().length);
    assertTrue(captured.getQueryParams().isEmpty());
  }

  @Test
  void proxyShouldFallbackToRootWhenRequestUriDoesNotStartWithProxy() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/not-proxy");
    HttpHeaders headers = new HttpHeaders();
    when(proxyService.proxyRequest(any()))
      .thenReturn(new ProxyResponse(200, new HttpHeaders(), new byte[0]));

    controller.proxy(request, headers, new byte[0]);

    ArgumentCaptor<ProxyRequest> requestCaptor = ArgumentCaptor.forClass(
      ProxyRequest.class
    );
    verify(proxyService).proxyRequest(requestCaptor.capture());
    assertEquals("/", requestCaptor.getValue().getPath());
  }
}
