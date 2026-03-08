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

/** Tests for {@link BindgenField}. */
@DisplayName("BindgenField Tests")
class BindgenFieldTest {

  private static final Logger LOGGER = Logger.getLogger(BindgenFieldTest.class.getName());

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("should create field with name and type only")
    void shouldCreateFieldWithNameAndTypeOnly() {
      LOGGER.info("Testing two-arg constructor");

      BindgenType type = BindgenType.primitive("string");
      BindgenField field = new BindgenField("name", type);

      assertEquals("name", field.getName());
      assertEquals(type, field.getType());
      assertTrue(field.getDocumentation().isEmpty());
    }

    @Test
    @DisplayName("should create field with name, type, and documentation")
    void shouldCreateFieldWithDocumentation() {
      LOGGER.info("Testing three-arg constructor with documentation");

      BindgenType type = BindgenType.primitive("i32");
      BindgenField field = new BindgenField("age", type, "The person's age in years");

      assertEquals("age", field.getName());
      assertEquals(type, field.getType());
      assertTrue(field.getDocumentation().isPresent());
      assertEquals("The person's age in years", field.getDocumentation().get());
    }

    @Test
    @DisplayName("should create field with null documentation")
    void shouldCreateFieldWithNullDocumentation() {
      LOGGER.info("Testing three-arg constructor with null documentation");

      BindgenType type = BindgenType.primitive("bool");
      BindgenField field = new BindgenField("active", type, null);

      assertEquals("active", field.getName());
      assertTrue(field.getDocumentation().isEmpty());
    }

    @Test
    @DisplayName("should throw NullPointerException when name is null")
    void shouldThrowWhenNameIsNull() {
      LOGGER.info("Testing constructor with null name");

      BindgenType type = BindgenType.primitive("i32");

      NullPointerException exception =
          assertThrows(NullPointerException.class, () -> new BindgenField(null, type));
      assertTrue(exception.getMessage().contains("name"), "Expected message to contain: name");
    }

    @Test
    @DisplayName("should throw NullPointerException when type is null")
    void shouldThrowWhenTypeIsNull() {
      LOGGER.info("Testing constructor with null type");

      NullPointerException exception =
          assertThrows(NullPointerException.class, () -> new BindgenField("field", null));
      assertTrue(exception.getMessage().contains("type"), "Expected message to contain: type");
    }

    @Test
    @DisplayName("should throw NullPointerException when name is null with documentation")
    void shouldThrowWhenNameIsNullWithDocumentation() {
      LOGGER.info("Testing three-arg constructor with null name");

      BindgenType type = BindgenType.primitive("i32");

      NullPointerException exception =
          assertThrows(NullPointerException.class, () -> new BindgenField(null, type, "docs"));
      assertTrue(exception.getMessage().contains("name"), "Expected message to contain: name");
    }

    @Test
    @DisplayName("should throw NullPointerException when type is null with documentation")
    void shouldThrowWhenTypeIsNullWithDocumentation() {
      LOGGER.info("Testing three-arg constructor with null type");

      NullPointerException exception =
          assertThrows(NullPointerException.class, () -> new BindgenField("field", null, "docs"));
      assertTrue(exception.getMessage().contains("type"), "Expected message to contain: type");
    }
  }

  @Nested
  @DisplayName("Getter Tests")
  class GetterTests {

    @Test
    @DisplayName("getName() should return field name")
    void getNameShouldReturnFieldName() {
      BindgenField field = new BindgenField("myField", BindgenType.primitive("string"));

      assertEquals("myField", field.getName());
    }

    @Test
    @DisplayName("getType() should return field type")
    void getTypeShouldReturnFieldType() {
      BindgenType type = BindgenType.primitive("u64");
      BindgenField field = new BindgenField("counter", type);

      assertEquals(type, field.getType());
      assertEquals("u64", field.getType().getName());
    }

    @Test
    @DisplayName("getDocumentation() should return empty when not set")
    void getDocumentationShouldReturnEmptyWhenNotSet() {
      BindgenField field = new BindgenField("field", BindgenType.primitive("i32"));

      assertTrue(field.getDocumentation().isEmpty());
    }

    @Test
    @DisplayName("getDocumentation() should return value when set")
    void getDocumentationShouldReturnValueWhenSet() {
      BindgenField field = new BindgenField("field", BindgenType.primitive("i32"), "Field docs");

      assertTrue(field.getDocumentation().isPresent());
      assertEquals("Field docs", field.getDocumentation().get());
    }
  }

  @Nested
  @DisplayName("Equals and HashCode Tests")
  class EqualsAndHashCodeTests {

    @Test
    @DisplayName("should be equal when name and type match")
    void shouldBeEqualWhenNameAndTypeMatch() {
      LOGGER.info("Testing equals() for matching fields");

      BindgenType type = BindgenType.primitive("i32");
      BindgenField field1 = new BindgenField("value", type);
      BindgenField field2 = new BindgenField("value", type);

      assertEquals(field2, field1);
      assertEquals(field2.hashCode(), field1.hashCode());
    }

    @Test
    @DisplayName("should be equal even when documentation differs")
    void shouldBeEqualEvenWhenDocumentationDiffers() {
      LOGGER.info("Testing equals() ignores documentation");

      BindgenType type = BindgenType.primitive("i32");
      BindgenField field1 = new BindgenField("value", type, "Doc 1");
      BindgenField field2 = new BindgenField("value", type, "Doc 2");

      assertEquals(field2, field1);
      assertEquals(field2.hashCode(), field1.hashCode());
    }

    @Test
    @DisplayName("should not be equal when names differ")
    void shouldNotBeEqualWhenNamesDiffer() {
      LOGGER.info("Testing equals() for different names");

      BindgenType type = BindgenType.primitive("i32");
      BindgenField field1 = new BindgenField("field1", type);
      BindgenField field2 = new BindgenField("field2", type);

      assertNotEquals(field2, field1);
    }

    @Test
    @DisplayName("should not be equal when types differ")
    void shouldNotBeEqualWhenTypesDiffer() {
      LOGGER.info("Testing equals() for different types");

      BindgenField field1 = new BindgenField("field", BindgenType.primitive("i32"));
      BindgenField field2 = new BindgenField("field", BindgenType.primitive("i64"));

      assertNotEquals(field2, field1);
    }

    @Test
    @DisplayName("should not be equal to null")
    void shouldNotBeEqualToNull() {
      BindgenField field = new BindgenField("field", BindgenType.primitive("i32"));

      assertNotEquals(null, field);
    }

    @Test
    @DisplayName("should not be equal to different class")
    void shouldNotBeEqualToDifferentClass() {
      BindgenField field = new BindgenField("field", BindgenType.primitive("i32"));

      assertNotEquals("field", field);
    }

    @Test
    @DisplayName("should be equal to itself")
    void shouldBeEqualToItself() {
      BindgenField field = new BindgenField("field", BindgenType.primitive("i32"));

      assertEquals(field, field);
    }
  }

  @Nested
  @DisplayName("ToString Tests")
  class ToStringTests {

    @Test
    @DisplayName("should include name and type in toString()")
    void shouldIncludeNameAndTypeInToString() {
      LOGGER.info("Testing toString() output");

      BindgenField field = new BindgenField("myField", BindgenType.primitive("string"));

      String toString = field.toString();

      assertTrue(
          toString.contains("name='myField'"), "Expected toString to contain: name='myField'");
      assertTrue(toString.contains("type="), "Expected toString to contain: type=");
      assertTrue(
          toString.startsWith("BindgenField{"), "Expected toString to start with: BindgenField{");
      assertTrue(toString.endsWith("}"), "Expected toString to end with: }");
    }
  }
}
