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

import java.util.Objects;

/** Represents a function parameter. */
public final class BindgenParameter {

  private final String name;
  private final BindgenType type;

  /**
   * Creates a new BindgenParameter.
   *
   * @param name the parameter name
   * @param type the parameter type
   */
  public BindgenParameter(final String name, final BindgenType type) {
    this.name = Objects.requireNonNull(name, "name");
    this.type = Objects.requireNonNull(type, "type");
  }

  /**
   * Returns the parameter name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the parameter type.
   *
   * @return the type
   */
  public BindgenType getType() {
    return type;
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    BindgenParameter that = (BindgenParameter) obj;
    return Objects.equals(name, that.name) && Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, type);
  }

  @Override
  public String toString() {
    return name + ": " + type.getName();
  }
}
