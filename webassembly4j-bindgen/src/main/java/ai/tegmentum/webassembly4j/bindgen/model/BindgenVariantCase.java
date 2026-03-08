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

/** Represents a case in a variant type. */
public final class BindgenVariantCase {

  private final String name;
  private final BindgenType payload;
  private final String documentation;

  /**
   * Creates a new BindgenVariantCase without a payload.
   *
   * @param name the case name
   */
  public BindgenVariantCase(final String name) {
    this(name, null, null);
  }

  /**
   * Creates a new BindgenVariantCase with a payload.
   *
   * @param name the case name
   * @param payload the payload type
   */
  public BindgenVariantCase(final String name, final BindgenType payload) {
    this(name, payload, null);
  }

  /**
   * Creates a new BindgenVariantCase with documentation.
   *
   * @param name the case name
   * @param payload the payload type (may be null)
   * @param documentation the documentation
   */
  public BindgenVariantCase(
      final String name, final BindgenType payload, final String documentation) {
    this.name = Objects.requireNonNull(name, "name");
    this.payload = payload;
    this.documentation = documentation;
  }

  /**
   * Returns the case name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the payload type if present.
   *
   * @return the payload type, or empty if no payload
   */
  public Optional<BindgenType> getPayload() {
    return Optional.ofNullable(payload);
  }

  /**
   * Checks if this case has a payload.
   *
   * @return true if there is a payload
   */
  public boolean hasPayload() {
    return payload != null;
  }

  /**
   * Returns the documentation for this case.
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
    BindgenVariantCase that = (BindgenVariantCase) obj;
    return Objects.equals(name, that.name) && Objects.equals(payload, that.payload);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, payload);
  }

  @Override
  public String toString() {
    if (payload != null) {
      return "BindgenVariantCase{name='" + name + "', payload=" + payload + "}";
    }
    return "BindgenVariantCase{name='" + name + "'}";
  }
}
