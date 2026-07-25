/*
 * Copyright 2026 Tegmentum AI
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import ai.tegmentum.webassembly4j.bindgen.BindgenConfig;
import ai.tegmentum.webassembly4j.bindgen.BindgenException;
import ai.tegmentum.webassembly4j.bindgen.GeneratedSource;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenFunction;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenInterface;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenType;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for the two blocking bindgen bugs surfaced by the
 * wasmos:host/embedder@0.1.0 shape check.
 *
 * <p>Each test constructs a bindgen interface that reproduces one bug, then
 * feeds the generated {@code *Impl} source (plus a small hand-written stub of
 * the runtime types the generator references) to the platform
 * {@link JavaCompiler}. A javac error means the generator regressed.
 *
 * <ol>
 *   <li>Bug 1 — the marshalling body redeclared internal locals ({@code args},
 *       {@code retptr}, {@code result}) that collide with a WIT parameter of
 *       the same name. Reproducer:
 *       {@code host-call(provider-handle, func-idx, args: string)}, matching
 *       {@code embedder-callbacks.host-call} in the wasmos world (the wire
 *       type there is {@code list<u8>}; we use {@code string} in the repro to
 *       exercise the same collision without depending on the separate
 *       {@code List<Byte>} lowering bug).
 *   <li>Bug 2 — a function returning {@code result<T, E>} used to emit a
 *       literal {@code return /* TODO: lift RESULT *&#47;;} — a comment, not
 *       an expression. Reproducer: {@code call-export(name) ->
 *       result<string, error>}, matching the shape of
 *       {@code runtime-instance.call-export}.
 * </ol>
 *
 * <p>The stub surface below covers only the pieces {@code
 * ImplementationCodeGenerator} + {@code MarshallingStrategy} name today:
 * {@code Instance / Module / Engine / Function} from the api module and
 * {@code MarshalContext / MemoryReader / MemoryAllocator / Memory /
 * StringCodec} from the runtime module. If the generator starts naming a new
 * type, that will surface as a javac error here — which is the point.
 */
@DisplayName("ImplementationCodeGenerator bindgen bugs")
class ImplementationCodeGeneratorBugsTest {

  private static final String PACKAGE = "ai.tegmentum.bindgen.generated";

  private ImplementationCodeGenerator generator() {
    return new ImplementationCodeGenerator(
        BindgenConfig.builder()
            .packageName(PACKAGE)
            .outputDirectory(Path.of("target/test-output"))
            .addWitSource(Path.of("test.wit"))
            .generateImplementations(true)
            .generateServiceLoader(false)
            .build());
  }

  @Test
  @DisplayName(
      "bug 1: WIT parameter named `args` no longer collides with the internal args local")
  void paramNamedArgsDoesNotCollide() throws Exception {
    // Matches wasmos:host/embedder embedder-callbacks.host-call, minus the
    // list<u8> arg type (see class javadoc).
    final BindgenInterface iface =
        BindgenInterface.builder()
            .name("embedder-callbacks")
            .addFunction(
                BindgenFunction.builder()
                    .name("host-call")
                    .addParameter("provider-handle", BindgenType.primitive("u32"))
                    .addParameter("func-idx", BindgenType.primitive("u32"))
                    .addParameter("args", BindgenType.primitive("string"))
                    .build())
            .build();

    final GeneratedSource impl = generator().generate(Collections.singletonList(iface)).get(0);
    assertEquals("EmbedderCallbacksImpl", impl.getClassName());
    final String content = impl.getContent();

    // The internal ArrayList must have a name distinct from the `args` param.
    assertFalse(
        content.contains("java.util.List<Object> args ="),
        "internal args local must not shadow the WIT parameter named `args`\n" + content);
    assertTrue(
        content.contains("java.util.List<Object> args$0"),
        "expected disambiguated internal args local, got:\n" + content);
    // The disambiguated local threads through the invoke site too.
    assertTrue(
        content.contains("args$0.toArray()"),
        "expected the invoke site to use the disambiguated args local; got:\n" + content);

    compileImpl(iface, impl);
  }

  @Test
  @DisplayName(
      "bug 1 (companion): a param named `result` disambiguates the internal result local too")
  void paramNamedResultDoesNotCollide() throws Exception {
    // The generator declares an `Object result` local when the return type
    // needs marshalling but is not stored via a retptr (the "primitive
    // returned but string parameter present" branch). Confirm that a param
    // literally named `result` no longer shadows it.
    final BindgenInterface iface =
        BindgenInterface.builder()
            .name("reporter")
            .addFunction(
                BindgenFunction.builder()
                    .name("report")
                    .addParameter("result", BindgenType.primitive("string"))
                    .returnType(BindgenType.primitive("i32"))
                    .build())
            .build();

    final GeneratedSource impl = generator().generate(Collections.singletonList(iface)).get(0);
    final String content = impl.getContent();
    assertFalse(
        content.contains("Object result ="),
        "internal `result` local must not shadow the WIT parameter named `result`\n" + content);
    assertTrue(
        content.contains("Object result$0 ="),
        "expected disambiguated result local; got:\n" + content);

    compileImpl(iface, impl);
  }

  @Test
  @DisplayName(
      "bug 2: a result<T, E> return dispatches on the Canonical-ABI discriminant")
  void resultReturnEmitsCompileableBody() throws Exception {
    // call-export: func(name: string) -> result<string, error>
    // The ok arm is a `string` (in-memory lifter wired); the err arm is a
    // record reference — payload lifting for records isn't in this cut, so
    // that arm falls back to a specific-tag throw. The dispatch shape is
    // real.
    final BindgenInterface iface =
        BindgenInterface.builder()
            .name("runtime-instance")
            .addFunction(
                BindgenFunction.builder()
                    .name("call-export")
                    .addParameter("name", BindgenType.primitive("string"))
                    .returnType(
                        BindgenType.result(
                            BindgenType.primitive("string"), BindgenType.reference("error")))
                    .build())
            .build();

    final GeneratedSource impl = generator().generate(Collections.singletonList(iface)).get(0);
    final String content = impl.getContent();

    // Pre-fix the body emitted this literal — the fix must not.
    assertFalse(
        content.contains("return /* TODO: lift RESULT */"),
        "result<T, E> body must not emit a bare TODO comment in return position:\n" + content);
    // The previous placeholder was a whole-body throw. Real dispatch has an
    // if/else on the tag; the whole-body throw is gone.
    assertFalse(
        content.contains("TODO(bindgen): result<T,E>"),
        "expected the whole-body TODO throw to be replaced by real dispatch:\n" + content);
    // Canonical-ABI discriminant read from the retptr, then branch on it.
    assertTrue(
        content.contains("marshal.reader().readU8("),
        "expected a u8 discriminant read from the retptr:\n" + content);
    assertTrue(
        content.contains("if (tag$ == 0)"),
        "expected an `if (tag$ == 0)` dispatch on the discriminant:\n" + content);
    // Ok arm lifts a string from `retptr + payloadOffset` (payload alignment
    // for `string` is 4, so payload offset is 4).
    assertTrue(
        content.contains("WitResult.ok(marshal.reader().readString"),
        "expected the ok arm to lift a string via readString:\n" + content);
    // Err arm can't lift a record payload today; specific-tag throw
    // documents the gap without pretending the arm works.
    assertTrue(
        content.contains("err-arm payload lift for REFERENCE"),
        "expected the err arm to throw with a REFERENCE-payload TODO:\n" + content);
    // The method signature is still generated correctly.
    assertTrue(
        content.contains("public WitResult<String, Error> callExport"),
        "expected the WitResult<String, Error> return type on callExport:\n" + content);

    compileImpl(iface, impl);
  }

  @Test
  @DisplayName(
      "bug 2 (companion): a result<_, E> return returns WitResult.ok(null) in the ok arm")
  void resultReturnWithUnitOkPayload() throws Exception {
    // verify-world: func(expected-wit: string) -> result<_, error>
    // Ok payload is unit; the ok arm returns WitResult.ok(null). Err arm
    // still falls back to a REFERENCE-payload throw.
    final BindgenInterface iface =
        BindgenInterface.builder()
            .name("runtime-instance")
            .addFunction(
                BindgenFunction.builder()
                    .name("verify-world")
                    .addParameter("expected-wit", BindgenType.primitive("string"))
                    .returnType(BindgenType.result(null, BindgenType.reference("error")))
                    .build())
            .build();

    final GeneratedSource impl = generator().generate(Collections.singletonList(iface)).get(0);
    final String content = impl.getContent();

    assertTrue(
        content.contains("return WitResult.ok(null)"),
        "expected the unit-ok arm to return WitResult.ok(null):\n" + content);
    assertTrue(
        content.contains("public WitResult<Void, Error> verifyWorld"),
        "expected the WitResult<Void, Error> return type on verifyWorld:\n" + content);

    compileImpl(iface, impl);
  }

  @Test
  @DisplayName(
      "bug 2 (companion): a result<i32, _> return lifts the ok arm from the payload offset")
  void resultReturnPrimitiveOkPayload() throws Exception {
    // Simplest ok-arm shape: primitive payload lifts via a bare
    // readI32 at the aligned payload offset. Unit err arm returns null.
    final BindgenInterface iface =
        BindgenInterface.builder()
            .name("counter")
            .addFunction(
                BindgenFunction.builder()
                    .name("current")
                    .addParameter("name", BindgenType.primitive("string"))
                    .returnType(BindgenType.result(BindgenType.primitive("i32"), null))
                    .build())
            .build();

    final GeneratedSource impl = generator().generate(Collections.singletonList(iface)).get(0);
    final String content = impl.getContent();

    assertTrue(
        content.contains("WitResult.ok(marshal.reader().readI32"),
        "expected the ok arm to lift an int via readI32:\n" + content);
    assertTrue(
        content.contains("return WitResult.err(null)"),
        "expected the unit-err arm to return WitResult.err(null):\n" + content);

    compileImpl(iface, impl);
  }

  // -------------------------------------------------------------------
  // javac harness — compile the generated Impl against a hand-written stub
  // of the runtime surface it references, so the test can prove
  // compileability without dragging in the api / runtime modules as test
  // dependencies of bindgen (a coupling that would go the wrong direction).
  // -------------------------------------------------------------------

  private static final String STUB_SOURCE =
      String.join(
          "\n",
          "package " + PACKAGE + ";",
          "import java.util.Optional;",
          "// api package stubs — signatures cribbed from webassembly4j-api and",
          "// webassembly4j-runtime. Every symbol the generator names must exist",
          "// here or javac drops the whole test. New reader methods land here",
          "// alongside the generator changes that reference them.",
          "class ApiStubs {",
          "  public interface Instance {",
          "    Optional<Function> function(String name);",
          "  }",
          "  public interface Module { void close(); }",
          "  public interface Engine { void close(); }",
          "  public interface Function { Object invoke(Object... args); }",
          "  public interface Memory { void write(long offset, byte[] bytes); }",
          "  public interface MemoryAllocator { int allocate(int size, int alignment); }",
          "  public static class MemoryReader {",
          "    public String readString(int offset) { return null; }",
          "    public byte[] readBytes(int offset) { return null; }",
          "    public int readU8(int offset) { return 0; }",
          "    public boolean readBool(int offset) { return false; }",
          "    public int readI32(int offset) { return 0; }",
          "    public long readI64(int offset) { return 0L; }",
          "    public float readF32(int offset) { return 0f; }",
          "    public double readF64(int offset) { return 0d; }",
          "  }",
          "  public static class MarshalContext {",
          "    public static MarshalContext fromInstance(Instance i) { return new MarshalContext(); }",
          "    public Memory memory() { return null; }",
          "    public MemoryAllocator allocator() { return null; }",
          "    public MemoryReader reader() { return null; }",
          "  }",
          "  public static class StringCodec {",
          "    public static int[] encode(String s, Memory m, MemoryAllocator a) { return new int[2]; }",
          "  }",
          "}");

  private static void compileImpl(BindgenInterface iface, GeneratedSource impl) throws Exception {
    final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "JDK javac not available; run tests on a JDK not a JRE");

    // Rewrite the generated Impl to point at our in-test stub package so
    // javac can resolve every symbol. The rewrite is text-level (safe: the
    // generator writes fully-qualified imports in a stable, deterministic
    // form).
    final String rewrittenImpl =
        impl.getContent()
            .replace(
                "import ai.tegmentum.webassembly4j.api.Instance;",
                "import " + PACKAGE + ".ApiStubs.Instance;")
            .replace(
                "import ai.tegmentum.webassembly4j.api.Module;",
                "import " + PACKAGE + ".ApiStubs.Module;")
            .replace(
                "import ai.tegmentum.webassembly4j.api.Engine;",
                "import " + PACKAGE + ".ApiStubs.Engine;")
            .replace(
                "import ai.tegmentum.webassembly4j.api.Function;",
                "import " + PACKAGE + ".ApiStubs.Function;")
            .replace(
                "import ai.tegmentum.webassembly4j.runtime.marshal.MarshalContext;",
                "import " + PACKAGE + ".ApiStubs.MarshalContext;")
            .replace(
                "import ai.tegmentum.webassembly4j.runtime.marshal.StringCodec;",
                "import " + PACKAGE + ".ApiStubs.StringCodec;");

    final List<JavaFileObject> units = new ArrayList<>();
    units.add(sourceFile(PACKAGE + "." + impl.getClassName(), rewrittenImpl));
    units.add(stubInterfaceFor(iface));
    // Sibling record referenced by bug 2's Impl in the error-side of
    // WitResult<..., Error>. Idempotent for tests that don't reference it.
    units.add(sourceFile(PACKAGE + ".Error", "package " + PACKAGE + "; public class Error {}"));
    units.add(sourceFile(PACKAGE + ".ApiStubs", STUB_SOURCE));

    final StringWriter diagnostics = new StringWriter();
    final String cp = System.getProperty("java.class.path");
    final boolean ok;
    try (var fm = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
      ok =
          compiler
              .getTask(
                  new PrintWriter(diagnostics),
                  fm,
                  null,
                  List.of("-classpath", cp, "-proc:none"),
                  null,
                  units)
              .call();
    }
    if (!ok) {
      fail(
          "javac failed on generated "
              + impl.getClassName()
              + ":\n"
              + diagnostics
              + "\n---SOURCE---\n"
              + rewrittenImpl);
    }
  }

  /**
   * Build a minimal interface source that matches the parent surface the
   * generated {@code *Impl} implements. Signatures mirror what
   * {@link ImplementationCodeGenerator#generateMethod} would produce so the
   * {@code @Override} annotations resolve.
   */
  private static JavaFileObject stubInterfaceFor(BindgenInterface iface) {
    final StringBuilder sb = new StringBuilder();
    sb.append("package ").append(PACKAGE).append(";\n");
    sb.append("import java.util.List;\n");
    sb.append("import ai.tegmentum.webassembly4j.bindgen.wit.WitResult;\n");
    sb.append("public interface ").append(pascal(iface.getName())).append(" {\n");
    for (BindgenFunction f : iface.getFunctions()) {
      sb.append("  ").append(signature(f)).append(";\n");
    }
    sb.append("}\n");
    return sourceFile(PACKAGE + "." + pascal(iface.getName()), sb.toString());
  }

  private static String signature(BindgenFunction f) {
    final StringBuilder sb = new StringBuilder();
    sb.append(returnDecl(f)).append(' ').append(camel(f.getName())).append('(');
    boolean first = true;
    for (var p : f.getParameters()) {
      if (!first) {
        sb.append(", ");
      }
      first = false;
      sb.append(typeDecl(p.getType())).append(' ').append(camel(p.getName()));
    }
    sb.append(')');
    return sb.toString();
  }

  private static String returnDecl(BindgenFunction f) {
    if (!f.hasReturnType()) {
      return "void";
    }
    return typeDecl(f.getReturnType().get());
  }

  private static String typeDecl(BindgenType t) {
    switch (t.getKind()) {
      case PRIMITIVE:
        return primitiveDecl(t.getName());
      case LIST:
        return "List<" + boxDecl(t.getElementType().get()) + ">";
      case RESULT:
        String ok = t.getOkType().map(ImplementationCodeGeneratorBugsTest::boxDecl).orElse("Void");
        String err =
            t.getErrorType().map(ImplementationCodeGeneratorBugsTest::boxDecl).orElse("Void");
        return "WitResult<" + ok + ", " + err + ">";
      case REFERENCE:
        return pascal(t.getReferencedTypeName().orElse(t.getName()));
      default:
        return "Object";
    }
  }

  private static String boxDecl(BindgenType t) {
    if (t.getKind() == BindgenType.Kind.PRIMITIVE) {
      switch (t.getName().toLowerCase()) {
        case "u8":
        case "s8":
          return "Byte";
        case "u16":
        case "s16":
          return "Short";
        case "u32":
        case "s32":
        case "i32":
        case "char":
          return "Integer";
        case "u64":
        case "s64":
        case "i64":
          return "Long";
        case "f32":
        case "float32":
          return "Float";
        case "f64":
        case "float64":
          return "Double";
        case "bool":
          return "Boolean";
        case "string":
          return "String";
        default:
          return "Object";
      }
    }
    return typeDecl(t);
  }

  private static String primitiveDecl(String name) {
    switch (name.toLowerCase()) {
      case "u8":
      case "s8":
        return "byte";
      case "u16":
      case "s16":
        return "short";
      case "u32":
      case "s32":
      case "i32":
      case "char":
        return "int";
      case "u64":
      case "s64":
      case "i64":
        return "long";
      case "f32":
      case "float32":
        return "float";
      case "f64":
      case "float64":
        return "double";
      case "bool":
        return "boolean";
      case "string":
        return "String";
      default:
        throw new IllegalArgumentException("unhandled primitive: " + name);
    }
  }

  private static String pascal(String kebab) {
    final StringBuilder sb = new StringBuilder();
    boolean up = true;
    for (int i = 0; i < kebab.length(); i++) {
      final char c = kebab.charAt(i);
      if (c == '-' || c == '_') {
        up = true;
      } else if (up) {
        sb.append(Character.toUpperCase(c));
        up = false;
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  private static String camel(String kebab) {
    final String p = pascal(kebab);
    if (p.isEmpty()) {
      return p;
    }
    return Character.toLowerCase(p.charAt(0)) + p.substring(1);
  }

  private static JavaFileObject sourceFile(String qualifiedName, String content) {
    return new SimpleJavaFileObject(
        URI.create("string:///" + qualifiedName.replace('.', '/') + ".java"),
        JavaFileObject.Kind.SOURCE) {
      @Override
      public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return content;
      }
    };
  }
}
