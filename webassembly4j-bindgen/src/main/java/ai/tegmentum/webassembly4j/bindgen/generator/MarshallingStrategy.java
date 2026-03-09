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

import ai.tegmentum.webassembly4j.bindgen.model.BindgenType;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;

/**
 * Maps bindgen types to JavaPoet code fragments for lowering (Java to WASM)
 * and lifting (WASM to Java) through the marshalling infrastructure.
 */
public final class MarshallingStrategy {

  private static final ClassName STRING_CODEC =
      ClassName.get("ai.tegmentum.webassembly4j.runtime.marshal", "StringCodec");

  private MarshallingStrategy() {
  }

  /**
   * Returns true if the given type requires marshalling through linear memory.
   *
   * @param type the bindgen type
   * @return true if marshalling is needed
   */
  public static boolean requiresMarshalling(BindgenType type) {
    if (type == null) {
      return false;
    }
    switch (type.getKind()) {
      case PRIMITIVE:
        return "string".equals(type.getName());
      case LIST:
      case RECORD:
      case VARIANT:
      case OPTION:
      case RESULT:
      case TUPLE:
        return true;
      case ENUM:
      case FLAGS:
        return false;
      case REFERENCE:
        // Conservative: assume references may require marshalling
        return true;
      default:
        return false;
    }
  }

  /**
   * Generates code to lower a Java value to WASM arguments.
   *
   * <p>For primitive types, this is a no-op (direct pass-through).
   * For complex types, this generates marshalling code that writes to linear memory.
   *
   * @param type the bindgen type
   * @param javaVar the Java variable name holding the value
   * @param argsListVar the name of the List variable to add lowered args to
   * @return the lowering code block
   */
  public static CodeBlock lowerArgument(BindgenType type, String javaVar, String argsListVar) {
    if (type == null) {
      return CodeBlock.of("$L.add($L);\n", argsListVar, javaVar);
    }

    switch (type.getKind()) {
      case PRIMITIVE:
        if ("string".equals(type.getName())) {
          return CodeBlock.builder()
              .addStatement("int[] $L_encoded = $T.encode($L, marshal.memory(), marshal.allocator())",
                  javaVar, STRING_CODEC, javaVar)
              .addStatement("$L.add($L_encoded[0])", argsListVar, javaVar)
              .addStatement("$L.add($L_encoded[1])", argsListVar, javaVar)
              .build();
        }
        return CodeBlock.of("$L.add($L);\n", argsListVar, javaVar);

      case LIST:
        return CodeBlock.builder()
            .addStatement("byte[] $L_bytes = ($L instanceof byte[]) ? (byte[]) (Object) $L : null",
                javaVar, javaVar, javaVar)
            .beginControlFlow("if ($L_bytes != null)", javaVar)
            .addStatement("int $L_ptr = marshal.allocator().allocate($L_bytes.length, 1)",
                javaVar, javaVar)
            .addStatement("marshal.memory().write($L_ptr, $L_bytes)", javaVar, javaVar)
            .addStatement("$L.add($L_ptr)", argsListVar, javaVar)
            .addStatement("$L.add($L_bytes.length)", argsListVar, javaVar)
            .endControlFlow()
            .build();

      case ENUM:
        return CodeBlock.of("$L.add($L.ordinal());\n", argsListVar, javaVar);

      default:
        // For complex types, generate a comment indicating further implementation needed
        return CodeBlock.builder()
            .addStatement("// TODO: marshal $L of kind $L", javaVar, type.getKind())
            .addStatement("$L.add($L)", argsListVar, javaVar)
            .build();
    }
  }

  /**
   * Generates code to lift a WASM return value to a Java value.
   *
   * @param type the bindgen type
   * @param retptrVar the variable name holding the return pointer in linear memory
   * @return the lifting code block (an expression)
   */
  public static CodeBlock liftReturn(BindgenType type, String retptrVar) {
    if (type == null) {
      return CodeBlock.of("null");
    }

    switch (type.getKind()) {
      case PRIMITIVE:
        if ("string".equals(type.getName())) {
          return CodeBlock.of("marshal.reader().readString($L)", retptrVar);
        }
        return liftPrimitive(type);

      case LIST:
        return CodeBlock.of("marshal.reader().readBytes($L)", retptrVar);

      case ENUM:
        return CodeBlock.of("/* TODO: lift enum from ordinal */");

      default:
        return CodeBlock.of("/* TODO: lift $L */", type.getKind());
    }
  }

  /**
   * Generates code to lift a primitive WASM return value.
   */
  private static CodeBlock liftPrimitive(BindgenType type) {
    String name = type.getName().toLowerCase();
    switch (name) {
      case "bool":
        return CodeBlock.of("((($T) result).intValue() != 0)", Number.class);
      case "s8":
      case "u8":
        return CodeBlock.of("(($T) result).byteValue()", Number.class);
      case "s16":
      case "u16":
        return CodeBlock.of("(($T) result).shortValue()", Number.class);
      case "s32":
      case "u32":
      case "i32":
        return CodeBlock.of("(($T) result).intValue()", Number.class);
      case "s64":
      case "u64":
      case "i64":
        return CodeBlock.of("(($T) result).longValue()", Number.class);
      case "f32":
      case "float32":
        return CodeBlock.of("(($T) result).floatValue()", Number.class);
      case "f64":
      case "float64":
        return CodeBlock.of("(($T) result).doubleValue()", Number.class);
      default:
        return CodeBlock.of("result");
    }
  }
}
