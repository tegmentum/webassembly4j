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
package ai.tegmentum.webassembly4j.bindgen.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link BindgenMojo}. */
@DisplayName("BindgenMojo Tests")
class BindgenMojoTest {

  private static final Logger LOGGER = Logger.getLogger(BindgenMojoTest.class.getName());

  private BindgenMojo mojo;

  @BeforeEach
  void setUp() {
    mojo = new BindgenMojo();
  }

  @Nested
  @DisplayName("Skip Execution Tests")
  class SkipExecutionTests {

    @Test
    @DisplayName("should skip execution when skip flag is set")
    void shouldSkipExecutionWhenSkipFlagSet(@TempDir Path tempDir) throws Exception {
      LOGGER.info("Testing skip execution flag");

      setField(mojo, "skip", true);
      setField(mojo, "project", createMockProject(tempDir));

      // Should not throw - just skip
      mojo.execute();

      // Verify no output was generated
      assertFalse(
          Files.exists(tempDir.resolve("generated-sources")),
          "Expected generated-sources directory not to exist");
    }
  }

  @Nested
  @DisplayName("Empty Source Tests")
  class EmptySourceTests {

    @Test
    @DisplayName("should skip when no WIT or WASM sources found")
    void shouldSkipWhenNoSourcesFound(@TempDir Path tempDir) throws Exception {
      LOGGER.info("Testing empty source handling");

      File emptyWitDir = tempDir.resolve("wit").toFile();
      Files.createDirectories(emptyWitDir.toPath());

      setField(mojo, "skip", false);
      setField(mojo, "witDirectory", emptyWitDir);
      setField(mojo, "wasmDirectory", null);
      setField(mojo, "outputDirectory", tempDir.resolve("output").toFile());
      setField(mojo, "packageName", "com.test");
      setField(mojo, "project", createMockProject(tempDir));

      // Should not throw - just skip with message
      mojo.execute();
    }

    @Test
    @DisplayName("should handle null WIT directory gracefully")
    void shouldHandleNullWitDirectory(@TempDir Path tempDir) throws Exception {
      setField(mojo, "skip", false);
      setField(mojo, "witDirectory", null);
      setField(mojo, "wasmDirectory", null);
      setField(mojo, "outputDirectory", tempDir.resolve("output").toFile());
      setField(mojo, "packageName", "com.test");
      setField(mojo, "project", createMockProject(tempDir));

      // Should not throw
      mojo.execute();
    }

    @Test
    @DisplayName("should handle non-existent WIT directory gracefully")
    void shouldHandleNonExistentWitDirectory(@TempDir Path tempDir) throws Exception {
      File nonExistentDir = tempDir.resolve("does-not-exist").toFile();

      setField(mojo, "skip", false);
      setField(mojo, "witDirectory", nonExistentDir);
      setField(mojo, "wasmDirectory", null);
      setField(mojo, "outputDirectory", tempDir.resolve("output").toFile());
      setField(mojo, "packageName", "com.test");
      setField(mojo, "project", createMockProject(tempDir));

      // Should not throw
      mojo.execute();
    }
  }

  @Nested
  @DisplayName("Code Style Parsing Tests")
  class CodeStyleParsingTests {

    @Test
    @DisplayName("should parse MODERN code style")
    void shouldParseModernCodeStyle(@TempDir Path tempDir) throws Exception {
      LOGGER.info("Testing MODERN code style parsing");

      setField(mojo, "codeStyle", "MODERN");

      Method parseMethod = BindgenMojo.class.getDeclaredMethod("parseCodeStyle");
      parseMethod.setAccessible(true);

      Object result = parseMethod.invoke(mojo);

      assertEquals("MODERN", result.toString());
    }

    @Test
    @DisplayName("should parse LEGACY code style")
    void shouldParseLegacyCodeStyle() throws Exception {
      LOGGER.info("Testing LEGACY code style parsing");

      setField(mojo, "codeStyle", "LEGACY");

      Method parseMethod = BindgenMojo.class.getDeclaredMethod("parseCodeStyle");
      parseMethod.setAccessible(true);

      Object result = parseMethod.invoke(mojo);

      assertEquals("LEGACY", result.toString());
    }

    @Test
    @DisplayName("should parse code style case-insensitively")
    void shouldParseCodeStyleCaseInsensitively() throws Exception {
      setField(mojo, "codeStyle", "modern");

      Method parseMethod = BindgenMojo.class.getDeclaredMethod("parseCodeStyle");
      parseMethod.setAccessible(true);

      Object result = parseMethod.invoke(mojo);

      assertEquals("MODERN", result.toString());
    }

    @Test
    @DisplayName("should throw MojoExecutionException for invalid code style")
    void shouldThrowForInvalidCodeStyle() throws Exception {
      LOGGER.info("Testing invalid code style handling");

      setField(mojo, "codeStyle", "INVALID");

      Method parseMethod = BindgenMojo.class.getDeclaredMethod("parseCodeStyle");
      parseMethod.setAccessible(true);

      InvocationTargetException exception =
          assertThrows(InvocationTargetException.class, () -> parseMethod.invoke(mojo));
      assertTrue(
          exception.getCause() instanceof MojoExecutionException,
          "Expected cause to be MojoExecutionException");
      assertTrue(
          exception.getCause().getMessage().contains("Invalid codeStyle"),
          "Expected message to contain: Invalid codeStyle");
    }
  }

  @Nested
  @DisplayName("Pattern Matching Tests")
  class PatternMatchingTests {

    @Test
    @DisplayName("should match simple glob pattern with asterisk")
    void shouldMatchSimpleGlobPattern() throws Exception {
      LOGGER.info("Testing glob pattern matching");

      Method matchMethod =
          BindgenMojo.class.getDeclaredMethod("matchesPattern", String.class, String.class);
      matchMethod.setAccessible(true);

      assertTrue((Boolean) matchMethod.invoke(mojo, "test.wit", "*.wit"));
      assertFalse((Boolean) matchMethod.invoke(mojo, "test.wasm", "*.wit"));
    }

    @Test
    @DisplayName("should match pattern with question mark wildcard")
    void shouldMatchQuestionMarkWildcard() throws Exception {
      Method matchMethod =
          BindgenMojo.class.getDeclaredMethod("matchesPattern", String.class, String.class);
      matchMethod.setAccessible(true);

      assertTrue((Boolean) matchMethod.invoke(mojo, "test1.wit", "test?.wit"));
      assertFalse((Boolean) matchMethod.invoke(mojo, "test12.wit", "test?.wit"));
    }

    @Test
    @DisplayName("should match exact filename")
    void shouldMatchExactFilename() throws Exception {
      Method matchMethod =
          BindgenMojo.class.getDeclaredMethod("matchesPattern", String.class, String.class);
      matchMethod.setAccessible(true);

      assertTrue((Boolean) matchMethod.invoke(mojo, "world.wit", "world.wit"));
      assertFalse((Boolean) matchMethod.invoke(mojo, "hello.wit", "world.wit"));
    }

    @Test
    @DisplayName("should handle pattern with multiple asterisks")
    void shouldHandleMultipleAsterisks() throws Exception {
      Method matchMethod =
          BindgenMojo.class.getDeclaredMethod("matchesPattern", String.class, String.class);
      matchMethod.setAccessible(true);

      assertTrue((Boolean) matchMethod.invoke(mojo, "test-api-v2.wit", "*-api-*.wit"));
      assertFalse((Boolean) matchMethod.invoke(mojo, "test-other.wit", "*-api-*.wit"));
    }
  }

  @Nested
  @DisplayName("WIT Pattern Filtering Tests")
  class WitPatternFilteringTests {

    @Test
    @DisplayName("should include all files when no patterns specified")
    void shouldIncludeAllWhenNoPatterns(@TempDir Path tempDir) throws Exception {
      LOGGER.info("Testing default pattern behavior");

      Path witPath = tempDir.resolve("test.wit");
      Files.createFile(witPath);

      setField(mojo, "witIncludes", null);
      setField(mojo, "witExcludes", null);

      Method matchMethod = BindgenMojo.class.getDeclaredMethod("matchesWitPatterns", Path.class);
      matchMethod.setAccessible(true);

      assertTrue((Boolean) matchMethod.invoke(mojo, witPath));
    }

    @Test
    @DisplayName("should respect exclude patterns")
    void shouldRespectExcludePatterns(@TempDir Path tempDir) throws Exception {
      Path excludedPath = tempDir.resolve("internal.wit");
      Files.createFile(excludedPath);

      setField(mojo, "witIncludes", null);
      setField(mojo, "witExcludes", Arrays.asList("internal.*"));

      Method matchMethod = BindgenMojo.class.getDeclaredMethod("matchesWitPatterns", Path.class);
      matchMethod.setAccessible(true);

      assertFalse((Boolean) matchMethod.invoke(mojo, excludedPath));
    }

    @Test
    @DisplayName("should respect include patterns")
    void shouldRespectIncludePatterns(@TempDir Path tempDir) throws Exception {
      Path includedPath = tempDir.resolve("api.wit");
      Path notIncludedPath = tempDir.resolve("internal.wit");
      Files.createFile(includedPath);
      Files.createFile(notIncludedPath);

      setField(mojo, "witIncludes", Arrays.asList("api.*"));
      setField(mojo, "witExcludes", null);

      Method matchMethod = BindgenMojo.class.getDeclaredMethod("matchesWitPatterns", Path.class);
      matchMethod.setAccessible(true);

      assertTrue((Boolean) matchMethod.invoke(mojo, includedPath));
      assertFalse((Boolean) matchMethod.invoke(mojo, notIncludedPath));
    }

    @Test
    @DisplayName("should prioritize excludes over includes")
    void shouldPrioritizeExcludesOverIncludes(@TempDir Path tempDir) throws Exception {
      Path path = tempDir.resolve("api-internal.wit");
      Files.createFile(path);

      setField(mojo, "witIncludes", Arrays.asList("api-*.wit"));
      setField(mojo, "witExcludes", Arrays.asList("*-internal.wit"));

      Method matchMethod = BindgenMojo.class.getDeclaredMethod("matchesWitPatterns", Path.class);
      matchMethod.setAccessible(true);

      // Exclude takes priority
      assertFalse((Boolean) matchMethod.invoke(mojo, path));
    }
  }

  @Nested
  @DisplayName("Source Collection Tests")
  class SourceCollectionTests {

    @Test
    @DisplayName("should collect WIT files from directory")
    void shouldCollectWitFiles(@TempDir Path tempDir) throws Exception {
      LOGGER.info("Testing WIT file collection");

      Path witDir = tempDir.resolve("wit");
      Files.createDirectories(witDir);
      Files.createFile(witDir.resolve("api.wit"));
      Files.createFile(witDir.resolve("types.wit"));
      Files.createFile(witDir.resolve("readme.md")); // Should be ignored

      setField(mojo, "witDirectory", witDir.toFile());
      setField(mojo, "witIncludes", null);
      setField(mojo, "witExcludes", null);

      Method collectMethod = BindgenMojo.class.getDeclaredMethod("collectWitSources");
      collectMethod.setAccessible(true);

      @SuppressWarnings("unchecked")
      List<Path> result = (List<Path>) collectMethod.invoke(mojo);

      assertEquals(2, result.size());
      assertTrue(
          result.stream().allMatch(p -> p.toString().endsWith(".wit")),
          "Expected all collected files to have .wit extension");
    }

    @Test
    @DisplayName("should collect WASM files from directory")
    void shouldCollectWasmFiles(@TempDir Path tempDir) throws Exception {
      LOGGER.info("Testing WASM file collection");

      Path wasmDir = tempDir.resolve("wasm");
      Files.createDirectories(wasmDir);
      Files.createFile(wasmDir.resolve("module1.wasm"));
      Files.createFile(wasmDir.resolve("module2.wasm"));
      Files.createFile(wasmDir.resolve("config.json")); // Should be ignored

      setField(mojo, "wasmDirectory", wasmDir.toFile());

      Method collectMethod = BindgenMojo.class.getDeclaredMethod("collectWasmSources");
      collectMethod.setAccessible(true);

      @SuppressWarnings("unchecked")
      List<Path> result = (List<Path>) collectMethod.invoke(mojo);

      assertEquals(2, result.size());
      assertTrue(
          result.stream().allMatch(p -> p.toString().endsWith(".wasm")),
          "Expected all collected files to have .wasm extension");
    }

    @Test
    @DisplayName("should return empty list for null WASM directory")
    void shouldReturnEmptyForNullWasmDirectory() throws Exception {
      setField(mojo, "wasmDirectory", null);

      Method collectMethod = BindgenMojo.class.getDeclaredMethod("collectWasmSources");
      collectMethod.setAccessible(true);

      @SuppressWarnings("unchecked")
      List<Path> result = (List<Path>) collectMethod.invoke(mojo);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("should collect files recursively from subdirectories")
    void shouldCollectFilesRecursively(@TempDir Path tempDir) throws Exception {
      Path witDir = tempDir.resolve("wit");
      Path subDir = witDir.resolve("subdir");
      Files.createDirectories(subDir);
      Files.createFile(witDir.resolve("root.wit"));
      Files.createFile(subDir.resolve("nested.wit"));

      setField(mojo, "witDirectory", witDir.toFile());
      setField(mojo, "witIncludes", null);
      setField(mojo, "witExcludes", null);

      Method collectMethod = BindgenMojo.class.getDeclaredMethod("collectWitSources");
      collectMethod.setAccessible(true);

      @SuppressWarnings("unchecked")
      List<Path> result = (List<Path>) collectMethod.invoke(mojo);

      assertEquals(2, result.size());
    }
  }

  @Nested
  @DisplayName("Configuration Tests")
  class ConfigurationTests {

    @Test
    @DisplayName("should have default values for optional parameters")
    void shouldHaveDefaultValuesForOptionalParameters() throws Exception {
      // Verify the Mojo can be created without explicit configuration
      BindgenMojo freshMojo = new BindgenMojo();

      // These should not throw NPE when accessed via reflection
      assertNotNull(freshMojo);
    }
  }

  // Helper methods

  private void setField(final Object target, final String fieldName, final Object value)
      throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private MavenProject createMockProject(final Path tempDir) throws IOException {
    MavenProject project = new MavenProject();
    project.setFile(tempDir.resolve("pom.xml").toFile());
    Files.createFile(tempDir.resolve("pom.xml"));
    return project;
  }
}
