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
import java.util.Optional;

/**
 * Maps bindgen types to JavaPoet code fragments for lowering (Java to WASM)
 * and lifting (WASM to Java) through the marshalling infrastructure.
 */
public final class MarshallingStrategy {

  private static final ClassName STRING_CODEC =
      ClassName.get("ai.tegmentum.webassembly4j.runtime.marshal", "StringCodec");
  private static final ClassName WIT_RESULT_CLASS =
      ClassName.get("ai.tegmentum.webassembly4j.bindgen.wit", "WitResult");

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
   * <p>For primitives and enums {@code valueVar} names the {@code Object}
   * returned by {@code Function.invoke}; for complex types it names the
   * return-pointer local in linear memory.
   *
   * @param type the bindgen type
   * @param valueVar the variable name (result Object, or retptr for complex types)
   * @return the lifting code block (an expression)
   */
  public static CodeBlock liftReturn(BindgenType type, String valueVar) {
    if (type == null) {
      return CodeBlock.of("null");
    }

    switch (type.getKind()) {
      case PRIMITIVE:
        if ("string".equals(type.getName())) {
          return CodeBlock.of("marshal.reader().readString($L)", valueVar);
        }
        return liftPrimitive(type, valueVar);

      case LIST:
        return CodeBlock.of("marshal.reader().readBytes($L)", valueVar);

      case ENUM:
        return CodeBlock.of("/* TODO: lift enum from ordinal */");

      default:
        return CodeBlock.of("/* TODO: lift $L */", type.getKind());
    }
  }

  /**
   * Emit an expression that lifts a Canonical-ABI-encoded {@code type} from a
   * concrete linear-memory offset expression. Returns {@link Optional#empty()}
   * when the payload kind has no in-memory lifter wired up yet (records,
   * variants, references, options, nested results). The caller is expected to
   * fall back to a statement-level placeholder (an
   * {@link UnsupportedOperationException} throw) in that case rather than
   * splicing a comment into an expression position.
   *
   * @param type the bindgen type of the value to lift
   * @param offsetExpr a Java expression whose value is the byte offset in
   *     linear memory where the value's Canonical-ABI representation begins
   * @return the lifted-value expression, or empty when unsupported
   */
  public static Optional<CodeBlock> liftFromOffset(final BindgenType type, final String offsetExpr) {
    if (type == null) {
      return Optional.empty();
    }
    switch (type.getKind()) {
      case PRIMITIVE:
        return Optional.of(liftPrimitiveFromOffset(type, offsetExpr));
      case LIST:
        // Canonical ABI encodes list<T> as (ptr, len). readBytes assumes u8;
        // that's fine for the wasmos world's list<u8> uses but would need
        // per-element lifting for list<record>. Guardrail-conservative: bail
        // to the fallback throw when the element isn't u8-shaped so we don't
        // silently mis-lift.
        {
          final BindgenType element = type.getElementType().orElse(null);
          if (element != null
              && element.getKind() == BindgenType.Kind.PRIMITIVE
              && isByteWidePrimitive(element.getName())) {
            return Optional.of(CodeBlock.of("marshal.reader().readBytes($L)", offsetExpr));
          }
          return Optional.empty();
        }
      case ENUM:
      case FLAGS:
      case RECORD:
      case VARIANT:
      case OPTION:
      case RESULT:
      case TUPLE:
      case REFERENCE:
      case RESOURCE:
      case FUNCTION:
      default:
        return Optional.empty();
    }
  }

  /**
   * Emit the dispatch statements that lift a {@code result<T, E>} return
   * whose Canonical-ABI representation lives at {@code retptrLocal}. The
   * emitted block reads the discriminant byte (0 = ok, 1 = err), branches,
   * and returns a {@link ai.tegmentum.webassembly4j.bindgen.wit.WitResult}
   * of the appropriate side.
   *
   * <p>Layout follows the Canonical ABI's variant rule for a 2-case variant:
   *
   * <pre>
   *   [tag: u8][pad ...][payload: max(size(T), size(E))]
   * </pre>
   *
   * where {@code payload} is aligned to {@code max(align(T), align(E))} and
   * the tag/pad total up to that alignment.
   *
   * <p>When a payload type has no in-memory lifter wired up (records,
   * references, options, nested results — see {@link #liftFromOffset}), the
   * corresponding arm emits an {@link UnsupportedOperationException} throw
   * with a specific TODO tag. The dispatch shape is real; the payload gap
   * is honest.
   *
   * @param resultType the RESULT-kinded bindgen return type
   * @param retptrLocal the Java identifier that holds the retptr int
   * @param methodName the outer method's WIT name — folded into the TODO
   *     message so post-mortems don't have to grep across the generator
   * @return statements that discharge the return with a real dispatch
   */
  public static CodeBlock resultDispatch(
      final BindgenType resultType, final String retptrLocal, final String methodName) {
    if (resultType == null || resultType.getKind() != BindgenType.Kind.RESULT) {
      throw new IllegalArgumentException(
          "resultDispatch requires a RESULT-kinded bindgen type, got: " + resultType);
    }
    final BindgenType okType = resultType.getOkType().orElse(null);
    final BindgenType errType = resultType.getErrorType().orElse(null);
    final int payloadOffset = resultPayloadOffset(okType, errType);
    // Unique local for the discriminant tag. The outer generator already
    // guarantees `retptrLocal` doesn't collide with a WIT parameter; the
    // tag local piggy-backs on the same convention (`$` cannot appear in a
    // JavaNaming-produced parameter name).
    final String tagLocal = "tag$";
    final CodeBlock.Builder out = CodeBlock.builder();
    out.addStatement("int $L = marshal.reader().readU8($L)", tagLocal, retptrLocal);
    out.beginControlFlow("if ($L == 0)", tagLocal);
    emitArm(out, okType, retptrLocal, payloadOffset, methodName, /* ok= */ true);
    out.nextControlFlow("else");
    emitArm(out, errType, retptrLocal, payloadOffset, methodName, /* ok= */ false);
    out.endControlFlow();
    return out.build();
  }

  /**
   * Return the linear-memory allocation size (bytes) an in-memory retptr for
   * {@code returnType} must reserve. Callers use this to size the
   * {@code allocator().allocate(size, align)} pre-call step so the payload
   * fits under the Canonical-ABI layout. Types with no computed layout get
   * a conservative 8-byte fallback that matches the pre-refactor default.
   */
  public static int retptrSize(final BindgenType returnType) {
    if (returnType == null) {
      return 8;
    }
    if (returnType.getKind() == BindgenType.Kind.RESULT) {
      final BindgenType ok = returnType.getOkType().orElse(null);
      final BindgenType err = returnType.getErrorType().orElse(null);
      final int payloadOffset = resultPayloadOffset(ok, err);
      final int payloadSize = Math.max(sizeOf(ok), sizeOf(err));
      final int total = payloadOffset + payloadSize;
      // Round up to alignment so downstream lifts don't read past the slot.
      final int align = resultAlignment(ok, err);
      return alignUp(total, align);
    }
    // Non-result complex returns keep the historical 8-byte reservation.
    return 8;
  }

  /**
   * Return the alignment (in bytes) an in-memory retptr for {@code returnType}
   * must satisfy. Matches {@link #retptrSize}'s sizing choices.
   */
  public static int retptrAlignment(final BindgenType returnType) {
    if (returnType == null) {
      return 4;
    }
    if (returnType.getKind() == BindgenType.Kind.RESULT) {
      final BindgenType ok = returnType.getOkType().orElse(null);
      final BindgenType err = returnType.getErrorType().orElse(null);
      return resultAlignment(ok, err);
    }
    return 4;
  }

  private static void emitArm(
      final CodeBlock.Builder out,
      final BindgenType payloadType,
      final String retptrLocal,
      final int payloadOffset,
      final String methodName,
      final boolean okArm) {
    final String factory = okArm ? "ok" : "err";
    if (payloadType == null) {
      // Unit payload — WitResult.<Void, ...>.ok(null) / err(null). The
      // generator's inferred WitResult<Void, E> / <T, Void> type parameters
      // pick up on the null.
      out.addStatement("return $T.$L(null)", WIT_RESULT_CLASS, factory);
      return;
    }
    final String offsetExpr =
        payloadOffset == 0 ? retptrLocal : "(" + retptrLocal + " + " + payloadOffset + ")";
    final Optional<CodeBlock> lifted = liftFromOffset(payloadType, offsetExpr);
    if (lifted.isPresent()) {
      out.addStatement("return $T.$L($L)", WIT_RESULT_CLASS, factory, lifted.get());
      return;
    }
    // Payload has no in-memory lifter yet. Throw so the class compiles and
    // the specific gap is discoverable via the exception message.
    out.addStatement(
        "throw new $T($S)",
        UnsupportedOperationException.class,
        "TODO(bindgen): "
            + factory
            + "-arm payload lift for "
            + payloadType.getKind()
            + " ("
            + payloadType.getName()
            + ") not yet wired in result<T,E> for "
            + methodName
            + " — see MarshallingStrategy#liftFromOffset");
  }

  /**
   * Byte offset of the payload slot within a Canonical-ABI-encoded
   * {@code result<T, E>}. Discriminant is a single byte at offset 0; payload
   * is aligned to {@code max(align(T), align(E), 1)}.
   */
  static int resultPayloadOffset(final BindgenType okType, final BindgenType errType) {
    final int align = resultAlignment(okType, errType);
    return alignUp(1, align);
  }

  static int resultAlignment(final BindgenType okType, final BindgenType errType) {
    return Math.max(1, Math.max(alignOf(okType), alignOf(errType)));
  }

  private static int alignUp(final int value, final int alignment) {
    if (alignment <= 1) {
      return value;
    }
    return (value + alignment - 1) & ~(alignment - 1);
  }

  /**
   * Canonical-ABI {@code alignment} of {@code type} in bytes. Conservative for
   * unknown / not-yet-implemented shapes: returns 4, matching the historical
   * retptr alignment and safe for both handles and (ptr, len) pairs.
   */
  static int alignOf(final BindgenType type) {
    if (type == null) {
      return 1;
    }
    switch (type.getKind()) {
      case PRIMITIVE:
        return primitiveAlign(type.getName());
      case ENUM:
        // ENUM discriminant width follows discriminant_size(n_cases). Data-
        // free layout so alignment == size.
        return enumDiscriminantWidth(type);
      case FLAGS:
        // FLAGS pack into 1/2/4-byte ints depending on flag count.
        return flagsWidth(type);
      case LIST:
      case RESOURCE:
      case REFERENCE:
        // list<T> = (ptr:i32, len:i32), align 4. Resource handles + opaque
        // references also lift as i32.
        return 4;
      case OPTION:
      case RESULT:
      case RECORD:
      case VARIANT:
      case TUPLE:
      case FUNCTION:
      default:
        return 4;
    }
  }

  /**
   * Canonical-ABI {@code size} of {@code type} in bytes. Conservative for
   * unknown / not-yet-implemented shapes: returns 8, which is safe for both
   * (ptr, len) pairs and 64-bit primitives.
   */
  static int sizeOf(final BindgenType type) {
    if (type == null) {
      return 0;
    }
    switch (type.getKind()) {
      case PRIMITIVE:
        return primitiveSize(type.getName());
      case ENUM:
        return enumDiscriminantWidth(type);
      case FLAGS:
        return flagsWidth(type);
      case LIST:
      case RESOURCE:
      case REFERENCE:
        return type.getKind() == BindgenType.Kind.LIST ? 8 : 4;
      case OPTION:
      case RESULT:
      case RECORD:
      case VARIANT:
      case TUPLE:
      case FUNCTION:
      default:
        return 8;
    }
  }

  private static int primitiveAlign(final String name) {
    switch (name.toLowerCase()) {
      case "bool":
      case "s8":
      case "u8":
        return 1;
      case "s16":
      case "u16":
        return 2;
      case "s32":
      case "u32":
      case "i32":
      case "f32":
      case "float32":
      case "char":
        return 4;
      case "s64":
      case "u64":
      case "i64":
      case "f64":
      case "float64":
        return 8;
      case "string":
        return 4; // (ptr:i32, len:i32)
      default:
        return 4;
    }
  }

  private static int primitiveSize(final String name) {
    switch (name.toLowerCase()) {
      case "bool":
      case "s8":
      case "u8":
        return 1;
      case "s16":
      case "u16":
        return 2;
      case "s32":
      case "u32":
      case "i32":
      case "f32":
      case "float32":
      case "char":
        return 4;
      case "s64":
      case "u64":
      case "i64":
      case "f64":
      case "float64":
        return 8;
      case "string":
        return 8; // (ptr:i32, len:i32)
      default:
        return 4;
    }
  }

  private static int enumDiscriminantWidth(final BindgenType type) {
    final int n =
        type.getEnumValues() != null && !type.getEnumValues().isEmpty()
            ? type.getEnumValues().size()
            : 2;
    if (n <= 256) {
      return 1;
    }
    if (n <= 65536) {
      return 2;
    }
    return 4;
  }

  private static int flagsWidth(final BindgenType type) {
    final int n =
        type.getEnumValues() != null && !type.getEnumValues().isEmpty()
            ? type.getEnumValues().size()
            : 1;
    if (n <= 8) {
      return 1;
    }
    if (n <= 16) {
      return 2;
    }
    return 4;
  }

  private static boolean isByteWidePrimitive(final String name) {
    if (name == null) {
      return false;
    }
    switch (name.toLowerCase()) {
      case "s8":
      case "u8":
        return true;
      default:
        return false;
    }
  }

  /**
   * Generates code to lift a primitive value from an in-memory offset. Used by
   * the result-dispatch generator when the ok/err payload is a primitive.
   */
  private static CodeBlock liftPrimitiveFromOffset(final BindgenType type, final String offsetExpr) {
    final String name = type.getName().toLowerCase();
    switch (name) {
      case "bool":
        return CodeBlock.of("marshal.reader().readBool($L)", offsetExpr);
      case "s8":
      case "u8":
        return CodeBlock.of("(byte) marshal.reader().readU8($L)", offsetExpr);
      case "s16":
      case "u16":
        // readI16 doesn't exist yet; sign-extend from a 32-bit read of the
        // low 4 bytes. Correct only when the store side zero-extended (u16)
        // or sign-extended (s16); Canonical ABI does exactly that for 16-bit
        // slots in variant payloads.
        return CodeBlock.of("(short) marshal.reader().readI32($L)", offsetExpr);
      case "s32":
      case "u32":
      case "i32":
      case "char":
        return CodeBlock.of("marshal.reader().readI32($L)", offsetExpr);
      case "s64":
      case "u64":
      case "i64":
        return CodeBlock.of("marshal.reader().readI64($L)", offsetExpr);
      case "f32":
      case "float32":
        return CodeBlock.of("marshal.reader().readF32($L)", offsetExpr);
      case "f64":
      case "float64":
        return CodeBlock.of("marshal.reader().readF64($L)", offsetExpr);
      case "string":
        return CodeBlock.of("marshal.reader().readString($L)", offsetExpr);
      default:
        return CodeBlock.of("marshal.reader().readI32($L)", offsetExpr);
    }
  }

  /**
   * Generates code to lift a primitive WASM return value.
   *
   * @param type the primitive bindgen type
   * @param resultVar the local variable holding the boxed {@code Object} returned by invoke
   */
  private static CodeBlock liftPrimitive(BindgenType type, String resultVar) {
    String name = type.getName().toLowerCase();
    switch (name) {
      case "bool":
        return CodeBlock.of("((($T) $L).intValue() != 0)", Number.class, resultVar);
      case "s8":
      case "u8":
        return CodeBlock.of("(($T) $L).byteValue()", Number.class, resultVar);
      case "s16":
      case "u16":
        return CodeBlock.of("(($T) $L).shortValue()", Number.class, resultVar);
      case "s32":
      case "u32":
      case "i32":
        return CodeBlock.of("(($T) $L).intValue()", Number.class, resultVar);
      case "s64":
      case "u64":
      case "i64":
        return CodeBlock.of("(($T) $L).longValue()", Number.class, resultVar);
      case "f32":
      case "float32":
        return CodeBlock.of("(($T) $L).floatValue()", Number.class, resultVar);
      case "f64":
      case "float64":
        return CodeBlock.of("(($T) $L).doubleValue()", Number.class, resultVar);
      default:
        return CodeBlock.of("$L", resultVar);
    }
  }
}
