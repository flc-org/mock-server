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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.testsuites.mockserver.dao.HostMappingDao;
import com.testsuites.mockserver.dao.MockServerConfigDao;
import com.testsuites.mockserver.dto.HostMapping;
import com.testsuites.mockserver.dto.MockServerConfig;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MockServerConfigServiceTest {

  @Mock
  private MockServerConfigDao mockServerConfigDao;

  @Mock
  private HostMappingDao hostMappingDao;

  private MockServerConfigService service;

  @BeforeEach
  void setUp() {
    service = new MockServerConfigService(mockServerConfigDao, hostMappingDao);
  }

  @Test
  void listShouldReturnAllConfigs() {
    when(mockServerConfigDao.findAll())
      .thenReturn(List.of(config("proxy-1", "serviceAHost")));

    List<MockServerConfig> configs = service.list();

    assertEquals(1, configs.size());
    assertEquals("proxy-1", configs.get(0).getProxySalt());
  }

  @Test
  void getShouldReturnConfigWhenFound() {
    when(mockServerConfigDao.findById("proxy-1"))
      .thenReturn(Optional.of(config("proxy-1", "serviceAHost")));

    MockServerConfig result = service.get("proxy-1");

    assertEquals("proxy-1", result.getProxySalt());
  }

  @Test
  void getShouldThrowNotFoundWhenMissing() {
    when(mockServerConfigDao.findById("proxy-1")).thenReturn(Optional.empty());

    ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
      service.get("proxy-1")
    );

    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    assertTrue(ex.getReason().contains("proxy-1"));
  }

  @Test
  void createShouldGenerateProxySaltRetryOnCollisionAndTrimFields() {
    when(mockServerConfigDao.existsByProxySalt(anyString()))
      .thenReturn(true)
      .thenReturn(false);
    when(mockServerConfigDao.save(any(MockServerConfig.class)))
      .thenAnswer(invocation -> invocation.getArgument(0));

    MockServerConfig created = service.create(
      new MockServerConfigService.UpsertCommand(
        "  endpoint  ",
        "  serviceAHost  ",
        "  spel:#request.path  "
      )
    );

    assertNotNull(created.getProxySalt());
    assertTrue(created.getProxySalt().matches("^proxy-[a-z0-9]{10}$"));
    assertEquals("endpoint", created.getEndpointDesc());
    assertEquals("serviceAHost", created.getHostKey());
    assertEquals("spel:#request.path", created.getHashGenerationFunction());
    verify(mockServerConfigDao, times(2)).existsByProxySalt(anyString());
  }

  @Test
  void updateShouldTrimAndSaveExistingEntity() {
    MockServerConfig existing = config("proxy-1", "serviceAHost");
    when(mockServerConfigDao.findById("proxy-1")).thenReturn(Optional.of(existing));
    when(mockServerConfigDao.save(any(MockServerConfig.class)))
      .thenAnswer(invocation -> invocation.getArgument(0));

    MockServerConfig updated = service.update(
      "proxy-1",
      new MockServerConfigService.UpsertCommand(
        "  endpoint  ",
        "  serviceAHost  ",
        "  spel:#request.path  "
      )
    );

    assertEquals("proxy-1", updated.getProxySalt());
    assertEquals("endpoint", updated.getEndpointDesc());
    assertEquals("serviceAHost", updated.getHostKey());
    assertEquals("spel:#request.path", updated.getHashGenerationFunction());
  }

  @Test
  void updateHashFunctionShouldRejectBlankValue() {
    ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
      service.updateHashFunction("proxy-1", "   ")
    );

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    assertTrue(ex.getReason().contains("hashGenerationFunction is required"));
  }

  @Test
  void updateHashFunctionShouldTrimAndSave() {
    MockServerConfig existing = config("proxy-1", "serviceAHost");
    existing.setHashGenerationFunction("old");
    when(mockServerConfigDao.findById("proxy-1")).thenReturn(Optional.of(existing));
    when(mockServerConfigDao.save(any(MockServerConfig.class)))
      .thenAnswer(invocation -> invocation.getArgument(0));

    MockServerConfig updated = service.updateHashFunction("proxy-1", "  spel:#a  ");

    assertEquals("spel:#a", updated.getHashGenerationFunction());
  }

  @Test
  void deleteShouldRemoveConfigWhenFound() {
    MockServerConfig existing = config("proxy-1", "serviceAHost");
    when(mockServerConfigDao.findById("proxy-1")).thenReturn(Optional.of(existing));

    service.delete("proxy-1");

    verify(mockServerConfigDao).delete(existing);
  }

  @Test
  void createShouldRejectBlankEndpointDesc() {
    ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
      service.create(
        new MockServerConfigService.UpsertCommand(" ", "host", "spel:#a")
      )
    );
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    assertTrue(ex.getReason().contains("endpointDesc is required"));
  }

  @Test
  void createShouldRejectBlankHostKey() {
    ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
      service.create(
        new MockServerConfigService.UpsertCommand("endpoint", " ", "spel:#a")
      )
    );
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    assertTrue(ex.getReason().contains("hostKey is required"));
  }

  @Test
  void createShouldRejectBlankHashFunction() {
    ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
      service.create(
        new MockServerConfigService.UpsertCommand("endpoint", "host", " ")
      )
    );
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    assertTrue(ex.getReason().contains("hashGenerationFunction is required"));
  }

  @Test
  void resolveDownstreamHostShouldReturnTrimmedHost() {
    when(hostMappingDao.findById("serviceAHost"))
      .thenReturn(Optional.of(hostMapping("serviceAHost", "  https://api.service-a.example  ")));

    String resolved = service.resolveDownstreamHost("serviceAHost");

    assertEquals("https://api.service-a.example", resolved);
  }

  @Test
  void resolveDownstreamHostShouldFailWhenMissing() {
    when(hostMappingDao.findById("missing")).thenReturn(Optional.empty());

    IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
      service.resolveDownstreamHost("missing")
    );

    assertEquals("No downstream host configured for hostKey=missing", ex.getMessage());
  }

  @Test
  void resolveDownstreamHostShouldFailWhenBlank() {
    IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
      service.resolveDownstreamHost("  ")
    );

    assertEquals("No downstream host configured for hostKey=  ", ex.getMessage());
  }

  private MockServerConfig config(String proxySalt, String hostKey) {
    MockServerConfig config = new MockServerConfig();
    config.setProxySalt(proxySalt);
    config.setEndpointDesc("endpoint");
    config.setHostKey(hostKey);
    config.setHashGenerationFunction("spel:#request.path");
    return config;
  }

  private HostMapping hostMapping(String hostKey, String hostName) {
    HostMapping hostMapping = new HostMapping();
    hostMapping.setHostKey(hostKey);
    hostMapping.setHostName(hostName);
    return hostMapping;
  }
}
