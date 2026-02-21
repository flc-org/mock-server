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

import com.testsuites.mockserver.proxy.ProxyRequest;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class HashGenerationService {

  private final ExpressionParser expressionParser = new SpelExpressionParser();

  public String generateHash(
    String hashGenerationFunction,
    ProxyRequest request
  ) {
    if (!StringUtils.hasText(hashGenerationFunction)) {
      throw new IllegalStateException(
        "hash_generation_function must be configured and non-blank"
      );
    }
    String function = hashGenerationFunction.trim();
    if (function.startsWith("spel:")) {
      log.debug("Using SpEL hash function.");
      return evaluateSpel(function.substring("spel:".length()), request);
    }
    if (function.startsWith("js:")) {
      log.debug("Using JavaScript hash function.");
      return evaluateJavaScript(function.substring("js:".length()), request);
    }
    log.debug("Using default SpEL hash function.");
    return evaluateSpel(function, request);
  }

  private String evaluateSpel(String expression, ProxyRequest request) {
    try {
      StandardEvaluationContext context = new StandardEvaluationContext();
      context.setVariable("request", request.toScriptObject());
      Object value = expressionParser
        .parseExpression(expression)
        .getValue(context);
      return normalizeResult(value);
    } catch (Exception ex) {
      throw new IllegalStateException(
        "Failed to evaluate SpEL hash function",
        ex
      );
    }
  }

  private String evaluateJavaScript(String script, ProxyRequest request) {
    ScriptEngineManager manager = new ScriptEngineManager();
    ScriptEngine engine = manager.getEngineByName("JavaScript");
    if (engine == null) {
      engine = manager.getEngineByName("nashorn");
    }
    if (engine == null) {
      throw new IllegalStateException(
        "JavaScript engine not found. Use a SpEL hash function or add a JS engine dependency."
      );
    }
    try {
      Bindings bindings = engine.createBindings();
      bindings.put("request", request.toScriptObject());
      Object result = engine.eval(script, bindings);
      if (result == null && bindings.containsKey("hash")) {
        result = bindings.get("hash");
      }
      return normalizeResult(result);
    } catch (Exception ex) {
      throw new IllegalStateException(
        "Failed to evaluate JavaScript hash function",
        ex
      );
    }
  }

  private String normalizeResult(Object result) {
    if (result == null) {
      throw new IllegalStateException("Hash function returned null");
    }
    String asString = String.valueOf(result);
    if (!StringUtils.hasText(asString)) {
      throw new IllegalStateException("Hash function returned blank value");
    }
    return asString;
  }
}
