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

import com.testsuites.mockserver.dao.HostMappingDao;
import com.testsuites.mockserver.dao.MockServerConfigDao;
import com.testsuites.mockserver.dto.HostMapping;
import com.testsuites.mockserver.dto.MockServerConfig;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MockServerConfigService {

  private final MockServerConfigDao mockServerConfigDao;
  private final HostMappingDao hostMappingDao;

  public List<MockServerConfig> list() {
    return mockServerConfigDao.findAll();
  }

  public MockServerConfig get(String proxySalt) {
    return findOrThrow(proxySalt);
  }

  public MockServerConfig create(UpsertCommand command) {
    validateInput(
      command.endpointDesc(),
      command.hostKey(),
      command.hashGenerationFunction()
    );
    MockServerConfig entity = new MockServerConfig();
    entity.setProxySalt(generateProxySalt());
    applyCommand(entity, command);
    return mockServerConfigDao.save(entity);
  }

  public MockServerConfig update(String proxySalt, UpsertCommand command) {
    validateInput(
      command.endpointDesc(),
      command.hostKey(),
      command.hashGenerationFunction()
    );
    MockServerConfig entity = findOrThrow(proxySalt);
    applyCommand(entity, command);
    return mockServerConfigDao.save(entity);
  }

  public MockServerConfig updateHashFunction(
    String proxySalt,
    String hashGenerationFunction
  ) {
    if (!StringUtils.hasText(hashGenerationFunction)) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "hashGenerationFunction is required"
      );
    }
    MockServerConfig entity = findOrThrow(proxySalt);
    entity.setHashGenerationFunction(hashGenerationFunction.trim());
    return mockServerConfigDao.save(entity);
  }

  public void delete(String proxySalt) {
    MockServerConfig entity = findOrThrow(proxySalt);
    mockServerConfigDao.delete(entity);
  }

  public String resolveDownstreamHost(String hostKey) {
    String normalizedHostKey = hostKey == null ? null : hostKey.trim();
    if (!StringUtils.hasText(normalizedHostKey)) {
      throw new IllegalStateException(
        "No downstream host configured for hostKey=" + hostKey
      );
    }
    HostMapping hostMapping = hostMappingDao
      .findById(normalizedHostKey)
      .orElseThrow(() ->
        new IllegalStateException(
          "No downstream host configured for hostKey=" + hostKey
        )
      );
    String resolved = hostMapping.getHostName();
    if (!StringUtils.hasText(resolved)) {
      throw new IllegalStateException(
        "No downstream host configured for hostKey=" + hostKey
      );
    }
    return resolved.trim();
  }

  private MockServerConfig findOrThrow(String proxySalt) {
    return mockServerConfigDao
      .findById(proxySalt)
      .orElseThrow(() ->
        new ResponseStatusException(
          HttpStatus.NOT_FOUND,
          "mock_server_config not found for proxySalt=" + proxySalt
        )
      );
  }

  private void applyCommand(MockServerConfig entity, UpsertCommand command) {
    entity.setEndpointDesc(command.endpointDesc().trim());
    entity.setHostKey(command.hostKey().trim());
    entity.setHashGenerationFunction(command.hashGenerationFunction().trim());
  }

  private void validateInput(
    String endpointDesc,
    String hostKey,
    String hashGenerationFunction
  ) {
    if (!StringUtils.hasText(endpointDesc)) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "endpointDesc is required"
      );
    }
    if (!StringUtils.hasText(hostKey)) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "hostKey is required"
      );
    }
    if (!StringUtils.hasText(hashGenerationFunction)) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "hashGenerationFunction is required"
      );
    }
  }

  private String generateProxySalt() {
    while (true) {
      String candidate =
        "proxy-" +
        UUID.randomUUID()
          .toString()
          .replace("-", "")
          .substring(0, 10)
          .toLowerCase(Locale.ROOT);
      if (!mockServerConfigDao.existsByProxySalt(candidate)) {
        return candidate;
      }
    }
  }

  public record UpsertCommand(
    String endpointDesc,
    String hostKey,
    String hashGenerationFunction
  ) {}
}
