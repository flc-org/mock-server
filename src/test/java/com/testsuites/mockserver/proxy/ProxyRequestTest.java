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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class ProxyRequestTest {

  @Test
  void constructorShouldApplyDefaultsForNullInputs() {
    ProxyRequest request = new ProxyRequest("GET", "/items", null, null, null, null);

    assertSame(HttpHeaders.EMPTY, request.getHeaders());
    assertEquals(0, request.getBody().length);
    assertTrue(request.getQueryParams().isEmpty());

    Map<String, Object> scriptObject = request.toScriptObject();
    assertEquals("", scriptObject.get("queryString"));
    assertEquals("", scriptObject.get("body"));
    assertEquals("", scriptObject.get("bodyBase64"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void toScriptObjectShouldContainHeadersBodyAndQueryParams() {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Request-Id", "a");
    headers.add("X-Request-Id", "b");
    Map<String, List<String>> queryParams = new LinkedHashMap<>();
    queryParams.put("limit", List.of("10"));
    queryParams.put("offset", List.of("0", "1"));
    byte[] body = "hello".getBytes(StandardCharsets.UTF_8);

    ProxyRequest request = new ProxyRequest(
      "POST",
      "/items",
      "limit=10&offset=0&offset=1",
      headers,
      body,
      queryParams
    );

    Map<String, Object> scriptObject = request.toScriptObject();
    Map<String, List<String>> headerMap =
      (Map<String, List<String>>) scriptObject.get("headers");
    Map<String, List<String>> queryMap =
      (Map<String, List<String>>) scriptObject.get("queryParams");

    assertEquals("POST", scriptObject.get("method"));
    assertEquals("/items", scriptObject.get("path"));
    assertEquals("limit=10&offset=0&offset=1", scriptObject.get("queryString"));
    assertEquals(List.of("a", "b"), headerMap.get("X-Request-Id"));
    assertEquals(List.of("10"), queryMap.get("limit"));
    assertEquals(List.of("0", "1"), queryMap.get("offset"));
    assertEquals("hello", scriptObject.get("body"));
    assertEquals(
      Base64.getEncoder().encodeToString(body),
      scriptObject.get("bodyBase64")
    );
  }
}
