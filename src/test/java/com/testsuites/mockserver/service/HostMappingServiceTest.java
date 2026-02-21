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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.testsuites.mockserver.dao.HostMappingDao;
import com.testsuites.mockserver.dto.HostMapping;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class HostMappingServiceTest {

  @Mock
  private HostMappingDao hostMappingDao;

  @Test
  void listShouldReturnAllMappings() {
    HostMappingService service = new HostMappingService(hostMappingDao);
    when(hostMappingDao.findAll())
      .thenReturn(List.of(mapping("serviceAHost", "https://api.service-a.example")));

    List<HostMapping> result = service.list();

    assertEquals(1, result.size());
    assertEquals("serviceAHost", result.get(0).getHostKey());
  }

  @Test
  void getShouldReturnMappingWhenFound() {
    HostMappingService service = new HostMappingService(hostMappingDao);
    when(hostMappingDao.findById("serviceAHost"))
      .thenReturn(Optional.of(mapping("serviceAHost", "https://api.service-a.example")));

    HostMapping result = service.get("serviceAHost");

    assertEquals("https://api.service-a.example", result.getHostName());
  }

  @Test
  void getShouldFailWhenMissing() {
    HostMappingService service = new HostMappingService(hostMappingDao);
    when(hostMappingDao.findById("missing")).thenReturn(Optional.empty());

    ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
      service.get("missing")
    );

    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    assertTrue(ex.getReason().contains("missing"));
  }

  @Test
  void createShouldSaveNewMapping() {
    HostMappingService service = new HostMappingService(hostMappingDao);
    when(hostMappingDao.existsById("serviceAHost")).thenReturn(false);
    when(hostMappingDao.save(any(HostMapping.class)))
      .thenAnswer(invocation -> invocation.getArgument(0));

    HostMapping created = service.create(
      new HostMappingService.UpsertCommand(
        "  serviceAHost  ",
        "  https://api.service-a.example  "
      )
    );

    assertEquals("serviceAHost", created.getHostKey());
    assertEquals("https://api.service-a.example", created.getHostName());
  }

  @Test
  void createShouldFailWhenAlreadyExists() {
    HostMappingService service = new HostMappingService(hostMappingDao);
    when(hostMappingDao.existsById("serviceAHost")).thenReturn(true);

    ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
      service.create(
        new HostMappingService.UpsertCommand(
          "serviceAHost",
          "https://api.service-a.example"
        )
      )
    );

    assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    assertTrue(ex.getReason().contains("serviceAHost"));
  }

  @Test
  void updateShouldModifyHostName() {
    HostMappingService service = new HostMappingService(hostMappingDao);
    HostMapping existing = mapping("serviceAHost", "https://old.example");
    when(hostMappingDao.findById("serviceAHost")).thenReturn(Optional.of(existing));
    when(hostMappingDao.save(any(HostMapping.class)))
      .thenAnswer(invocation -> invocation.getArgument(0));

    HostMapping updated = service.update(
      "serviceAHost",
      new HostMappingService.UpsertCommand(
        "serviceAHost",
        "  https://api.service-a.example  "
      )
    );

    assertEquals("serviceAHost", updated.getHostKey());
    assertEquals("https://api.service-a.example", updated.getHostName());
  }

  @Test
  void updateShouldFailWhenPathAndBodyHostKeyDiffer() {
    HostMappingService service = new HostMappingService(hostMappingDao);

    ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
      service.update(
        "serviceAHost",
        new HostMappingService.UpsertCommand(
          "serviceBHost",
          "https://api.service-b.example"
        )
      )
    );

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    assertTrue(ex.getReason().contains("must match"));
  }

  @Test
  void deleteShouldRemoveMapping() {
    HostMappingService service = new HostMappingService(hostMappingDao);
    HostMapping existing = mapping("serviceAHost", "https://api.service-a.example");
    when(hostMappingDao.findById("serviceAHost")).thenReturn(Optional.of(existing));

    service.delete("serviceAHost");

    verify(hostMappingDao).delete(existing);
  }

  @Test
  void createShouldFailWhenHostKeyMissing() {
    HostMappingService service = new HostMappingService(hostMappingDao);

    ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
      service.create(
        new HostMappingService.UpsertCommand(
          "  ",
          "https://api.service-a.example"
        )
      )
    );

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    assertTrue(ex.getReason().contains("hostKey is required"));
  }

  @Test
  void createShouldFailWhenHostNameMissing() {
    HostMappingService service = new HostMappingService(hostMappingDao);

    ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
      service.create(new HostMappingService.UpsertCommand("serviceAHost", "  "))
    );

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    assertTrue(ex.getReason().contains("hostName is required"));
  }

  private HostMapping mapping(String hostKey, String hostName) {
    HostMapping hostMapping = new HostMapping();
    hostMapping.setHostKey(hostKey);
    hostMapping.setHostName(hostName);
    return hostMapping;
  }
}
