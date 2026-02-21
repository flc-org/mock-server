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

import com.testsuites.mockserver.dao.CaptureAuditDao;
import com.testsuites.mockserver.dto.CaptureAuditRecord;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/capture-audits")
@RequiredArgsConstructor
public class CaptureAuditController {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 200;

  private final CaptureAuditDao captureAuditDao;

  @GetMapping
  public PagedResult<CaptureAuditResponse> list(
    @RequestParam(required = false) String captureKey,
    @RequestParam(required = false) String proxySalt,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size
  ) {
    Pageable pageable = PageRequest.of(
      normalizePage(page),
      normalizePageSize(size),
      Sort.by(
        Sort.Order.desc("requestDateTime"),
        Sort.Order.desc("id")
      )
    );

    Specification<CaptureAuditRecord> specification = Specification
      .where(equalsIgnoreCase("captureKey", captureKey))
      .and(equalsIgnoreCase("proxySalt", proxySalt));

    Page<CaptureAuditRecord> records = captureAuditDao.findAll(
      specification,
      pageable
    );

    List<CaptureAuditResponse> content = records
      .stream()
      .map(record ->
        new CaptureAuditResponse(
          record.getId(),
          record.getCaptureKey(),
          record.getProxySalt(),
          record.getReq(),
          record.getRequestDateTime()
        )
      )
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

  private Specification<CaptureAuditRecord> equalsIgnoreCase(
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

  private int normalizePage(int page) {
    return Math.max(page, 0);
  }

  private int normalizePageSize(int size) {
    if (size <= 0) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(size, MAX_PAGE_SIZE);
  }

  public record CaptureAuditResponse(
    Long id,
    String captureKey,
    String proxySalt,
    String req,
    LocalDateTime requestDateTime
  ) {}

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
