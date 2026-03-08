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

package ai.tegmentum.webassembly4j.bindgen.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The unified bindgen model containing all parsed types, interfaces, and functions.
 *
 * <p>This model is built from either WIT files or WASM module introspection and serves as the input
 * for code generation.
 */
public final class BindgenModel {

  private final String name;
  private final List<BindgenInterface> interfaces;
  private final List<BindgenType> types;
  private final List<BindgenFunction> functions;
  private final Map<String, BindgenType> typeRegistry;
  private final String sourceFile;

  private BindgenModel(final Builder builder) {
    this.name = builder.name;
    this.interfaces = Collections.unmodifiableList(new ArrayList<>(builder.interfaces));
    this.types = Collections.unmodifiableList(new ArrayList<>(builder.types));
    this.functions = Collections.unmodifiableList(new ArrayList<>(builder.functions));
    this.typeRegistry = Collections.unmodifiableMap(new HashMap<>(builder.typeRegistry));
    this.sourceFile = builder.sourceFile;
  }

  /**
   * Creates a new builder for BindgenModel.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns the model name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the interfaces in this model.
   *
   * @return the list of interfaces
   */
  public List<BindgenInterface> getInterfaces() {
    return interfaces;
  }

  /**
   * Returns the top-level types in this model.
   *
   * @return the list of types
   */
  public List<BindgenType> getTypes() {
    return types;
  }

  /**
   * Returns the top-level functions in this model.
   *
   * @return the list of functions
   */
  public List<BindgenFunction> getFunctions() {
    return functions;
  }

  /**
   * Returns the source file this model was built from.
   *
   * @return the source file path, or empty if not available
   */
  public Optional<String> getSourceFile() {
    return Optional.ofNullable(sourceFile);
  }

  /**
   * Looks up a type by name.
   *
   * @param typeName the type name
   * @return the type, or empty if not found
   */
  public Optional<BindgenType> lookupType(final String typeName) {
    return Optional.ofNullable(typeRegistry.get(typeName));
  }

  /**
   * Checks if a type exists in this model.
   *
   * @param typeName the type name
   * @return true if the type exists
   */
  public boolean hasType(final String typeName) {
    return typeRegistry.containsKey(typeName);
  }

  /**
   * Returns all type names in this model.
   *
   * @return the set of type names
   */
  public Iterable<String> getTypeNames() {
    return typeRegistry.keySet();
  }

  /**
   * Returns the total number of types in this model.
   *
   * @return the type count
   */
  public int getTypeCount() {
    return typeRegistry.size();
  }

  /**
   * Returns the total number of functions in this model (including those in interfaces).
   *
   * @return the function count
   */
  public int getTotalFunctionCount() {
    int count = functions.size();
    for (BindgenInterface iface : interfaces) {
      count += iface.getFunctions().size();
    }
    return count;
  }

  /**
   * Checks if this model is empty.
   *
   * @return true if there are no types, interfaces, or functions
   */
  public boolean isEmpty() {
    return interfaces.isEmpty() && types.isEmpty() && functions.isEmpty();
  }

  /**
   * Merges another model into this one.
   *
   * @param other the model to merge
   * @return a new merged model
   */
  public BindgenModel merge(final BindgenModel other) {
    Builder builder = builder().name(this.name);

    // Add all from this model
    for (BindgenInterface iface : this.interfaces) {
      builder.addInterface(iface);
    }
    for (BindgenType type : this.types) {
      builder.addType(type);
    }
    for (BindgenFunction func : this.functions) {
      builder.addFunction(func);
    }

    // Add all from other model
    for (BindgenInterface iface : other.interfaces) {
      builder.addInterface(iface);
    }
    for (BindgenType type : other.types) {
      builder.addType(type);
    }
    for (BindgenFunction func : other.functions) {
      builder.addFunction(func);
    }

    return builder.build();
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    BindgenModel that = (BindgenModel) obj;
    return Objects.equals(name, that.name)
        && Objects.equals(interfaces, that.interfaces)
        && Objects.equals(types, that.types)
        && Objects.equals(functions, that.functions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, interfaces, types, functions);
  }

  @Override
  public String toString() {
    return "BindgenModel{name='"
        + name
        + "', interfaces="
        + interfaces.size()
        + ", types="
        + types.size()
        + ", functions="
        + functions.size()
        + "}";
  }

  /** Builder for BindgenModel. */
  public static final class Builder {
    private String name = "";
    private List<BindgenInterface> interfaces = new ArrayList<>();
    private List<BindgenType> types = new ArrayList<>();
    private List<BindgenFunction> functions = new ArrayList<>();
    private Map<String, BindgenType> typeRegistry = new HashMap<>();
    private String sourceFile;

    private Builder() {}

    public Builder name(final String name) {
      this.name = name;
      return this;
    }

    /**
     * Sets the list of interfaces and registers their types.
     *
     * @param interfaces the list of interfaces
     * @return this builder
     */
    public Builder interfaces(final List<BindgenInterface> interfaces) {
      this.interfaces = new ArrayList<>(interfaces);
      // Register types from interfaces
      for (BindgenInterface iface : interfaces) {
        for (BindgenType type : iface.getTypes()) {
          typeRegistry.put(type.getName(), type);
        }
      }
      return this;
    }

    /**
     * Adds an interface and registers its types.
     *
     * @param iface the interface to add
     * @return this builder
     */
    public Builder addInterface(final BindgenInterface iface) {
      this.interfaces.add(iface);
      // Register types from interface
      for (BindgenType type : iface.getTypes()) {
        typeRegistry.put(type.getName(), type);
      }
      return this;
    }

    /**
     * Sets the list of standalone types and registers them.
     *
     * @param types the list of types
     * @return this builder
     */
    public Builder types(final List<BindgenType> types) {
      this.types = new ArrayList<>(types);
      for (BindgenType type : types) {
        typeRegistry.put(type.getName(), type);
      }
      return this;
    }

    /**
     * Adds a standalone type and registers it.
     *
     * @param type the type to add
     * @return this builder
     */
    public Builder addType(final BindgenType type) {
      this.types.add(type);
      typeRegistry.put(type.getName(), type);
      return this;
    }

    public Builder functions(final List<BindgenFunction> functions) {
      this.functions = new ArrayList<>(functions);
      return this;
    }

    public Builder addFunction(final BindgenFunction function) {
      this.functions.add(function);
      return this;
    }

    public Builder sourceFile(final String sourceFile) {
      this.sourceFile = sourceFile;
      return this;
    }

    public BindgenModel build() {
      return new BindgenModel(this);
    }
  }
}
