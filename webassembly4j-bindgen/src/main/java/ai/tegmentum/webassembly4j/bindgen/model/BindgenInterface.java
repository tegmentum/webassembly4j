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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Represents a WIT interface in the bindgen model. */
public final class BindgenInterface {

  private final String name;
  private final String packageName;
  private final List<BindgenType> types;
  private final List<BindgenFunction> functions;
  private final String documentation;

  private BindgenInterface(final Builder builder) {
    this.name = builder.name;
    this.packageName = builder.packageName;
    this.types = Collections.unmodifiableList(new ArrayList<>(builder.types));
    this.functions = Collections.unmodifiableList(new ArrayList<>(builder.functions));
    this.documentation = builder.documentation;
  }

  /**
   * Creates a new builder for BindgenInterface.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns the interface name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the WIT package name if present.
   *
   * @return the package name, or empty if not part of a package
   */
  public Optional<String> getPackageName() {
    return Optional.ofNullable(packageName);
  }

  /**
   * Returns the types defined in this interface.
   *
   * @return the list of types
   */
  public List<BindgenType> getTypes() {
    return types;
  }

  /**
   * Returns the functions defined in this interface.
   *
   * @return the list of functions
   */
  public List<BindgenFunction> getFunctions() {
    return functions;
  }

  /**
   * Returns the documentation for this interface.
   *
   * @return the documentation, or empty if not documented
   */
  public Optional<String> getDocumentation() {
    return Optional.ofNullable(documentation);
  }

  /**
   * Returns the fully qualified name of this interface.
   *
   * @return the fully qualified name
   */
  public String getFullyQualifiedName() {
    if (packageName != null && !packageName.isEmpty()) {
      return packageName + "/" + name;
    }
    return name;
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    BindgenInterface that = (BindgenInterface) obj;
    return Objects.equals(name, that.name) && Objects.equals(packageName, that.packageName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, packageName);
  }

  @Override
  public String toString() {
    return "BindgenInterface{name='"
        + getFullyQualifiedName()
        + "', types="
        + types.size()
        + ", functions="
        + functions.size()
        + "}";
  }

  /** Builder for BindgenInterface. */
  public static final class Builder {
    private String name;
    private String packageName;
    private List<BindgenType> types = new ArrayList<>();
    private List<BindgenFunction> functions = new ArrayList<>();
    private String documentation;

    private Builder() {}

    public Builder name(final String name) {
      this.name = name;
      return this;
    }

    public Builder packageName(final String packageName) {
      this.packageName = packageName;
      return this;
    }

    public Builder types(final List<BindgenType> types) {
      this.types = new ArrayList<>(types);
      return this;
    }

    public Builder addType(final BindgenType type) {
      this.types.add(type);
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

    public Builder documentation(final String documentation) {
      this.documentation = documentation;
      return this;
    }

    public BindgenInterface build() {
      return new BindgenInterface(this);
    }
  }
}
