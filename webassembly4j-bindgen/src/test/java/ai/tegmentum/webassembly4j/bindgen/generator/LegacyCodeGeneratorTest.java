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
package ai.tegmentum.webassembly4j.bindgen.generator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.tegmentum.webassembly4j.bindgen.BindgenConfig;
import ai.tegmentum.webassembly4j.bindgen.BindgenException;
import ai.tegmentum.webassembly4j.bindgen.CodeStyle;
import ai.tegmentum.webassembly4j.bindgen.GeneratedSource;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenField;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenFunction;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenInterface;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenParameter;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenType;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenVariantCase;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link LegacyCodeGenerator}. */
@DisplayName("LegacyCodeGenerator Tests")
class LegacyCodeGeneratorTest {

  private static final Logger LOGGER = Logger.getLogger(LegacyCodeGeneratorTest.class.getName());
  private static final String TEST_PACKAGE = "com.example.legacy";

  private LegacyCodeGenerator generator;
  private BindgenConfig config;

  @BeforeEach
  void setUp(@TempDir Path tempDir) {
    config =
        BindgenConfig.builder()
            .codeStyle(CodeStyle.LEGACY)
            .packageName(TEST_PACKAGE)
            .outputDirectory(tempDir)
            .generateJavadoc(true)
            .generateBuilders(true)
            .build();
    generator = new LegacyCodeGenerator(config);
  }

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("should create generator with valid config")
    void shouldCreateGeneratorWithValidConfig(@TempDir Path tempDir) {
      LOGGER.info("Testing constructor");

      BindgenConfig cfg =
          BindgenConfig.builder()
              .codeStyle(CodeStyle.LEGACY)
              .packageName("com.test")
              .outputDirectory(tempDir)
              .build();

      LegacyCodeGenerator gen = new LegacyCodeGenerator(cfg);

      assertNotNull(gen);
    }
  }

  @Nested
  @DisplayName("Record Generation Tests")
  class RecordGenerationTests {

    @Test
    @DisplayName("should generate POJO class for record type")
    void shouldGeneratePojoClassForRecord() throws BindgenException {
      LOGGER.info("Testing POJO record generation");

      BindgenType recordType =
          BindgenType.builder()
              .name("person")
              .kind(BindgenType.Kind.RECORD)
              .addField(new BindgenField("first-name", BindgenType.primitive("string")))
              .addField(new BindgenField("age", BindgenType.primitive("u32")))
              .build();

      GeneratedSource source = generator.generateType(recordType);

      String content = source.getContent();
      assertTrue(
          content.contains("public final class Person"),
          "Expected content to contain: public final class Person");
      assertTrue(
          content.contains("private final String firstName"),
          "Expected content to contain: private final String firstName");
      assertTrue(
          content.contains("private final int age"),
          "Expected content to contain: private final int age");
    }

    @Test
    @DisplayName("should generate traditional getXxx style getters")
    void shouldGenerateTraditionalGetters() throws BindgenException {
      LOGGER.info("Testing traditional getters");

      BindgenType recordType =
          BindgenType.builder()
              .name("point")
              .kind(BindgenType.Kind.RECORD)
              .addField(new BindgenField("x", BindgenType.primitive("f32")))
              .addField(new BindgenField("y", BindgenType.primitive("f32")))
              .build();

      GeneratedSource source = generator.generateType(recordType);

      String content = source.getContent();
      // Legacy style uses getXxx() not xxx()
      assertTrue(
          content.contains("public float getX()"),
          "Expected content to contain: public float getX()");
      assertTrue(
          content.contains("public float getY()"),
          "Expected content to contain: public float getY()");
    }

    @Test
    @DisplayName("should generate builder when configured")
    void shouldGenerateBuilderWhenConfigured() throws BindgenException {
      LOGGER.info("Testing builder generation");

      BindgenType recordType =
          BindgenType.builder()
              .name("config")
              .kind(BindgenType.Kind.RECORD)
              .addField(new BindgenField("name", BindgenType.primitive("string")))
              .addField(new BindgenField("value", BindgenType.primitive("i32")))
              .build();

      GeneratedSource source = generator.generateType(recordType);

      String content = source.getContent();
      assertTrue(
          content.contains("public static final class Builder"),
          "Expected content to contain: public static final class Builder");
      assertTrue(
          content.contains("public static Builder builder()"),
          "Expected content to contain: public static Builder builder()");
      assertTrue(
          content.contains("public Builder name("),
          "Expected content to contain: public Builder name(");
      assertTrue(
          content.contains("public Builder value("),
          "Expected content to contain: public Builder value(");
      assertTrue(
          content.contains("public Config build()"),
          "Expected content to contain: public Config build()");
    }

    @Test
    @DisplayName("should not generate builder when disabled")
    void shouldNotGenerateBuilderWhenDisabled(@TempDir Path tempDir) throws BindgenException {
      LOGGER.info("Testing builder disabled");

      BindgenConfig noBuilderConfig =
          BindgenConfig.builder()
              .codeStyle(CodeStyle.LEGACY)
              .packageName(TEST_PACKAGE)
              .outputDirectory(tempDir)
              .generateBuilders(false)
              .build();
      LegacyCodeGenerator noBuilderGenerator = new LegacyCodeGenerator(noBuilderConfig);

      BindgenType recordType =
          BindgenType.builder()
              .name("simple")
              .kind(BindgenType.Kind.RECORD)
              .addField(new BindgenField("value", BindgenType.primitive("i32")))
              .build();

      GeneratedSource source = noBuilderGenerator.generateType(recordType);

      String content = source.getContent();
      assertFalse(
          content.contains("class Builder"), "Expected content not to contain: class Builder");
      assertFalse(
          content.contains("public static Builder builder()"),
          "Expected content not to contain: public static Builder builder()");
    }

    @Test
    @DisplayName("should not generate builder for empty record")
    void shouldNotGenerateBuilderForEmptyRecord() throws BindgenException {
      BindgenType emptyRecord =
          BindgenType.builder().name("empty").kind(BindgenType.Kind.RECORD).build();

      GeneratedSource source = generator.generateType(emptyRecord);

      String content = source.getContent();
      // Builder is not useful for empty records
      assertFalse(
          content.contains("class Builder"), "Expected content not to contain: class Builder");
    }

    @Test
    @DisplayName("should generate equals method")
    void shouldGenerateEqualsMethod() throws BindgenException {
      BindgenType recordType =
          BindgenType.builder()
              .name("simple")
              .kind(BindgenType.Kind.RECORD)
              .addField(new BindgenField("value", BindgenType.primitive("i32")))
              .build();

      GeneratedSource source = generator.generateType(recordType);

      String content = source.getContent();
      assertTrue(content.contains("@Override"), "Expected content to contain: @Override");
      assertTrue(
          content.contains("public boolean equals(Object obj)"),
          "Expected content to contain: public boolean equals(Object obj)");
      assertTrue(
          content.contains("if (this == obj) return true"),
          "Expected content to contain: if (this == obj) return true");
    }

    @Test
    @DisplayName("should generate hashCode method")
    void shouldGenerateHashCodeMethod() throws BindgenException {
      BindgenType recordType =
          BindgenType.builder()
              .name("simple")
              .kind(BindgenType.Kind.RECORD)
              .addField(new BindgenField("value", BindgenType.primitive("i32")))
              .build();

      GeneratedSource source = generator.generateType(recordType);

      String content = source.getContent();
      assertTrue(content.contains("@Override"), "Expected content to contain: @Override");
      assertTrue(
          content.contains("public int hashCode()"),
          "Expected content to contain: public int hashCode()");
    }

    @Test
    @DisplayName("should generate toString method")
    void shouldGenerateToStringMethod() throws BindgenException {
      BindgenType recordType =
          BindgenType.builder()
              .name("simple")
              .kind(BindgenType.Kind.RECORD)
              .addField(new BindgenField("value", BindgenType.primitive("i32")))
              .build();

      GeneratedSource source = generator.generateType(recordType);

      String content = source.getContent();
      assertTrue(content.contains("@Override"), "Expected content to contain: @Override");
      assertTrue(
          content.contains("public String toString()"),
          "Expected content to contain: public String toString()");
    }

    @Test
    @DisplayName("should include Javadoc when configured")
    void shouldIncludeJavadocWhenConfigured() throws BindgenException {
      BindgenType recordType =
          BindgenType.builder()
              .name("documented")
              .kind(BindgenType.Kind.RECORD)
              .documentation("This is a documented type.")
              .addField(new BindgenField("value", BindgenType.primitive("i32"), "The value."))
              .build();

      GeneratedSource source = generator.generateType(recordType);

      String content = source.getContent();
      assertTrue(
          content.contains("This is a documented type."),
          "Expected content to contain: This is a documented type.");
    }
  }

  @Nested
  @DisplayName("Variant Generation Tests")
  class VariantGenerationTests {

    @Test
    @DisplayName("should generate abstract class for variant")
    void shouldGenerateAbstractClassForVariant() throws BindgenException {
      LOGGER.info("Testing variant abstract class generation");

      BindgenType variantType =
          BindgenType.builder()
              .name("result-type")
              .kind(BindgenType.Kind.VARIANT)
              .addCase(new BindgenVariantCase("success", BindgenType.primitive("string")))
              .addCase(new BindgenVariantCase("error", BindgenType.primitive("string")))
              .build();

      GeneratedSource source = generator.generateType(variantType);

      String content = source.getContent();
      assertTrue(
          content.contains("public abstract class ResultType"),
          "Expected content to contain: public abstract class ResultType");
    }

    @Test
    @DisplayName("should generate visitor interface for variant")
    void shouldGenerateVisitorInterface() throws BindgenException {
      LOGGER.info("Testing visitor pattern generation");

      BindgenType variantType =
          BindgenType.builder()
              .name("option")
              .kind(BindgenType.Kind.VARIANT)
              .addCase(new BindgenVariantCase("some", BindgenType.primitive("i32")))
              .addCase(new BindgenVariantCase("none"))
              .build();

      GeneratedSource source = generator.generateType(variantType);

      String content = source.getContent();
      assertTrue(
          content.contains("public interface Visitor<T>"),
          "Expected content to contain: public interface Visitor<T>");
      assertTrue(
          content.contains("T visitSome(Some value)"),
          "Expected content to contain: T visitSome(Some value)");
      assertTrue(
          content.contains("T visitNone(None value)"),
          "Expected content to contain: T visitNone(None value)");
    }

    @Test
    @DisplayName("should generate accept method for visitor pattern")
    void shouldGenerateAcceptMethod() throws BindgenException {
      BindgenType variantType =
          BindgenType.builder()
              .name("either")
              .kind(BindgenType.Kind.VARIANT)
              .addCase(new BindgenVariantCase("left", BindgenType.primitive("string")))
              .addCase(new BindgenVariantCase("right", BindgenType.primitive("i32")))
              .build();

      GeneratedSource source = generator.generateType(variantType);

      String content = source.getContent();
      assertTrue(
          content.contains("public abstract <T> T accept(Visitor<T> visitor)"),
          "Expected content to contain: public abstract <T> T accept(Visitor<T> visitor)");
    }

    @Test
    @DisplayName("should generate case classes extending base class")
    void shouldGenerateCaseClassesExtendingBase() throws BindgenException {
      BindgenType variantType =
          BindgenType.builder()
              .name("my-variant")
              .kind(BindgenType.Kind.VARIANT)
              .addCase(new BindgenVariantCase("case-one"))
              .addCase(new BindgenVariantCase("case-two", BindgenType.primitive("i32")))
              .build();

      GeneratedSource source = generator.generateType(variantType);

      String content = source.getContent();
      assertTrue(
          content.contains("public static final class CaseOne extends MyVariant"),
          "Expected content to contain: public static final class CaseOne extends MyVariant");
      assertTrue(
          content.contains("public static final class CaseTwo extends MyVariant"),
          "Expected content to contain: public static final class CaseTwo extends MyVariant");
    }

    @Test
    @DisplayName("should generate getValue for case with payload")
    void shouldGenerateGetValueForPayloadCase() throws BindgenException {
      BindgenType variantType =
          BindgenType.builder()
              .name("wrapped")
              .kind(BindgenType.Kind.VARIANT)
              .addCase(new BindgenVariantCase("value", BindgenType.primitive("string")))
              .build();

      GeneratedSource source = generator.generateType(variantType);

      String content = source.getContent();
      // Legacy style uses getValue() not value()
      assertTrue(
          content.contains("public String getValue()"),
          "Expected content to contain: public String getValue()");
    }
  }

  @Nested
  @DisplayName("Resource Generation Tests")
  class ResourceGenerationTests {

    @Test
    @DisplayName("should generate resource class implementing AutoCloseable")
    void shouldGenerateResourceClass() throws BindgenException {
      LOGGER.info("Testing resource generation");

      BindgenType resourceType =
          BindgenType.builder().name("file-handle").kind(BindgenType.Kind.RESOURCE).build();

      GeneratedSource source = generator.generateType(resourceType);

      String content = source.getContent();
      assertTrue(
          content.contains("public class FileHandle implements AutoCloseable"),
          "Expected content to contain: public class FileHandle implements AutoCloseable");
      assertTrue(
          content.contains("private final long handle"),
          "Expected content to contain: private final long handle");
      assertTrue(
          content.contains("public void close()"),
          "Expected content to contain: public void close()");
    }

    @Test
    @DisplayName("should generate getHandle accessor for resource")
    void shouldGenerateGetHandleAccessor() throws BindgenException {
      BindgenType resourceType =
          BindgenType.builder().name("stream").kind(BindgenType.Kind.RESOURCE).build();

      GeneratedSource source = generator.generateType(resourceType);

      String content = source.getContent();
      // Legacy style uses getHandle() not handle()
      assertTrue(
          content.contains("public long getHandle()"),
          "Expected content to contain: public long getHandle()");
    }
  }

  @Nested
  @DisplayName("Enum Generation Tests")
  class EnumGenerationTests {

    @Test
    @DisplayName("should generate Java enum for WIT enum")
    void shouldGenerateJavaEnum() throws BindgenException {
      LOGGER.info("Testing enum generation");

      BindgenType enumType =
          BindgenType.builder()
              .name("direction")
              .kind(BindgenType.Kind.ENUM)
              .addEnumValue("north")
              .addEnumValue("south")
              .addEnumValue("east")
              .addEnumValue("west")
              .build();

      GeneratedSource source = generator.generateType(enumType);

      String content = source.getContent();
      assertTrue(
          content.contains("public enum Direction"),
          "Expected content to contain: public enum Direction");
      assertTrue(content.contains("NORTH"), "Expected content to contain: NORTH");
      assertTrue(content.contains("SOUTH"), "Expected content to contain: SOUTH");
      assertTrue(content.contains("EAST"), "Expected content to contain: EAST");
      assertTrue(content.contains("WEST"), "Expected content to contain: WEST");
    }
  }

  @Nested
  @DisplayName("Interface Generation Tests")
  class InterfaceGenerationTests {

    @Test
    @DisplayName("should generate Java interface for WIT interface")
    void shouldGenerateJavaInterface() throws BindgenException {
      LOGGER.info("Testing interface generation");

      BindgenFunction func =
          BindgenFunction.builder()
              .name("process")
              .addParameter(new BindgenParameter("input", BindgenType.primitive("string")))
              .returnType(BindgenType.primitive("i32"))
              .build();

      BindgenInterface iface =
          BindgenInterface.builder().name("processor").addFunction(func).build();

      GeneratedSource source = generator.generateInterface(iface);

      String content = source.getContent();
      assertTrue(
          content.contains("public interface Processor"),
          "Expected content to contain: public interface Processor");
      assertTrue(
          content.contains("int process(String input)"),
          "Expected content to contain: int process(String input)");
    }
  }

  @Nested
  @DisplayName("Comparison with Modern Generator")
  class ComparisonWithModernTests {

    @Test
    @DisplayName("legacy should use getXxx while modern uses xxx")
    void legacyShouldUseGetXxxWhileModernUsesXxx(@TempDir Path tempDir) throws BindgenException {
      LOGGER.info("Testing getter style difference");

      BindgenType recordType =
          BindgenType.builder()
              .name("test")
              .kind(BindgenType.Kind.RECORD)
              .addField(new BindgenField("value", BindgenType.primitive("i32")))
              .build();

      // Legacy generator
      GeneratedSource legacySource = generator.generateType(recordType);

      // Modern generator
      BindgenConfig modernConfig =
          BindgenConfig.builder()
              .codeStyle(CodeStyle.MODERN)
              .packageName(TEST_PACKAGE)
              .outputDirectory(tempDir)
              .build();
      ModernCodeGenerator modernGen = new ModernCodeGenerator(modernConfig);
      GeneratedSource modernSource = modernGen.generateType(recordType);

      assertTrue(
          legacySource.getContent().contains("public int getValue()"),
          "Expected legacy content to contain: public int getValue()");
      assertTrue(
          modernSource.getContent().contains("public int value()"),
          "Expected modern content to contain: public int value()");
    }

    @Test
    @DisplayName("legacy should use abstract class for variant while modern uses interface")
    void legacyShouldUseAbstractClassWhileModernUsesInterface(@TempDir Path tempDir)
        throws BindgenException {
      LOGGER.info("Testing variant type difference");

      BindgenType variantType =
          BindgenType.builder()
              .name("option")
              .kind(BindgenType.Kind.VARIANT)
              .addCase(new BindgenVariantCase("some", BindgenType.primitive("i32")))
              .addCase(new BindgenVariantCase("none"))
              .build();

      // Legacy generator
      GeneratedSource legacySource = generator.generateType(variantType);

      // Modern generator
      BindgenConfig modernConfig =
          BindgenConfig.builder()
              .codeStyle(CodeStyle.MODERN)
              .packageName(TEST_PACKAGE)
              .outputDirectory(tempDir)
              .build();
      ModernCodeGenerator modernGen = new ModernCodeGenerator(modernConfig);
      GeneratedSource modernSource = modernGen.generateType(variantType);

      assertTrue(
          legacySource.getContent().contains("public abstract class Option"),
          "Expected legacy content to contain: public abstract class Option");
      assertTrue(
          modernSource.getContent().contains("public interface Option"),
          "Expected modern content to contain: public interface Option");
    }
  }
}
