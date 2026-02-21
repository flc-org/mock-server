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

import com.testsuites.mockserver.dao.RecordedResponseDao;
import com.testsuites.mockserver.dto.RecordedResponse;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/recorded-responses")
@RequiredArgsConstructor
public class RecordedResponseController {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 200;

  private final RecordedResponseDao recordedResponseDao;

  @GetMapping
  public PagedResult<RecordedResponseView> list(
    @RequestParam(required = false) String proxySalt,
    @RequestParam(required = false) String urlContains,
    @RequestParam(required = false) String requestBodyContains,
    @RequestParam(required = false) String headersContains,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size
  ) {
    Pageable pageable = PageRequest.of(
      normalizePage(page),
      normalizePageSize(size),
      Sort.by(Sort.Order.desc("id"))
    );

    Specification<RecordedResponse> specification = Specification
      .where(equalsIgnoreCase("mockServerConfigProxySalt", proxySalt))
      .and(containsIgnoreCase("targetUrl", urlContains))
      .and(contains("requestBody", requestBodyContains))
      .and(contains("requestHeadersJson", headersContains));

    Page<RecordedResponse> records = recordedResponseDao.findAll(
      specification,
      pageable
    );

    List<RecordedResponseView> content = records
      .stream()
      .map(this::toView)
      .toList();

    return new PagedResult<>(
      content,
      records.getNumber(),
      records.getSize(),
      records.getTotalElements(),
      records.getTotalPages(),
      records.isFirst(),
      records.isLast()
    );
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.OK)
  public DeleteRecordedResponsesResult delete(
    @RequestBody DeleteRecordedResponsesRequest request
  ) {
    List<Long> ids = normalizeIds(request);
    long deletedCount = recordedResponseDao.countByIdIn(ids);
    if (deletedCount > 0) {
      recordedResponseDao.deleteAllByIdInBatch(ids);
    }
    return new DeleteRecordedResponsesResult(deletedCount);
  }

  private RecordedResponseView toView(RecordedResponse record) {
    return new RecordedResponseView(
      record.getId(),
      record.getHttpMethod(),
      record.getTargetUrl(),
      record.getRequestHash(),
      record.getMockServerConfigProxySalt(),
      record.getResponseStatus(),
      record.getResponseContentType(),
      record.getRequestBody(),
      record.getRequestHeadersJson()
    );
  }

  private List<Long> normalizeIds(DeleteRecordedResponsesRequest request) {
    if (request == null || request.ids() == null || request.ids().isEmpty()) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "ids must not be empty"
      );
    }
    List<Long> ids = request
      .ids()
      .stream()
      .filter(id -> id != null && id > 0)
      .collect(
        java.util.stream.Collectors.collectingAndThen(
          java.util.stream.Collectors.toCollection(LinkedHashSet::new),
          List::copyOf
        )
      );
    if (ids.isEmpty()) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "ids must not be empty"
      );
    }
    return ids;
  }

  private Specification<RecordedResponse> equalsIgnoreCase(
    String fieldName,
    String filter
  ) {
    if (!StringUtils.hasText(filter)) {
      return null;
    }
    String normalized = filter.trim().toLowerCase(Locale.ROOT);
    return (root, query, cb) ->
      cb.equal(cb.lower(root.get(fieldName)), normalized);
  }

  private Specification<RecordedResponse> containsIgnoreCase(
    String fieldName,
    String filter
  ) {
    if (!StringUtils.hasText(filter)) {
      return null;
    }
    String normalized = "%" + filter.trim().toLowerCase(Locale.ROOT) + "%";
    return (root, query, cb) ->
      cb.like(cb.lower(cb.coalesce(root.get(fieldName), "")), normalized);
  }

  private Specification<RecordedResponse> contains(
    String fieldName,
    String filter
  ) {
    if (!StringUtils.hasText(filter)) {
      return null;
    }
    String normalized = "%" + filter.trim() + "%";
    return (root, query, cb) -> cb.like(root.get(fieldName), normalized);
  }

  private int normalizePage(int page) {
    return Math.max(page, 0);
  }

  private int normalizePageSize(int size) {
    if (size <= 0) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(size, MAX_PAGE_SIZE);
  }

  public record RecordedResponseView(
    Long id,
    String httpMethod,
    String targetUrl,
    String requestHash,
    String mockServerConfigProxySalt,
    int responseStatus,
    String responseContentType,
    String requestBody,
    String requestHeadersJson
  ) {}

  public record DeleteRecordedResponsesRequest(List<Long> ids) {}

  public record DeleteRecordedResponsesResult(long deletedCount) {}

  public record PagedResult<T>(
    List<T> content,
    int pageNumber,
    int pageSize,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last
  ) {}
}
