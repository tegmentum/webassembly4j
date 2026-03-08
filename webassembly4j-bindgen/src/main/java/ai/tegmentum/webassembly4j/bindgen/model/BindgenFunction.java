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

/** Represents a function in the bindgen model. */
public final class BindgenFunction {

  private final String name;
  private final List<BindgenParameter> parameters;
  private final BindgenType returnType;
  private final String documentation;
  private final boolean isAsync;
  private final boolean isConstructor;
  private final boolean isStatic;

  private BindgenFunction(final Builder builder) {
    this.name = builder.name;
    this.parameters = Collections.unmodifiableList(new ArrayList<>(builder.parameters));
    this.returnType = builder.returnType;
    this.documentation = builder.documentation;
    this.isAsync = builder.isAsync;
    this.isConstructor = builder.isConstructor;
    this.isStatic = builder.isStatic;
  }

  /**
   * Creates a new builder for BindgenFunction.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns the function name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the function parameters.
   *
   * @return the list of parameters
   */
  public List<BindgenParameter> getParameters() {
    return parameters;
  }

  /**
   * Returns the return type if present.
   *
   * @return the return type, or empty for void functions
   */
  public Optional<BindgenType> getReturnType() {
    return Optional.ofNullable(returnType);
  }

  /**
   * Returns the documentation for this function.
   *
   * @return the documentation, or empty if not documented
   */
  public Optional<String> getDocumentation() {
    return Optional.ofNullable(documentation);
  }

  /**
   * Checks if this is an async function.
   *
   * @return true if async
   */
  public boolean isAsync() {
    return isAsync;
  }

  /**
   * Checks if this is a constructor.
   *
   * @return true if constructor
   */
  public boolean isConstructor() {
    return isConstructor;
  }

  /**
   * Checks if this is a static function.
   *
   * @return true if static
   */
  public boolean isStatic() {
    return isStatic;
  }

  /**
   * Checks if this function has a return value.
   *
   * @return true if there is a return type
   */
  public boolean hasReturnType() {
    return returnType != null;
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    BindgenFunction that = (BindgenFunction) obj;
    return Objects.equals(name, that.name) && Objects.equals(parameters, that.parameters);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, parameters);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("BindgenFunction{name='").append(name).append("'");
    sb.append(", params=").append(parameters.size());
    if (returnType != null) {
      sb.append(", returns=").append(returnType.getName());
    }
    sb.append("}");
    return sb.toString();
  }

  /** Builder for BindgenFunction. */
  public static final class Builder {
    private String name;
    private List<BindgenParameter> parameters = new ArrayList<>();
    private BindgenType returnType;
    private String documentation;
    private boolean isAsync;
    private boolean isConstructor;
    private boolean isStatic;

    private Builder() {}

    public Builder name(final String name) {
      this.name = name;
      return this;
    }

    public Builder parameters(final List<BindgenParameter> parameters) {
      this.parameters = new ArrayList<>(parameters);
      return this;
    }

    public Builder addParameter(final BindgenParameter parameter) {
      this.parameters.add(parameter);
      return this;
    }

    public Builder addParameter(final String name, final BindgenType type) {
      this.parameters.add(new BindgenParameter(name, type));
      return this;
    }

    public Builder returnType(final BindgenType returnType) {
      this.returnType = returnType;
      return this;
    }

    public Builder documentation(final String documentation) {
      this.documentation = documentation;
      return this;
    }

    public Builder async(final boolean isAsync) {
      this.isAsync = isAsync;
      return this;
    }

    public Builder constructor(final boolean isConstructor) {
      this.isConstructor = isConstructor;
      return this;
    }

    public Builder staticMethod(final boolean isStatic) {
      this.isStatic = isStatic;
      return this;
    }

    public BindgenFunction build() {
      return new BindgenFunction(this);
    }
  }
}
