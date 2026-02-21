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

import com.testsuites.mockserver.dao.MockServerConfigDao;
import com.testsuites.mockserver.dto.MockServerConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockServerConfigMatcher {

  private final MockServerConfigDao mockServerConfigDao;

  public MockServerConfig findConfig(String requestPath) {
    String normalizedPath = normalizePath(requestPath);
    String proxySalt = extractFirstPathSegment(normalizedPath);
    return mockServerConfigDao
      .findById(proxySalt)
      .orElseThrow(() -> {
        log.warn(
          "No mock_server_config for proxySalt={} path={}",
          proxySalt,
          normalizedPath
        );
        return new IllegalStateException(
          "No mock_server_config for proxySalt=" + proxySalt
        );
      });
  }

  private String extractFirstPathSegment(String normalizedPath) {
    String withoutSlash = normalizedPath.substring(1);
    if (!StringUtils.hasText(withoutSlash)) {
      throw new IllegalStateException("Invalid proxy path: missing proxySalt");
    }
    int nextSlash = withoutSlash.indexOf('/');
    return nextSlash < 0 ? withoutSlash : withoutSlash.substring(0, nextSlash);
  }

  private String normalizePath(String path) {
    if (!StringUtils.hasText(path)) {
      return "/";
    }
    return path.startsWith("/") ? path : "/" + path;
  }
}
