/*
 * Copyright 2025 Tegmentum AI
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
package ai.tegmentum.webassembly4j.bindgen.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link BindgenFunction}. */
@DisplayName("BindgenFunction Tests")
class BindgenFunctionTest {

  private static final Logger LOGGER = Logger.getLogger(BindgenFunctionTest.class.getName());

  @Nested
  @DisplayName("Builder Tests")
  class BuilderTests {

    @Test
    @DisplayName("should create function with builder and default values")
    void shouldCreateFunctionWithDefaultValues() {
      LOGGER.info("Testing builder with default values");

      BindgenFunction function = BindgenFunction.builder().name("doSomething").build();

      assertEquals("doSomething", function.getName());
      assertTrue(function.getParameters().isEmpty());
      assertTrue(function.getReturnType().isEmpty());
      assertTrue(function.getDocumentation().isEmpty());
      assertFalse(function.isAsync());
      assertFalse(function.isConstructor());
      assertFalse(function.isStatic());
      assertFalse(function.hasReturnType());
    }

    @Test
    @DisplayName("should create function with parameters using addParameter")
    void shouldCreateFunctionWithParametersUsingAddParameter() {
      LOGGER.info("Testing builder with addParameter()");

      BindgenType i32Type = BindgenType.primitive("i32");
      BindgenType stringType = BindgenType.primitive("string");
      BindgenParameter param1 = new BindgenParameter("count", i32Type);
      BindgenParameter param2 = new BindgenParameter("name", stringType);

      BindgenFunction function =
          BindgenFunction.builder().name("greet").addParameter(param1).addParameter(param2).build();

      assertEquals(2, function.getParameters().size());
      assertEquals(List.of(param1, param2), function.getParameters());
    }

    @Test
    @DisplayName("should create function with parameters using name and type shorthand")
    void shouldCreateFunctionWithParametersUsingShorthand() {
      LOGGER.info("Testing builder with addParameter(name, type) shorthand");

      BindgenType i32Type = BindgenType.primitive("i32");

      BindgenFunction function =
          BindgenFunction.builder().name("increment").addParameter("value", i32Type).build();

      assertEquals(1, function.getParameters().size());
      assertEquals("value", function.getParameters().get(0).getName());
      assertEquals(i32Type, function.getParameters().get(0).getType());
    }

    @Test
    @DisplayName("should create function with parameters() method")
    void shouldCreateFunctionWithParametersMethod() {
      LOGGER.info("Testing builder with parameters() list method");

      List<BindgenParameter> params =
          Arrays.asList(
              new BindgenParameter("a", BindgenType.primitive("i32")),
              new BindgenParameter("b", BindgenType.primitive("i32")));

      BindgenFunction function = BindgenFunction.builder().name("add").parameters(params).build();

      assertEquals(2, function.getParameters().size());
    }

    @Test
    @DisplayName("should create function with return type")
    void shouldCreateFunctionWithReturnType() {
      LOGGER.info("Testing builder with return type");

      BindgenType returnType = BindgenType.primitive("string");

      BindgenFunction function =
          BindgenFunction.builder().name("getName").returnType(returnType).build();

      assertTrue(function.getReturnType().isPresent());
      assertEquals(returnType, function.getReturnType().get());
      assertTrue(function.hasReturnType());
    }

    @Test
    @DisplayName("should create function with documentation")
    void shouldCreateFunctionWithDocumentation() {
      LOGGER.info("Testing builder with documentation");

      BindgenFunction function =
          BindgenFunction.builder()
              .name("calculate")
              .documentation("Calculates the result based on input")
              .build();

      assertTrue(function.getDocumentation().isPresent());
      assertEquals("Calculates the result based on input", function.getDocumentation().get());
    }

    @Test
    @DisplayName("should create async function")
    void shouldCreateAsyncFunction() {
      LOGGER.info("Testing builder with async flag");

      BindgenFunction function = BindgenFunction.builder().name("fetchData").async(true).build();

      assertTrue(function.isAsync());
    }

    @Test
    @DisplayName("should create constructor function")
    void shouldCreateConstructorFunction() {
      LOGGER.info("Testing builder with constructor flag");

      BindgenFunction function = BindgenFunction.builder().name("new").constructor(true).build();

      assertTrue(function.isConstructor());
    }

    @Test
    @DisplayName("should create static function")
    void shouldCreateStaticFunction() {
      LOGGER.info("Testing builder with static flag");

      BindgenFunction function =
          BindgenFunction.builder().name("getInstance").staticMethod(true).build();

      assertTrue(function.isStatic());
    }

    @Test
    @DisplayName("should create fully configured function")
    void shouldCreateFullyConfiguredFunction() {
      LOGGER.info("Testing builder with all options");

      BindgenType i32Type = BindgenType.primitive("i32");
      BindgenType stringType = BindgenType.primitive("string");

      BindgenFunction function =
          BindgenFunction.builder()
              .name("process")
              .addParameter("input", i32Type)
              .returnType(stringType)
              .documentation("Processes the input and returns a string")
              .async(true)
              .constructor(false)
              .staticMethod(true)
              .build();

      assertEquals("process", function.getName());
      assertEquals(1, function.getParameters().size());
      assertTrue(function.getReturnType().isPresent());
      assertEquals(stringType, function.getReturnType().get());
      assertTrue(function.getDocumentation().isPresent());
      assertTrue(function.isAsync());
      assertFalse(function.isConstructor());
      assertTrue(function.isStatic());
    }
  }

  @Nested
  @DisplayName("Getter Tests")
  class GetterTests {

    @Test
    @DisplayName("getName() should return function name")
    void getNameShouldReturnFunctionName() {
      BindgenFunction function = BindgenFunction.builder().name("testFunction").build();

      assertEquals("testFunction", function.getName());
    }

    @Test
    @DisplayName("getParameters() should return empty list when no parameters")
    void getParametersShouldReturnEmptyListWhenNoParameters() {
      BindgenFunction function = BindgenFunction.builder().name("noParams").build();

      assertTrue(function.getParameters().isEmpty());
    }

    @Test
    @DisplayName("getReturnType() should return empty when no return type")
    void getReturnTypeShouldReturnEmptyWhenNoReturnType() {
      BindgenFunction function = BindgenFunction.builder().name("voidFunction").build();

      assertTrue(function.getReturnType().isEmpty());
    }

    @Test
    @DisplayName("hasReturnType() should return false for void functions")
    void hasReturnTypeShouldReturnFalseForVoidFunctions() {
      BindgenFunction function = BindgenFunction.builder().name("voidFunction").build();

      assertFalse(function.hasReturnType());
    }

    @Test
    @DisplayName("hasReturnType() should return true when return type is set")
    void hasReturnTypeShouldReturnTrueWhenReturnTypeIsSet() {
      BindgenFunction function =
          BindgenFunction.builder()
              .name("returningFunction")
              .returnType(BindgenType.primitive("i32"))
              .build();

      assertTrue(function.hasReturnType());
    }
  }

  @Nested
  @DisplayName("Equals and HashCode Tests")
  class EqualsAndHashCodeTests {

    @Test
    @DisplayName("should be equal when name and parameters match")
    void shouldBeEqualWhenNameAndParametersMatch() {
      LOGGER.info("Testing equals() for matching functions");

      BindgenType i32Type = BindgenType.primitive("i32");

      BindgenFunction function1 =
          BindgenFunction.builder()
              .name("add")
              .addParameter("a", i32Type)
              .addParameter("b", i32Type)
              .build();

      BindgenFunction function2 =
          BindgenFunction.builder()
              .name("add")
              .addParameter("a", i32Type)
              .addParameter("b", i32Type)
              .build();

      assertEquals(function2, function1);
      assertEquals(function2.hashCode(), function1.hashCode());
    }

    @Test
    @DisplayName("should not be equal when names differ")
    void shouldNotBeEqualWhenNamesDiffer() {
      LOGGER.info("Testing equals() for different names");

      BindgenFunction function1 = BindgenFunction.builder().name("func1").build();
      BindgenFunction function2 = BindgenFunction.builder().name("func2").build();

      assertNotEquals(function2, function1);
    }

    @Test
    @DisplayName("should not be equal when parameters differ")
    void shouldNotBeEqualWhenParametersDiffer() {
      LOGGER.info("Testing equals() for different parameters");

      BindgenFunction function1 =
          BindgenFunction.builder()
              .name("func")
              .addParameter("a", BindgenType.primitive("i32"))
              .build();

      BindgenFunction function2 =
          BindgenFunction.builder()
              .name("func")
              .addParameter("b", BindgenType.primitive("i64"))
              .build();

      assertNotEquals(function2, function1);
    }

    @Test
    @DisplayName("should not be equal to null")
    void shouldNotBeEqualToNull() {
      BindgenFunction function = BindgenFunction.builder().name("func").build();

      assertNotEquals(null, function);
    }

    @Test
    @DisplayName("should not be equal to different class")
    void shouldNotBeEqualToDifferentClass() {
      BindgenFunction function = BindgenFunction.builder().name("func").build();

      assertNotEquals("func", function);
    }

    @Test
    @DisplayName("should be equal to itself")
    void shouldBeEqualToItself() {
      BindgenFunction function = BindgenFunction.builder().name("func").build();

      assertEquals(function, function);
    }
  }

  @Nested
  @DisplayName("ToString Tests")
  class ToStringTests {

    @Test
    @DisplayName("should include name in toString()")
    void shouldIncludeNameInToString() {
      LOGGER.info("Testing toString() output");

      BindgenFunction function = BindgenFunction.builder().name("myFunction").build();

      String toString = function.toString();

      assertTrue(
          toString.contains("name='myFunction'"),
          "Expected toString to contain: name='myFunction'");
      assertTrue(
          toString.startsWith("BindgenFunction{"),
          "Expected toString to start with: BindgenFunction{");
    }

    @Test
    @DisplayName("should include parameter count in toString()")
    void shouldIncludeParameterCountInToString() {
      BindgenFunction function =
          BindgenFunction.builder()
              .name("func")
              .addParameter("a", BindgenType.primitive("i32"))
              .addParameter("b", BindgenType.primitive("i32"))
              .build();

      String toString = function.toString();

      assertTrue(toString.contains("params=2"), "Expected toString to contain: params=2");
    }

    @Test
    @DisplayName("should include return type in toString() when present")
    void shouldIncludeReturnTypeInToStringWhenPresent() {
      BindgenFunction function =
          BindgenFunction.builder()
              .name("func")
              .returnType(BindgenType.primitive("string"))
              .build();

      String toString = function.toString();

      assertTrue(
          toString.contains("returns=string"), "Expected toString to contain: returns=string");
    }

    @Test
    @DisplayName("should not include returns when no return type")
    void shouldNotIncludeReturnsWhenNoReturnType() {
      BindgenFunction function = BindgenFunction.builder().name("func").build();

      String toString = function.toString();

      assertFalse(toString.contains("returns="), "Expected toString not to contain: returns=");
    }
  }

  @Nested
  @DisplayName("Immutability Tests")
  class ImmutabilityTests {

    @Test
    @DisplayName("parameters list should be immutable")
    void parametersListShouldBeImmutable() {
      LOGGER.info("Testing that parameters list is immutable");

      BindgenFunction function =
          BindgenFunction.builder()
              .name("func")
              .addParameter("a", BindgenType.primitive("i32"))
              .build();

      List<BindgenParameter> params = function.getParameters();

      assertThrows(
          UnsupportedOperationException.class,
          () -> params.add(new BindgenParameter("b", BindgenType.primitive("i32"))));
    }
  }
}
