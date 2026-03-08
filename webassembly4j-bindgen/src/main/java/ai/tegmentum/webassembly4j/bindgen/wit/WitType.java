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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a WebAssembly Interface Type (WIT) type definition.
 *
 * <p>WIT types form the core type system for WebAssembly component interfaces, providing
 * comprehensive type definitions including primitives, composites, and resources.
 *
 * @since 1.0.0
 */
public final class WitType {

  private final String name;
  private final WitTypeKind kind;
  private final Map<String, Object> metadata;
  private final Optional<String> documentation;

  /**
   * Creates a new WIT type definition.
   *
   * @param name the type name
   * @param kind the type kind
   * @param metadata additional type metadata
   * @param documentation optional documentation
   */
  public WitType(
      final String name,
      final WitTypeKind kind,
      final Map<String, Object> metadata,
      final Optional<String> documentation) {
    this.name = Objects.requireNonNull(name, "name");
    this.kind = Objects.requireNonNull(kind, "kind");
    this.metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    this.documentation = Objects.requireNonNull(documentation, "documentation");
  }

  /**
   * Creates a primitive WIT type.
   *
   * @param primitive the primitive type
   * @return a new WIT type
   */
  public static WitType primitive(final WitPrimitiveType primitive) {
    return new WitType(
        primitive.name().toLowerCase(Locale.ROOT),
        WitTypeKind.primitive(primitive),
        Map.of(),
        Optional.empty());
  }

  /** Creates a boolean WIT type. */
  public static WitType createBool() {
    return primitive(WitPrimitiveType.BOOL);
  }

  /** Creates a signed 8-bit integer WIT type. */
  public static WitType createS8() {
    return primitive(WitPrimitiveType.S8);
  }

  /** Creates an unsigned 8-bit integer WIT type. */
  public static WitType createU8() {
    return primitive(WitPrimitiveType.U8);
  }

  /** Creates a signed 16-bit integer WIT type. */
  public static WitType createS16() {
    return primitive(WitPrimitiveType.S16);
  }

  /** Creates an unsigned 16-bit integer WIT type. */
  public static WitType createU16() {
    return primitive(WitPrimitiveType.U16);
  }

  /** Creates a signed 32-bit integer WIT type. */
  public static WitType createS32() {
    return primitive(WitPrimitiveType.S32);
  }

  /** Creates an unsigned 32-bit integer WIT type. */
  public static WitType createU32() {
    return primitive(WitPrimitiveType.U32);
  }

  /** Creates a signed 64-bit integer WIT type. */
  public static WitType createS64() {
    return primitive(WitPrimitiveType.S64);
  }

  /** Creates an unsigned 64-bit integer WIT type. */
  public static WitType createU64() {
    return primitive(WitPrimitiveType.U64);
  }

  /** Creates a 32-bit floating-point WIT type. */
  public static WitType createFloat32() {
    return primitive(WitPrimitiveType.FLOAT32);
  }

  /** Creates a 64-bit floating-point WIT type. */
  public static WitType createFloat64() {
    return primitive(WitPrimitiveType.FLOAT64);
  }

  /** Creates a Unicode character WIT type. */
  public static WitType createChar() {
    return primitive(WitPrimitiveType.CHAR);
  }

  /** Creates a UTF-8 string WIT type. */
  public static WitType createString() {
    return primitive(WitPrimitiveType.STRING);
  }

  /**
   * Creates a record WIT type.
   *
   * @param name the record name
   * @param fields the record fields
   * @return a new WIT type
   */
  public static WitType record(final String name, final Map<String, WitType> fields) {
    return new WitType(
        name, WitTypeKind.record(fields), Map.of("fieldCount", fields.size()), Optional.empty());
  }

  /**
   * Creates a variant WIT type.
   *
   * @param name the variant name
   * @param cases the variant cases
   * @return a new WIT type
   */
  public static WitType variant(final String name, final Map<String, Optional<WitType>> cases) {
    return new WitType(
        name, WitTypeKind.variant(cases), Map.of("caseCount", cases.size()), Optional.empty());
  }

  /**
   * Creates an enum WIT type.
   *
   * @param name the enum name
   * @param values the enum values
   * @return a new WIT type
   */
  public static WitType enumType(final String name, final List<String> values) {
    return new WitType(
        name, WitTypeKind.enumType(values), Map.of("valueCount", values.size()), Optional.empty());
  }

  /**
   * Creates a flags WIT type.
   *
   * @param name the flags name
   * @param flags the flag names
   * @return a new WIT type
   */
  public static WitType flags(final String name, final List<String> flags) {
    return new WitType(
        name, WitTypeKind.flags(flags), Map.of("flagCount", flags.size()), Optional.empty());
  }

  /**
   * Creates a list WIT type.
   *
   * @param elementType the element type
   * @return a new WIT type
   */
  public static WitType list(final WitType elementType) {
    return new WitType(
        "list<" + elementType.getName() + ">",
        WitTypeKind.list(elementType),
        Map.of("elementType", elementType.getName()),
        Optional.empty());
  }

  /**
   * Creates an option WIT type.
   *
   * @param innerType the inner type
   * @return a new WIT type
   */
  public static WitType option(final WitType innerType) {
    return new WitType(
        "option<" + innerType.getName() + ">",
        WitTypeKind.option(innerType),
        Map.of("innerType", innerType.getName()),
        Optional.empty());
  }

  /**
   * Creates a result WIT type.
   *
   * @param okType the success type
   * @param errorType the error type
   * @return a new WIT type
   */
  public static WitType result(final Optional<WitType> okType, final Optional<WitType> errorType) {
    final StringBuilder nameBuilder = new StringBuilder("result");
    if (okType.isPresent() || errorType.isPresent()) {
      nameBuilder.append("<");
      if (okType.isPresent()) {
        nameBuilder.append(okType.get().getName());
      } else {
        nameBuilder.append("_");
      }
      nameBuilder.append(", ");
      if (errorType.isPresent()) {
        nameBuilder.append(errorType.get().getName());
      } else {
        nameBuilder.append("_");
      }
      nameBuilder.append(">");
    }

    return new WitType(
        nameBuilder.toString(), WitTypeKind.result(okType, errorType), Map.of(), Optional.empty());
  }

  /**
   * Creates a tuple WIT type.
   *
   * @param elementTypes the element types
   * @return a new WIT type
   */
  public static WitType tuple(final List<WitType> elementTypes) {
    final StringBuilder nameBuilder = new StringBuilder("tuple<");
    for (int i = 0; i < elementTypes.size(); i++) {
      if (i > 0) {
        nameBuilder.append(", ");
      }
      nameBuilder.append(elementTypes.get(i).getName());
    }
    nameBuilder.append(">");

    return new WitType(
        nameBuilder.toString(),
        WitTypeKind.tuple(elementTypes),
        Map.of("elementCount", elementTypes.size()),
        Optional.empty());
  }

  /**
   * Creates a tuple WIT type from varargs.
   *
   * @param elementTypes the element types
   * @return a new WIT type
   */
  public static WitType tuple(final WitType... elementTypes) {
    return tuple(List.of(elementTypes));
  }

  /**
   * Creates a resource WIT type.
   *
   * @param name the resource name
   * @param resourceId the resource identifier
   * @return a new WIT type
   */
  public static WitType resource(final String name, final String resourceId) {
    return new WitType(
        name, WitTypeKind.resource(resourceId), Map.of("resourceId", resourceId), Optional.empty());
  }

  /**
   * Gets the name of this type.
   *
   * @return the type name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the kind of this type.
   *
   * @return the type kind
   */
  public WitTypeKind getKind() {
    return kind;
  }

  /**
   * Gets the metadata for this type.
   *
   * @return the type metadata
   */
  public Map<String, Object> getMetadata() {
    return metadata;
  }

  /**
   * Gets the documentation for this type.
   *
   * @return the optional documentation
   */
  public Optional<String> getDocumentation() {
    return documentation;
  }

  /**
   * Checks if this type is compatible with another type.
   *
   * @param other the other type
   * @return true if compatible, false otherwise
   */
  public boolean isCompatibleWith(final WitType other) {
    if (other == null) {
      return false;
    }

    // Same type is always compatible
    if (this.equals(other)) {
      return true;
    }

    // Check structural compatibility based on type kind
    return kind.isCompatibleWith(other.kind);
  }

  /**
   * Gets the size in bytes for this type (for primitive types).
   *
   * @return the size in bytes, or empty for composite types
   */
  public Optional<Integer> getSizeBytes() {
    return kind.getSizeBytes();
  }

  /**
   * Checks if this is a primitive type.
   *
   * @return true if primitive, false otherwise
   */
  public boolean isPrimitive() {
    return kind.isPrimitive();
  }

  /**
   * Checks if this is a composite type.
   *
   * @return true if composite, false otherwise
   */
  public boolean isComposite() {
    return kind.isComposite();
  }

  /**
   * Checks if this is a resource type.
   *
   * @return true if resource, false otherwise
   */
  public boolean isResource() {
    return kind.isResource();
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final WitType witType = (WitType) obj;
    return Objects.equals(name, witType.name)
        && Objects.equals(kind, witType.kind)
        && Objects.equals(metadata, witType.metadata);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, kind, metadata);
  }

  @Override
  public String toString() {
    return "WitType{"
        + "name='"
        + name
        + '\''
        + ", kind="
        + kind
        + ", metadata="
        + metadata
        + ", documentation="
        + documentation
        + '}';
  }
}
