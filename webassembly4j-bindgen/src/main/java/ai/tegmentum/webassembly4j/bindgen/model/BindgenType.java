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

/**
 * Represents a type in the bindgen model.
 *
 * <p>This class represents all types that can appear in WIT or WASM, including primitives, records,
 * variants, enums, lists, options, and results.
 */
public final class BindgenType {

  /** The kind of type. */
  public enum Kind {
    /** A primitive type (bool, i32, string, etc.). */
    PRIMITIVE,
    /** A record type with named fields. */
    RECORD,
    /** A variant type (tagged union). */
    VARIANT,
    /** An enum type. */
    ENUM,
    /** A flags type (set of boolean flags). */
    FLAGS,
    /** A list type. */
    LIST,
    /** An option type. */
    OPTION,
    /** A result type. */
    RESULT,
    /** A tuple type. */
    TUPLE,
    /** A resource type. */
    RESOURCE,
    /** A reference to another type. */
    REFERENCE,
    /** A function type. */
    FUNCTION
  }

  private final String name;
  private final Kind kind;
  private final String documentation;
  private final List<BindgenField> fields;
  private final List<BindgenVariantCase> cases;
  private final List<String> enumValues;
  private final BindgenType elementType;
  private final BindgenType okType;
  private final BindgenType errorType;
  private final List<BindgenType> tupleElements;
  private final String referencedTypeName;

  private BindgenType(final Builder builder) {
    this.name = builder.name;
    this.kind = builder.kind;
    this.documentation = builder.documentation;
    this.fields = Collections.unmodifiableList(new ArrayList<>(builder.fields));
    this.cases = Collections.unmodifiableList(new ArrayList<>(builder.cases));
    this.enumValues = Collections.unmodifiableList(new ArrayList<>(builder.enumValues));
    this.elementType = builder.elementType;
    this.okType = builder.okType;
    this.errorType = builder.errorType;
    this.tupleElements = Collections.unmodifiableList(new ArrayList<>(builder.tupleElements));
    this.referencedTypeName = builder.referencedTypeName;
  }

  /**
   * Creates a new builder for BindgenType.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Creates a primitive type.
   *
   * @param primitiveName the primitive type name (e.g., "i32", "string")
   * @return the primitive type
   */
  public static BindgenType primitive(final String primitiveName) {
    return builder().name(primitiveName).kind(Kind.PRIMITIVE).build();
  }

  /**
   * Creates a reference to another type.
   *
   * @param typeName the referenced type name
   * @return the reference type
   */
  public static BindgenType reference(final String typeName) {
    return builder().name(typeName).kind(Kind.REFERENCE).referencedTypeName(typeName).build();
  }

  /**
   * Creates a list type.
   *
   * @param elementType the element type
   * @return the list type
   */
  public static BindgenType list(final BindgenType elementType) {
    return builder()
        .name("list<" + elementType.getName() + ">")
        .kind(Kind.LIST)
        .elementType(elementType)
        .build();
  }

  /**
   * Creates an option type.
   *
   * @param innerType the inner type
   * @return the option type
   */
  public static BindgenType option(final BindgenType innerType) {
    return builder()
        .name("option<" + innerType.getName() + ">")
        .kind(Kind.OPTION)
        .elementType(innerType)
        .build();
  }

  /**
   * Creates a result type.
   *
   * @param okType the success type (may be null)
   * @param errorType the error type (may be null)
   * @return the result type
   */
  public static BindgenType result(final BindgenType okType, final BindgenType errorType) {
    String name =
        "result<"
            + (okType != null ? okType.getName() : "_")
            + ", "
            + (errorType != null ? errorType.getName() : "_")
            + ">";
    return builder().name(name).kind(Kind.RESULT).okType(okType).errorType(errorType).build();
  }

  /**
   * Returns the type name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the type kind.
   *
   * @return the kind
   */
  public Kind getKind() {
    return kind;
  }

  /**
   * Returns the documentation for this type.
   *
   * @return the documentation, or empty if not documented
   */
  public Optional<String> getDocumentation() {
    return Optional.ofNullable(documentation);
  }

  /**
   * Returns the fields for a record type.
   *
   * @return the list of fields
   */
  public List<BindgenField> getFields() {
    return fields;
  }

  /**
   * Returns the cases for a variant type.
   *
   * @return the list of cases
   */
  public List<BindgenVariantCase> getCases() {
    return cases;
  }

  /**
   * Returns the values for an enum type.
   *
   * @return the list of enum values
   */
  public List<String> getEnumValues() {
    return enumValues;
  }

  /**
   * Returns the element type for list or option types.
   *
   * @return the element type, or empty if not applicable
   */
  public Optional<BindgenType> getElementType() {
    return Optional.ofNullable(elementType);
  }

  /**
   * Returns the success type for result types.
   *
   * @return the ok type, or empty if not applicable
   */
  public Optional<BindgenType> getOkType() {
    return Optional.ofNullable(okType);
  }

  /**
   * Returns the error type for result types.
   *
   * @return the error type, or empty if not applicable
   */
  public Optional<BindgenType> getErrorType() {
    return Optional.ofNullable(errorType);
  }

  /**
   * Returns the tuple elements for tuple types.
   *
   * @return the list of tuple element types
   */
  public List<BindgenType> getTupleElements() {
    return tupleElements;
  }

  /**
   * Returns the referenced type name for reference types.
   *
   * @return the referenced type name, or empty if not a reference
   */
  public Optional<String> getReferencedTypeName() {
    return Optional.ofNullable(referencedTypeName);
  }

  /**
   * Checks if this is a primitive type.
   *
   * @return true if primitive
   */
  public boolean isPrimitive() {
    return kind == Kind.PRIMITIVE;
  }

  /**
   * Checks if this is a record type.
   *
   * @return true if record
   */
  public boolean isRecord() {
    return kind == Kind.RECORD;
  }

  /**
   * Checks if this is a variant type.
   *
   * @return true if variant
   */
  public boolean isVariant() {
    return kind == Kind.VARIANT;
  }

  /**
   * Checks if this is an enum type.
   *
   * @return true if enum
   */
  public boolean isEnum() {
    return kind == Kind.ENUM;
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    BindgenType that = (BindgenType) obj;
    return Objects.equals(name, that.name) && kind == that.kind;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, kind);
  }

  @Override
  public String toString() {
    return "BindgenType{name='" + name + "', kind=" + kind + "}";
  }

  /** Builder for BindgenType. */
  public static final class Builder {
    private String name;
    private Kind kind = Kind.REFERENCE;
    private String documentation;
    private List<BindgenField> fields = new ArrayList<>();
    private List<BindgenVariantCase> cases = new ArrayList<>();
    private List<String> enumValues = new ArrayList<>();
    private BindgenType elementType;
    private BindgenType okType;
    private BindgenType errorType;
    private List<BindgenType> tupleElements = new ArrayList<>();
    private String referencedTypeName;

    private Builder() {}

    public Builder name(final String name) {
      this.name = name;
      return this;
    }

    public Builder kind(final Kind kind) {
      this.kind = kind;
      return this;
    }

    public Builder documentation(final String documentation) {
      this.documentation = documentation;
      return this;
    }

    public Builder fields(final List<BindgenField> fields) {
      this.fields = new ArrayList<>(fields);
      return this;
    }

    public Builder addField(final BindgenField field) {
      this.fields.add(field);
      return this;
    }

    public Builder cases(final List<BindgenVariantCase> cases) {
      this.cases = new ArrayList<>(cases);
      return this;
    }

    public Builder addCase(final BindgenVariantCase variantCase) {
      this.cases.add(variantCase);
      return this;
    }

    public Builder enumValues(final List<String> values) {
      this.enumValues = new ArrayList<>(values);
      return this;
    }

    public Builder addEnumValue(final String value) {
      this.enumValues.add(value);
      return this;
    }

    public Builder elementType(final BindgenType elementType) {
      this.elementType = elementType;
      return this;
    }

    public Builder okType(final BindgenType okType) {
      this.okType = okType;
      return this;
    }

    public Builder errorType(final BindgenType errorType) {
      this.errorType = errorType;
      return this;
    }

    public Builder tupleElements(final List<BindgenType> elements) {
      this.tupleElements = new ArrayList<>(elements);
      return this;
    }

    public Builder referencedTypeName(final String typeName) {
      this.referencedTypeName = typeName;
      return this;
    }

    public BindgenType build() {
      return new BindgenType(this);
    }
  }
}
