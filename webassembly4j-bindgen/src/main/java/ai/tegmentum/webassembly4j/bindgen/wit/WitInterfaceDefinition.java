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
import java.util.Map;
import java.util.Set;

/**
 * Represents a WebAssembly Interface Type (WIT) interface definition.
 *
 * <p>WIT interfaces define the contract between WebAssembly components, providing type-safe
 * interaction and composition capabilities in the Component Model.
 *
 * <p>This interface provides access to interface metadata, type definitions, function signatures,
 * and validation capabilities.
 *
 * @since 1.0.0
 */
public interface WitInterfaceDefinition {

  /**
   * Gets the name of this WIT interface.
   *
   * @return the interface name
   */
  String getName();

  /**
   * Gets the version of this WIT interface.
   *
   * @return the interface version as a string
   */
  String getVersion();

  /**
   * Gets the package name containing this interface.
   *
   * @return the package name
   */
  String getPackageName();

  /**
   * Gets all function names in this interface.
   *
   * @return list of function names
   */
  List<String> getFunctionNames();

  /**
   * Gets all functions defined in this interface.
   *
   * @return unmodifiable map of function name to function definition
   */
  default Map<String, WitFunction> getFunctions() {
    return Map.of();
  }

  /**
   * Gets all type names in this interface.
   *
   * @return list of type names
   */
  List<String> getTypeNames();

  /**
   * Gets all types defined in this interface.
   *
   * @return unmodifiable map of type name to type definition
   */
  default Map<String, WitType> getTypes() {
    return Map.of();
  }

  /**
   * Gets the interfaces that this interface depends on.
   *
   * @return set of interface dependencies
   */
  Set<String> getDependencies();

  /**
   * Checks if this interface is compatible with another interface.
   *
   * @param other the other interface to check compatibility with
   * @return compatibility result
   */
  WitCompatibilityResult isCompatibleWith(WitInterfaceDefinition other);

  /**
   * Gets the raw WIT definition text.
   *
   * @return the WIT definition as text
   */
  String getWitText();

  /**
   * Gets imports required by this interface.
   *
   * @return list of import names
   */
  List<String> getImportNames();

  /**
   * Gets exports provided by this interface.
   *
   * @return list of export names
   */
  List<String> getExportNames();
}
