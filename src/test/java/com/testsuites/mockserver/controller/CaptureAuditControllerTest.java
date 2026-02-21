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
package com.testsuites.mockserver.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.testsuites.mockserver.dao.CaptureAuditDao;
import com.testsuites.mockserver.dto.CaptureAuditRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class CaptureAuditControllerTest {

  @Mock
  private CaptureAuditDao captureAuditDao;

  @InjectMocks
  private CaptureAuditController controller;

  @Test
  void listShouldReturnPagedRows() {
    CaptureAuditRecord first = record(
      2L,
      "capture-B",
      "proxy-2",
      "{\"x\":2}",
      LocalDateTime.of(2026, 2, 19, 2, 30, 0)
    );
    when(captureAuditDao.findAll(any(Specification.class), any(Pageable.class)))
      .thenReturn(new PageImpl<>(List.of(first), PageRequest.of(1, 10), 21));

    CaptureAuditController.PagedResult<CaptureAuditController.CaptureAuditResponse> response = controller.list(
      "capture-B",
      "proxy-2",
      1,
      10
    );

    assertEquals(1, response.content().size());
    assertEquals(2L, response.content().get(0).id());
    assertEquals("capture-B", response.content().get(0).captureKey());
    assertEquals("proxy-2", response.content().get(0).proxySalt());
    assertEquals(1, response.pageNumber());
    assertEquals(10, response.pageSize());
    assertEquals(21L, response.totalElements());
    assertEquals(3, response.totalPages());

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(
      Pageable.class
    );
    verify(captureAuditDao).findAll(any(Specification.class), pageableCaptor.capture());
    assertEquals(1, pageableCaptor.getValue().getPageNumber());
    assertEquals(10, pageableCaptor.getValue().getPageSize());
  }

  private CaptureAuditRecord record(
    Long id,
    String captureKey,
    String proxySalt,
    String req,
    LocalDateTime requestDateTime
  ) {
    CaptureAuditRecord record = new CaptureAuditRecord();
    record.setId(id);
    record.setCaptureKey(captureKey);
    record.setProxySalt(proxySalt);
    record.setReq(req);
    record.setRequestDateTime(requestDateTime);
    return record;
  }
}
