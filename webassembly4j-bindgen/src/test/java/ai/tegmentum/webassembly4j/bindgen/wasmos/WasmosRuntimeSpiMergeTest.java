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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.tegmentum.webassembly4j.bindgen.BindgenConfig;
import ai.tegmentum.webassembly4j.bindgen.CodeGenerator;
import ai.tegmentum.webassembly4j.bindgen.CodeStyle;
import ai.tegmentum.webassembly4j.bindgen.GeneratedSource;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression: when {@code --runtime-provider} is set and the WIT surface is
 * spread across multiple files sharing a package, the generated SPI interface
 * MUST contain dispatch methods for every resource across every file — not
 * just the last file processed.
 *
 * <p>Before the fix, {@code CodeGenerator.generate()} ran the java code
 * generator per-file, so each pass emitted its own {@code SplitRuntime.java}
 * and the last write overwrote the earlier ones. Downstream: multi-file WIT
 * packages (like {@code wasmos:runtime@0.2.0}, which is 11 files) lost every
 * interface's methods except the last one's from the merged SPI.
 */
@DisplayName("bindgen 2.0 SPI merge across multi-file WIT packages")
class WasmosRuntimeSpiMergeTest {

  private static final String PACKAGE = "ai.tegmentum.wasmos.split.generated";
  private static final String SPI_NAME = "SplitRuntime";

  private static Path alphaWit;
  private static Path betaWit;

  @BeforeAll
  static void locateFixtures() throws URISyntaxException {
    alphaWit = resolveFixture("wasmos-runtime-split/alpha.wit");
    betaWit = resolveFixture("wasmos-runtime-split/beta.wit");
  }

  private static Path resolveFixture(final String name) throws URISyntaxException {
    final URL resource = WasmosRuntimeSpiMergeTest.class.getClassLoader().getResource(name);
    assertNotNull(resource, "fixture " + name + " missing from test resources");
    return Path.of(URLDecoder.decode(resource.toURI().getPath(), StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("SPI interface contains methods from both alpha.wit and beta.wit")
  void spiMergesResourcesAcrossFiles(@TempDir Path outputDir) throws Exception {
    final BindgenConfig config =
        BindgenConfig.builder()
            .packageName(PACKAGE)
            .outputDirectory(outputDir)
            .codeStyle(CodeStyle.MODERN)
            .addWitSource(alphaWit)
            .addWitSource(betaWit)
            .generateImplementations(false)
            .generateServiceLoader(false)
            .runtimeProviderName(SPI_NAME)
            .build();

    final CodeGenerator generator = new CodeGenerator(config);
    final List<GeneratedSource> sources = generator.generate();

    final Map<String, String> byClass = new HashMap<>();
    for (final GeneratedSource source : sources) {
      byClass.put(source.getClassName(), source.getContent());
    }

    assertTrue(byClass.containsKey(SPI_NAME), SPI_NAME + " interface must be generated");
    assertTrue(
        byClass.containsKey(SPI_NAME + "Registry"),
        SPI_NAME + "Registry class must be generated");

    final String iface = byClass.get(SPI_NAME);
    assertTrue(
        iface.contains("alphaCreate("),
        "SPI missing alphaCreate — alpha.wit interface was dropped:\n" + iface);
    assertTrue(iface.contains("alphaPing(long handle)"), "SPI missing alphaPing");
    assertTrue(iface.contains("void alphaClose(long handle)"), "SPI missing alphaClose");
    assertTrue(
        iface.contains("betaCreate("),
        "SPI missing betaCreate — beta.wit interface was dropped:\n" + iface);
    assertTrue(iface.contains("betaEcho(long handle,"), "SPI missing betaEcho");
    assertTrue(iface.contains("void betaClose(long handle)"), "SPI missing betaClose");
  }
}
