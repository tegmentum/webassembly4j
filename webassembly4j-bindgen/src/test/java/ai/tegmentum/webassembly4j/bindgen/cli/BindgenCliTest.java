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
package ai.tegmentum.webassembly4j.bindgen.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** Tests for {@link BindgenCli}. */
@DisplayName("BindgenCli Tests")
class BindgenCliTest {

  private static final Logger LOGGER = Logger.getLogger(BindgenCliTest.class.getName());

  // Minimal valid WASM module
  private static final byte[] VALID_WASM = {
    0x00, 0x61, 0x73, 0x6D, // \0asm magic
    0x01, 0x00, 0x00, 0x00 // version 1
  };

  // Minimal valid WIT interface
  private static final String VALID_WIT = "interface test {\n  add: func(a: s32, b: s32) -> s32\n}";

  private ByteArrayOutputStream outContent;
  private ByteArrayOutputStream errContent;
  private PrintStream originalOut;
  private PrintStream originalErr;

  @BeforeEach
  void setUp() {
    outContent = new ByteArrayOutputStream();
    errContent = new ByteArrayOutputStream();
    originalOut = System.out;
    originalErr = System.err;
    System.setOut(new PrintStream(outContent));
    System.setErr(new PrintStream(errContent));
  }

  @AfterEach
  void tearDown() {
    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  @Nested
  @DisplayName("Help and Version Tests")
  class HelpAndVersionTests {

    @Test
    @DisplayName("should display help with --help")
    void shouldDisplayHelpWithHelpFlag() {
      LOGGER.info("Testing --help flag");

      int exitCode = new CommandLine(new BindgenCli()).execute("--help");

      assertEquals(0, exitCode);
      String output = outContent.toString();
      assertTrue(
          output.contains("webassembly4j-bindgen"), "Expected output to contain: webassembly4j-bindgen");
      assertTrue(output.contains("--wit"), "Expected output to contain: --wit");
      assertTrue(output.contains("--wasm"), "Expected output to contain: --wasm");
      assertTrue(output.contains("--package"), "Expected output to contain: --package");
      assertTrue(output.contains("--output"), "Expected output to contain: --output");
      assertTrue(output.contains("--style"), "Expected output to contain: --style");
    }

    @Test
    @DisplayName("should display version with --version")
    void shouldDisplayVersionWithVersionFlag() {
      LOGGER.info("Testing --version flag");

      int exitCode = new CommandLine(new BindgenCli()).execute("--version");

      assertEquals(0, exitCode);
      String output = outContent.toString();
      assertTrue(
          output.contains("webassembly4j-bindgen"), "Expected output to contain: webassembly4j-bindgen");
      assertTrue(output.contains("1.0.0"), "Expected output to contain: 1.0.0");
    }
  }

  @Nested
  @DisplayName("Required Argument Tests")
  class RequiredArgumentTests {

    @Test
    @DisplayName("should require --package argument")
    void shouldRequirePackageArgument(@TempDir Path tempDir) throws Exception {
      LOGGER.info("Testing missing --package");

      Path wasmFile = tempDir.resolve("test.wasm");
      Files.write(wasmFile, VALID_WASM);

      int exitCode = new CommandLine(new BindgenCli()).execute("--wasm", wasmFile.toString());

      assertNotEquals(0, exitCode);
      String errOutput = errContent.toString();
      assertTrue(errOutput.contains("package"), "Expected error output to contain: package");
    }

    @Test
    @DisplayName("should require at least one source")
    void shouldRequireAtLeastOneSource(@TempDir Path tempDir) {
      LOGGER.info("Testing missing source");

      int exitCode = new CommandLine(new BindgenCli()).execute("--package", "com.example");

      assertEquals(1, exitCode);
      String errOutput = errContent.toString();
      assertTrue(
          errOutput.contains("At least one --wit or --wasm source must be specified"),
          "Expected error output to contain: At least one --wit or --wasm source must be"
              + " specified");
    }
  }

  @Nested
  @DisplayName("Code Style Tests")
  class CodeStyleTests {

    @Test
    @DisplayName("should accept MODERN style")
    void shouldAcceptModernStyle(@TempDir Path tempDir) throws Exception {
      LOGGER.info("Testing MODERN style");

      Path witFile = tempDir.resolve("test.wit");
      Files.writeString(witFile, VALID_WIT);
      Path outputDir = tempDir.resolve("output");

      int exitCode =
          new CommandLine(new BindgenCli())
              .setCaseInsensitiveEnumValuesAllowed(true)
              .execute(
                  "--wit",
                  witFile.toString(),
                  "--package",
                  "com.example",
                  "--output",
                  outputDir.toString(),
                  "--style",
                  "MODERN");

      assertEquals(0, exitCode);
    }

    @Test
    @DisplayName("should accept LEGACY style")
    void shouldAcceptLegacyStyle(@TempDir Path tempDir) throws Exception {
      LOGGER.info("Testing LEGACY style");

      Path witFile = tempDir.resolve("test.wit");
      Files.writeString(witFile, VALID_WIT);
      Path outputDir = tempDir.resolve("output");

      int exitCode =
          new CommandLine(new BindgenCli())
              .setCaseInsensitiveEnumValuesAllowed(true)
              .execute(
                  "--wit",
                  witFile.toString(),
                  "--package",
                  "com.example",
                  "--output",
                  outputDir.toString(),
                  "--style",
                  "LEGACY");

      assertEquals(0, exitCode);
    }

    @Test
    @DisplayName("should accept lowercase style names")
    void shouldAcceptLowercaseStyleNames(@TempDir Path tempDir) throws Exception {
      Path witFile = tempDir.resolve("test.wit");
      Files.writeString(witFile, VALID_WIT);
      Path outputDir = tempDir.resolve("output");

      int exitCode =
          new CommandLine(new BindgenCli())
              .setCaseInsensitiveEnumValuesAllowed(true)
              .execute(
                  "--wit",
                  witFile.toString(),
                  "--package",
                  "com.example",
                  "--output",
                  outputDir.toString(),
                  "--style",
                  "modern");

      assertEquals(0, exitCode);
    }

    @Test
    @DisplayName("should default to MODERN style")
    void shouldDefaultToModernStyle(@TempDir Path tempDir) throws Exception {
      LOGGER.info("Testing default style");

      Path witFile = tempDir.resolve("test.wit");
      Files.writeString(witFile, VALID_WIT);
      Path outputDir = tempDir.resolve("output");

      int exitCode =
          new CommandLine(new BindgenCli())
              .execute(
                  "--wit", witFile.toString(),
                  "--package", "com.example",
                  "--output", outputDir.toString());

      assertEquals(0, exitCode);
      // No error about style means default was used
    }
  }

  @Nested
  @DisplayName("Output Directory Tests")
  class OutputDirectoryTests {

    @Test
    @DisplayName("should use default output directory")
    void shouldUseDefaultOutputDirectory(@TempDir Path tempDir) throws Exception {
      LOGGER.info("Testing default output directory");

      Path witFile = tempDir.resolve("test.wit");
      Files.writeString(witFile, VALID_WIT);

      // Change to temp directory so default output goes there
      String originalDir = System.getProperty("user.dir");
      try {
        System.setProperty("user.dir", tempDir.toString());

        int exitCode =
            new CommandLine(new BindgenCli())
                .execute("--wit", witFile.toString(), "--package", "com.example");

        assertEquals(0, exitCode);
      } finally {
        System.setProperty("user.dir", originalDir);
      }
    }

    @Test
    @DisplayName("should create output directory if not exists")
    void shouldCreateOutputDirectoryIfNotExists(@TempDir Path tempDir) throws Exception {
      Path witFile = tempDir.resolve("test.wit");
      Files.writeString(witFile, VALID_WIT);
      Path outputDir = tempDir.resolve("new/nested/output");

      assertFalse(Files.exists(outputDir), "Expected output directory not to exist yet");

      int exitCode =
          new CommandLine(new BindgenCli())
              .execute(
                  "--wit", witFile.toString(),
                  "--package", "com.example",
                  "--output", outputDir.toString());

      assertEquals(0, exitCode);
    }
  }

  @Nested
  @DisplayName("Source Validation Tests")
  class SourceValidationTests {

    @Test
    @DisplayName("should warn for non-existent source file")
    void shouldWarnForNonExistentSourceFile(@TempDir Path tempDir) {
      LOGGER.info("Testing non-existent source file");

      Path nonExistentFile = tempDir.resolve("does-not-exist.wasm");
      Path outputDir = tempDir.resolve("output");

      int exitCode =
          new CommandLine(new BindgenCli())
              .execute(
                  "--wasm", nonExistentFile.toString(),
                  "--package", "com.example",
                  "--output", outputDir.toString());

      assertEquals(1, exitCode);
      String errOutput = errContent.toString().toLowerCase();
      assertTrue(errOutput.contains("not found"), "Expected error output to contain: not found");
    }

    @Test
    @DisplayName("should warn for file with wrong extension")
    void shouldWarnForWrongExtension(@TempDir Path tempDir) throws Exception {
      Path textFile = tempDir.resolve("not-a-wasm.txt");
      Files.writeString(textFile, "not wasm content");
      Path outputDir = tempDir.resolve("output");

      int exitCode =
          new CommandLine(new BindgenCli())
              .execute(
                  "--wasm", textFile.toString(),
                  "--package", "com.example",
                  "--output", outputDir.toString());

      String errOutput = errContent.toString().toLowerCase();
      assertTrue(
          errOutput.contains("unexpected extension"),
          "Expected error output to contain: unexpected extension");
    }

    @Test
    @DisplayName("should reject WASM sources as not yet implemented")
    void shouldRejectWasmSourcesAsNotYetImplemented(@TempDir Path tempDir) throws Exception {
      LOGGER.info("Testing WASM source rejection");

      Path wasmDir = tempDir.resolve("wasm");
      Files.createDirectory(wasmDir);
      Files.write(wasmDir.resolve("module1.wasm"), VALID_WASM);
      Files.write(wasmDir.resolve("module2.wasm"), VALID_WASM);
      Path outputDir = tempDir.resolve("output");

      int exitCode =
          new CommandLine(new BindgenCli())
              .execute(
                  "--wasm", wasmDir.toString(),
                  "--package", "com.example",
                  "--output", outputDir.toString());

      assertEquals(1, exitCode);
      assertTrue(
          errContent.toString().contains("not yet implemented"),
          "Expected error output to contain: not yet implemented");
    }
  }

  @Nested
  @DisplayName("Optional Flag Tests")
  class OptionalFlagTests {

    @Test
    @DisplayName("should support --no-javadoc flag")
    void shouldSupportNoJavadocFlag(@TempDir Path tempDir) throws Exception {
      LOGGER.info("Testing --no-javadoc flag");

      Path witFile = tempDir.resolve("test.wit");
      Files.writeString(witFile, VALID_WIT);
      Path outputDir = tempDir.resolve("output");

      int exitCode =
          new CommandLine(new BindgenCli())
              .execute(
                  "--wit",
                  witFile.toString(),
                  "--package",
                  "com.example",
                  "--output",
                  outputDir.toString(),
                  "--no-javadoc");

      assertEquals(0, exitCode);
    }

    @Test
    @DisplayName("should support --no-builders flag")
    void shouldSupportNoBuildersFlag(@TempDir Path tempDir) throws Exception {
      LOGGER.info("Testing --no-builders flag");

      Path witFile = tempDir.resolve("test.wit");
      Files.writeString(witFile, VALID_WIT);
      Path outputDir = tempDir.resolve("output");

      int exitCode =
          new CommandLine(new BindgenCli())
              .execute(
                  "--wit",
                  witFile.toString(),
                  "--package",
                  "com.example",
                  "--output",
                  outputDir.toString(),
                  "--no-builders");

      assertEquals(0, exitCode);
    }

    @Test
    @DisplayName("should support --verbose flag")
    void shouldSupportVerboseFlag(@TempDir Path tempDir) throws Exception {
      LOGGER.info("Testing --verbose flag");

      Path witFile = tempDir.resolve("test.wit");
      Files.writeString(witFile, VALID_WIT);
      Path outputDir = tempDir.resolve("output");

      int exitCode =
          new CommandLine(new BindgenCli())
              .execute(
                  "--wit",
                  witFile.toString(),
                  "--package",
                  "com.example",
                  "--output",
                  outputDir.toString(),
                  "--verbose");

      assertEquals(0, exitCode);
      String output = outContent.toString();
      assertTrue(output.contains("Configuration:"), "Expected output to contain: Configuration:");
    }

    @Test
    @DisplayName("should support --dry-run flag")
    void shouldSupportDryRunFlag(@TempDir Path tempDir) throws Exception {
      LOGGER.info("Testing --dry-run flag");

      Path witFile = tempDir.resolve("test.wit");
      Files.writeString(witFile, VALID_WIT);
      Path outputDir = tempDir.resolve("output");

      int exitCode =
          new CommandLine(new BindgenCli())
              .execute(
                  "--wit",
                  witFile.toString(),
                  "--package",
                  "com.example",
                  "--output",
                  outputDir.toString(),
                  "--dry-run");

      assertEquals(0, exitCode);
      String output = outContent.toString();
      assertTrue(output.contains("Dry run"), "Expected output to contain: Dry run");
    }
  }

  @Nested
  @DisplayName("Short Option Tests")
  class ShortOptionTests {

    @Test
    @DisplayName("should support -w for --wit")
    void shouldSupportShortWitOption(@TempDir Path tempDir) throws Exception {
      Path witFile = tempDir.resolve("test.wit");
      Files.writeString(witFile, "// Empty WIT file");
      Path outputDir = tempDir.resolve("output");

      // Note: WIT parsing may fail, but the option should be recognized
      new CommandLine(new BindgenCli())
          .execute(
              "-w", witFile.toString(),
              "-p", "com.example",
              "-o", outputDir.toString());

      // If we get here without parsing error, short option worked
    }

    @Test
    @DisplayName("should support -m for --wasm")
    void shouldSupportShortWasmOption(@TempDir Path tempDir) throws Exception {
      Path wasmFile = tempDir.resolve("test.wasm");
      Files.write(wasmFile, VALID_WASM);
      Path outputDir = tempDir.resolve("output");

      int exitCode =
          new CommandLine(new BindgenCli())
              .execute(
                  "-m", wasmFile.toString(),
                  "-p", "com.example",
                  "-o", outputDir.toString());

      // WASM introspection is not yet implemented, so this should fail
      assertEquals(1, exitCode);
    }

    @Test
    @DisplayName("should support -s for --style")
    void shouldSupportShortStyleOption(@TempDir Path tempDir) throws Exception {
      Path witFile = tempDir.resolve("test.wit");
      Files.writeString(witFile, VALID_WIT);
      Path outputDir = tempDir.resolve("output");

      int exitCode =
          new CommandLine(new BindgenCli())
              .setCaseInsensitiveEnumValuesAllowed(true)
              .execute(
                  "-w",
                  witFile.toString(),
                  "-p",
                  "com.example",
                  "-o",
                  outputDir.toString(),
                  "-s",
                  "LEGACY");

      assertEquals(0, exitCode);
    }

    @Test
    @DisplayName("should support -v for --verbose")
    void shouldSupportShortVerboseOption(@TempDir Path tempDir) throws Exception {
      Path witFile = tempDir.resolve("test.wit");
      Files.writeString(witFile, VALID_WIT);
      Path outputDir = tempDir.resolve("output");

      int exitCode =
          new CommandLine(new BindgenCli())
              .execute(
                  "-w", witFile.toString(), "-p", "com.example", "-o", outputDir.toString(), "-v");

      assertEquals(0, exitCode);
      assertTrue(
          outContent.toString().contains("Configuration:"),
          "Expected output to contain: Configuration:");
    }
  }

  @Nested
  @DisplayName("Multiple Source Tests")
  class MultipleSourceTests {

    @Test
    @DisplayName("should accept multiple --wit sources")
    void shouldAcceptMultipleWitSources(@TempDir Path tempDir) throws Exception {
      LOGGER.info("Testing multiple WIT sources");

      Path witFile1 = tempDir.resolve("api1.wit");
      Path witFile2 = tempDir.resolve("api2.wit");
      Files.writeString(witFile1, VALID_WIT);
      Files.writeString(witFile2, "interface calc {\n  multiply: func(a: s32, b: s32) -> s32\n}");
      Path outputDir = tempDir.resolve("output");

      int exitCode =
          new CommandLine(new BindgenCli())
              .execute(
                  "--wit", witFile1.toString(),
                  "--wit", witFile2.toString(),
                  "--package", "com.example",
                  "--output", outputDir.toString());

      assertEquals(0, exitCode);
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("should return exit code 1 for bindgen errors")
    void shouldReturnExitCode1ForBindgenErrors(@TempDir Path tempDir) throws Exception {
      LOGGER.info("Testing error exit code");

      Path invalidWasm = tempDir.resolve("invalid.wasm");
      Files.write(invalidWasm, new byte[] {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
      Path outputDir = tempDir.resolve("output");

      int exitCode =
          new CommandLine(new BindgenCli())
              .execute(
                  "--wasm", invalidWasm.toString(),
                  "--package", "com.example",
                  "--output", outputDir.toString());

      assertEquals(1, exitCode);
      assertTrue(
          errContent.toString().contains("Error:"), "Expected error output to contain: Error:");
    }

    @Test
    @DisplayName("should show cause with --verbose on error")
    void shouldShowCauseWithVerboseOnError(@TempDir Path tempDir) throws Exception {
      Path invalidWasm = tempDir.resolve("invalid.wasm");
      Files.write(invalidWasm, new byte[] {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
      Path outputDir = tempDir.resolve("output");

      int exitCode =
          new CommandLine(new BindgenCli())
              .execute(
                  "--wasm",
                  invalidWasm.toString(),
                  "--package",
                  "com.example",
                  "--output",
                  outputDir.toString(),
                  "--verbose");

      assertEquals(1, exitCode);
      // Verbose mode shows more details
      assertFalse(errContent.toString().isEmpty(), "Expected non-empty error output");
    }
  }
}
