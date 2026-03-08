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
package ai.tegmentum.webassembly4j.bindgen.wit;

import java.util.Objects;
import java.util.Optional;

/**
 * WIT parameter representation.
 *
 * <p>Represents a function parameter definition parsed from a WIT (WebAssembly Interface Type)
 * interface, including its name, type, optionality, and documentation.
 *
 * @since 1.0.0
 */
public final class WitParameter {
  private final String name;
  private final WitType type;
  private final boolean isOptional;
  private final Optional<String> documentation;

  /**
   * Creates a new WIT function parameter.
   *
   * @param name the parameter name
   * @param type the parameter type
   * @param isOptional whether the parameter is optional
   * @param documentation optional documentation for the parameter
   */
  public WitParameter(
      final String name,
      final WitType type,
      final boolean isOptional,
      final Optional<String> documentation) {
    this.name = Objects.requireNonNull(name);
    this.type = Objects.requireNonNull(type);
    this.isOptional = isOptional;
    this.documentation = Objects.requireNonNull(documentation);
  }

  /**
   * Gets the parameter name.
   *
   * @return the parameter name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the parameter type.
   *
   * @return the WIT type of this parameter
   */
  public WitType getType() {
    return type;
  }

  /**
   * Checks if the parameter is optional.
   *
   * @return true if the parameter is optional, false otherwise
   */
  public boolean isOptional() {
    return isOptional;
  }

  /**
   * Gets the parameter documentation.
   *
   * @return optional documentation string
   */
  public Optional<String> getDocumentation() {
    return documentation;
  }
}
