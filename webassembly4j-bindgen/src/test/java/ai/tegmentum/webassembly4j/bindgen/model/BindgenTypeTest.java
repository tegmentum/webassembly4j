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

import ai.tegmentum.webassembly4j.bindgen.model.BindgenType.Kind;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link BindgenType}. */
@DisplayName("BindgenType Tests")
class BindgenTypeTest {

  private static final Logger LOGGER = Logger.getLogger(BindgenTypeTest.class.getName());

  @Nested
  @DisplayName("Kind Enum Tests")
  class KindEnumTests {

    @Test
    @DisplayName("should have all expected kind values")
    void shouldHaveAllExpectedKindValues() {
      LOGGER.info("Verifying all Kind enum values are present");

      Kind[] kinds = Kind.values();

      Set<Kind> expectedKinds =
          Set.of(
              Kind.PRIMITIVE,
              Kind.RECORD,
              Kind.VARIANT,
              Kind.ENUM,
              Kind.FLAGS,
              Kind.LIST,
              Kind.OPTION,
              Kind.RESULT,
              Kind.TUPLE,
              Kind.RESOURCE,
              Kind.REFERENCE,
              Kind.FUNCTION);
      assertEquals(expectedKinds, new HashSet<>(Arrays.asList(kinds)));
      assertEquals(12, kinds.length);
    }

    @Test
    @DisplayName("should convert kind to string correctly")
    void shouldConvertKindToStringCorrectly() {
      assertEquals("PRIMITIVE", Kind.PRIMITIVE.toString());
      assertEquals("RECORD", Kind.RECORD.toString());
      assertEquals("VARIANT", Kind.VARIANT.toString());
    }

    @Test
    @DisplayName("should get kind by name")
    void shouldGetKindByName() {
      assertEquals(Kind.PRIMITIVE, Kind.valueOf("PRIMITIVE"));
      assertEquals(Kind.LIST, Kind.valueOf("LIST"));
      assertEquals(Kind.OPTION, Kind.valueOf("OPTION"));
    }
  }

  @Nested
  @DisplayName("Factory Method Tests")
  class FactoryMethodTests {

    @Test
    @DisplayName("should create primitive type with primitive() factory")
    void shouldCreatePrimitiveTypeWithFactory() {
      LOGGER.info("Testing primitive() factory method");

      BindgenType type = BindgenType.primitive("i32");

      assertEquals("i32", type.getName());
      assertEquals(Kind.PRIMITIVE, type.getKind());
      assertTrue(type.isPrimitive());
      assertFalse(type.isRecord());
      assertFalse(type.isVariant());
      assertFalse(type.isEnum());
    }

    @Test
    @DisplayName("should create reference type with reference() factory")
    void shouldCreateReferenceTypeWithFactory() {
      LOGGER.info("Testing reference() factory method");

      BindgenType type = BindgenType.reference("MyRecord");

      assertEquals("MyRecord", type.getName());
      assertEquals(Kind.REFERENCE, type.getKind());
      assertTrue(type.getReferencedTypeName().isPresent());
      assertEquals("MyRecord", type.getReferencedTypeName().get());
    }

    @Test
    @DisplayName("should create list type with list() factory")
    void shouldCreateListTypeWithFactory() {
      LOGGER.info("Testing list() factory method");

      BindgenType elementType = BindgenType.primitive("string");
      BindgenType listType = BindgenType.list(elementType);

      assertEquals("list<string>", listType.getName());
      assertEquals(Kind.LIST, listType.getKind());
      assertTrue(listType.getElementType().isPresent());
      assertEquals(elementType, listType.getElementType().get());
    }

    @Test
    @DisplayName("should create option type with option() factory")
    void shouldCreateOptionTypeWithFactory() {
      LOGGER.info("Testing option() factory method");

      BindgenType innerType = BindgenType.primitive("u32");
      BindgenType optionType = BindgenType.option(innerType);

      assertEquals("option<u32>", optionType.getName());
      assertEquals(Kind.OPTION, optionType.getKind());
      assertTrue(optionType.getElementType().isPresent());
      assertEquals(innerType, optionType.getElementType().get());
    }

    @Test
    @DisplayName("should create result type with both ok and error types")
    void shouldCreateResultTypeWithBothTypes() {
      LOGGER.info("Testing result() factory method with both types");

      BindgenType okType = BindgenType.primitive("string");
      BindgenType errorType = BindgenType.primitive("u32");
      BindgenType resultType = BindgenType.result(okType, errorType);

      assertEquals("result<string, u32>", resultType.getName());
      assertEquals(Kind.RESULT, resultType.getKind());
      assertTrue(resultType.getOkType().isPresent());
      assertEquals(okType, resultType.getOkType().get());
      assertTrue(resultType.getErrorType().isPresent());
      assertEquals(errorType, resultType.getErrorType().get());
    }

    @Test
    @DisplayName("should create result type with null ok type")
    void shouldCreateResultTypeWithNullOkType() {
      LOGGER.info("Testing result() factory method with null ok type");

      BindgenType errorType = BindgenType.primitive("u32");
      BindgenType resultType = BindgenType.result(null, errorType);

      assertEquals("result<_, u32>", resultType.getName());
      assertTrue(resultType.getOkType().isEmpty());
      assertTrue(resultType.getErrorType().isPresent());
      assertEquals(errorType, resultType.getErrorType().get());
    }

    @Test
    @DisplayName("should create result type with null error type")
    void shouldCreateResultTypeWithNullErrorType() {
      LOGGER.info("Testing result() factory method with null error type");

      BindgenType okType = BindgenType.primitive("string");
      BindgenType resultType = BindgenType.result(okType, null);

      assertEquals("result<string, _>", resultType.getName());
      assertTrue(resultType.getOkType().isPresent());
      assertEquals(okType, resultType.getOkType().get());
      assertTrue(resultType.getErrorType().isEmpty());
    }
  }

  @Nested
  @DisplayName("Builder Tests")
  class BuilderTests {

    @Test
    @DisplayName("should create type with builder and default values")
    void shouldCreateTypeWithDefaultValues() {
      LOGGER.info("Testing builder with default values");

      BindgenType type = BindgenType.builder().name("test").build();

      assertEquals("test", type.getName());
      assertEquals(Kind.REFERENCE, type.getKind());
      assertTrue(type.getDocumentation().isEmpty());
      assertTrue(type.getFields().isEmpty());
      assertTrue(type.getCases().isEmpty());
      assertTrue(type.getEnumValues().isEmpty());
      assertTrue(type.getTupleElements().isEmpty());
    }

    @Test
    @DisplayName("should create record type with fields")
    void shouldCreateRecordTypeWithFields() {
      LOGGER.info("Testing builder for record type");

      BindgenField field1 = new BindgenField("name", BindgenType.primitive("string"));
      BindgenField field2 = new BindgenField("age", BindgenType.primitive("u32"));

      BindgenType recordType =
          BindgenType.builder()
              .name("Person")
              .kind(Kind.RECORD)
              .addField(field1)
              .addField(field2)
              .documentation("A person record")
              .build();

      assertEquals("Person", recordType.getName());
      assertEquals(Kind.RECORD, recordType.getKind());
      assertTrue(recordType.isRecord());
      assertEquals(2, recordType.getFields().size());
      assertEquals(List.of(field1, field2), recordType.getFields());
      assertTrue(recordType.getDocumentation().isPresent());
      assertEquals("A person record", recordType.getDocumentation().get());
    }

    @Test
    @DisplayName("should create record type with fields() method")
    void shouldCreateRecordTypeWithFieldsMethod() {
      LOGGER.info("Testing builder with fields() list method");

      List<BindgenField> fields =
          Arrays.asList(
              new BindgenField("x", BindgenType.primitive("i32")),
              new BindgenField("y", BindgenType.primitive("i32")));

      BindgenType recordType =
          BindgenType.builder().name("Point").kind(Kind.RECORD).fields(fields).build();

      assertEquals(2, recordType.getFields().size());
    }

    @Test
    @DisplayName("should create variant type with cases")
    void shouldCreateVariantTypeWithCases() {
      LOGGER.info("Testing builder for variant type");

      BindgenVariantCase case1 = new BindgenVariantCase("none");
      BindgenVariantCase case2 = new BindgenVariantCase("some", BindgenType.primitive("i32"));

      BindgenType variantType =
          BindgenType.builder()
              .name("MyOption")
              .kind(Kind.VARIANT)
              .addCase(case1)
              .addCase(case2)
              .build();

      assertEquals("MyOption", variantType.getName());
      assertEquals(Kind.VARIANT, variantType.getKind());
      assertTrue(variantType.isVariant());
      assertEquals(2, variantType.getCases().size());
      assertEquals(List.of(case1, case2), variantType.getCases());
    }

    @Test
    @DisplayName("should create variant type with cases() method")
    void shouldCreateVariantTypeWithCasesMethod() {
      LOGGER.info("Testing builder with cases() list method");

      List<BindgenVariantCase> cases =
          Arrays.asList(
              new BindgenVariantCase("left", BindgenType.primitive("i32")),
              new BindgenVariantCase("right", BindgenType.primitive("string")));

      BindgenType variantType =
          BindgenType.builder().name("Either").kind(Kind.VARIANT).cases(cases).build();

      assertEquals(2, variantType.getCases().size());
    }

    @Test
    @DisplayName("should create enum type with values")
    void shouldCreateEnumTypeWithValues() {
      LOGGER.info("Testing builder for enum type");

      BindgenType enumType =
          BindgenType.builder()
              .name("Color")
              .kind(Kind.ENUM)
              .addEnumValue("red")
              .addEnumValue("green")
              .addEnumValue("blue")
              .build();

      assertEquals("Color", enumType.getName());
      assertEquals(Kind.ENUM, enumType.getKind());
      assertTrue(enumType.isEnum());
      assertEquals(3, enumType.getEnumValues().size());
      assertEquals(List.of("red", "green", "blue"), enumType.getEnumValues());
    }

    @Test
    @DisplayName("should create enum type with enumValues() method")
    void shouldCreateEnumTypeWithEnumValuesMethod() {
      LOGGER.info("Testing builder with enumValues() list method");

      List<String> values = Arrays.asList("monday", "tuesday", "wednesday");

      BindgenType enumType =
          BindgenType.builder().name("Day").kind(Kind.ENUM).enumValues(values).build();

      assertEquals(3, enumType.getEnumValues().size());
      assertEquals(List.of("monday", "tuesday", "wednesday"), enumType.getEnumValues());
    }

    @Test
    @DisplayName("should create tuple type with elements")
    void shouldCreateTupleTypeWithElements() {
      LOGGER.info("Testing builder for tuple type");

      BindgenType tupleType =
          BindgenType.builder()
              .name("tuple<i32, string>")
              .kind(Kind.TUPLE)
              .tupleElements(
                  Arrays.asList(BindgenType.primitive("i32"), BindgenType.primitive("string")))
              .build();

      assertEquals(Kind.TUPLE, tupleType.getKind());
      assertEquals(2, tupleType.getTupleElements().size());
    }

    @Test
    @DisplayName("should set element type for list types")
    void shouldSetElementTypeForListTypes() {
      LOGGER.info("Testing builder with elementType()");

      BindgenType elementType = BindgenType.primitive("u8");
      BindgenType listType =
          BindgenType.builder().name("list<u8>").kind(Kind.LIST).elementType(elementType).build();

      assertTrue(listType.getElementType().isPresent());
      assertEquals(elementType, listType.getElementType().get());
    }

    @Test
    @DisplayName("should set ok and error types for result types")
    void shouldSetOkAndErrorTypes() {
      LOGGER.info("Testing builder with okType() and errorType()");

      BindgenType okType = BindgenType.primitive("string");
      BindgenType errorType = BindgenType.primitive("i32");

      BindgenType resultType =
          BindgenType.builder()
              .name("result<string, i32>")
              .kind(Kind.RESULT)
              .okType(okType)
              .errorType(errorType)
              .build();

      assertTrue(resultType.getOkType().isPresent());
      assertEquals(okType, resultType.getOkType().get());
      assertTrue(resultType.getErrorType().isPresent());
      assertEquals(errorType, resultType.getErrorType().get());
    }

    @Test
    @DisplayName("should set referenced type name")
    void shouldSetReferencedTypeName() {
      LOGGER.info("Testing builder with referencedTypeName()");

      BindgenType refType =
          BindgenType.builder()
              .name("MyType")
              .kind(Kind.REFERENCE)
              .referencedTypeName("MyType")
              .build();

      assertTrue(refType.getReferencedTypeName().isPresent());
      assertEquals("MyType", refType.getReferencedTypeName().get());
    }
  }

  @Nested
  @DisplayName("Type Check Method Tests")
  class TypeCheckMethodTests {

    @Test
    @DisplayName("isPrimitive() should return true only for primitive types")
    void isPrimitiveShouldReturnTrueOnlyForPrimitiveTypes() {
      BindgenType primitive = BindgenType.primitive("i32");
      BindgenType record = BindgenType.builder().name("R").kind(Kind.RECORD).build();

      assertTrue(primitive.isPrimitive());
      assertFalse(record.isPrimitive());
    }

    @Test
    @DisplayName("isRecord() should return true only for record types")
    void isRecordShouldReturnTrueOnlyForRecordTypes() {
      BindgenType record = BindgenType.builder().name("R").kind(Kind.RECORD).build();
      BindgenType primitive = BindgenType.primitive("i32");

      assertTrue(record.isRecord());
      assertFalse(primitive.isRecord());
    }

    @Test
    @DisplayName("isVariant() should return true only for variant types")
    void isVariantShouldReturnTrueOnlyForVariantTypes() {
      BindgenType variant = BindgenType.builder().name("V").kind(Kind.VARIANT).build();
      BindgenType primitive = BindgenType.primitive("i32");

      assertTrue(variant.isVariant());
      assertFalse(primitive.isVariant());
    }

    @Test
    @DisplayName("isEnum() should return true only for enum types")
    void isEnumShouldReturnTrueOnlyForEnumTypes() {
      BindgenType enumType = BindgenType.builder().name("E").kind(Kind.ENUM).build();
      BindgenType primitive = BindgenType.primitive("i32");

      assertTrue(enumType.isEnum());
      assertFalse(primitive.isEnum());
    }
  }

  @Nested
  @DisplayName("Equals and HashCode Tests")
  class EqualsAndHashCodeTests {

    @Test
    @DisplayName("should be equal when name and kind match")
    void shouldBeEqualWhenNameAndKindMatch() {
      LOGGER.info("Testing equals() for matching types");

      BindgenType type1 = BindgenType.primitive("i32");
      BindgenType type2 = BindgenType.primitive("i32");

      assertEquals(type2, type1);
      assertEquals(type2.hashCode(), type1.hashCode());
    }

    @Test
    @DisplayName("should not be equal when names differ")
    void shouldNotBeEqualWhenNamesDiffer() {
      LOGGER.info("Testing equals() for different names");

      BindgenType type1 = BindgenType.primitive("i32");
      BindgenType type2 = BindgenType.primitive("i64");

      assertNotEquals(type2, type1);
    }

    @Test
    @DisplayName("should not be equal when kinds differ")
    void shouldNotBeEqualWhenKindsDiffer() {
      LOGGER.info("Testing equals() for different kinds");

      BindgenType type1 = BindgenType.builder().name("MyType").kind(Kind.RECORD).build();
      BindgenType type2 = BindgenType.builder().name("MyType").kind(Kind.VARIANT).build();

      assertNotEquals(type2, type1);
    }

    @Test
    @DisplayName("should not be equal to null")
    void shouldNotBeEqualToNull() {
      BindgenType type = BindgenType.primitive("i32");

      assertNotEquals(null, type);
    }

    @Test
    @DisplayName("should not be equal to different class")
    void shouldNotBeEqualToDifferentClass() {
      BindgenType type = BindgenType.primitive("i32");

      assertNotEquals("i32", type);
    }

    @Test
    @DisplayName("should be equal to itself")
    void shouldBeEqualToItself() {
      BindgenType type = BindgenType.primitive("i32");

      assertEquals(type, type);
    }
  }

  @Nested
  @DisplayName("ToString Tests")
  class ToStringTests {

    @Test
    @DisplayName("should include name and kind in toString()")
    void shouldIncludeNameAndKindInToString() {
      LOGGER.info("Testing toString() output");

      BindgenType type = BindgenType.primitive("string");
      String toString = type.toString();

      assertTrue(toString.contains("name='string'"), "Expected toString to contain: name='string'");
      assertTrue(
          toString.contains("kind=PRIMITIVE"), "Expected toString to contain: kind=PRIMITIVE");
      assertTrue(
          toString.startsWith("BindgenType{"), "Expected toString to start with: BindgenType{");
      assertTrue(toString.endsWith("}"), "Expected toString to end with: }");
    }
  }

  @Nested
  @DisplayName("Immutability Tests")
  class ImmutabilityTests {

    @Test
    @DisplayName("fields list should be immutable")
    void fieldsListShouldBeImmutable() {
      LOGGER.info("Testing that fields list is immutable");

      BindgenType recordType =
          BindgenType.builder()
              .name("Record")
              .kind(Kind.RECORD)
              .addField(new BindgenField("f", BindgenType.primitive("i32")))
              .build();

      List<BindgenField> fields = recordType.getFields();

      assertThrows(
          UnsupportedOperationException.class,
          () -> fields.add(new BindgenField("f2", BindgenType.primitive("i32"))));
    }

    @Test
    @DisplayName("cases list should be immutable")
    void casesListShouldBeImmutable() {
      LOGGER.info("Testing that cases list is immutable");

      BindgenType variantType =
          BindgenType.builder()
              .name("Variant")
              .kind(Kind.VARIANT)
              .addCase(new BindgenVariantCase("a"))
              .build();

      List<BindgenVariantCase> cases = variantType.getCases();

      assertThrows(
          UnsupportedOperationException.class, () -> cases.add(new BindgenVariantCase("b")));
    }

    @Test
    @DisplayName("enumValues list should be immutable")
    void enumValuesListShouldBeImmutable() {
      LOGGER.info("Testing that enumValues list is immutable");

      BindgenType enumType =
          BindgenType.builder().name("Enum").kind(Kind.ENUM).addEnumValue("a").build();

      List<String> values = enumType.getEnumValues();

      assertThrows(UnsupportedOperationException.class, () -> values.add("b"));
    }

    @Test
    @DisplayName("tupleElements list should be immutable")
    void tupleElementsListShouldBeImmutable() {
      LOGGER.info("Testing that tupleElements list is immutable");

      BindgenType tupleType =
          BindgenType.builder()
              .name("Tuple")
              .kind(Kind.TUPLE)
              .tupleElements(Arrays.asList(BindgenType.primitive("i32")))
              .build();

      List<BindgenType> elements = tupleType.getTupleElements();

      assertThrows(
          UnsupportedOperationException.class, () -> elements.add(BindgenType.primitive("i64")));
    }
  }
}
