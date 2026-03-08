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

import java.util.Locale;

/**
 * WebAssembly Interface Types (WIT) primitive types.
 *
 * <p>These are the fundamental scalar types supported by the WebAssembly Component Model type
 * system.
 *
 * @since 1.0.0
 */
public enum WitPrimitiveType {
  /** Boolean type. */
  BOOL(1),

  /** Signed 8-bit integer. */
  S8(1),

  /** Unsigned 8-bit integer. */
  U8(1),

  /** Signed 16-bit integer. */
  S16(2),

  /** Unsigned 16-bit integer. */
  U16(2),

  /** Signed 32-bit integer. */
  S32(4),

  /** Unsigned 32-bit integer. */
  U32(4),

  /** Signed 64-bit integer. */
  S64(8),

  /** Unsigned 64-bit integer. */
  U64(8),

  /** 32-bit IEEE 754 floating point. */
  FLOAT32(4),

  /** 64-bit IEEE 754 floating point. */
  FLOAT64(8),

  /** Unicode character (32-bit). */
  CHAR(4),

  /** UTF-8 string. */
  STRING(-1); // Variable size

  private final int sizeBytes;

  WitPrimitiveType(final int sizeBytes) {
    this.sizeBytes = sizeBytes;
  }

  /**
   * Gets the size in bytes for this primitive type.
   *
   * @return the size in bytes, or -1 for variable-size types
   */
  public int getSizeBytes() {
    return sizeBytes;
  }

  /**
   * Checks if this is a variable-size type.
   *
   * @return true if variable size, false if fixed size
   */
  public boolean isVariableSize() {
    return sizeBytes < 0;
  }

  /**
   * Checks if this is an integer type.
   *
   * @return true if integer, false otherwise
   */
  public boolean isInteger() {
    return this == S8
        || this == U8
        || this == S16
        || this == U16
        || this == S32
        || this == U32
        || this == S64
        || this == U64;
  }

  /**
   * Checks if this is a floating point type.
   *
   * @return true if floating point, false otherwise
   */
  public boolean isFloatingPoint() {
    return this == FLOAT32 || this == FLOAT64;
  }

  /**
   * Checks if this is a signed integer type.
   *
   * @return true if signed integer, false otherwise
   */
  public boolean isSignedInteger() {
    return this == S8 || this == S16 || this == S32 || this == S64;
  }

  /**
   * Checks if this is an unsigned integer type.
   *
   * @return true if unsigned integer, false otherwise
   */
  public boolean isUnsignedInteger() {
    return this == U8 || this == U16 || this == U32 || this == U64;
  }

  /**
   * Gets the corresponding Java type for this WIT primitive.
   *
   * @return the Java type class
   */
  public Class<?> getJavaType() {
    switch (this) {
      case BOOL:
        return boolean.class;
      case S8:
      case U8:
        return byte.class;
      case S16:
      case U16:
        return short.class;
      case S32:
        return int.class;
      case U32:
        return int.class; // Java doesn't have unsigned int, use int
      case S64:
      case U64:
        return long.class;
      case FLOAT32:
        return float.class;
      case FLOAT64:
        return double.class;
      case CHAR:
        return char.class;
      case STRING:
        return String.class;
      default:
        throw new IllegalStateException("Unknown primitive type: " + this);
    }
  }

  /**
   * Converts a WIT primitive type name to the corresponding enum value.
   *
   * @param typeName the type name (case-insensitive)
   * @return the corresponding primitive type
   * @throws IllegalArgumentException if the type name is not recognized
   */
  public static WitPrimitiveType fromString(final String typeName) {
    if (typeName == null || typeName.isEmpty()) {
      throw new IllegalArgumentException("Type name cannot be null or empty");
    }

    final String normalized = typeName.toLowerCase(Locale.ROOT).trim();
    switch (normalized) {
      case "bool":
      case "boolean":
        return BOOL;
      case "s8":
      case "i8":
        return S8;
      case "u8":
        return U8;
      case "s16":
      case "i16":
        return S16;
      case "u16":
        return U16;
      case "s32":
      case "i32":
        return S32;
      case "u32":
        return U32;
      case "s64":
      case "i64":
        return S64;
      case "u64":
        return U64;
      case "f32":
      case "float32":
        return FLOAT32;
      case "f64":
      case "float64":
        return FLOAT64;
      case "char":
        return CHAR;
      case "string":
        return STRING;
      default:
        throw new IllegalArgumentException("Unknown WIT primitive type: " + typeName);
    }
  }

  /**
   * Gets the canonical WIT type name for this primitive type.
   *
   * @return the canonical type name
   */
  public String getWitTypeName() {
    return name().toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
