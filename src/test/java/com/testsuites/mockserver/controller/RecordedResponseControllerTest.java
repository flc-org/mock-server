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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.testsuites.mockserver.dao.RecordedResponseDao;
import com.testsuites.mockserver.dto.RecordedResponse;
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
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RecordedResponseControllerTest {

  @Mock
  private RecordedResponseDao recordedResponseDao;

  @InjectMocks
  private RecordedResponseController controller;

  @Test
  void listShouldReturnPagedFilteredRows() {
    RecordedResponse match = record(
      10L,
      "proxy-1",
      "https://service-a/orders",
      "{\"order\":\"ABC\"}",
      "{\"X-Req\":[\"abc\"]}"
    );
    when(recordedResponseDao.findAll(any(Specification.class), any(Pageable.class)))
      .thenReturn(new PageImpl<>(List.of(match), PageRequest.of(2, 5), 12));

    RecordedResponseController.PagedResult<RecordedResponseController.RecordedResponseView> response = controller.list(
      "proxy-1",
      "service-a",
      "abc",
      "x-req",
      2,
      5
    );

    assertEquals(1, response.content().size());
    assertEquals(10L, response.content().get(0).id());
    assertEquals("proxy-1", response.content().get(0).mockServerConfigProxySalt());
    assertEquals(2, response.pageNumber());
    assertEquals(5, response.pageSize());
    assertEquals(11L, response.totalElements());
    assertEquals(3, response.totalPages());

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(
      Pageable.class
    );
    verify(recordedResponseDao).findAll(any(Specification.class), pageableCaptor.capture());
    assertEquals(2, pageableCaptor.getValue().getPageNumber());
    assertEquals(5, pageableCaptor.getValue().getPageSize());
  }

  @Test
  void deleteShouldRemoveSelectedIdsAndReturnDeletedCount() {
    when(recordedResponseDao.countByIdIn(List.of(10L, 5L))).thenReturn(2L);

    RecordedResponseController.DeleteRecordedResponsesResult response = controller.delete(
      new RecordedResponseController.DeleteRecordedResponsesRequest(
        List.of(10L, 10L, 5L)
      )
    );

    assertEquals(2L, response.deletedCount());
    verify(recordedResponseDao).deleteAllByIdInBatch(List.of(10L, 5L));
  }

  @Test
  void deleteShouldRejectEmptyIds() {
    ResponseStatusException ex = assertThrows(
      ResponseStatusException.class,
      () ->
        controller.delete(
          new RecordedResponseController.DeleteRecordedResponsesRequest(
            List.of()
          )
        )
    );

    assertEquals(400, ex.getStatusCode().value());
  }

  private RecordedResponse record(
    Long id,
    String proxySalt,
    String targetUrl,
    String requestBody,
    String requestHeadersJson
  ) {
    RecordedResponse record = new RecordedResponse();
    record.setId(id);
    record.setHttpMethod("POST");
    record.setTargetUrl(targetUrl);
    record.setRequestHash("hash-" + id);
    record.setMockServerConfigProxySalt(proxySalt);
    record.setResponseStatus(200);
    record.setResponseContentType("application/json");
    record.setResponseBody(new byte[0]);
    record.setResponseHeadersJson("{}");
    record.setRequestBody(requestBody);
    record.setRequestHeadersJson(requestHeadersJson);
    return record;
  }
}
