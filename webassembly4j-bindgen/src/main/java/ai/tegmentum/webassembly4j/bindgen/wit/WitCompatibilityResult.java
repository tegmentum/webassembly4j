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

import java.util.Set;

/**
 * Result of WIT interface compatibility checking.
 *
 * <p>This class provides detailed information about the compatibility between two WIT interfaces,
 * including compatibility status and any issues found.
 *
 * @since 1.0.0
 */
public final class WitCompatibilityResult {

  private final boolean compatible;
  private final String details;
  private final Set<String> satisfiedImports;
  private final Set<String> unsatisfiedImports;

  /**
   * Creates a new WIT compatibility result.
   *
   * @param compatible whether the interfaces are compatible
   * @param details compatibility details
   * @param satisfiedImports set of satisfied imports
   * @param unsatisfiedImports set of unsatisfied imports
   */
  public WitCompatibilityResult(
      final boolean compatible,
      final String details,
      final Set<String> satisfiedImports,
      final Set<String> unsatisfiedImports) {
    this.compatible = compatible;
    this.details = details;
    this.satisfiedImports = Set.copyOf(satisfiedImports);
    this.unsatisfiedImports = Set.copyOf(unsatisfiedImports);
  }

  /**
   * Creates a compatible result.
   *
   * @param details compatibility details
   * @param satisfiedImports set of satisfied imports
   * @return a compatible result
   */
  public static WitCompatibilityResult compatible(
      final String details, final Set<String> satisfiedImports) {
    return new WitCompatibilityResult(true, details, satisfiedImports, Set.of());
  }

  /**
   * Creates an incompatible result with the given issues.
   *
   * @param details compatibility details
   * @param unsatisfiedImports set of unsatisfied imports
   * @return an incompatible result
   */
  public static WitCompatibilityResult incompatible(
      final String details, final Set<String> unsatisfiedImports) {
    return new WitCompatibilityResult(false, details, Set.of(), unsatisfiedImports);
  }

  /**
   * Checks if the interfaces are compatible.
   *
   * @return true if compatible, false otherwise
   */
  public boolean isCompatible() {
    return compatible;
  }

  /**
   * Gets the compatibility details.
   *
   * @return the compatibility details
   */
  public String getDetails() {
    return details;
  }

  /**
   * Gets the set of satisfied imports.
   *
   * @return set of satisfied imports
   */
  public Set<String> getSatisfiedImports() {
    return satisfiedImports;
  }

  /**
   * Gets the set of unsatisfied imports.
   *
   * @return set of unsatisfied imports
   */
  public Set<String> getUnsatisfiedImports() {
    return unsatisfiedImports;
  }

  /**
   * Checks if there are any unsatisfied imports.
   *
   * @return true if there are unsatisfied imports
   */
  public boolean hasUnsatisfiedImports() {
    return !unsatisfiedImports.isEmpty();
  }

  @Override
  public String toString() {
    return "WitCompatibilityResult{compatible=" + compatible + ", details='" + details + "'}";
  }
}
