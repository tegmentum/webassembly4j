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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * WIT function representation.
 *
 * <p>Represents a function definition parsed from a WIT (WebAssembly Interface Type) interface,
 * including its name, parameters, return types, and documentation.
 *
 * @since 1.0.0
 */
public final class WitFunction {
  private final String name;
  private final List<WitParameter> parameters;
  private final List<WitType> returnTypes;
  private final boolean isAsync;
  private final Optional<String> documentation;

  /**
   * Creates a new WIT function definition.
   *
   * @param name the function name
   * @param parameters the function parameters
   * @param returnTypes the function return types
   * @param isAsync whether the function is asynchronous
   * @param documentation optional documentation for the function
   */
  public WitFunction(
      final String name,
      final List<WitParameter> parameters,
      final List<WitType> returnTypes,
      final boolean isAsync,
      final Optional<String> documentation) {
    this.name = Objects.requireNonNull(name);
    this.parameters = List.copyOf(Objects.requireNonNull(parameters));
    this.returnTypes = List.copyOf(Objects.requireNonNull(returnTypes));
    this.isAsync = isAsync;
    this.documentation = Objects.requireNonNull(documentation);
  }

  /**
   * Gets the function name.
   *
   * @return the function name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the function parameters.
   *
   * @return an unmodifiable list of function parameters
   */
  public List<WitParameter> getParameters() {
    return parameters;
  }

  /**
   * Gets the function return types.
   *
   * @return an unmodifiable list of return types
   */
  public List<WitType> getReturnTypes() {
    return returnTypes;
  }

  /**
   * Checks if the function is asynchronous.
   *
   * @return true if the function is async, false otherwise
   */
  public boolean isAsync() {
    return isAsync;
  }

  /**
   * Gets the function documentation.
   *
   * @return optional documentation string
   */
  public Optional<String> getDocumentation() {
    return documentation;
  }
}
