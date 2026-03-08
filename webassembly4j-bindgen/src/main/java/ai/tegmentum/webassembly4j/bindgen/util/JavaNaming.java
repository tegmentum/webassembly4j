/*
 * Copyright 2024 Tegmentum AI. All rights reserved.
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utility class for converting WIT/WASM names to valid Java identifiers.
 *
 * <p>This class handles the conversion of WIT naming conventions (kebab-case) to Java naming
 * conventions (camelCase for methods/fields, PascalCase for classes).
 */
public final class JavaNaming {

  private static final Pattern KEBAB_PATTERN = Pattern.compile("-");
  private static final Pattern SNAKE_PATTERN = Pattern.compile("_");
  private static final Pattern NON_JAVA_IDENTIFIER = Pattern.compile("[^a-zA-Z0-9_$]");

  /** Java reserved keywords that cannot be used as identifiers. */
  private static final Set<String> JAVA_KEYWORDS =
      new HashSet<>(
          Arrays.asList(
              "abstract",
              "assert",
              "boolean",
              "break",
              "byte",
              "case",
              "catch",
              "char",
              "class",
              "const",
              "continue",
              "default",
              "do",
              "double",
              "else",
              "enum",
              "extends",
              "false",
              "final",
              "finally",
              "float",
              "for",
              "goto",
              "if",
              "implements",
              "import",
              "instanceof",
              "int",
              "interface",
              "long",
              "native",
              "new",
              "null",
              "package",
              "private",
              "protected",
              "public",
              "return",
              "short",
              "static",
              "strictfp",
              "super",
              "switch",
              "synchronized",
              "this",
              "throw",
              "throws",
              "transient",
              "true",
              "try",
              "void",
              "volatile",
              "while",
              "var",
              "yield",
              "record",
              "sealed",
              "permits"));

  private JavaNaming() {
    // Utility class
  }

  /**
   * Converts a WIT type name to a Java class name (PascalCase).
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>"http-request" → "HttpRequest"
   *   <li>"my-custom-type" → "MyCustomType"
   *   <li>"simple" → "Simple"
   * </ul>
   *
   * @param witName the WIT type name
   * @return the Java class name
   */
  public static String toClassName(final String witName) {
    Objects.requireNonNull(witName, "witName");
    if (witName.isEmpty()) {
      throw new IllegalArgumentException("WIT name cannot be empty");
    }
    String result = toPascalCase(sanitize(witName));
    // Ensure result starts with a letter or underscore
    if (!result.isEmpty() && Character.isDigit(result.charAt(0))) {
      result = "_" + result;
    }
    return result;
  }

  /**
   * Converts a WIT field or function name to a Java method/field name (camelCase).
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>"get-value" → "getValue"
   *   <li>"my-field" → "myField"
   *   <li>"simple" → "simple"
   * </ul>
   *
   * @param witName the WIT field or function name
   * @return the Java field/method name
   */
  public static String toFieldName(final String witName) {
    Objects.requireNonNull(witName, "witName");
    if (witName.isEmpty()) {
      throw new IllegalArgumentException("WIT name cannot be empty");
    }
    String result = toCamelCase(sanitize(witName));
    // Ensure result starts with a letter or underscore
    if (!result.isEmpty() && Character.isDigit(result.charAt(0))) {
      result = "_" + result;
    }
    return escapeKeyword(result);
  }

  /**
   * Converts a WIT function name to a Java method name (camelCase).
   *
   * @param witName the WIT function name
   * @return the Java method name
   */
  public static String toMethodName(final String witName) {
    return toFieldName(witName);
  }

  /**
   * Converts a WIT parameter name to a Java parameter name.
   *
   * @param witName the WIT parameter name
   * @return the Java parameter name
   */
  public static String toParameterName(final String witName) {
    return toFieldName(witName);
  }

  /**
   * Converts a WIT enum variant name to a Java enum constant name (UPPER_SNAKE_CASE).
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>"my-value" → "MY_VALUE"
   *   <li>"simple" → "SIMPLE"
   * </ul>
   *
   * @param witName the WIT enum variant name
   * @return the Java enum constant name
   */
  public static String toEnumConstant(final String witName) {
    Objects.requireNonNull(witName, "witName");
    if (witName.isEmpty()) {
      throw new IllegalArgumentException("WIT name cannot be empty");
    }
    return toUpperSnakeCase(sanitize(witName));
  }

  /**
   * Converts a WIT constant name to a Java constant name (UPPER_SNAKE_CASE).
   *
   * @param witName the WIT constant name
   * @return the Java constant name
   */
  public static String toConstantName(final String witName) {
    return toEnumConstant(witName);
  }

  /**
   * Converts a WIT package name to a Java package name.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>"wasi:http" → "wasi.http"
   *   <li>"my-package:types" → "my_package.types"
   * </ul>
   *
   * @param witPackage the WIT package name
   * @return the Java package name
   */
  public static String toPackageName(final String witPackage) {
    Objects.requireNonNull(witPackage, "witPackage");
    if (witPackage.isEmpty()) {
      return "";
    }
    // Replace : with . and - with _
    String result = witPackage.replace(':', '.').replace('-', '_');
    // Make lowercase
    return result.toLowerCase(Locale.ROOT);
  }

  /**
   * Checks if the given name is a Java reserved keyword.
   *
   * @param name the name to check
   * @return true if it's a reserved keyword
   */
  public static boolean isKeyword(final String name) {
    return JAVA_KEYWORDS.contains(name);
  }

  /**
   * Escapes a name if it's a Java reserved keyword by appending an underscore.
   *
   * @param name the name to escape
   * @return the escaped name if it was a keyword, otherwise the original name
   */
  public static String escapeKeyword(final String name) {
    if (isKeyword(name)) {
      return name + "_";
    }
    return name;
  }

  /**
   * Checks if the given name is a valid Java identifier.
   *
   * @param name the name to check
   * @return true if it's a valid Java identifier
   */
  public static boolean isValidIdentifier(final String name) {
    if (name == null || name.isEmpty()) {
      return false;
    }
    if (!Character.isJavaIdentifierStart(name.charAt(0))) {
      return false;
    }
    for (int i = 1; i < name.length(); i++) {
      if (!Character.isJavaIdentifierPart(name.charAt(i))) {
        return false;
      }
    }
    return !isKeyword(name);
  }

  /**
   * Sanitizes a name by removing or replacing invalid characters.
   *
   * @param name the name to sanitize
   * @return the sanitized name
   */
  private static String sanitize(final String name) {
    // Replace invalid characters with underscores
    String sanitized = NON_JAVA_IDENTIFIER.matcher(name).replaceAll("_");
    // Ensure it starts with a letter or underscore
    if (!sanitized.isEmpty() && Character.isDigit(sanitized.charAt(0))) {
      sanitized = "_" + sanitized;
    }
    return sanitized;
  }

  /**
   * Converts a kebab-case or snake_case string to PascalCase.
   *
   * @param input the input string
   * @return the PascalCase string
   */
  private static String toPascalCase(final String input) {
    // Split on hyphens and underscores
    String[] parts = KEBAB_PATTERN.split(input);
    StringBuilder result = new StringBuilder();
    for (String part : parts) {
      String[] subParts = SNAKE_PATTERN.split(part);
      for (String subPart : subParts) {
        if (!subPart.isEmpty()) {
          result.append(Character.toUpperCase(subPart.charAt(0)));
          if (subPart.length() > 1) {
            result.append(subPart.substring(1).toLowerCase(Locale.ROOT));
          }
        }
      }
    }
    return result.toString();
  }

  /**
   * Converts a kebab-case or snake_case string to camelCase.
   *
   * @param input the input string
   * @return the camelCase string
   */
  private static String toCamelCase(final String input) {
    String pascalCase = toPascalCase(input);
    if (pascalCase.isEmpty()) {
      return pascalCase;
    }
    return Character.toLowerCase(pascalCase.charAt(0)) + pascalCase.substring(1);
  }

  /**
   * Converts a kebab-case or camelCase string to UPPER_SNAKE_CASE.
   *
   * @param input the input string
   * @return the UPPER_SNAKE_CASE string
   */
  private static String toUpperSnakeCase(final String input) {
    // Replace hyphens with underscores
    String result = KEBAB_PATTERN.matcher(input).replaceAll("_");
    // Insert underscores before uppercase letters (for camelCase input)
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < result.length(); i++) {
      char c = result.charAt(i);
      if (i > 0 && Character.isUpperCase(c) && Character.isLowerCase(result.charAt(i - 1))) {
        sb.append('_');
      }
      sb.append(Character.toUpperCase(c));
    }
    return sb.toString();
  }
}
