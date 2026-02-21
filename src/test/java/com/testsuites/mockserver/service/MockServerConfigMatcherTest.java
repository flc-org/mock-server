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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.testsuites.mockserver.dao.MockServerConfigDao;
import com.testsuites.mockserver.dto.MockServerConfig;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MockServerConfigMatcherTest {

  @Mock
  private MockServerConfigDao mockServerConfigDao;

  @InjectMocks
  private MockServerConfigMatcher matcher;

  @Test
  void findConfigShouldSupportPathWithoutLeadingSlash() {
    MockServerConfig config = new MockServerConfig();
    config.setProxySalt("proxy-1");
    when(mockServerConfigDao.findById("proxy-1")).thenReturn(Optional.of(config));

    MockServerConfig result = matcher.findConfig("proxy-1/orders");

    assertSame(config, result);
  }

  @Test
  void findConfigShouldSupportSinglePathSegment() {
    MockServerConfig config = new MockServerConfig();
    config.setProxySalt("proxy-1");
    when(mockServerConfigDao.findById("proxy-1")).thenReturn(Optional.of(config));

    MockServerConfig result = matcher.findConfig("/proxy-1");

    assertSame(config, result);
  }

  @Test
  void findConfigShouldThrowWhenProxySaltMissing() {
    IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
      matcher.findConfig("/")
    );

    assertEquals("Invalid proxy path: missing proxySalt", ex.getMessage());
    verifyNoInteractions(mockServerConfigDao);
  }

  @Test
  void findConfigShouldThrowWhenPathIsBlank() {
    IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
      matcher.findConfig("   ")
    );

    assertEquals("Invalid proxy path: missing proxySalt", ex.getMessage());
    verifyNoInteractions(mockServerConfigDao);
  }

  @Test
  void findConfigShouldThrowWhenConfigMissing() {
    when(mockServerConfigDao.findById("proxy-1")).thenReturn(Optional.empty());

    IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
      matcher.findConfig("/proxy-1/orders")
    );

    assertEquals("No mock_server_config for proxySalt=proxy-1", ex.getMessage());
  }
}
