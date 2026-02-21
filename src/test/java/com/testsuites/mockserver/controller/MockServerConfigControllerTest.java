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

import com.testsuites.mockserver.dto.MockServerConfig;
import com.testsuites.mockserver.service.MockServerConfigService;
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
class MockServerConfigControllerTest {

  @Mock
  private MockServerConfigService mockServerConfigService;

  @InjectMocks
  private MockServerConfigController controller;

  @Test
  void listShouldMapEntitiesToResponse() {
    when(mockServerConfigService.list())
      .thenReturn(List.of(config("proxy-1", "ueqsHost", "desc", "spel:#a")));

    List<MockServerConfigController.MockServerConfigResponse> response = controller.list();

    assertEquals(1, response.size());
    assertEquals("proxy-1", response.get(0).proxySalt());
    assertEquals("ueqsHost", response.get(0).hostKey());
    assertEquals("desc", response.get(0).endpointDesc());
    assertEquals("spel:#a", response.get(0).hashGenerationFunction());
  }

  @Test
  void getShouldReturnMappedResponse() {
    when(mockServerConfigService.get("proxy-1"))
      .thenReturn(config("proxy-1", "ueqsHost", "desc", "spel:#a"));

    MockServerConfigController.MockServerConfigResponse response = controller.get(
      "proxy-1"
    );

    assertEquals("proxy-1", response.proxySalt());
    assertEquals("ueqsHost", response.hostKey());
    assertEquals("desc", response.endpointDesc());
    assertEquals("spel:#a", response.hashGenerationFunction());
  }

  @Test
  void createShouldReturnCreatedResponse() {
    MockServerConfigController.MockServerConfigRequest request = new MockServerConfigController.MockServerConfigRequest(
      "desc",
      "ueqsHost",
      "spel:#a"
    );
    when(
      mockServerConfigService.create(
        eq(new MockServerConfigService.UpsertCommand("desc", "ueqsHost", "spel:#a"))
      )
    )
      .thenReturn(config("proxy-1", "ueqsHost", "desc", "spel:#a"));

    ResponseEntity<MockServerConfigController.MockServerConfigResponse> response = controller.create(
      request
    );

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertEquals("proxy-1", response.getBody().proxySalt());
    assertEquals("ueqsHost", response.getBody().hostKey());
  }

  @Test
  void updateShouldReturnMappedResponse() {
    MockServerConfigController.MockServerConfigRequest request = new MockServerConfigController.MockServerConfigRequest(
      "desc",
      "ueqsHost",
      "spel:#a"
    );
    when(
      mockServerConfigService.update(
        eq("proxy-1"),
        eq(new MockServerConfigService.UpsertCommand("desc", "ueqsHost", "spel:#a"))
      )
    )
      .thenReturn(config("proxy-1", "ueqsHost", "desc", "spel:#a"));

    MockServerConfigController.MockServerConfigResponse response = controller.update(
      "proxy-1",
      request
    );

    assertEquals("proxy-1", response.proxySalt());
    assertEquals("ueqsHost", response.hostKey());
  }

  @Test
  void setHashGenerationFunctionShouldDelegateAndMapResponse() {
    MockServerConfigController.HashFunctionRequest request = new MockServerConfigController.HashFunctionRequest(
      "spel:#a"
    );
    when(mockServerConfigService.updateHashFunction("proxy-1", "spel:#a"))
      .thenReturn(config("proxy-1", "ueqsHost", "desc", "spel:#a"));

    MockServerConfigController.MockServerConfigResponse response = controller.setHashGenerationFunction(
      "proxy-1",
      request
    );

    assertEquals("proxy-1", response.proxySalt());
    assertEquals("spel:#a", response.hashGenerationFunction());
  }

  @Test
  void deleteShouldReturnNoContent() {
    ResponseEntity<Void> response = controller.delete("proxy-1");

    verify(mockServerConfigService).delete("proxy-1");
    assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    assertNull(response.getBody());
  }

  @Test
  void createShouldPassExpectedUpsertCommand() {
    MockServerConfigController.MockServerConfigRequest request = new MockServerConfigController.MockServerConfigRequest(
      "desc",
      "ueqsHost",
      "spel:#a"
    );
    when(mockServerConfigService.create(org.mockito.ArgumentMatchers.any()))
      .thenReturn(config("proxy-1", "ueqsHost", "desc", "spel:#a"));

    controller.create(request);

    ArgumentCaptor<MockServerConfigService.UpsertCommand> captor =
      ArgumentCaptor.forClass(MockServerConfigService.UpsertCommand.class);
    verify(mockServerConfigService).create(captor.capture());
    assertEquals("desc", captor.getValue().endpointDesc());
    assertEquals("ueqsHost", captor.getValue().hostKey());
    assertEquals("spel:#a", captor.getValue().hashGenerationFunction());
  }

  private MockServerConfig config(
    String proxySalt,
    String hostKey,
    String endpointDesc,
    String hashGenerationFunction
  ) {
    MockServerConfig config = new MockServerConfig();
    config.setProxySalt(proxySalt);
    config.setHostKey(hostKey);
    config.setEndpointDesc(endpointDesc);
    config.setHashGenerationFunction(hashGenerationFunction);
    return config;
  }
}
