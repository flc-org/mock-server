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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.Getter;
import org.springframework.http.HttpHeaders;

@Getter
public class ProxyRequest {

  private final String method;
  private final String path;
  private final String queryString;
  private final HttpHeaders headers;
  private final byte[] body;
  private final Map<String, List<String>> queryParams;

  public ProxyRequest(
    String method,
    String path,
    String queryString,
    HttpHeaders headers,
    byte[] body,
    Map<String, List<String>> queryParams
  ) {
    this.method = method;
    this.path = path;
    this.queryString = queryString;
    this.headers = headers == null ? HttpHeaders.EMPTY : headers;
    this.body = body == null ? new byte[0] : body;
    this.queryParams = queryParams == null
      ? Collections.emptyMap()
      : queryParams;
  }

  public Map<String, Object> toScriptObject() {
    Map<String, Object> object = new TreeMap<>();
    Map<String, List<String>> headerMap = new LinkedHashMap<>();
    headers.forEach((name, values) -> headerMap.put(name, List.copyOf(values)));
    object.put("method", method);
    object.put("path", path);
    object.put("queryString", queryString == null ? "" : queryString);
    object.put("queryParams", queryParams);
    object.put("headers", headerMap);
    object.put("body", new String(body, StandardCharsets.UTF_8));
    object.put("bodyBase64", Base64.getEncoder().encodeToString(body));
    return object;
  }
}
