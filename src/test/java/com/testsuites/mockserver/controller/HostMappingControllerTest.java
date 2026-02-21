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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.testsuites.mockserver.dto.HostMapping;
import com.testsuites.mockserver.service.HostMappingService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class HostMappingControllerTest {

  @Mock
  private HostMappingService hostMappingService;

  @InjectMocks
  private HostMappingController controller;

  @Test
  void listShouldMapEntitiesToResponse() {
    when(hostMappingService.list())
      .thenReturn(List.of(mapping("serviceAHost", "https://api.service-a.example")));

    List<HostMappingController.HostMappingResponse> response = controller.list();

    assertEquals(1, response.size());
    assertEquals("serviceAHost", response.get(0).hostKey());
    assertEquals("https://api.service-a.example", response.get(0).hostName());
  }

  @Test
  void getShouldReturnMappedResponse() {
    when(hostMappingService.get("serviceAHost"))
      .thenReturn(mapping("serviceAHost", "https://api.service-a.example"));

    HostMappingController.HostMappingResponse response = controller.get(
      "serviceAHost"
    );

    assertEquals("serviceAHost", response.hostKey());
    assertEquals("https://api.service-a.example", response.hostName());
  }

  @Test
  void createShouldReturnCreatedResponse() {
    HostMappingController.HostMappingRequest request = new HostMappingController.HostMappingRequest(
      "serviceAHost",
      "https://api.service-a.example"
    );
    when(
      hostMappingService.create(
        eq(
          new HostMappingService.UpsertCommand(
            "serviceAHost",
            "https://api.service-a.example"
          )
        )
      )
    )
      .thenReturn(mapping("serviceAHost", "https://api.service-a.example"));

    ResponseEntity<HostMappingController.HostMappingResponse> response = controller.create(
      request
    );

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertEquals("serviceAHost", response.getBody().hostKey());
    assertEquals("https://api.service-a.example", response.getBody().hostName());
  }

  @Test
  void updateShouldReturnMappedResponse() {
    HostMappingController.HostMappingRequest request = new HostMappingController.HostMappingRequest(
      "serviceAHost",
      "https://api.service-a.example"
    );
    when(
      hostMappingService.update(
        eq("serviceAHost"),
        eq(
          new HostMappingService.UpsertCommand(
            "serviceAHost",
            "https://api.service-a.example"
          )
        )
      )
    )
      .thenReturn(mapping("serviceAHost", "https://api.service-a.example"));

    HostMappingController.HostMappingResponse response = controller.update(
      "serviceAHost",
      request
    );

    assertEquals("serviceAHost", response.hostKey());
    assertEquals("https://api.service-a.example", response.hostName());
  }

  @Test
  void deleteShouldReturnNoContent() {
    ResponseEntity<Void> response = controller.delete("serviceAHost");

    verify(hostMappingService).delete("serviceAHost");
    assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    assertNull(response.getBody());
  }

  @Test
  void createShouldPassExpectedUpsertCommand() {
    HostMappingController.HostMappingRequest request = new HostMappingController.HostMappingRequest(
      "serviceAHost",
      "https://api.service-a.example"
    );
    when(hostMappingService.create(org.mockito.ArgumentMatchers.any()))
      .thenReturn(mapping("serviceAHost", "https://api.service-a.example"));

    controller.create(request);

    ArgumentCaptor<HostMappingService.UpsertCommand> captor =
      ArgumentCaptor.forClass(HostMappingService.UpsertCommand.class);
    verify(hostMappingService).create(captor.capture());
    assertEquals("serviceAHost", captor.getValue().hostKey());
    assertEquals("https://api.service-a.example", captor.getValue().hostName());
  }

  private HostMapping mapping(String hostKey, String hostName) {
    HostMapping hostMapping = new HostMapping();
    hostMapping.setHostKey(hostKey);
    hostMapping.setHostName(hostName);
    return hostMapping;
  }
}
