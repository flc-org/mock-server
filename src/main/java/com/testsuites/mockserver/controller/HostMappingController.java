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

import com.testsuites.mockserver.dto.HostMapping;
import com.testsuites.mockserver.service.HostMappingService;
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
@RequestMapping("/host-mappings")
@RequiredArgsConstructor
public class HostMappingController {

  private final HostMappingService hostMappingService;

  @GetMapping
  public List<HostMappingResponse> list() {
    return hostMappingService.list().stream().map(this::toResponse).toList();
  }

  @GetMapping("/{hostKey}")
  public HostMappingResponse get(@PathVariable String hostKey) {
    return toResponse(hostMappingService.get(hostKey));
  }

  @PostMapping
  public ResponseEntity<HostMappingResponse> create(
    @RequestBody HostMappingRequest request
  ) {
    HostMapping created = hostMappingService.create(toCommand(request));
    return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
  }

  @PutMapping("/{hostKey}")
  public HostMappingResponse update(
    @PathVariable String hostKey,
    @RequestBody HostMappingRequest request
  ) {
    HostMapping updated = hostMappingService.update(
      hostKey,
      toCommand(request)
    );
    return toResponse(updated);
  }

  @DeleteMapping("/{hostKey}")
  public ResponseEntity<Void> delete(@PathVariable String hostKey) {
    hostMappingService.delete(hostKey);
    return ResponseEntity.noContent().build();
  }

  private HostMappingService.UpsertCommand toCommand(HostMappingRequest request) {
    return new HostMappingService.UpsertCommand(
      request.hostKey(),
      request.hostName()
    );
  }

  private HostMappingResponse toResponse(HostMapping hostMapping) {
    return new HostMappingResponse(
      hostMapping.getHostKey(),
      hostMapping.getHostName()
    );
  }

  public record HostMappingRequest(String hostKey, String hostName) {}

  public record HostMappingResponse(String hostKey, String hostName) {}
}
