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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.testsuites.mockserver.proxy.ProxyRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class HashGenerationServiceTest {

  private final HashGenerationService service = new HashGenerationService();

  @Test
  void generateHashShouldFailWhenFunctionIsBlank() {
    IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
      service.generateHash("   ", sampleRequest())
    );
    assertTrue(ex.getMessage().contains("must be configured"));
  }

  @Test
  void generateHashShouldEvaluateSpelWithPrefix() {
    String hash = service.generateHash(
      "spel:#request['path'] + '-v1'",
      sampleRequest()
    );

    assertEquals("/orders-v1", hash);
  }

  @Test
  void generateHashShouldEvaluateDefaultSpelWhenPrefixMissing() {
    String hash = service.generateHash(
      "#request['method'] + ':' + #request['path']",
      sampleRequest()
    );

    assertEquals("GET:/orders", hash);
  }

  @Test
  void generateHashShouldWrapSpelEvaluationErrors() {
    IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
      service.generateHash("spel:T(java.lang.Integer).parseInt('x')", sampleRequest())
    );
    assertTrue(ex.getMessage().contains("Failed to evaluate SpEL hash function"));
  }

  @Test
  void generateHashShouldFailWhenResultIsNull() {
    IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
      service.generateHash("spel:null", sampleRequest())
    );
    assertEquals("Failed to evaluate SpEL hash function", ex.getMessage());
    assertEquals("Hash function returned null", ex.getCause().getMessage());
  }

  @Test
  void generateHashShouldFailWhenResultIsBlank() {
    IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
      service.generateHash("spel:'   '", sampleRequest())
    );
    assertEquals("Failed to evaluate SpEL hash function", ex.getMessage());
    assertEquals("Hash function returned blank value", ex.getCause().getMessage());
  }

  @Test
  void generateHashShouldEvaluateJavaScriptWithPrefix() {
    String hash = service.generateHash("js:'js-hash'", sampleRequest());

    assertEquals("js-hash", hash);
  }

  @Test
  void generateHashShouldUseHashBindingWhenScriptReturnsNull() {
    String hash = service.generateHash(
      "js:var hash = 'binding-hash'; null;",
      sampleRequest()
    );

    assertEquals("binding-hash", hash);
  }

  @Test
  void generateHashShouldWrapJavaScriptErrors() {
    IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
      service.generateHash("js:var a = ;", sampleRequest())
    );
    assertTrue(
      ex.getMessage().contains("Failed to evaluate JavaScript hash function")
    );
  }

  @Test
  void generateHashShouldFailWhenJsEngineIsUnavailable() {
    ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(new ClassLoader(null) {});
    try {
      IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
        service.generateHash("js:'x'", sampleRequest())
      );
      assertTrue(ex.getMessage().contains("JavaScript engine not found"));
    } finally {
      Thread.currentThread().setContextClassLoader(originalClassLoader);
    }
  }

  private ProxyRequest sampleRequest() {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Test", "1");
    return new ProxyRequest(
      "GET",
      "/orders",
      "include=true",
      headers,
      "body".getBytes(StandardCharsets.UTF_8),
      Map.of("include", java.util.List.of("true"))
    );
  }
}
