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
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
  name = "mock_server_config",
  indexes = { @Index(name = "idx_mock_cfg_host_key", columnList = "host_key") }
)
@Getter
@Setter
public class MockServerConfig {

  @Id
  @Column(name = "proxy_salt", length = 64, nullable = false)
  private String proxySalt;

  @Column(name = "endpoint_desc", length = 255, nullable = false)
  private String endpointDesc;

  @Column(name = "host_key", length = 128, nullable = false)
  private String hostKey;

  @Lob
  @Column(name = "hash_generation_function", nullable = false)
  private String hashGenerationFunction;
}
