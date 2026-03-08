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
package ai.tegmentum.webassembly4j.bindgen.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for JavaNaming utility. */
class JavaNamingTest {

  @Test
  void shouldConvertKebabCaseToClassName() {
    assertEquals("HttpRequest", JavaNaming.toClassName("http-request"));
    assertEquals("MyCustomType", JavaNaming.toClassName("my-custom-type"));
    assertEquals("Simple", JavaNaming.toClassName("simple"));
  }

  @Test
  void shouldConvertKebabCaseToFieldName() {
    assertEquals("getValue", JavaNaming.toFieldName("get-value"));
    assertEquals("myField", JavaNaming.toFieldName("my-field"));
    assertEquals("simple", JavaNaming.toFieldName("simple"));
  }

  @Test
  void shouldConvertToEnumConstant() {
    assertEquals("MY_VALUE", JavaNaming.toEnumConstant("my-value"));
    assertEquals("SIMPLE", JavaNaming.toEnumConstant("simple"));
    assertEquals("HTTP_REQUEST", JavaNaming.toEnumConstant("http-request"));
  }

  @Test
  void shouldConvertWitPackageToJavaPackage() {
    assertEquals("wasi.http", JavaNaming.toPackageName("wasi:http"));
    assertEquals("my_package.types", JavaNaming.toPackageName("my-package:types"));
    assertEquals("", JavaNaming.toPackageName(""));
  }

  @Test
  void shouldEscapeJavaKeywords() {
    assertEquals("class_", JavaNaming.toFieldName("class"));
    assertEquals("import_", JavaNaming.toFieldName("import"));
    assertEquals("default_", JavaNaming.toFieldName("default"));
    assertEquals("record_", JavaNaming.toFieldName("record"));
  }

  @Test
  void shouldIdentifyKeywords() {
    assertTrue(JavaNaming.isKeyword("class"));
    assertTrue(JavaNaming.isKeyword("void"));
    assertTrue(JavaNaming.isKeyword("record"));
    assertFalse(JavaNaming.isKeyword("myField"));
  }

  @Test
  void shouldValidateIdentifiers() {
    assertTrue(JavaNaming.isValidIdentifier("myField"));
    assertTrue(JavaNaming.isValidIdentifier("MyClass"));
    assertTrue(JavaNaming.isValidIdentifier("_private"));
    assertFalse(JavaNaming.isValidIdentifier("123invalid"));
    assertFalse(JavaNaming.isValidIdentifier("class")); // keyword
    assertFalse(JavaNaming.isValidIdentifier(""));
    assertFalse(JavaNaming.isValidIdentifier(null));
  }

  @Test
  void shouldHandleSnakeCaseInput() {
    assertEquals("MyCustomType", JavaNaming.toClassName("my_custom_type"));
    assertEquals("getValue", JavaNaming.toFieldName("get_value"));
  }

  @Test
  void shouldHandleMixedCase() {
    assertEquals("HttpRequestType", JavaNaming.toClassName("http-request_type"));
  }

  @Test
  void shouldThrowOnEmptyClassName() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> JavaNaming.toClassName(""));
    assertTrue(
        exception.getMessage().contains("cannot be empty"),
        "Expected message to contain: cannot be empty");
  }

  @Test
  void shouldThrowOnNullClassName() {
    assertThrows(NullPointerException.class, () -> JavaNaming.toClassName(null));
  }

  @Test
  void shouldHandleLeadingDigits() {
    // Leading digits should be prefixed with underscore
    assertEquals("_123Type", JavaNaming.toClassName("123-type"));
    assertEquals("_2ndValue", JavaNaming.toFieldName("2nd-value"));
  }
}
