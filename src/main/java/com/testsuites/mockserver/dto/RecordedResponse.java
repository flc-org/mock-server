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
package com.testsuites.mockserver.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
  name = "recorded_response",
  indexes = {
    @Index(
      name = "idx_recorded_response_cfg_hash",
      columnList = "mock_server_config_proxy_salt,request_hash"
    ),
  }
)
@Getter
@Setter
public class RecordedResponse {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "http_method", nullable = false, length = 16)
  private String httpMethod;

  @Column(name = "target_url", nullable = false, length = 2048)
  private String targetUrl;

  @Column(name = "request_hash", nullable = false, length = 64)
  private String requestHash;

  @Column(name = "mock_server_config_proxy_salt", nullable = false, length = 64)
  private String mockServerConfigProxySalt;

  @Column(name = "response_status", nullable = false)
  private int responseStatus;

  @Column(name = "response_content_type", length = 255)
  private String responseContentType;

  @Lob
  @Column(name = "response_body", nullable = false)
  private byte[] responseBody;

  @Lob
  @Column(name = "response_headers_json", nullable = false)
  private String responseHeadersJson;

  @Lob
  @Column(name = "request_body")
  private String requestBody;

  @Lob
  @Column(name = "request_headers_json")
  private String requestHeadersJson;
}
