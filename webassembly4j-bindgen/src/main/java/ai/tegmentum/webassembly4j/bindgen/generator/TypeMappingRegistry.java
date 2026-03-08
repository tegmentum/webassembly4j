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

package ai.tegmentum.webassembly4j.bindgen.generator;

import ai.tegmentum.webassembly4j.bindgen.CodeStyle;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Maps WIT and WASM types to Java types.
 *
 * <p>This registry provides type mapping from WebAssembly Interface Types (WIT) and WASM value
 * types to their corresponding Java types, respecting the configured code style.
 */
public final class TypeMappingRegistry {

  private final CodeStyle codeStyle;
  private final String basePackage;

  /**
   * Creates a new TypeMappingRegistry.
   *
   * @param codeStyle the code generation style
   * @param basePackage the base package for generated types
   */
  public TypeMappingRegistry(final CodeStyle codeStyle, final String basePackage) {
    this.codeStyle = Objects.requireNonNull(codeStyle, "codeStyle");
    this.basePackage = Objects.requireNonNull(basePackage, "basePackage");
  }

  /**
   * Maps a WIT primitive type to a Java type.
   *
   * @param witPrimitive the WIT primitive type name
   * @return the corresponding Java type
   */
  public TypeName mapWitPrimitive(final String witPrimitive) {
    Objects.requireNonNull(witPrimitive, "witPrimitive");
    switch (witPrimitive.toLowerCase()) {
      case "bool":
        return TypeName.BOOLEAN;
      case "s8":
        return TypeName.BYTE;
      case "s16":
        return TypeName.SHORT;
      case "s32":
      case "i32": // WASM type alias
        return TypeName.INT;
      case "s64":
      case "i64": // WASM type alias
        return TypeName.LONG;
      case "u8":
        return TypeName.BYTE; // With @Unsigned annotation in generated code
      case "u16":
        return TypeName.SHORT;
      case "u32":
        return TypeName.INT;
      case "u64":
        return TypeName.LONG;
      case "f32":
      case "float32":
        return TypeName.FLOAT;
      case "f64":
      case "float64":
        return TypeName.DOUBLE;
      case "char":
        return TypeName.INT; // Unicode code point
      case "string":
        return ClassName.get(String.class);
      default:
        throw new IllegalArgumentException("Unknown WIT primitive type: " + witPrimitive);
    }
  }

  /**
   * Maps a WASM value type to a Java type.
   *
   * @param wasmType the WASM value type name
   * @return the corresponding Java type
   */
  public TypeName mapWasmType(final String wasmType) {
    Objects.requireNonNull(wasmType, "wasmType");
    switch (wasmType.toLowerCase()) {
      case "i32":
        return TypeName.INT;
      case "i64":
        return TypeName.LONG;
      case "f32":
        return TypeName.FLOAT;
      case "f64":
        return TypeName.DOUBLE;
      case "v128":
        return ArrayTypeName.of(TypeName.BYTE);
      case "funcref":
        return ClassName.get("ai.tegmentum.webassembly4j", "FunctionReference");
      case "externref":
        return ClassName.get(Object.class);
      default:
        throw new IllegalArgumentException("Unknown WASM type: " + wasmType);
    }
  }

  /**
   * Creates a Java List type for a WIT list type.
   *
   * @param elementType the element type
   * @return the parameterized List type
   */
  public TypeName mapList(final TypeName elementType) {
    Objects.requireNonNull(elementType, "elementType");
    return ParameterizedTypeName.get(ClassName.get(List.class), elementType.box());
  }

  /**
   * Creates a Java Optional type for a WIT option type.
   *
   * @param innerType the inner type
   * @return the parameterized Optional type
   */
  public TypeName mapOption(final TypeName innerType) {
    Objects.requireNonNull(innerType, "innerType");
    return ParameterizedTypeName.get(ClassName.get(Optional.class), innerType.box());
  }

  /**
   * Creates a result type for a WIT result type.
   *
   * @param okType the success type (may be null for unit)
   * @param errorType the error type (may be null for unit)
   * @return the result type name
   */
  public TypeName mapResult(final TypeName okType, final TypeName errorType) {
    ClassName resultClass = ClassName.get("ai.tegmentum.webassembly4j.bindgen.wit", "WitResult");

    TypeName actualOkType = okType != null ? okType.box() : ClassName.get(Void.class);
    TypeName actualErrorType = errorType != null ? errorType.box() : ClassName.get(Void.class);

    return ParameterizedTypeName.get(resultClass, actualOkType, actualErrorType);
  }

  /**
   * Creates a tuple type for a WIT tuple.
   *
   * @param elementTypes the element types
   * @return the tuple type name
   */
  public TypeName mapTuple(final List<TypeName> elementTypes) {
    Objects.requireNonNull(elementTypes, "elementTypes");
    if (elementTypes.isEmpty()) {
      return ClassName.get(Void.class);
    }
    if (elementTypes.size() == 1) {
      return elementTypes.get(0);
    }
    // Use a generated tuple type or built-in pair for 2 elements
    String tupleName = "Tuple" + elementTypes.size();
    return ClassName.get(basePackage + ".tuple", tupleName);
  }

  /**
   * Creates a type name for a generated record or class.
   *
   * @param typeName the WIT type name
   * @return the Java class name
   */
  public ClassName mapGeneratedType(final String typeName) {
    Objects.requireNonNull(typeName, "typeName");
    return ClassName.get(basePackage, capitalize(toCamelCase(typeName)));
  }

  /**
   * Creates a type name for a generated interface.
   *
   * @param interfaceName the WIT interface name
   * @return the Java class name
   */
  public ClassName mapInterface(final String interfaceName) {
    Objects.requireNonNull(interfaceName, "interfaceName");
    return ClassName.get(basePackage, capitalize(toCamelCase(interfaceName)));
  }

  /**
   * Returns the code style used by this registry.
   *
   * @return the code style
   */
  public CodeStyle getCodeStyle() {
    return codeStyle;
  }

  /**
   * Returns the base package for generated types.
   *
   * @return the base package
   */
  public String getBasePackage() {
    return basePackage;
  }

  /**
   * Checks if the given type is a WIT primitive type.
   *
   * @param typeName the type name to check
   * @return true if it's a primitive type
   */
  public boolean isPrimitive(final String typeName) {
    if (typeName == null) {
      return false;
    }
    switch (typeName.toLowerCase()) {
      case "bool":
      case "s8":
      case "s16":
      case "s32":
      case "s64":
      case "u8":
      case "u16":
      case "u32":
      case "u64":
      case "i32": // WASM type alias
      case "i64": // WASM type alias
      case "f32":
      case "f64":
      case "float32":
      case "float64":
      case "char":
      case "string":
        return true;
      default:
        return false;
    }
  }

  private static String toCamelCase(final String input) {
    StringBuilder result = new StringBuilder();
    boolean capitalizeNext = false;
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (c == '-' || c == '_') {
        capitalizeNext = true;
      } else if (capitalizeNext) {
        result.append(Character.toUpperCase(c));
        capitalizeNext = false;
      } else {
        result.append(c);
      }
    }
    return result.toString();
  }

  private static String capitalize(final String input) {
    if (input == null || input.isEmpty()) {
      return input;
    }
    return Character.toUpperCase(input.charAt(0)) + input.substring(1);
  }
}
