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

import com.testsuites.mockserver.dto.MockServerConfig;
import com.testsuites.mockserver.service.MockServerConfigService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock-server-configs")
@RequiredArgsConstructor
public class MockServerConfigController {

  private final MockServerConfigService mockServerConfigService;

  @GetMapping
  public List<MockServerConfigResponse> list() {
    return mockServerConfigService
      .list()
      .stream()
      .map(this::toResponse)
      .toList();
  }

  @GetMapping("/{proxySalt}")
  public MockServerConfigResponse get(@PathVariable String proxySalt) {
    return toResponse(mockServerConfigService.get(proxySalt));
  }

  @PostMapping
  public ResponseEntity<MockServerConfigResponse> create(
    @RequestBody MockServerConfigRequest request
  ) {
    MockServerConfig created = mockServerConfigService.create(
      toCommand(request)
    );
    return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
  }

  @PutMapping("/{proxySalt}")
  public MockServerConfigResponse update(
    @PathVariable String proxySalt,
    @RequestBody MockServerConfigRequest request
  ) {
    MockServerConfig updated = mockServerConfigService.update(
      proxySalt,
      toCommand(request)
    );
    return toResponse(updated);
  }

  @PutMapping("/{proxySalt}/hash-generation-function")
  public MockServerConfigResponse setHashGenerationFunction(
    @PathVariable String proxySalt,
    @RequestBody HashFunctionRequest request
  ) {
    MockServerConfig updated = mockServerConfigService.updateHashFunction(
      proxySalt,
      request.hashGenerationFunction()
    );
    return toResponse(updated);
  }

  @DeleteMapping("/{proxySalt}")
  public ResponseEntity<Void> delete(@PathVariable String proxySalt) {
    mockServerConfigService.delete(proxySalt);
    return ResponseEntity.noContent().build();
  }

  private MockServerConfigService.UpsertCommand toCommand(
    MockServerConfigRequest request
  ) {
    return new MockServerConfigService.UpsertCommand(
      request.endpointDesc(),
      request.hostKey(),
      request.hashGenerationFunction()
    );
  }

  private MockServerConfigResponse toResponse(MockServerConfig config) {
    return new MockServerConfigResponse(
      config.getEndpointDesc(),
      config.getHostKey(),
      config.getProxySalt(),
      config.getHashGenerationFunction()
    );
  }

  public record MockServerConfigRequest(
    String endpointDesc,
    String hostKey,
    String hashGenerationFunction
  ) {}

  public record HashFunctionRequest(String hashGenerationFunction) {}

  public record MockServerConfigResponse(
    String endpointDesc,
    String hostKey,
    String proxySalt,
    String hashGenerationFunction
  ) {}
}
