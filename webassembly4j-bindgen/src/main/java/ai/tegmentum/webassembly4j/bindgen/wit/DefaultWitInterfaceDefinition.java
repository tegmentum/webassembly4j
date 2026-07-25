/*
 * Copyright 2026 Tegmentum AI
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default {@link WitInterfaceDefinition} implementation.
 *
 * <p>Extracted from the retired {@code WitInterfaceParser} inner class so both
 * the new {@link WitParser}-based path and the legacy {@link WitInterfaceParser}
 * facade can share one implementation. Backed by
 * {@link LinkedHashMap}-based unmodifiable maps to preserve WIT declaration
 * order — cross-JVM deterministic ordering keeps generated Java source
 * byte-identical.
 */
final class DefaultWitInterfaceDefinition implements WitInterfaceDefinition {

  private final String name;
  private final String version;
  private final String packageName;
  private final Map<String, WitFunction> functions;
  private final Map<String, WitType> types;
  private final List<String> imports;
  private final List<String> exports;
  private final String witText;

  DefaultWitInterfaceDefinition(
      final String name,
      final String version,
      final String packageName,
      final Map<String, WitFunction> functions,
      final Map<String, WitType> types,
      final List<String> imports,
      final List<String> exports,
      final String witText) {
    this.name = name;
    this.version = version;
    this.packageName = packageName;
    this.functions = Collections.unmodifiableMap(new LinkedHashMap<>(functions));
    this.types = Collections.unmodifiableMap(new LinkedHashMap<>(types));
    this.imports = List.copyOf(imports);
    this.exports = List.copyOf(exports);
    this.witText = witText;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getVersion() {
    return version;
  }

  @Override
  public String getPackageName() {
    return packageName;
  }

  @Override
  public List<String> getFunctionNames() {
    return new ArrayList<>(functions.keySet());
  }

  @Override
  public Map<String, WitFunction> getFunctions() {
    return functions;
  }

  @Override
  public List<String> getTypeNames() {
    return new ArrayList<>(types.keySet());
  }

  @Override
  public Map<String, WitType> getTypes() {
    return types;
  }

  @Override
  public Set<String> getDependencies() {
    return Set.copyOf(imports);
  }

  @Override
  public WitCompatibilityResult isCompatibleWith(final WitInterfaceDefinition other) {
    if (!name.equals(other.getName())) {
      return new WitCompatibilityResult(
          false,
          "Interface names do not match: " + name + " vs " + other.getName(),
          Set.of(),
          Set.of());
    }

    final Set<String> ourFunctions = Set.copyOf(functions.keySet());
    final Set<String> otherFunctions = Set.copyOf(other.getFunctionNames());
    final Set<String> ourTypes = Set.copyOf(types.keySet());
    final Set<String> otherTypes = Set.copyOf(other.getTypeNames());

    final Set<String> missingFunctions = new HashSet<>(ourFunctions);
    missingFunctions.removeAll(otherFunctions);
    final Set<String> extraFunctions = new HashSet<>(otherFunctions);
    extraFunctions.removeAll(ourFunctions);

    final Set<String> missingTypes = new HashSet<>(ourTypes);
    missingTypes.removeAll(otherTypes);
    final Set<String> extraTypes = new HashSet<>(otherTypes);
    extraTypes.removeAll(ourTypes);

    final Set<String> allMissing = new HashSet<>();
    missingFunctions.forEach(f -> allMissing.add("function:" + f));
    missingTypes.forEach(t -> allMissing.add("type:" + t));

    final Set<String> allExtra = new HashSet<>();
    extraFunctions.forEach(f -> allExtra.add("function:" + f));
    extraTypes.forEach(t -> allExtra.add("type:" + t));

    final boolean compatible = allMissing.isEmpty() && allExtra.isEmpty();
    final String message =
        compatible
            ? "Interfaces are compatible"
            : "Interfaces differ: missing=" + allMissing + ", extra=" + allExtra;

    return new WitCompatibilityResult(compatible, message, allMissing, allExtra);
  }

  @Override
  public String getWitText() {
    return witText;
  }

  @Override
  public List<String> getImportNames() {
    return imports;
  }

  @Override
  public List<String> getExportNames() {
    return exports;
  }
}
