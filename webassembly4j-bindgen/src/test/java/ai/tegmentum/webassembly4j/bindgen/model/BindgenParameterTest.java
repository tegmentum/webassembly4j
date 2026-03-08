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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link BindgenParameter}. */
@DisplayName("BindgenParameter Tests")
class BindgenParameterTest {

  private static final Logger LOGGER = Logger.getLogger(BindgenParameterTest.class.getName());

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("should create parameter with valid name and type")
    void shouldCreateParameterWithValidNameAndType() {
      LOGGER.info("Testing constructor with valid parameters");

      BindgenType type = BindgenType.primitive("i32");
      BindgenParameter param = new BindgenParameter("count", type);

      assertEquals("count", param.getName());
      assertEquals(type, param.getType());
    }

    @Test
    @DisplayName("should throw NullPointerException when name is null")
    void shouldThrowWhenNameIsNull() {
      LOGGER.info("Testing constructor with null name");

      BindgenType type = BindgenType.primitive("i32");

      NullPointerException exception =
          assertThrows(NullPointerException.class, () -> new BindgenParameter(null, type));
      assertTrue(exception.getMessage().contains("name"), "Expected message to contain: name");
    }

    @Test
    @DisplayName("should throw NullPointerException when type is null")
    void shouldThrowWhenTypeIsNull() {
      LOGGER.info("Testing constructor with null type");

      NullPointerException exception =
          assertThrows(NullPointerException.class, () -> new BindgenParameter("param", null));
      assertTrue(exception.getMessage().contains("type"), "Expected message to contain: type");
    }
  }

  @Nested
  @DisplayName("Getter Tests")
  class GetterTests {

    @Test
    @DisplayName("getName() should return parameter name")
    void getNameShouldReturnParameterName() {
      BindgenParameter param = new BindgenParameter("value", BindgenType.primitive("string"));

      assertEquals("value", param.getName());
    }

    @Test
    @DisplayName("getType() should return parameter type")
    void getTypeShouldReturnParameterType() {
      BindgenType type = BindgenType.primitive("u64");
      BindgenParameter param = new BindgenParameter("offset", type);

      assertEquals(type, param.getType());
      assertEquals("u64", param.getType().getName());
    }
  }

  @Nested
  @DisplayName("Equals and HashCode Tests")
  class EqualsAndHashCodeTests {

    @Test
    @DisplayName("should be equal when name and type match")
    void shouldBeEqualWhenNameAndTypeMatch() {
      LOGGER.info("Testing equals() for matching parameters");

      BindgenType type = BindgenType.primitive("i32");
      BindgenParameter param1 = new BindgenParameter("value", type);
      BindgenParameter param2 = new BindgenParameter("value", type);

      assertEquals(param2, param1);
      assertEquals(param2.hashCode(), param1.hashCode());
    }

    @Test
    @DisplayName("should not be equal when names differ")
    void shouldNotBeEqualWhenNamesDiffer() {
      LOGGER.info("Testing equals() for different names");

      BindgenType type = BindgenType.primitive("i32");
      BindgenParameter param1 = new BindgenParameter("value1", type);
      BindgenParameter param2 = new BindgenParameter("value2", type);

      assertNotEquals(param2, param1);
    }

    @Test
    @DisplayName("should not be equal when types differ")
    void shouldNotBeEqualWhenTypesDiffer() {
      LOGGER.info("Testing equals() for different types");

      BindgenParameter param1 = new BindgenParameter("value", BindgenType.primitive("i32"));
      BindgenParameter param2 = new BindgenParameter("value", BindgenType.primitive("i64"));

      assertNotEquals(param2, param1);
    }

    @Test
    @DisplayName("should not be equal to null")
    void shouldNotBeEqualToNull() {
      BindgenParameter param = new BindgenParameter("value", BindgenType.primitive("i32"));

      assertNotEquals(null, param);
    }

    @Test
    @DisplayName("should not be equal to different class")
    void shouldNotBeEqualToDifferentClass() {
      BindgenParameter param = new BindgenParameter("value", BindgenType.primitive("i32"));

      assertNotEquals("value", param);
    }

    @Test
    @DisplayName("should be equal to itself")
    void shouldBeEqualToItself() {
      BindgenParameter param = new BindgenParameter("value", BindgenType.primitive("i32"));

      assertEquals(param, param);
    }
  }

  @Nested
  @DisplayName("ToString Tests")
  class ToStringTests {

    @Test
    @DisplayName("should format as name: typeName")
    void shouldFormatAsNameColonTypeName() {
      LOGGER.info("Testing toString() output format");

      BindgenParameter param = new BindgenParameter("count", BindgenType.primitive("i32"));

      String toString = param.toString();

      assertEquals("count: i32", toString);
    }

    @Test
    @DisplayName("should handle complex type names")
    void shouldHandleComplexTypeNames() {
      BindgenType listType = BindgenType.list(BindgenType.primitive("string"));
      BindgenParameter param = new BindgenParameter("names", listType);

      String toString = param.toString();

      assertEquals("names: list<string>", toString);
    }
  }
}
