/*
 * Copyright 2026 Tegmentum AI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.tegmentum.webassembly4j.bindgen.wit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.tegmentum.webassembly4j.bindgen.BindgenException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Direct tests for {@link WitResourceBodyParser}, and specifically for the
 * fix that lifts a resource method's constructor / static / instance shape
 * onto {@link WitFunction#isConstructor()} / {@link WitFunction#isStatic()}
 * so downstream {@link
 * ai.tegmentum.webassembly4j.bindgen.model.BindgenFunction} translation
 * inherits those flags without a separate translation path.
 */
@DisplayName("WitResourceBodyParser resource-method flag lift")
class WitResourceBodyParserTest {

  @Test
  @DisplayName("constructor(...) → WitFunction with isConstructor=true, isStatic=false")
  void constructorFlagsSetCorrectly() throws BindgenException {
    final String body = "constructor(interface-name: string, num-funcs: u32);";
    final List<WitFunction> methods = WitResourceBodyParser.parse("host-provider", body);
    assertEquals(1, methods.size(), "expected exactly one parsed method");
    final WitFunction ctor = methods.get(0);
    assertEquals("constructor", ctor.getName());
    assertTrue(ctor.isConstructor(), "constructor flag must be true");
    assertFalse(ctor.isStatic(), "static flag must be false for a constructor");
    assertEquals(2, ctor.getParameters().size(), "expected 2 parsed parameters");
    // Constructor has no return type.
    assertEquals(0, ctor.getReturnTypes().size(), "constructor return-type list must be empty");
  }

  @Test
  @DisplayName("name: static func(...) → WitFunction with isStatic=true, isConstructor=false")
  void staticFuncFlagsSetCorrectly() throws BindgenException {
    final String body =
        "instantiate: static func(component-bytes: list<u8>, imports: list<import-satisfaction>) "
            + "-> result<runtime-instance, error>;";
    final List<WitFunction> methods = WitResourceBodyParser.parse("runtime-instance", body);
    assertEquals(1, methods.size());
    final WitFunction m = methods.get(0);
    assertEquals("instantiate", m.getName());
    assertFalse(m.isConstructor(), "static func must not be a constructor");
    assertTrue(m.isStatic(), "static flag must be true");
    assertEquals(2, m.getParameters().size());
    assertEquals(1, m.getReturnTypes().size(), "expected a single carried return-type expression");
  }

  @Test
  @DisplayName("name: func(...) → WitFunction with both flags false (plain instance method)")
  void instanceFuncFlagsSetCorrectly() throws BindgenException {
    final String body = "call-export: func(name: string, args: list<u8>) -> result<list<u8>, error>;";
    final List<WitFunction> methods = WitResourceBodyParser.parse("runtime-instance", body);
    assertEquals(1, methods.size());
    final WitFunction m = methods.get(0);
    assertEquals("call-export", m.getName());
    assertFalse(m.isConstructor(), "plain func must not be a constructor");
    assertFalse(m.isStatic(), "plain func must not be static");
  }

  @Test
  @DisplayName("all three shapes coexist and retain declaration order")
  void mixedShapesRoundTrip() throws BindgenException {
    final String body =
        "constructor(seed: u32);\n"
            + "reset: static func();\n"
            + "next: func() -> u32;";
    final List<WitFunction> methods = WitResourceBodyParser.parse("prng", body);
    assertEquals(3, methods.size());
    assertTrue(methods.get(0).isConstructor(), "first method must be the constructor");
    assertTrue(methods.get(1).isStatic(), "second method must be static");
    assertFalse(methods.get(2).isConstructor(), "third method is instance");
    assertFalse(methods.get(2).isStatic(), "third method is instance");
    assertEquals("constructor", methods.get(0).getName());
    assertEquals("reset", methods.get(1).getName());
    assertEquals("next", methods.get(2).getName());
  }
}
