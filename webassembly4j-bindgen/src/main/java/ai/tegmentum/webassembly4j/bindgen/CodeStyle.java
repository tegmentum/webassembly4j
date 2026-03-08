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

package ai.tegmentum.webassembly4j.bindgen;

/**
 * Code generation style options for Java binding generation.
 *
 * <p>This enum determines the Java language features used in generated code, allowing users to
 * target different Java versions and coding preferences.
 */
public enum CodeStyle {

  /**
   * Modern Java 17+ style using records, sealed interfaces, and pattern matching.
   *
   * <p>Features used:
   *
   * <ul>
   *   <li>Records for WIT record types
   *   <li>Sealed interfaces with record implementations for variants
   *   <li>Pattern matching support in switch expressions
   *   <li>Text blocks for generated documentation
   * </ul>
   */
  MODERN("17"),

  /**
   * Legacy Java 8+ compatible style using POJOs, interfaces, and builder patterns.
   *
   * <p>Features used:
   *
   * <ul>
   *   <li>POJOs with final fields for WIT record types
   *   <li>Abstract classes with visitor pattern for variants
   *   <li>Builder pattern for complex types
   *   <li>Traditional Javadoc formatting
   * </ul>
   */
  LEGACY("8");

  private final String minimumJavaVersion;

  CodeStyle(final String minimumJavaVersion) {
    this.minimumJavaVersion = minimumJavaVersion;
  }

  /**
   * Returns the minimum Java version required for this code style.
   *
   * @return the minimum Java version as a string (e.g., "8", "17")
   */
  public String getMinimumJavaVersion() {
    return minimumJavaVersion;
  }

  /**
   * Checks if this code style supports records.
   *
   * @return true if records are used, false otherwise
   */
  public boolean supportsRecords() {
    return this == MODERN;
  }

  /**
   * Checks if this code style supports sealed interfaces.
   *
   * @return true if sealed interfaces are used, false otherwise
   */
  public boolean supportsSealedInterfaces() {
    return this == MODERN;
  }

  /**
   * Checks if this code style generates builder classes.
   *
   * @return true if builder classes are generated, false otherwise
   */
  public boolean generatesBuilders() {
    return this == LEGACY;
  }
}
