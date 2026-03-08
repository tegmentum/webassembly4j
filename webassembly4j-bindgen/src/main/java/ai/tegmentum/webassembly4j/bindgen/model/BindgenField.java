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
import java.util.Optional;

/** Represents a field in a record type. */
public final class BindgenField {

  private final String name;
  private final BindgenType type;
  private final String documentation;

  /**
   * Creates a new BindgenField.
   *
   * @param name the field name
   * @param type the field type
   */
  public BindgenField(final String name, final BindgenType type) {
    this(name, type, null);
  }

  /**
   * Creates a new BindgenField with documentation.
   *
   * @param name the field name
   * @param type the field type
   * @param documentation the documentation
   */
  public BindgenField(final String name, final BindgenType type, final String documentation) {
    this.name = Objects.requireNonNull(name, "name");
    this.type = Objects.requireNonNull(type, "type");
    this.documentation = documentation;
  }

  /**
   * Returns the field name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the field type.
   *
   * @return the type
   */
  public BindgenType getType() {
    return type;
  }

  /**
   * Returns the documentation for this field.
   *
   * @return the documentation, or empty if not documented
   */
  public Optional<String> getDocumentation() {
    return Optional.ofNullable(documentation);
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    BindgenField that = (BindgenField) obj;
    return Objects.equals(name, that.name) && Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, type);
  }

  @Override
  public String toString() {
    return "BindgenField{name='" + name + "', type=" + type + "}";
  }
}
