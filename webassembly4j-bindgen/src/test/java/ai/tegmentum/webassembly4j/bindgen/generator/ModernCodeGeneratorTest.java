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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.tegmentum.webassembly4j.bindgen.BindgenConfig;
import ai.tegmentum.webassembly4j.bindgen.BindgenException;
import ai.tegmentum.webassembly4j.bindgen.CodeStyle;
import ai.tegmentum.webassembly4j.bindgen.GeneratedSource;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenField;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenFunction;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenInterface;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenModel;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenParameter;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenType;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenVariantCase;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link ModernCodeGenerator}. */
@DisplayName("ModernCodeGenerator Tests")
class ModernCodeGeneratorTest {

  private static final Logger LOGGER = Logger.getLogger(ModernCodeGeneratorTest.class.getName());
  private static final String TEST_PACKAGE = "com.example.generated";

  private ModernCodeGenerator generator;
  private BindgenConfig config;

  @BeforeEach
  void setUp(@TempDir Path tempDir) {
    config =
        BindgenConfig.builder()
            .codeStyle(CodeStyle.MODERN)
            .packageName(TEST_PACKAGE)
            .outputDirectory(tempDir)
            .generateJavadoc(true)
            .generateBuilders(false)
            .build();
    generator = new ModernCodeGenerator(config);
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
              .codeStyle(CodeStyle.MODERN)
              .packageName("com.test")
              .outputDirectory(tempDir)
              .build();

      ModernCodeGenerator gen = new ModernCodeGenerator(cfg);

      assertNotNull(gen);
    }
  }

  @Nested
  @DisplayName("Record Generation Tests")
  class RecordGenerationTests {

    @Test
    @DisplayName("should generate class for record type with fields")
    void shouldGenerateClassForRecordWithFields() throws BindgenException {
      LOGGER.info("Testing record generation with fields");

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
    @DisplayName("should generate record-style getters (fieldName() not getFieldName())")
    void shouldGenerateRecordStyleGetters() throws BindgenException {
      LOGGER.info("Testing record-style getters");

      BindgenType recordType =
          BindgenType.builder()
              .name("point")
              .kind(BindgenType.Kind.RECORD)
              .addField(new BindgenField("x", BindgenType.primitive("f32")))
              .addField(new BindgenField("y", BindgenType.primitive("f32")))
              .build();

      GeneratedSource source = generator.generateType(recordType);

      String content = source.getContent();
      // Modern style uses fieldName() not getFieldName()
      assertTrue(
          content.contains("public float x()"), "Expected content to contain: public float x()");
      assertTrue(
          content.contains("public float y()"), "Expected content to contain: public float y()");
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
    @DisplayName("should generate empty record with no fields")
    void shouldGenerateEmptyRecordWithNoFields() throws BindgenException {
      LOGGER.info("Testing empty record generation");

      BindgenType emptyRecord =
          BindgenType.builder().name("empty").kind(BindgenType.Kind.RECORD).build();

      GeneratedSource source = generator.generateType(emptyRecord);

      String content = source.getContent();
      assertTrue(
          content.contains("public final class Empty"),
          "Expected content to contain: public final class Empty");
    }

    @Test
    @DisplayName("should include Javadoc when configured")
    void shouldIncludeJavadocWhenConfigured() throws BindgenException {
      BindgenType recordType =
          BindgenType.builder()
              .name("documented")
              .kind(BindgenType.Kind.RECORD)
              .documentation("This is a documented type.")
              .addField(new BindgenField("value", BindgenType.primitive("i32"), "The value field."))
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
    @DisplayName("should generate interface for variant type")
    void shouldGenerateInterfaceForVariant() throws BindgenException {
      LOGGER.info("Testing variant generation");

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
          content.contains("public interface ResultType"),
          "Expected content to contain: public interface ResultType");
    }

    @Test
    @DisplayName("should generate nested case classes for variant")
    void shouldGenerateNestedCaseClasses() throws BindgenException {
      BindgenType variantType =
          BindgenType.builder()
              .name("option-int")
              .kind(BindgenType.Kind.VARIANT)
              .addCase(new BindgenVariantCase("some", BindgenType.primitive("i32")))
              .addCase(new BindgenVariantCase("none"))
              .build();

      GeneratedSource source = generator.generateType(variantType);

      String content = source.getContent();
      // Classes nested in interfaces are implicitly public and static
      assertTrue(
          content.contains("final class Some"), "Expected content to contain: final class Some");
      assertTrue(
          content.contains("final class None"), "Expected content to contain: final class None");
    }

    @Test
    @DisplayName("should generate payload accessor for case with payload")
    void shouldGeneratePayloadAccessor() throws BindgenException {
      BindgenType variantType =
          BindgenType.builder()
              .name("maybe-string")
              .kind(BindgenType.Kind.VARIANT)
              .addCase(new BindgenVariantCase("just", BindgenType.primitive("string")))
              .build();

      GeneratedSource source = generator.generateType(variantType);

      String content = source.getContent();
      assertTrue(
          content.contains("public String value()"),
          "Expected content to contain: public String value()");
    }
  }

  @Nested
  @DisplayName("Enum Generation Tests")
  class EnumGenerationTests {

    @Test
    @DisplayName("should generate Java enum for WIT enum type")
    void shouldGenerateJavaEnumForWitEnum() throws BindgenException {
      LOGGER.info("Testing enum generation");

      BindgenType enumType =
          BindgenType.builder()
              .name("color")
              .kind(BindgenType.Kind.ENUM)
              .addEnumValue("red")
              .addEnumValue("green")
              .addEnumValue("blue")
              .build();

      GeneratedSource source = generator.generateType(enumType);

      String content = source.getContent();
      assertTrue(
          content.contains("public enum Color"), "Expected content to contain: public enum Color");
      assertTrue(content.contains("RED"), "Expected content to contain: RED");
      assertTrue(content.contains("GREEN"), "Expected content to contain: GREEN");
      assertTrue(content.contains("BLUE"), "Expected content to contain: BLUE");
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
    @DisplayName("should generate handle accessor for resource")
    void shouldGenerateHandleAccessor() throws BindgenException {
      BindgenType resourceType =
          BindgenType.builder().name("stream").kind(BindgenType.Kind.RESOURCE).build();

      GeneratedSource source = generator.generateType(resourceType);

      String content = source.getContent();
      // Modern style uses handle() not getHandle()
      assertTrue(
          content.contains("public long handle()"),
          "Expected content to contain: public long handle()");
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

    @Test
    @DisplayName("should generate abstract method declarations")
    void shouldGenerateAbstractMethods() throws BindgenException {
      BindgenFunction func = BindgenFunction.builder().name("do-something").build();

      BindgenInterface iface = BindgenInterface.builder().name("service").addFunction(func).build();

      GeneratedSource source = generator.generateInterface(iface);

      String content = source.getContent();
      // Interface methods are implicitly public abstract
      assertTrue(
          content.contains("void doSomething()"),
          "Expected content to contain: void doSomething()");
    }
  }

  @Nested
  @DisplayName("Model Generation Tests")
  class ModelGenerationTests {

    @Test
    @DisplayName("should generate all types from model")
    void shouldGenerateAllTypesFromModel() throws BindgenException {
      LOGGER.info("Testing model generation");

      BindgenType type1 =
          BindgenType.builder()
              .name("type-one")
              .kind(BindgenType.Kind.RECORD)
              .addField(new BindgenField("value", BindgenType.primitive("i32")))
              .build();

      BindgenType type2 =
          BindgenType.builder()
              .name("type-two")
              .kind(BindgenType.Kind.ENUM)
              .addEnumValue("a")
              .addEnumValue("b")
              .build();

      BindgenModel model =
          BindgenModel.builder().name("test-model").addType(type1).addType(type2).build();

      List<GeneratedSource> sources = generator.generate(model);

      assertEquals(2, sources.size());
      assertTrue(
          sources.stream().anyMatch(s -> s.getClassName().equals("TypeOne")),
          "Expected sources to contain TypeOne");
      assertTrue(
          sources.stream().anyMatch(s -> s.getClassName().equals("TypeTwo")),
          "Expected sources to contain TypeTwo");
    }

    @Test
    @DisplayName("should generate interfaces from model")
    void shouldGenerateInterfacesFromModel() throws BindgenException {
      BindgenInterface iface =
          BindgenInterface.builder()
              .name("api")
              .addFunction(BindgenFunction.builder().name("call").build())
              .build();

      BindgenModel model = BindgenModel.builder().name("api-model").addInterface(iface).build();

      List<GeneratedSource> sources = generator.generate(model);

      assertTrue(
          sources.stream().anyMatch(s -> s.getClassName().equals("Api")),
          "Expected sources to contain Api");
    }
  }

  @Nested
  @DisplayName("Package and Naming Tests")
  class PackageAndNamingTests {

    @Test
    @DisplayName("should use configured package name")
    void shouldUseConfiguredPackageName() throws BindgenException {
      BindgenType type = BindgenType.builder().name("test").kind(BindgenType.Kind.RECORD).build();

      GeneratedSource source = generator.generateType(type);

      assertEquals(TEST_PACKAGE, source.getPackageName());
      assertTrue(
          source.getContent().contains("package " + TEST_PACKAGE),
          "Expected content to contain: package " + TEST_PACKAGE);
    }

    @Test
    @DisplayName("should convert kebab-case names to PascalCase")
    void shouldConvertKebabCaseToPascalCase() throws BindgenException {
      BindgenType type =
          BindgenType.builder().name("my-kebab-case-type").kind(BindgenType.Kind.RECORD).build();

      GeneratedSource source = generator.generateType(type);

      assertEquals("MyKebabCaseType", source.getClassName());
    }
  }
}
