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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.tegmentum.webassembly4j.bindgen.CodeStyle;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link TypeMappingRegistry}. */
@DisplayName("TypeMappingRegistry Tests")
class TypeMappingRegistryTest {

  private static final Logger LOGGER = Logger.getLogger(TypeMappingRegistryTest.class.getName());
  private static final String TEST_PACKAGE = "com.example.test";

  private TypeMappingRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new TypeMappingRegistry(CodeStyle.MODERN, TEST_PACKAGE);
  }

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("should create registry with valid parameters")
    void shouldCreateRegistryWithValidParameters() {
      LOGGER.info("Testing constructor with valid parameters");

      TypeMappingRegistry reg = new TypeMappingRegistry(CodeStyle.LEGACY, "com.example");

      assertEquals(CodeStyle.LEGACY, reg.getCodeStyle());
      assertEquals("com.example", reg.getBasePackage());
    }

    @Test
    @DisplayName("should throw NullPointerException when codeStyle is null")
    void shouldThrowWhenCodeStyleIsNull() {
      LOGGER.info("Testing constructor with null codeStyle");

      NullPointerException exception =
          assertThrows(
              NullPointerException.class, () -> new TypeMappingRegistry(null, TEST_PACKAGE));
      assertTrue(
          exception.getMessage().contains("codeStyle"), "Expected message to contain: codeStyle");
    }

    @Test
    @DisplayName("should throw NullPointerException when basePackage is null")
    void shouldThrowWhenBasePackageIsNull() {
      LOGGER.info("Testing constructor with null basePackage");

      NullPointerException exception =
          assertThrows(
              NullPointerException.class, () -> new TypeMappingRegistry(CodeStyle.MODERN, null));
      assertTrue(
          exception.getMessage().contains("basePackage"),
          "Expected message to contain: basePackage");
    }
  }

  @Nested
  @DisplayName("WIT Primitive Mapping Tests")
  class WitPrimitiveMappingTests {

    @ParameterizedTest
    @CsvSource({
      "bool, boolean",
      "s8, byte",
      "s16, short",
      "s32, int",
      "s64, long",
      "u8, byte",
      "u16, short",
      "u32, int",
      "u64, long",
      "f32, float",
      "f64, double",
      "float32, float",
      "float64, double",
      "char, int"
    })
    @DisplayName("should map WIT primitive types to Java primitives")
    void shouldMapWitPrimitivesToJavaPrimitives(final String witType, final String expected) {
      LOGGER.info("Testing WIT primitive mapping: " + witType + " -> " + expected);

      TypeName result = registry.mapWitPrimitive(witType);

      assertEquals(expected, result.toString());
    }

    @Test
    @DisplayName("should map string to java.lang.String")
    void shouldMapStringToJavaString() {
      LOGGER.info("Testing string mapping");

      TypeName result = registry.mapWitPrimitive("string");

      assertEquals(ClassName.get(String.class), result);
    }

    @Test
    @DisplayName("should handle case-insensitive type names")
    void shouldHandleCaseInsensitiveTypeNames() {
      LOGGER.info("Testing case-insensitivity");

      assertEquals(TypeName.BOOLEAN, registry.mapWitPrimitive("BOOL"));
      assertEquals(TypeName.BOOLEAN, registry.mapWitPrimitive("Bool"));
      assertEquals(TypeName.INT, registry.mapWitPrimitive("S32"));
    }

    @Test
    @DisplayName("should throw IllegalArgumentException for unknown type")
    void shouldThrowForUnknownType() {
      IllegalArgumentException exception =
          assertThrows(IllegalArgumentException.class, () -> registry.mapWitPrimitive("unknown"));
      assertTrue(
          exception.getMessage().contains("Unknown WIT primitive type: unknown"),
          "Expected message to contain: Unknown WIT primitive type: unknown");
    }

    @Test
    @DisplayName("should throw NullPointerException for null type")
    void shouldThrowForNullType() {
      NullPointerException exception =
          assertThrows(NullPointerException.class, () -> registry.mapWitPrimitive(null));
      assertTrue(
          exception.getMessage().contains("witPrimitive"),
          "Expected message to contain: witPrimitive");
    }
  }

  @Nested
  @DisplayName("WASM Type Mapping Tests")
  class WasmTypeMappingTests {

    @ParameterizedTest
    @CsvSource({"i32, int", "i64, long", "f32, float", "f64, double"})
    @DisplayName("should map WASM numeric types to Java primitives")
    void shouldMapWasmNumericTypes(final String wasmType, final String expected) {
      LOGGER.info("Testing WASM type mapping: " + wasmType + " -> " + expected);

      TypeName result = registry.mapWasmType(wasmType);

      assertEquals(expected, result.toString());
    }

    @Test
    @DisplayName("should map v128 to byte[]")
    void shouldMapV128ToByteArray() {
      TypeName result = registry.mapWasmType("v128");

      assertEquals(ArrayTypeName.of(TypeName.BYTE), result);
    }

    @Test
    @DisplayName("should map funcref to FunctionReference")
    void shouldMapFuncrefToFunctionReference() {
      TypeName result = registry.mapWasmType("funcref");

      assertEquals("ai.tegmentum.webassembly4j.FunctionReference", result.toString());
    }

    @Test
    @DisplayName("should map externref to Object")
    void shouldMapExternrefToObject() {
      TypeName result = registry.mapWasmType("externref");

      assertEquals(ClassName.get(Object.class), result);
    }

    @Test
    @DisplayName("should handle case-insensitive WASM types")
    void shouldHandleCaseInsensitiveWasmTypes() {
      assertEquals(TypeName.INT, registry.mapWasmType("I32"));
      assertEquals(TypeName.DOUBLE, registry.mapWasmType("F64"));
    }

    @Test
    @DisplayName("should throw IllegalArgumentException for unknown WASM type")
    void shouldThrowForUnknownWasmType() {
      IllegalArgumentException exception =
          assertThrows(IllegalArgumentException.class, () -> registry.mapWasmType("invalid"));
      assertTrue(
          exception.getMessage().contains("Unknown WASM type: invalid"),
          "Expected message to contain: Unknown WASM type: invalid");
    }

    @Test
    @DisplayName("should throw NullPointerException for null WASM type")
    void shouldThrowForNullWasmType() {
      NullPointerException exception =
          assertThrows(NullPointerException.class, () -> registry.mapWasmType(null));
      assertTrue(
          exception.getMessage().contains("wasmType"), "Expected message to contain: wasmType");
    }
  }

  @Nested
  @DisplayName("List Mapping Tests")
  class ListMappingTests {

    @Test
    @DisplayName("should create List<Integer> for int element type")
    void shouldCreateListOfIntegerForInt() {
      LOGGER.info("Testing list mapping for int");

      TypeName result = registry.mapList(TypeName.INT);

      assertInstanceOf(ParameterizedTypeName.class, result);
      assertEquals("java.util.List<java.lang.Integer>", result.toString());
    }

    @Test
    @DisplayName("should create List<String> for String element type")
    void shouldCreateListOfString() {
      TypeName result = registry.mapList(ClassName.get(String.class));

      assertEquals("java.util.List<java.lang.String>", result.toString());
    }

    @Test
    @DisplayName("should box primitive types in list")
    void shouldBoxPrimitiveTypesInList() {
      assertEquals(
          "java.util.List<java.lang.Boolean>", registry.mapList(TypeName.BOOLEAN).toString());
      assertEquals("java.util.List<java.lang.Long>", registry.mapList(TypeName.LONG).toString());
    }

    @Test
    @DisplayName("should throw NullPointerException for null element type")
    void shouldThrowForNullElementType() {
      NullPointerException exception =
          assertThrows(NullPointerException.class, () -> registry.mapList(null));
      assertTrue(
          exception.getMessage().contains("elementType"),
          "Expected message to contain: elementType");
    }
  }

  @Nested
  @DisplayName("Option Mapping Tests")
  class OptionMappingTests {

    @Test
    @DisplayName("should create Optional<Integer> for int inner type")
    void shouldCreateOptionalOfIntegerForInt() {
      LOGGER.info("Testing option mapping for int");

      TypeName result = registry.mapOption(TypeName.INT);

      assertInstanceOf(ParameterizedTypeName.class, result);
      assertEquals("java.util.Optional<java.lang.Integer>", result.toString());
    }

    @Test
    @DisplayName("should create Optional<String> for String inner type")
    void shouldCreateOptionalOfString() {
      TypeName result = registry.mapOption(ClassName.get(String.class));

      assertEquals("java.util.Optional<java.lang.String>", result.toString());
    }

    @Test
    @DisplayName("should throw NullPointerException for null inner type")
    void shouldThrowForNullInnerType() {
      NullPointerException exception =
          assertThrows(NullPointerException.class, () -> registry.mapOption(null));
      assertTrue(
          exception.getMessage().contains("innerType"), "Expected message to contain: innerType");
    }
  }

  @Nested
  @DisplayName("Result Mapping Tests")
  class ResultMappingTests {

    @Test
    @DisplayName("should create WitResult with ok and error types")
    void shouldCreateResultWithBothTypes() {
      LOGGER.info("Testing result mapping with both types");

      TypeName result = registry.mapResult(TypeName.INT, ClassName.get(String.class));

      assertInstanceOf(ParameterizedTypeName.class, result);
      assertEquals(
          "ai.tegmentum.webassembly4j.bindgen.wit.WitResult<java.lang.Integer, java.lang.String>",
          result.toString());
    }

    @Test
    @DisplayName("should use Void for null ok type")
    void shouldUseVoidForNullOkType() {
      TypeName result = registry.mapResult(null, ClassName.get(String.class));

      assertEquals(
          "ai.tegmentum.webassembly4j.bindgen.wit.WitResult<java.lang.Void, java.lang.String>",
          result.toString());
    }

    @Test
    @DisplayName("should use Void for null error type")
    void shouldUseVoidForNullErrorType() {
      TypeName result = registry.mapResult(TypeName.INT, null);

      assertEquals(
          "ai.tegmentum.webassembly4j.bindgen.wit.WitResult<java.lang.Integer, java.lang.Void>",
          result.toString());
    }

    @Test
    @DisplayName("should use Void for both null types")
    void shouldUseVoidForBothNullTypes() {
      TypeName result = registry.mapResult(null, null);

      assertEquals(
          "ai.tegmentum.webassembly4j.bindgen.wit.WitResult<java.lang.Void, java.lang.Void>",
          result.toString());
    }
  }

  @Nested
  @DisplayName("Tuple Mapping Tests")
  class TupleMappingTests {

    @Test
    @DisplayName("should return Void for empty tuple")
    void shouldReturnVoidForEmptyTuple() {
      LOGGER.info("Testing tuple mapping for empty list");

      TypeName result = registry.mapTuple(List.of());

      assertEquals(ClassName.get(Void.class), result);
    }

    @Test
    @DisplayName("should return element type for single-element tuple")
    void shouldReturnElementTypeForSingleElement() {
      TypeName result = registry.mapTuple(List.of(TypeName.INT));

      assertEquals(TypeName.INT, result);
    }

    @Test
    @DisplayName("should return Tuple2 for two-element tuple")
    void shouldReturnTuple2ForTwoElements() {
      TypeName result = registry.mapTuple(Arrays.asList(TypeName.INT, TypeName.LONG));

      assertEquals(TEST_PACKAGE + ".tuple.Tuple2", result.toString());
    }

    @Test
    @DisplayName("should return TupleN for N-element tuple")
    void shouldReturnTupleNForNElements() {
      TypeName result =
          registry.mapTuple(
              Arrays.asList(TypeName.INT, TypeName.LONG, TypeName.FLOAT, TypeName.DOUBLE));

      assertEquals(TEST_PACKAGE + ".tuple.Tuple4", result.toString());
    }

    @Test
    @DisplayName("should throw NullPointerException for null list")
    void shouldThrowForNullList() {
      NullPointerException exception =
          assertThrows(NullPointerException.class, () -> registry.mapTuple(null));
      assertTrue(
          exception.getMessage().contains("elementTypes"),
          "Expected message to contain: elementTypes");
    }
  }

  @Nested
  @DisplayName("Generated Type Mapping Tests")
  class GeneratedTypeMappingTests {

    @Test
    @DisplayName("should create ClassName for simple type name")
    void shouldCreateClassNameForSimpleType() {
      LOGGER.info("Testing generated type mapping");

      ClassName result = registry.mapGeneratedType("my-type");

      assertEquals(TEST_PACKAGE, result.packageName());
      assertEquals("MyType", result.simpleName());
    }

    @Test
    @DisplayName("should convert kebab-case to PascalCase")
    void shouldConvertKebabCaseToPascalCase() {
      ClassName result = registry.mapGeneratedType("my-long-type-name");

      assertEquals("MyLongTypeName", result.simpleName());
    }

    @Test
    @DisplayName("should convert snake_case to PascalCase")
    void shouldConvertSnakeCaseToPascalCase() {
      ClassName result = registry.mapGeneratedType("my_long_type_name");

      assertEquals("MyLongTypeName", result.simpleName());
    }

    @Test
    @DisplayName("should capitalize first letter")
    void shouldCapitalizeFirstLetter() {
      ClassName result = registry.mapGeneratedType("lowercase");

      assertEquals("Lowercase", result.simpleName());
    }

    @Test
    @DisplayName("should throw NullPointerException for null type name")
    void shouldThrowForNullTypeName() {
      NullPointerException exception =
          assertThrows(NullPointerException.class, () -> registry.mapGeneratedType(null));
      assertTrue(
          exception.getMessage().contains("typeName"), "Expected message to contain: typeName");
    }
  }

  @Nested
  @DisplayName("Interface Mapping Tests")
  class InterfaceMappingTests {

    @Test
    @DisplayName("should create ClassName for interface name")
    void shouldCreateClassNameForInterface() {
      LOGGER.info("Testing interface mapping");

      ClassName result = registry.mapInterface("my-interface");

      assertEquals(TEST_PACKAGE, result.packageName());
      assertEquals("MyInterface", result.simpleName());
    }

    @Test
    @DisplayName("should throw NullPointerException for null interface name")
    void shouldThrowForNullInterfaceName() {
      NullPointerException exception =
          assertThrows(NullPointerException.class, () -> registry.mapInterface(null));
      assertTrue(
          exception.getMessage().contains("interfaceName"),
          "Expected message to contain: interfaceName");
    }
  }

  @Nested
  @DisplayName("Primitive Check Tests")
  class PrimitiveCheckTests {

    @ParameterizedTest
    @ValueSource(
        strings = {
          "bool", "s8", "s16", "s32", "s64", "u8", "u16", "u32", "u64", "f32", "f64", "float32",
          "float64", "char", "string"
        })
    @DisplayName("should return true for primitive types")
    void shouldReturnTrueForPrimitives(final String typeName) {
      assertTrue(registry.isPrimitive(typeName));
    }

    @ParameterizedTest
    @ValueSource(strings = {"record", "variant", "my-type", "list", "option"})
    @DisplayName("should return false for non-primitive types")
    void shouldReturnFalseForNonPrimitives(final String typeName) {
      assertFalse(registry.isPrimitive(typeName));
    }

    @Test
    @DisplayName("should handle case-insensitive primitive check")
    void shouldHandleCaseInsensitivePrimitiveCheck() {
      assertTrue(registry.isPrimitive("BOOL"));
      assertTrue(registry.isPrimitive("String"));
      assertTrue(registry.isPrimitive("S32"));
    }

    @Test
    @DisplayName("should return false for null")
    void shouldReturnFalseForNull() {
      assertFalse(registry.isPrimitive(null));
    }
  }

  @Nested
  @DisplayName("Getter Tests")
  class GetterTests {

    @Test
    @DisplayName("getCodeStyle should return configured code style")
    void getCodeStyleShouldReturnConfiguredStyle() {
      TypeMappingRegistry modernRegistry = new TypeMappingRegistry(CodeStyle.MODERN, TEST_PACKAGE);
      TypeMappingRegistry legacyRegistry = new TypeMappingRegistry(CodeStyle.LEGACY, TEST_PACKAGE);

      assertEquals(CodeStyle.MODERN, modernRegistry.getCodeStyle());
      assertEquals(CodeStyle.LEGACY, legacyRegistry.getCodeStyle());
    }

    @Test
    @DisplayName("getBasePackage should return configured base package")
    void getBasePackageShouldReturnConfiguredPackage() {
      TypeMappingRegistry reg1 = new TypeMappingRegistry(CodeStyle.MODERN, "com.example.one");
      TypeMappingRegistry reg2 = new TypeMappingRegistry(CodeStyle.MODERN, "org.another.pkg");

      assertEquals("com.example.one", reg1.getBasePackage());
      assertEquals("org.another.pkg", reg2.getBasePackage());
    }
  }
}
