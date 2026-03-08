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

import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link BindgenInterface}. */
@DisplayName("BindgenInterface Tests")
class BindgenInterfaceTest {

  private static final Logger LOGGER = Logger.getLogger(BindgenInterfaceTest.class.getName());

  @Nested
  @DisplayName("Builder Tests")
  class BuilderTests {

    @Test
    @DisplayName("should create interface with builder and default values")
    void shouldCreateInterfaceWithDefaultValues() {
      LOGGER.info("Testing builder with default values");

      BindgenInterface iface = BindgenInterface.builder().name("my-interface").build();

      assertEquals("my-interface", iface.getName());
      assertTrue(iface.getPackageName().isEmpty());
      assertTrue(iface.getTypes().isEmpty());
      assertTrue(iface.getFunctions().isEmpty());
      assertTrue(iface.getDocumentation().isEmpty());
    }

    @Test
    @DisplayName("should create interface with package name")
    void shouldCreateInterfaceWithPackageName() {
      LOGGER.info("Testing builder with package name");

      BindgenInterface iface =
          BindgenInterface.builder().name("types").packageName("wasi:io").build();

      assertEquals("types", iface.getName());
      assertTrue(iface.getPackageName().isPresent());
      assertEquals("wasi:io", iface.getPackageName().get());
    }

    @Test
    @DisplayName("should create interface with types using addType")
    void shouldCreateInterfaceWithTypesUsingAddType() {
      LOGGER.info("Testing builder with addType()");

      BindgenType type1 = BindgenType.primitive("i32");
      BindgenType type2 =
          BindgenType.builder().name("MyRecord").kind(BindgenType.Kind.RECORD).build();

      BindgenInterface iface =
          BindgenInterface.builder().name("types").addType(type1).addType(type2).build();

      assertEquals(2, iface.getTypes().size());
      assertEquals(List.of(type1, type2), iface.getTypes());
    }

    @Test
    @DisplayName("should create interface with types() method")
    void shouldCreateInterfaceWithTypesMethod() {
      LOGGER.info("Testing builder with types() list method");

      List<BindgenType> types =
          Arrays.asList(BindgenType.primitive("string"), BindgenType.primitive("bool"));

      BindgenInterface iface = BindgenInterface.builder().name("primitives").types(types).build();

      assertEquals(2, iface.getTypes().size());
    }

    @Test
    @DisplayName("should create interface with functions using addFunction")
    void shouldCreateInterfaceWithFunctionsUsingAddFunction() {
      LOGGER.info("Testing builder with addFunction()");

      BindgenFunction func1 = BindgenFunction.builder().name("get").build();
      BindgenFunction func2 = BindgenFunction.builder().name("set").build();

      BindgenInterface iface =
          BindgenInterface.builder().name("api").addFunction(func1).addFunction(func2).build();

      assertEquals(2, iface.getFunctions().size());
      assertEquals(List.of(func1, func2), iface.getFunctions());
    }

    @Test
    @DisplayName("should create interface with functions() method")
    void shouldCreateInterfaceWithFunctionsMethod() {
      LOGGER.info("Testing builder with functions() list method");

      List<BindgenFunction> functions =
          Arrays.asList(
              BindgenFunction.builder().name("read").build(),
              BindgenFunction.builder().name("write").build());

      BindgenInterface iface = BindgenInterface.builder().name("io").functions(functions).build();

      assertEquals(2, iface.getFunctions().size());
    }

    @Test
    @DisplayName("should create interface with documentation")
    void shouldCreateInterfaceWithDocumentation() {
      LOGGER.info("Testing builder with documentation");

      BindgenInterface iface =
          BindgenInterface.builder()
              .name("documented")
              .documentation("This interface provides utility functions")
              .build();

      assertTrue(iface.getDocumentation().isPresent());
      assertEquals("This interface provides utility functions", iface.getDocumentation().get());
    }

    @Test
    @DisplayName("should create fully configured interface")
    void shouldCreateFullyConfiguredInterface() {
      LOGGER.info("Testing builder with all options");

      BindgenType type = BindgenType.primitive("i32");
      BindgenFunction function = BindgenFunction.builder().name("compute").build();

      BindgenInterface iface =
          BindgenInterface.builder()
              .name("calculator")
              .packageName("math:core")
              .addType(type)
              .addFunction(function)
              .documentation("Calculator interface")
              .build();

      assertEquals("calculator", iface.getName());
      assertTrue(iface.getPackageName().isPresent());
      assertEquals("math:core", iface.getPackageName().get());
      assertEquals(1, iface.getTypes().size());
      assertEquals(1, iface.getFunctions().size());
      assertTrue(iface.getDocumentation().isPresent());
    }
  }

  @Nested
  @DisplayName("Fully Qualified Name Tests")
  class FullyQualifiedNameTests {

    @Test
    @DisplayName("should return name only when no package name")
    void shouldReturnNameOnlyWhenNoPackageName() {
      LOGGER.info("Testing getFullyQualifiedName() without package");

      BindgenInterface iface = BindgenInterface.builder().name("types").build();

      assertEquals("types", iface.getFullyQualifiedName());
    }

    @Test
    @DisplayName("should return package/name when package name is set")
    void shouldReturnPackageSlashNameWhenPackageSet() {
      LOGGER.info("Testing getFullyQualifiedName() with package");

      BindgenInterface iface =
          BindgenInterface.builder().name("types").packageName("wasi:io").build();

      assertEquals("wasi:io/types", iface.getFullyQualifiedName());
    }

    @Test
    @DisplayName("should return name only when package name is empty string")
    void shouldReturnNameOnlyWhenPackageNameIsEmpty() {
      LOGGER.info("Testing getFullyQualifiedName() with empty package");

      BindgenInterface iface = BindgenInterface.builder().name("types").packageName("").build();

      assertEquals("types", iface.getFullyQualifiedName());
    }
  }

  @Nested
  @DisplayName("Getter Tests")
  class GetterTests {

    @Test
    @DisplayName("getName() should return interface name")
    void getNameShouldReturnInterfaceName() {
      BindgenInterface iface = BindgenInterface.builder().name("test-interface").build();

      assertEquals("test-interface", iface.getName());
    }

    @Test
    @DisplayName("getPackageName() should return empty when not set")
    void getPackageNameShouldReturnEmptyWhenNotSet() {
      BindgenInterface iface = BindgenInterface.builder().name("test").build();

      assertTrue(iface.getPackageName().isEmpty());
    }

    @Test
    @DisplayName("getPackageName() should return value when set")
    void getPackageNameShouldReturnValueWhenSet() {
      BindgenInterface iface =
          BindgenInterface.builder().name("test").packageName("my:pkg").build();

      assertTrue(iface.getPackageName().isPresent());
      assertEquals("my:pkg", iface.getPackageName().get());
    }

    @Test
    @DisplayName("getTypes() should return empty list when no types")
    void getTypesShouldReturnEmptyListWhenNoTypes() {
      BindgenInterface iface = BindgenInterface.builder().name("empty").build();

      assertTrue(iface.getTypes().isEmpty());
    }

    @Test
    @DisplayName("getFunctions() should return empty list when no functions")
    void getFunctionsShouldReturnEmptyListWhenNoFunctions() {
      BindgenInterface iface = BindgenInterface.builder().name("empty").build();

      assertTrue(iface.getFunctions().isEmpty());
    }
  }

  @Nested
  @DisplayName("Equals and HashCode Tests")
  class EqualsAndHashCodeTests {

    @Test
    @DisplayName("should be equal when name and packageName match")
    void shouldBeEqualWhenNameAndPackageNameMatch() {
      LOGGER.info("Testing equals() for matching interfaces");

      BindgenInterface iface1 =
          BindgenInterface.builder().name("types").packageName("wasi:io").build();

      BindgenInterface iface2 =
          BindgenInterface.builder().name("types").packageName("wasi:io").build();

      assertEquals(iface2, iface1);
      assertEquals(iface2.hashCode(), iface1.hashCode());
    }

    @Test
    @DisplayName("should be equal even when types differ")
    void shouldBeEqualEvenWhenTypesDiffer() {
      LOGGER.info("Testing equals() ignores types");

      BindgenInterface iface1 =
          BindgenInterface.builder().name("types").addType(BindgenType.primitive("i32")).build();

      BindgenInterface iface2 =
          BindgenInterface.builder().name("types").addType(BindgenType.primitive("i64")).build();

      assertEquals(iface2, iface1);
    }

    @Test
    @DisplayName("should not be equal when names differ")
    void shouldNotBeEqualWhenNamesDiffer() {
      LOGGER.info("Testing equals() for different names");

      BindgenInterface iface1 = BindgenInterface.builder().name("types").build();
      BindgenInterface iface2 = BindgenInterface.builder().name("functions").build();

      assertNotEquals(iface2, iface1);
    }

    @Test
    @DisplayName("should not be equal when packageNames differ")
    void shouldNotBeEqualWhenPackageNamesDiffer() {
      LOGGER.info("Testing equals() for different package names");

      BindgenInterface iface1 =
          BindgenInterface.builder().name("types").packageName("pkg1").build();

      BindgenInterface iface2 =
          BindgenInterface.builder().name("types").packageName("pkg2").build();

      assertNotEquals(iface2, iface1);
    }

    @Test
    @DisplayName("should not be equal to null")
    void shouldNotBeEqualToNull() {
      BindgenInterface iface = BindgenInterface.builder().name("test").build();

      assertNotEquals(null, iface);
    }

    @Test
    @DisplayName("should not be equal to different class")
    void shouldNotBeEqualToDifferentClass() {
      BindgenInterface iface = BindgenInterface.builder().name("test").build();

      assertNotEquals("test", iface);
    }

    @Test
    @DisplayName("should be equal to itself")
    void shouldBeEqualToItself() {
      BindgenInterface iface = BindgenInterface.builder().name("test").build();

      assertEquals(iface, iface);
    }
  }

  @Nested
  @DisplayName("ToString Tests")
  class ToStringTests {

    @Test
    @DisplayName("should include name and counts in toString()")
    void shouldIncludeNameAndCountsInToString() {
      LOGGER.info("Testing toString() output");

      BindgenInterface iface =
          BindgenInterface.builder()
              .name("api")
              .addType(BindgenType.primitive("i32"))
              .addFunction(BindgenFunction.builder().name("get").build())
              .build();

      String toString = iface.toString();

      assertTrue(toString.contains("name='api'"), "Expected toString to contain: name='api'");
      assertTrue(toString.contains("types=1"), "Expected toString to contain: types=1");
      assertTrue(toString.contains("functions=1"), "Expected toString to contain: functions=1");
      assertTrue(
          toString.startsWith("BindgenInterface{"),
          "Expected toString to start with: BindgenInterface{");
      assertTrue(toString.endsWith("}"), "Expected toString to end with: }");
    }

    @Test
    @DisplayName("should include fully qualified name in toString()")
    void shouldIncludeFullyQualifiedNameInToString() {
      BindgenInterface iface =
          BindgenInterface.builder().name("types").packageName("wasi:io").build();

      String toString = iface.toString();

      assertTrue(
          toString.contains("name='wasi:io/types'"),
          "Expected toString to contain: name='wasi:io/types'");
    }
  }

  @Nested
  @DisplayName("Immutability Tests")
  class ImmutabilityTests {

    @Test
    @DisplayName("types list should be immutable")
    void typesListShouldBeImmutable() {
      LOGGER.info("Testing that types list is immutable");

      BindgenInterface iface =
          BindgenInterface.builder().name("test").addType(BindgenType.primitive("i32")).build();

      List<BindgenType> types = iface.getTypes();

      assertThrows(
          UnsupportedOperationException.class, () -> types.add(BindgenType.primitive("i64")));
    }

    @Test
    @DisplayName("functions list should be immutable")
    void functionsListShouldBeImmutable() {
      LOGGER.info("Testing that functions list is immutable");

      BindgenInterface iface =
          BindgenInterface.builder()
              .name("test")
              .addFunction(BindgenFunction.builder().name("get").build())
              .build();

      List<BindgenFunction> functions = iface.getFunctions();

      assertThrows(
          UnsupportedOperationException.class,
          () -> functions.add(BindgenFunction.builder().name("set").build()));
    }
  }
}
