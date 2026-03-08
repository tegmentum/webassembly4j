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
package ai.tegmentum.webassembly4j.bindgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Tests for {@link CodeStyle}. */
@DisplayName("CodeStyle Tests")
class CodeStyleTest {

  private static final Logger LOGGER = Logger.getLogger(CodeStyleTest.class.getName());

  @Nested
  @DisplayName("Enum Value Tests")
  class EnumValueTests {

    @Test
    @DisplayName("should have exactly two code styles")
    void shouldHaveExactlyTwoCodeStyles() {
      LOGGER.info("Verifying CodeStyle enum values");

      CodeStyle[] styles = CodeStyle.values();

      assertEquals(2, styles.length);
      Set<CodeStyle> styleSet = new HashSet<>();
      for (CodeStyle s : styles) {
        styleSet.add(s);
      }
      assertEquals(Set.of(CodeStyle.MODERN, CodeStyle.LEGACY), styleSet);
      assertEquals(2, styles.length);
    }

    @Test
    @DisplayName("should get style by name")
    void shouldGetStyleByName() {
      assertEquals(CodeStyle.MODERN, CodeStyle.valueOf("MODERN"));
      assertEquals(CodeStyle.LEGACY, CodeStyle.valueOf("LEGACY"));
    }
  }

  @Nested
  @DisplayName("Minimum Java Version Tests")
  class MinimumJavaVersionTests {

    @Test
    @DisplayName("MODERN should require Java 17")
    void modernShouldRequireJava17() {
      LOGGER.info("Testing MODERN minimum Java version");

      assertEquals("17", CodeStyle.MODERN.getMinimumJavaVersion());
    }

    @Test
    @DisplayName("LEGACY should require Java 8")
    void legacyShouldRequireJava8() {
      LOGGER.info("Testing LEGACY minimum Java version");

      assertEquals("8", CodeStyle.LEGACY.getMinimumJavaVersion());
    }

    @ParameterizedTest
    @EnumSource(CodeStyle.class)
    @DisplayName("all styles should have non-null minimum Java version")
    void allStylesShouldHaveNonNullMinimumJavaVersion(final CodeStyle style) {
      assertNotNull(style.getMinimumJavaVersion());
      assertFalse(style.getMinimumJavaVersion().isEmpty());
    }
  }

  @Nested
  @DisplayName("Feature Support Tests")
  class FeatureSupportTests {

    @Test
    @DisplayName("MODERN should support records")
    void modernShouldSupportRecords() {
      LOGGER.info("Testing MODERN records support");

      assertTrue(CodeStyle.MODERN.supportsRecords());
    }

    @Test
    @DisplayName("LEGACY should not support records")
    void legacyShouldNotSupportRecords() {
      LOGGER.info("Testing LEGACY records support");

      assertFalse(CodeStyle.LEGACY.supportsRecords());
    }

    @Test
    @DisplayName("MODERN should support sealed interfaces")
    void modernShouldSupportSealedInterfaces() {
      LOGGER.info("Testing MODERN sealed interfaces support");

      assertTrue(CodeStyle.MODERN.supportsSealedInterfaces());
    }

    @Test
    @DisplayName("LEGACY should not support sealed interfaces")
    void legacyShouldNotSupportSealedInterfaces() {
      LOGGER.info("Testing LEGACY sealed interfaces support");

      assertFalse(CodeStyle.LEGACY.supportsSealedInterfaces());
    }

    @Test
    @DisplayName("MODERN should not generate builders")
    void modernShouldNotGenerateBuilders() {
      LOGGER.info("Testing MODERN builder generation");

      assertFalse(CodeStyle.MODERN.generatesBuilders());
    }

    @Test
    @DisplayName("LEGACY should generate builders")
    void legacyShouldGenerateBuilders() {
      LOGGER.info("Testing LEGACY builder generation");

      assertTrue(CodeStyle.LEGACY.generatesBuilders());
    }
  }

  @Nested
  @DisplayName("Feature Consistency Tests")
  class FeatureConsistencyTests {

    @Test
    @DisplayName("MODERN features should be consistent")
    void modernFeaturesShouldBeConsistent() {
      LOGGER.info("Testing MODERN feature consistency");

      CodeStyle modern = CodeStyle.MODERN;

      // Modern style should have all modern features
      assertTrue(modern.supportsRecords());
      assertTrue(modern.supportsSealedInterfaces());
      // Modern style should not need builders (records are enough)
      assertFalse(modern.generatesBuilders());
    }

    @Test
    @DisplayName("LEGACY features should be consistent")
    void legacyFeaturesShouldBeConsistent() {
      LOGGER.info("Testing LEGACY feature consistency");

      CodeStyle legacy = CodeStyle.LEGACY;

      // Legacy style should not have any modern features
      assertFalse(legacy.supportsRecords());
      assertFalse(legacy.supportsSealedInterfaces());
      // Legacy style needs builders to create immutable objects
      assertTrue(legacy.generatesBuilders());
    }
  }

  @Nested
  @DisplayName("ToString Tests")
  class ToStringTests {

    @Test
    @DisplayName("MODERN toString should be 'MODERN'")
    void modernToStringShouldBeModern() {
      assertEquals("MODERN", CodeStyle.MODERN.toString());
    }

    @Test
    @DisplayName("LEGACY toString should be 'LEGACY'")
    void legacyToStringShouldBeLegacy() {
      assertEquals("LEGACY", CodeStyle.LEGACY.toString());
    }
  }

  @Nested
  @DisplayName("Name Tests")
  class NameTests {

    @Test
    @DisplayName("MODERN should have correct name")
    void modernShouldHaveCorrectName() {
      assertEquals("MODERN", CodeStyle.MODERN.name());
    }

    @Test
    @DisplayName("LEGACY should have correct name")
    void legacyShouldHaveCorrectName() {
      assertEquals("LEGACY", CodeStyle.LEGACY.name());
    }
  }
}
