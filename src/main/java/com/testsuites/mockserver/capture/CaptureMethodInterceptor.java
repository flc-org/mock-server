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

import java.lang.reflect.Method;
import java.util.concurrent.Semaphore;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Aspect
@Component
public class CaptureMethodInterceptor {

  private final Semaphore semaphore = new Semaphore(1, true);
  private final CaptureAudit captureAudit;
  private final ExpressionParser expressionParser = new SpelExpressionParser();
  private final ParameterNameDiscoverer parameterNameDiscoverer =
    new DefaultParameterNameDiscoverer();

  public CaptureMethodInterceptor(CaptureAudit captureAudit) {
    this.captureAudit = captureAudit;
  }

  @Around("@annotation(captureMethod)")
  public Object serializeCaptureExecution(
    ProceedingJoinPoint joinPoint,
    CaptureMethod captureMethod
  ) throws Throwable {
    String captureKey = extractCaptureKey(
      joinPoint,
      captureMethod.captureKeyExtractor()
    );
    boolean acquired = false;
    try {
      semaphore.acquire();
      acquired = true;
      captureAudit.registerCaptureKey(captureKey);
      return joinPoint.proceed();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
        "Interrupted while waiting to execute capture method",
        ex
      );
    } finally {
      if (acquired) {
        captureAudit.clearCurrentCaptureKey();
        semaphore.release();
      }
    }
  }

  private String extractCaptureKey(
    ProceedingJoinPoint joinPoint,
    String captureKeyExtractor
  ) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
      joinPoint.getTarget(),
      method,
      joinPoint.getArgs(),
      parameterNameDiscoverer
    );
    Object evaluated = expressionParser.parseExpression(captureKeyExtractor).getValue(
      context
    );
    if (evaluated == null) {
      throw new IllegalArgumentException(
        "captureKeyExtractor evaluated to null for method " + method.getName()
      );
    }
    String captureKey = evaluated.toString();
    if (!StringUtils.hasText(captureKey)) {
      throw new IllegalArgumentException(
        "captureKeyExtractor evaluated to blank for method " + method.getName()
      );
    }
    return captureKey;
  }
}
