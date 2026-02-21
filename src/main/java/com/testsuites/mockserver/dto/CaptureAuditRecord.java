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
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
  name = "capture_audit",
  indexes = {
    @Index(name = "idx_capture_audit_capture_key", columnList = "capture_key"),
    @Index(
      name = "idx_capture_audit_request_date_time",
      columnList = "request_date_time"
    ),
  }
)
@Getter
@Setter
public class CaptureAuditRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "capture_key", nullable = false, length = 512)
  private String captureKey;

  @Column(name = "proxy_salt", nullable = false, length = 64)
  private String proxySalt;

  @Lob
  @Column(name = "req", nullable = false)
  private String req;

  @Column(name = "request_date_time", nullable = false)
  private LocalDateTime requestDateTime;
}
