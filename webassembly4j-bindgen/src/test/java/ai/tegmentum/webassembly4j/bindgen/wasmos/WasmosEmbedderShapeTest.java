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
package ai.tegmentum.webassembly4j.bindgen.wasmos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import ai.tegmentum.webassembly4j.bindgen.BindgenConfig;
import ai.tegmentum.webassembly4j.bindgen.BindgenException;
import ai.tegmentum.webassembly4j.bindgen.CodeGenerator;
import ai.tegmentum.webassembly4j.bindgen.CodeStyle;
import ai.tegmentum.webassembly4j.bindgen.GeneratedSource;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Shape-check test for the {@code wasmos:host/embedder@0.1.0} WIT world.
 *
 * <p>This is the JVM lane's proof-of-shape per ADR-006: bindgen consumes the
 * embedder WIT and produces a Java surface a JVM embedder can compile
 * against without hand-writing anything except the native {@code
 * wasmos_start} shim.
 *
 * <p>The test:
 *
 * <ol>
 *   <li>Runs {@link CodeGenerator} against the fixture copy of {@code
 *       embedder.wit} (kept under {@code src/test/resources/wasmos-embedder/}).
 *       Implementation classes are disabled because {@code
 *       ImplementationCodeGenerator} generates broken marshalling glue for the
 *       {@code hostCall} shape (the parameter-name / local-variable collision
 *       is a pre-existing bug in that generator, unrelated to this shape).
 *   <li>Asserts each file in the expected surface exists and contains the
 *       right members.
 *   <li>Feeds every generated file to the platform {@link JavaCompiler} and
 *       fails on any javac diagnostic.
 * </ol>
 */
@DisplayName("wasmos:host/embedder@0.1.0 shape check")
class WasmosEmbedderShapeTest {

  private static final String WIT_RESOURCE = "wasmos-embedder/embedder.wit";
  private static final String PACKAGE = "ai.tegmentum.wasmos.embedder.generated";

  private static Path witFixture;

  @BeforeAll
  static void locateFixture() throws URISyntaxException {
    final URL resource = WasmosEmbedderShapeTest.class.getClassLoader().getResource(WIT_RESOURCE);
    assertNotNull(resource, "fixture " + WIT_RESOURCE + " missing from test resources");
    witFixture = Path.of(URLDecoder.decode(resource.toURI().getPath(), StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("bindgen generates the expected embedder surface and it compiles")
  void generatesAndCompiles(@TempDir Path outputDir) throws Exception {
    final BindgenConfig config =
        BindgenConfig.builder()
            .packageName(PACKAGE)
            .outputDirectory(outputDir)
            .codeStyle(CodeStyle.MODERN)
            .addWitSource(witFixture)
            .generateImplementations(false)
            .generateServiceLoader(false)
            .build();

    final CodeGenerator generator = new CodeGenerator(config);
    final List<GeneratedSource> sources = generator.generate();
    assertTrue(sources.size() >= 7, "expected at least 7 generated sources, got " + sources.size());

    final Map<String, String> byClass = toSourceMap(sources);

    // Sanity: every top-level type / interface / resource is present.
    assertTrue(byClass.containsKey("ErrorCode"), "ErrorCode enum missing");
    assertTrue(byClass.containsKey("Error"), "Error record missing");
    assertTrue(byClass.containsKey("ImportSatisfaction"), "ImportSatisfaction record missing");
    assertTrue(byClass.containsKey("HostProvider"), "HostProvider resource missing");
    assertTrue(byClass.containsKey("RuntimeInstance"), "RuntimeInstance resource missing");
    assertTrue(byClass.containsKey("EmbedderApi"), "EmbedderApi interface missing");
    assertTrue(byClass.containsKey("EmbedderCallbacks"), "EmbedderCallbacks interface missing");

    // ErrorCode: all 7 WIT variants.
    final String errorCode = byClass.get("ErrorCode");
    for (final String v :
        List.of(
            "INVALID_COMPONENT",
            "UNSATISFIED_IMPORT",
            "MISSING_EXPORT",
            "HOST_CALLBACK_FAILED",
            "TRAP",
            "WIT_MISMATCH",
            "OTHER")) {
      assertTrue(errorCode.contains(v), "ErrorCode missing variant " + v);
    }

    // Error record: has both fields.
    final String errorType = byClass.get("Error");
    assertTrue(errorType.contains("ErrorCode code"), "Error missing 'code' field");
    assertTrue(errorType.contains("String message"), "Error missing 'message' field");

    // RuntimeInstance: static instantiate + instance methods.
    final String rti = byClass.get("RuntimeInstance");
    assertTrue(
        rti.contains("public static") && rti.contains("instantiate("),
        "RuntimeInstance.instantiate must be public static");
    assertTrue(rti.contains("callExport("), "RuntimeInstance.callExport missing");
    assertTrue(rti.contains("introspect()"), "RuntimeInstance.introspect missing");
    assertTrue(rti.contains("verifyWorld("), "RuntimeInstance.verifyWorld missing");
    assertTrue(rti.contains("implements AutoCloseable"), "RuntimeInstance must be AutoCloseable");

    // HostProvider: static create(interfaceName, numFuncs) from the WIT constructor.
    final String hp = byClass.get("HostProvider");
    assertTrue(
        hp.contains("public static HostProvider create(String interfaceName, int numFuncs)"),
        "HostProvider must expose a create(String, int) factory for the WIT constructor");
    assertTrue(hp.contains("implements AutoCloseable"), "HostProvider must be AutoCloseable");

    // EmbedderCallbacks: three abstract methods.
    final String cb = byClass.get("EmbedderCallbacks");
    assertTrue(cb.contains("public interface EmbedderCallbacks"), "EmbedderCallbacks must be interface");
    assertTrue(cb.contains("hostCall("), "EmbedderCallbacks missing hostCall");
    assertTrue(cb.contains("nowMonotonicNs()"), "EmbedderCallbacks missing nowMonotonicNs");
    assertTrue(cb.contains("readRandom("), "EmbedderCallbacks missing readRandom");

    // Determinism: same input, same generated content.
    final CodeGenerator generator2 = new CodeGenerator(config);
    final Map<String, String> secondPass = toSourceMap(generator2.generate());
    assertEquals(byClass.keySet(), secondPass.keySet(), "class set must be deterministic");
    for (final String className : byClass.keySet()) {
      assertEquals(byClass.get(className), secondPass.get(className), "content drift in " + className);
    }

    // Compile every generated file with the platform javac. This is what
    // gives the shape check teeth — regex assertions only prove the text is
    // there, not that the whole surface is a valid Java program.
    for (final GeneratedSource src : sources) {
      src.writeTo(outputDir);
    }
    compileGeneratedSurface(outputDir);
  }

  private static Map<String, String> toSourceMap(final List<GeneratedSource> sources) {
    final Map<String, String> map = new HashMap<>();
    for (final GeneratedSource src : sources) {
      map.put(src.getClassName(), src.getContent());
    }
    return map;
  }

  private static void compileGeneratedSurface(final Path outputDir) throws Exception {
    final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(
        compiler,
        "No JavaCompiler available on the running JDK — run the test suite on a JDK, not a JRE.");

    final List<File> javaFiles = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(outputDir)) {
      paths
          .filter(p -> p.toString().endsWith(".java"))
          .forEach(p -> javaFiles.add(p.toFile()));
    }
    if (javaFiles.isEmpty()) {
      fail("No generated .java files found under " + outputDir);
    }

    final Path classDir = outputDir.resolve("classes");
    Files.createDirectories(classDir);

    // Provide the classpath: WitResult (in bindgen module classes) is
    // referenced by the generated code. Reuse the running test's classpath.
    final String cp = System.getProperty("java.class.path");
    final List<String> options = new ArrayList<>();
    options.add("-d");
    options.add(classDir.toString());
    options.add("-source");
    options.add("11");
    options.add("-target");
    options.add("11");
    options.add("-classpath");
    options.add(cp);

    final ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
    final StringWriter diagnosticSink = new StringWriter();
    final int rc;
    try (var fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
      final Iterable<? extends javax.tools.JavaFileObject> compilationUnits =
          fileManager.getJavaFileObjectsFromFiles(javaFiles);
      rc =
          compiler
                  .getTask(
                      new PrintWriter(diagnosticSink), fileManager, null, options, null, compilationUnits)
                  .call()
              ? 0
              : 1;
    }
    if (rc != 0) {
      fail(
          "javac failed on generated surface:\n"
              + diagnosticSink
              + "\n"
              + errBuf.toString(StandardCharsets.UTF_8));
    }
  }
}
