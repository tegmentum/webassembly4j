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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The parsed result of a full WIT source file.
 *
 * <p>Emitted by {@link WitParser}, this replaces the wrapper-chain output
 * (retired {@code WitWorldPreprocessor.Interface} + separate resource-body
 * parse) with a single flat representation the {@code CodeGenerator} can
 * consume directly. Interfaces and worlds are surfaced as
 * {@link ParsedInterface} entries in WIT declaration order with
 * {@link ParsedInterface#isWorld()} distinguishing world entries from
 * plain interfaces.
 *
 * <p>Resources declared inside an interface or world body are visible in
 * two ways on the same entry: the resource {@code type} lands in
 * {@link ParsedInterface#getTypes()} with a {@code RESOURCE} kind, and its
 * methods land in {@link ParsedInterface#getResourceMethods()} keyed by
 * resource name. This single structural view removes the second
 * (WitResourceBodyParser) parse pass the previous stack required.
 */
public final class WitDocument {

  private final Optional<String> packageName;
  private final Optional<String> packageVersion;
  private final List<ParsedInterface> interfaces;

  WitDocument(
      final Optional<String> packageName,
      final Optional<String> packageVersion,
      final List<ParsedInterface> interfaces) {
    this.packageName = packageName;
    this.packageVersion = packageVersion;
    this.interfaces = List.copyOf(interfaces);
  }

  /** The declared {@code package} name (e.g. {@code wasmos:host}), if present. */
  public Optional<String> getPackageName() {
    return packageName;
  }

  /** The declared {@code @version} after the package name, if present. */
  public Optional<String> getPackageVersion() {
    return packageVersion;
  }

  /**
   * Every interface and world in the source, in WIT declaration order.
   * Worlds carry {@link ParsedInterface#isWorld()} true; the caller
   * decides whether to hoist their types onto the model root or emit
   * them under a Java interface carrier.
   */
  public List<ParsedInterface> getInterfaces() {
    return interfaces;
  }

  /**
   * A single interface or hoisted world, with all its types, top-level
   * functions, and per-resource method lists resolved.
   */
  public static final class ParsedInterface {

    private final String name;
    private final boolean world;
    private final Map<String, WitType> types;
    private final Map<String, WitFunction> functions;
    private final Map<String, List<WitFunction>> resourceMethods;

    ParsedInterface(
        final String name,
        final boolean world,
        final Map<String, WitType> types,
        final Map<String, WitFunction> functions,
        final Map<String, List<WitFunction>> resourceMethods) {
      this.name = name;
      this.world = world;
      // Preserve WIT declaration order across JVMs — see the note on
      // WitInterfaceParser.parseTypes for why LinkedHashMap-backed
      // unmodifiable maps are load-bearing for byte-identical output.
      this.types = Collections.unmodifiableMap(new LinkedHashMap<>(types));
      this.functions = Collections.unmodifiableMap(new LinkedHashMap<>(functions));
      final Map<String, List<WitFunction>> copy = new LinkedHashMap<>();
      for (final Map.Entry<String, List<WitFunction>> e : resourceMethods.entrySet()) {
        copy.put(e.getKey(), List.copyOf(e.getValue()));
      }
      this.resourceMethods = Collections.unmodifiableMap(copy);
    }

    /** The interface / world name as written in WIT. */
    public String getName() {
      return name;
    }

    /**
     * True when this entry was projected from a {@code world X { ... }}
     * block. Worlds' import / export / use clauses reference already-
     * declared interfaces and don't contribute new type surface; the
     * caller typically hoists a world's types to top-level model types
     * rather than wrap them in an empty Java carrier.
     */
    public boolean isWorld() {
      return world;
    }

    /**
     * All types declared in the body, keyed by WIT name. Includes
     * records, variants, enums, flags, aliases, and resources.
     */
    public Map<String, WitType> getTypes() {
      return types;
    }

    /**
     * Top-level functions declared directly in the body (not resource
     * methods).
     */
    public Map<String, WitFunction> getFunctions() {
      return functions;
    }

    /**
     * Methods declared inside each resource body, keyed by resource name.
     * {@link WitFunction#isConstructor()} / {@link WitFunction#isStatic()}
     * carry the resource-method shape onto the same translation path used
     * for top-level functions.
     */
    public Map<String, List<WitFunction>> getResourceMethods() {
      return resourceMethods;
    }
  }
}
