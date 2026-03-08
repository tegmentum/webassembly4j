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

import java.util.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link BindgenVariantCase}. */
@DisplayName("BindgenVariantCase Tests")
class BindgenVariantCaseTest {

  private static final Logger LOGGER = Logger.getLogger(BindgenVariantCaseTest.class.getName());

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("should create variant case with name only")
    void shouldCreateVariantCaseWithNameOnly() {
      LOGGER.info("Testing single-arg constructor");

      BindgenVariantCase variantCase = new BindgenVariantCase("none");

      assertEquals("none", variantCase.getName());
      assertTrue(variantCase.getPayload().isEmpty());
      assertFalse(variantCase.hasPayload());
      assertTrue(variantCase.getDocumentation().isEmpty());
    }

    @Test
    @DisplayName("should create variant case with name and payload")
    void shouldCreateVariantCaseWithNameAndPayload() {
      LOGGER.info("Testing two-arg constructor with payload");

      BindgenType payload = BindgenType.primitive("i32");
      BindgenVariantCase variantCase = new BindgenVariantCase("some", payload);

      assertEquals("some", variantCase.getName());
      assertTrue(variantCase.getPayload().isPresent());
      assertEquals(payload, variantCase.getPayload().get());
      assertTrue(variantCase.hasPayload());
      assertTrue(variantCase.getDocumentation().isEmpty());
    }

    @Test
    @DisplayName("should create variant case with name, payload, and documentation")
    void shouldCreateVariantCaseWithDocumentation() {
      LOGGER.info("Testing three-arg constructor with documentation");

      BindgenType payload = BindgenType.primitive("string");
      BindgenVariantCase variantCase =
          new BindgenVariantCase("error", payload, "An error occurred");

      assertEquals("error", variantCase.getName());
      assertTrue(variantCase.getPayload().isPresent());
      assertEquals(payload, variantCase.getPayload().get());
      assertTrue(variantCase.hasPayload());
      assertTrue(variantCase.getDocumentation().isPresent());
      assertEquals("An error occurred", variantCase.getDocumentation().get());
    }

    @Test
    @DisplayName("should create variant case with null payload and documentation")
    void shouldCreateVariantCaseWithNullPayloadAndDocumentation() {
      LOGGER.info("Testing three-arg constructor with null payload");

      BindgenVariantCase variantCase = new BindgenVariantCase("empty", null, "No value");

      assertEquals("empty", variantCase.getName());
      assertTrue(variantCase.getPayload().isEmpty());
      assertFalse(variantCase.hasPayload());
      assertTrue(variantCase.getDocumentation().isPresent());
      assertEquals("No value", variantCase.getDocumentation().get());
    }

    @Test
    @DisplayName("should throw NullPointerException when name is null (single arg)")
    void shouldThrowWhenNameIsNullSingleArg() {
      LOGGER.info("Testing single-arg constructor with null name");

      NullPointerException exception =
          assertThrows(NullPointerException.class, () -> new BindgenVariantCase(null));
      assertTrue(exception.getMessage().contains("name"), "Expected message to contain: name");
    }

    @Test
    @DisplayName("should throw NullPointerException when name is null (two args)")
    void shouldThrowWhenNameIsNullTwoArgs() {
      LOGGER.info("Testing two-arg constructor with null name");

      BindgenType payload = BindgenType.primitive("i32");

      NullPointerException exception =
          assertThrows(NullPointerException.class, () -> new BindgenVariantCase(null, payload));
      assertTrue(exception.getMessage().contains("name"), "Expected message to contain: name");
    }

    @Test
    @DisplayName("should throw NullPointerException when name is null (three args)")
    void shouldThrowWhenNameIsNullThreeArgs() {
      LOGGER.info("Testing three-arg constructor with null name");

      BindgenType payload = BindgenType.primitive("i32");

      NullPointerException exception =
          assertThrows(
              NullPointerException.class, () -> new BindgenVariantCase(null, payload, "docs"));
      assertTrue(exception.getMessage().contains("name"), "Expected message to contain: name");
    }
  }

  @Nested
  @DisplayName("Getter Tests")
  class GetterTests {

    @Test
    @DisplayName("getName() should return case name")
    void getNameShouldReturnCaseName() {
      BindgenVariantCase variantCase = new BindgenVariantCase("myCase");

      assertEquals("myCase", variantCase.getName());
    }

    @Test
    @DisplayName("getPayload() should return empty when no payload")
    void getPayloadShouldReturnEmptyWhenNoPayload() {
      BindgenVariantCase variantCase = new BindgenVariantCase("noPayload");

      assertTrue(variantCase.getPayload().isEmpty());
    }

    @Test
    @DisplayName("getPayload() should return value when payload exists")
    void getPayloadShouldReturnValueWhenPayloadExists() {
      BindgenType payload = BindgenType.primitive("u64");
      BindgenVariantCase variantCase = new BindgenVariantCase("withPayload", payload);

      assertTrue(variantCase.getPayload().isPresent());
      assertEquals(payload, variantCase.getPayload().get());
    }

    @Test
    @DisplayName("hasPayload() should return false when no payload")
    void hasPayloadShouldReturnFalseWhenNoPayload() {
      BindgenVariantCase variantCase = new BindgenVariantCase("noPayload");

      assertFalse(variantCase.hasPayload());
    }

    @Test
    @DisplayName("hasPayload() should return true when payload exists")
    void hasPayloadShouldReturnTrueWhenPayloadExists() {
      BindgenVariantCase variantCase =
          new BindgenVariantCase("withPayload", BindgenType.primitive("i32"));

      assertTrue(variantCase.hasPayload());
    }

    @Test
    @DisplayName("getDocumentation() should return empty when not set")
    void getDocumentationShouldReturnEmptyWhenNotSet() {
      BindgenVariantCase variantCase = new BindgenVariantCase("case");

      assertTrue(variantCase.getDocumentation().isEmpty());
    }

    @Test
    @DisplayName("getDocumentation() should return value when set")
    void getDocumentationShouldReturnValueWhenSet() {
      BindgenVariantCase variantCase = new BindgenVariantCase("case", null, "Case documentation");

      assertTrue(variantCase.getDocumentation().isPresent());
      assertEquals("Case documentation", variantCase.getDocumentation().get());
    }
  }

  @Nested
  @DisplayName("Equals and HashCode Tests")
  class EqualsAndHashCodeTests {

    @Test
    @DisplayName("should be equal when name and payload match (no payload)")
    void shouldBeEqualWhenNameMatchNoPayload() {
      LOGGER.info("Testing equals() for matching cases without payload");

      BindgenVariantCase case1 = new BindgenVariantCase("none");
      BindgenVariantCase case2 = new BindgenVariantCase("none");

      assertEquals(case2, case1);
      assertEquals(case2.hashCode(), case1.hashCode());
    }

    @Test
    @DisplayName("should be equal when name and payload match (with payload)")
    void shouldBeEqualWhenNameAndPayloadMatch() {
      LOGGER.info("Testing equals() for matching cases with payload");

      BindgenType payload = BindgenType.primitive("i32");
      BindgenVariantCase case1 = new BindgenVariantCase("some", payload);
      BindgenVariantCase case2 = new BindgenVariantCase("some", payload);

      assertEquals(case2, case1);
      assertEquals(case2.hashCode(), case1.hashCode());
    }

    @Test
    @DisplayName("should be equal even when documentation differs")
    void shouldBeEqualEvenWhenDocumentationDiffers() {
      LOGGER.info("Testing equals() ignores documentation");

      BindgenType payload = BindgenType.primitive("i32");
      BindgenVariantCase case1 = new BindgenVariantCase("case", payload, "Doc 1");
      BindgenVariantCase case2 = new BindgenVariantCase("case", payload, "Doc 2");

      assertEquals(case2, case1);
      assertEquals(case2.hashCode(), case1.hashCode());
    }

    @Test
    @DisplayName("should not be equal when names differ")
    void shouldNotBeEqualWhenNamesDiffer() {
      LOGGER.info("Testing equals() for different names");

      BindgenVariantCase case1 = new BindgenVariantCase("case1");
      BindgenVariantCase case2 = new BindgenVariantCase("case2");

      assertNotEquals(case2, case1);
    }

    @Test
    @DisplayName("should not be equal when payloads differ")
    void shouldNotBeEqualWhenPayloadsDiffer() {
      LOGGER.info("Testing equals() for different payloads");

      BindgenVariantCase case1 = new BindgenVariantCase("case", BindgenType.primitive("i32"));
      BindgenVariantCase case2 = new BindgenVariantCase("case", BindgenType.primitive("i64"));

      assertNotEquals(case2, case1);
    }

    @Test
    @DisplayName("should not be equal when one has payload and other does not")
    void shouldNotBeEqualWhenPayloadPresenceDiffers() {
      LOGGER.info("Testing equals() for payload presence difference");

      BindgenVariantCase case1 = new BindgenVariantCase("case");
      BindgenVariantCase case2 = new BindgenVariantCase("case", BindgenType.primitive("i32"));

      assertNotEquals(case2, case1);
    }

    @Test
    @DisplayName("should not be equal to null")
    void shouldNotBeEqualToNull() {
      BindgenVariantCase variantCase = new BindgenVariantCase("case");

      assertNotEquals(null, variantCase);
    }

    @Test
    @DisplayName("should not be equal to different class")
    void shouldNotBeEqualToDifferentClass() {
      BindgenVariantCase variantCase = new BindgenVariantCase("case");

      assertNotEquals("case", variantCase);
    }

    @Test
    @DisplayName("should be equal to itself")
    void shouldBeEqualToItself() {
      BindgenVariantCase variantCase = new BindgenVariantCase("case");

      assertEquals(variantCase, variantCase);
    }
  }

  @Nested
  @DisplayName("ToString Tests")
  class ToStringTests {

    @Test
    @DisplayName("should include name in toString() for case without payload")
    void shouldIncludeNameInToStringWithoutPayload() {
      LOGGER.info("Testing toString() output without payload");

      BindgenVariantCase variantCase = new BindgenVariantCase("none");

      String toString = variantCase.toString();

      assertTrue(toString.contains("name='none'"), "Expected toString to contain: name='none'");
      assertTrue(
          toString.startsWith("BindgenVariantCase{"),
          "Expected toString to start with: BindgenVariantCase{");
      assertTrue(toString.endsWith("}"), "Expected toString to end with: }");
      assertFalse(toString.contains("payload="), "Expected toString not to contain: payload=");
    }

    @Test
    @DisplayName("should include name and payload in toString() for case with payload")
    void shouldIncludeNameAndPayloadInToStringWithPayload() {
      LOGGER.info("Testing toString() output with payload");

      BindgenVariantCase variantCase = new BindgenVariantCase("some", BindgenType.primitive("i32"));

      String toString = variantCase.toString();

      assertTrue(toString.contains("name='some'"), "Expected toString to contain: name='some'");
      assertTrue(toString.contains("payload="), "Expected toString to contain: payload=");
      assertTrue(
          toString.startsWith("BindgenVariantCase{"),
          "Expected toString to start with: BindgenVariantCase{");
      assertTrue(toString.endsWith("}"), "Expected toString to end with: }");
    }
  }
}
