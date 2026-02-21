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
package com.testsuites.mockserver.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.testsuites.mockserver.proxy.ProxyRequest;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class CaptureMethodInterceptorTest {

  @Test
  void serializeCaptureExecutionShouldAllowOnlyOneCaptureAtATime()
    throws Exception {
    CaptureAudit captureAudit = new CaptureAudit();
    CaptureMethodInterceptor interceptor = new CaptureMethodInterceptor(
      captureAudit
    );
    Method method = method("captureStringKey", String.class);
    CaptureMethod captureMethod = method.getAnnotation(CaptureMethod.class);

    CountDownLatch firstStarted = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    CountDownLatch secondStarted = new CountDownLatch(1);
    AtomicInteger running = new AtomicInteger(0);
    AtomicInteger maxRunning = new AtomicInteger(0);

    ProceedingJoinPoint firstJoinPoint = joinPoint(
      method,
      new Object[] { "first-key" },
      () -> {
        assertTrue(captureAudit.hasCaptureKey("first-key"));
        int current = running.incrementAndGet();
        maxRunning.updateAndGet(previous -> Math.max(previous, current));
        firstStarted.countDown();
        releaseFirst.await(2, TimeUnit.SECONDS);
        running.decrementAndGet();
        return "first";
      }
    );

    ProceedingJoinPoint secondJoinPoint = joinPoint(
      method,
      new Object[] { "second-key" },
      () -> {
        secondStarted.countDown();
        assertTrue(captureAudit.hasCaptureKey("second-key"));
        int current = running.incrementAndGet();
        maxRunning.updateAndGet(previous -> Math.max(previous, current));
        running.decrementAndGet();
        return "second";
      }
    );

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Object> first = executor.submit(() -> {
        try {
          return interceptor.serializeCaptureExecution(
            firstJoinPoint,
            captureMethod
          );
        } catch (Throwable throwable) {
          throw new RuntimeException(throwable);
        }
      });
      assertTrue(firstStarted.await(2, TimeUnit.SECONDS));

      Future<Object> second = executor.submit(() -> {
        try {
          return interceptor.serializeCaptureExecution(
            secondJoinPoint,
            captureMethod
          );
        } catch (Throwable throwable) {
          throw new RuntimeException(throwable);
        }
      });

      assertFalse(secondStarted.await(200, TimeUnit.MILLISECONDS));
      assertFalse(second.isDone());

      releaseFirst.countDown();

      assertEquals("first", first.get(2, TimeUnit.SECONDS));
      assertEquals("second", second.get(2, TimeUnit.SECONDS));
      assertEquals(1, maxRunning.get());
      assertFalse(captureAudit.hasCaptureKey("first-key"));
      assertFalse(captureAudit.hasCaptureKey("second-key"));
    } finally {
      executor.shutdownNow();
      executor.awaitTermination(2, TimeUnit.SECONDS);
    }
  }

  @Test
  void serializeCaptureExecutionShouldResolveCaptureKeyFromProxyRequestPath()
    throws Throwable {
    CaptureAudit captureAudit = new CaptureAudit();
    CaptureMethodInterceptor interceptor = new CaptureMethodInterceptor(
      captureAudit
    );
    Method method = method("captureProxyRequest", ProxyRequest.class);
    CaptureMethod captureMethod = method.getAnnotation(CaptureMethod.class);
    ProxyRequest proxyRequest = new ProxyRequest(
      "GET",
      "/proxy-1/orders",
      null,
      new HttpHeaders(),
      new byte[0],
      new LinkedHashMap<>()
    );
    ProceedingJoinPoint joinPoint = joinPoint(
      method,
      new Object[] { proxyRequest },
      () -> {
        assertTrue(captureAudit.hasCaptureKey("/proxy-1/orders"));
        return "ok";
      }
    );

    Object result = interceptor.serializeCaptureExecution(joinPoint, captureMethod);

    assertEquals("ok", result);
    assertFalse(captureAudit.hasCaptureKey("/proxy-1/orders"));
  }

  @Test
  void serializeCaptureExecutionShouldFailWhenCaptureKeyExtractorResolvesToNull() {
    CaptureAudit captureAudit = new CaptureAudit();
    CaptureMethodInterceptor interceptor = new CaptureMethodInterceptor(
      captureAudit
    );
    Method method = method("captureNullKey", String.class);
    CaptureMethod captureMethod = method.getAnnotation(CaptureMethod.class);
    ProceedingJoinPoint joinPoint = joinPoint(
      method,
      new Object[] { "ignored" },
      () -> "ok"
    );

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
      interceptor.serializeCaptureExecution(joinPoint, captureMethod)
    );

    assertTrue(ex.getMessage().contains("evaluated to null"));
  }

  @Test
  void serializeCaptureExecutionShouldRestoreInterruptFlagWhenInterrupted() {
    CaptureAudit captureAudit = new CaptureAudit();
    CaptureMethodInterceptor interceptor = new CaptureMethodInterceptor(
      captureAudit
    );
    Method method = method("captureStringKey", String.class);
    CaptureMethod captureMethod = method.getAnnotation(CaptureMethod.class);
    ProceedingJoinPoint joinPoint = joinPoint(
      method,
      new Object[] { "interrupt-key" },
      () -> "ok"
    );

    Thread.currentThread().interrupt();
    try {
      IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
        interceptor.serializeCaptureExecution(joinPoint, captureMethod)
      );
      assertTrue(ex.getMessage().contains("Interrupted while waiting"));
      assertTrue(Thread.currentThread().isInterrupted());
      verify(joinPoint, never()).proceed();
      assertFalse(captureAudit.hasCaptureKey("interrupt-key"));
    } catch (Throwable e) {
        throw new RuntimeException(e);
    } finally {
      Thread.interrupted();
    }
  }

  @CaptureMethod(captureKeyExtractor = "#p0")
  private void captureStringKey(String captureKey) {}

  @CaptureMethod(captureKeyExtractor = "#p0.path")
  private void captureProxyRequest(ProxyRequest proxyRequest) {}

  @CaptureMethod(captureKeyExtractor = "null")
  private void captureNullKey(String captureKey) {}

  private Method method(String name, Class<?> parameterType) {
    try {
      return CaptureMethodInterceptorTest.class.getDeclaredMethod(
        name,
        parameterType
      );
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("Test setup error", e);
    }
  }

  private ProceedingJoinPoint joinPoint(
    Method method,
    Object[] args,
    ThrowingSupplier<Object> proceedSupplier
  ) {
    MethodSignature signature = mock(MethodSignature.class);
    when(signature.getMethod()).thenReturn(method);

    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    when(joinPoint.getSignature()).thenReturn(signature);
    when(joinPoint.getTarget()).thenReturn(this);
    when(joinPoint.getArgs()).thenReturn(args);
    try {
      when(joinPoint.proceed())
        .thenAnswer(invocation -> proceedSupplier.get());
    } catch (Throwable throwable) {
      throw new IllegalStateException("Test setup error", throwable);
    }
    return joinPoint;
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Throwable;
  }
}
