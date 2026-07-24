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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import ai.tegmentum.webassembly4j.bindgen.BindgenConfig;
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
 * Bindgen 2.0 SPI-dispatch shape check for the {@code wasmos:host/embedder@0.1.0} WIT world.
 *
 * <p>Verifies that when {@code runtimeProviderName} is set, the generator:
 *
 * <ol>
 *   <li>Emits a new {@code EmbedderRuntime} interface + {@code EmbedderRuntimeRegistry} class in
 *       the same target package.
 *   <li>Rewrites every resource method body to dispatch through the registry (no more {@code throw
 *       new UnsupportedOperationException}).
 *   <li>Still compiles cleanly through the platform javac.
 *   <li>Produces byte-identical output across two consecutive runs (determinism).
 * </ol>
 */
@DisplayName("bindgen 2.0 SPI dispatch: wasmos:host/embedder@0.1.0")
class WasmosEmbedderSpiDispatchTest {

  private static final String WIT_RESOURCE = "wasmos-embedder/embedder.wit";
  private static final String PACKAGE = "ai.tegmentum.wasmos.embedder.generated";
  private static final String SPI_NAME = "EmbedderRuntime";

  private static Path witFixture;

  @BeforeAll
  static void locateFixture() throws URISyntaxException {
    final URL resource =
        WasmosEmbedderSpiDispatchTest.class.getClassLoader().getResource(WIT_RESOURCE);
    assertNotNull(resource, "fixture " + WIT_RESOURCE + " missing from test resources");
    witFixture = Path.of(URLDecoder.decode(resource.toURI().getPath(), StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("SPI mode emits interface + registry and dispatches resource bodies")
  void spiModeGeneratesDispatchingSurface(@TempDir Path outputDir) throws Exception {
    final BindgenConfig config =
        BindgenConfig.builder()
            .packageName(PACKAGE)
            .outputDirectory(outputDir)
            .codeStyle(CodeStyle.MODERN)
            .addWitSource(witFixture)
            .generateImplementations(false)
            .generateServiceLoader(false)
            .runtimeProviderName(SPI_NAME)
            .build();

    final CodeGenerator generator = new CodeGenerator(config);
    final List<GeneratedSource> sources = generator.generate();
    final Map<String, String> byClass = toSourceMap(sources);

    // SPI interface + registry both present.
    assertTrue(byClass.containsKey(SPI_NAME), SPI_NAME + " interface must be generated");
    assertTrue(
        byClass.containsKey(SPI_NAME + "Registry"),
        SPI_NAME + "Registry class must be generated");

    final String iface = byClass.get(SPI_NAME);
    assertTrue(
        iface.contains("public interface " + SPI_NAME),
        SPI_NAME + " must be a public interface");
    // Method-per-resource-method: HostProvider constructor + close.
    assertTrue(iface.contains("HostProvider hostProviderCreate("), "SPI missing hostProviderCreate");
    assertTrue(iface.contains("void hostProviderClose(long handle)"), "SPI missing hostProviderClose");
    // Method-per-resource-method: RuntimeInstance static + instance methods + close.
    assertTrue(
        iface.contains("runtimeInstanceInstantiate("), "SPI missing runtimeInstanceInstantiate");
    assertTrue(
        iface.contains("runtimeInstanceCallExport(long handle,"),
        "SPI missing runtimeInstanceCallExport(long, ...)");
    assertTrue(
        iface.contains("runtimeInstanceIntrospect(long handle)"),
        "SPI missing runtimeInstanceIntrospect(long)");
    assertTrue(
        iface.contains("runtimeInstanceVerifyWorld(long handle,"),
        "SPI missing runtimeInstanceVerifyWorld(long, ...)");
    assertTrue(
        iface.contains("void runtimeInstanceClose(long handle)"),
        "SPI missing runtimeInstanceClose(long)");

    final String registry = byClass.get(SPI_NAME + "Registry");
    assertTrue(
        registry.contains("public static void install(" + SPI_NAME + " provider)"),
        "Registry missing install(SPI)");
    assertTrue(
        registry.contains("public static " + SPI_NAME + " runtime()"),
        "Registry missing runtime()");
    assertTrue(registry.contains("public static void uninstall()"), "Registry missing uninstall()");
    assertTrue(
        registry.contains("private static volatile"),
        "Registry provider field must be private static volatile");

    // RuntimeInstance dispatches, doesn't throw.
    final String rti = byClass.get("RuntimeInstance");
    assertNotNull(rti, "RuntimeInstance still emitted in SPI mode");
    assertFalse(
        rti.contains("throw new UnsupportedOperationException"),
        "RuntimeInstance must not throw UnsupportedOperationException in SPI mode:\n" + rti);
    assertTrue(
        rti.contains(SPI_NAME + "Registry.runtime().runtimeInstanceInstantiate("),
        "RuntimeInstance.instantiate must dispatch through registry");
    assertTrue(
        rti.contains(SPI_NAME + "Registry.runtime().runtimeInstanceCallExport(this.handle,"),
        "RuntimeInstance.callExport must pass this.handle to registry");
    assertTrue(
        rti.contains(SPI_NAME + "Registry.runtime().runtimeInstanceClose(this.handle)"),
        "RuntimeInstance.close must dispatch through registry");

    // HostProvider constructor factory dispatches, doesn't throw.
    final String hp = byClass.get("HostProvider");
    assertNotNull(hp, "HostProvider still emitted in SPI mode");
    assertFalse(
        hp.contains("throw new UnsupportedOperationException"),
        "HostProvider must not throw UnsupportedOperationException in SPI mode:\n" + hp);
    assertTrue(
        hp.contains(SPI_NAME + "Registry.runtime().hostProviderCreate("),
        "HostProvider.create must dispatch through registry");
    assertTrue(
        hp.contains(SPI_NAME + "Registry.runtime().hostProviderClose(this.handle)"),
        "HostProvider.close must dispatch through registry");
    // SPI-mode contract: even for resources with a WIT constructor, the
    // handle-holding ctor must be public so an out-of-package SPI impl can
    // construct wrapped resources. Legacy mode makes it protected.
    assertTrue(
        hp.contains("public HostProvider(long handle)"),
        "HostProvider(long) must be public in SPI mode for out-of-package impls");

    // Determinism: same input, same generated content.
    final CodeGenerator generator2 = new CodeGenerator(config);
    final Map<String, String> secondPass = toSourceMap(generator2.generate());
    assertEquals(byClass.keySet(), secondPass.keySet(), "class set must be deterministic");
    for (final String className : byClass.keySet()) {
      assertEquals(
          byClass.get(className), secondPass.get(className), "content drift in " + className);
    }

    // Compile the whole SPI-mode surface with platform javac.
    for (final GeneratedSource src : sources) {
      src.writeTo(outputDir);
    }
    compileGeneratedSurface(outputDir);
  }

  @Test
  @DisplayName("legacy mode (no runtimeProviderName) still emits throwing bodies")
  void legacyModeUnchanged(@TempDir Path outputDir) throws Exception {
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
    final Map<String, String> byClass = toSourceMap(generator.generate());
    assertFalse(byClass.containsKey(SPI_NAME), "SPI interface must NOT emit in legacy mode");
    assertFalse(
        byClass.containsKey(SPI_NAME + "Registry"),
        "SPI registry must NOT emit in legacy mode");
    assertTrue(
        byClass.get("RuntimeInstance").contains("throw new UnsupportedOperationException"),
        "Legacy RuntimeInstance bodies must still throw");
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
    assertNotNull(compiler, "No JavaCompiler available on the running JDK.");

    final List<File> javaFiles = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(outputDir)) {
      paths.filter(p -> p.toString().endsWith(".java")).forEach(p -> javaFiles.add(p.toFile()));
    }
    if (javaFiles.isEmpty()) {
      fail("No generated .java files found under " + outputDir);
    }

    final Path classDir = outputDir.resolve("classes");
    Files.createDirectories(classDir);

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
                      new PrintWriter(diagnosticSink),
                      fileManager,
                      null,
                      options,
                      null,
                      compilationUnits)
                  .call()
              ? 0
              : 1;
    }
    if (rc != 0) {
      fail(
          "javac failed on SPI-mode generated surface:\n"
              + diagnosticSink
              + "\n"
              + errBuf.toString(StandardCharsets.UTF_8));
    }
  }
}
