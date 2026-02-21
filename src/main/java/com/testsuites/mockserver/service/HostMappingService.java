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
import com.testsuites.mockserver.dto.HostMapping;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class HostMappingService {

  private final HostMappingDao hostMappingDao;

  public List<HostMapping> list() {
    return hostMappingDao.findAll();
  }

  public HostMapping get(String hostKey) {
    return findOrThrow(hostKey);
  }

  public HostMapping create(UpsertCommand command) {
    validate(command.hostKey(), command.hostName());
    String normalizedHostKey = command.hostKey().trim();
    if (hostMappingDao.existsById(normalizedHostKey)) {
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "host_mapping already exists for hostKey=" + normalizedHostKey
      );
    }
    HostMapping hostMapping = new HostMapping();
    hostMapping.setHostKey(normalizedHostKey);
    hostMapping.setHostName(command.hostName().trim());
    return hostMappingDao.save(hostMapping);
  }

  public HostMapping update(String hostKey, UpsertCommand command) {
    validate(command.hostKey(), command.hostName());
    String normalizedPathHostKey = normalizeHostKey(hostKey);
    String normalizedBodyHostKey = command.hostKey().trim();
    if (!normalizedPathHostKey.equals(normalizedBodyHostKey)) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Path hostKey and body hostKey must match"
      );
    }
    HostMapping hostMapping = findOrThrow(normalizedPathHostKey);
    hostMapping.setHostName(command.hostName().trim());
    return hostMappingDao.save(hostMapping);
  }

  public void delete(String hostKey) {
    HostMapping existing = findOrThrow(hostKey);
    hostMappingDao.delete(existing);
  }

  private HostMapping findOrThrow(String hostKey) {
    String normalizedHostKey = normalizeHostKey(hostKey);
    return hostMappingDao
      .findById(normalizedHostKey)
      .orElseThrow(() ->
        new ResponseStatusException(
          HttpStatus.NOT_FOUND,
          "host_mapping not found for hostKey=" + normalizedHostKey
        )
      );
  }

  private void validate(String hostKey, String hostName) {
    if (!StringUtils.hasText(hostKey)) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "hostKey is required"
      );
    }
    if (!StringUtils.hasText(hostName)) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "hostName is required"
      );
    }
  }

  private String normalizeHostKey(String hostKey) {
    if (!StringUtils.hasText(hostKey)) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "hostKey is required"
      );
    }
    return hostKey.trim();
  }

  public record UpsertCommand(String hostKey, String hostName) {}
}
