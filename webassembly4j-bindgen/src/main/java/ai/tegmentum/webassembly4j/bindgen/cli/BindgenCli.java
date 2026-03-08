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

package ai.tegmentum.webassembly4j.bindgen.cli;

import ai.tegmentum.webassembly4j.bindgen.BindgenConfig;
import ai.tegmentum.webassembly4j.bindgen.BindgenException;
import ai.tegmentum.webassembly4j.bindgen.CodeGenerator;
import ai.tegmentum.webassembly4j.bindgen.CodeStyle;
import ai.tegmentum.webassembly4j.bindgen.GeneratedSource;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * CLI entry point for webassembly4j-bindgen.
 *
 * <p>Usage examples:
 *
 * <pre>
 * # Generate from WIT files
 * webassembly4j-bindgen --wit src/main/wit --package com.example --output target/generated
 *
 * # Generate from WASM module
 * webassembly4j-bindgen --wasm module.wasm --package com.example --output target/generated
 *
 * # Generate legacy Java 8 compatible code
 * webassembly4j-bindgen --wit src/main/wit --package com.example --style LEGACY
 *
 * # Generate with multiple sources
 * webassembly4j-bindgen --wit api.wit --wit types.wit --package com.example
 * </pre>
 */
@Command(
    name = "webassembly4j-bindgen",
    mixinStandardHelpOptions = true,
    version = "webassembly4j-bindgen 1.0.0",
    description = "Generates Java bindings from WIT files and WebAssembly modules",
    sortOptions = false)
public final class BindgenCli implements Callable<Integer> {

  @Option(
      names = {"--wit", "-w"},
      description = "WIT source file or directory (can be specified multiple times)",
      paramLabel = "FILE")
  private List<File> witSources;

  @Option(
      names = {"--wasm", "-m"},
      description = "WASM module file for introspection (can be specified multiple times)",
      paramLabel = "FILE")
  private List<File> wasmSources;

  @Option(
      names = {"--package", "-p"},
      required = true,
      description = "Target Java package name",
      paramLabel = "PACKAGE")
  private String packageName;

  @Option(
      names = {"--output", "-o"},
      defaultValue = "generated-sources",
      description = "Output directory for generated sources (default: ${DEFAULT-VALUE})",
      paramLabel = "DIR")
  private File outputDirectory;

  @Option(
      names = {"--style", "-s"},
      defaultValue = "MODERN",
      description =
          "Code generation style: MODERN (Java 17+) or LEGACY (Java 8+) "
              + "(default: ${DEFAULT-VALUE})")
  private CodeStyle codeStyle;

  @Option(
      names = {"--no-javadoc"},
      description = "Disable Javadoc generation")
  private boolean noJavadoc;

  @Option(
      names = {"--no-builders"},
      description = "Disable builder generation (for LEGACY style)")
  private boolean noBuilders;

  @Option(
      names = {"--verbose", "-v"},
      description = "Enable verbose output")
  private boolean verbose;

  @Option(
      names = {"--dry-run"},
      description = "Show what would be generated without writing files")
  private boolean dryRun;

  @Override
  public Integer call() {
    try {
      // Validate inputs
      if ((witSources == null || witSources.isEmpty())
          && (wasmSources == null || wasmSources.isEmpty())) {
        System.err.println("Error: At least one --wit or --wasm source must be specified");
        System.err.println("Use --help for usage information");
        return 1;
      }

      // Validate source files exist
      List<Path> validWitSources = validateSources(witSources, ".wit");
      List<Path> validWasmSources = validateSources(wasmSources, ".wasm");

      if (validWitSources.isEmpty() && validWasmSources.isEmpty()) {
        System.err.println("Error: No valid source files found");
        return 1;
      }

      // Build configuration
      BindgenConfig config =
          BindgenConfig.builder()
              .codeStyle(codeStyle)
              .packageName(packageName)
              .outputDirectory(outputDirectory.toPath())
              .witSources(validWitSources)
              .wasmSources(validWasmSources)
              .generateJavadoc(!noJavadoc)
              .generateBuilders(!noBuilders)
              .build();

      if (verbose) {
        printConfiguration(config);
      }

      // Generate code
      CodeGenerator generator = new CodeGenerator(config);
      List<GeneratedSource> sources = generator.generate();

      if (dryRun) {
        System.out.println("Dry run - would generate the following files:");
        for (GeneratedSource source : sources) {
          Path targetPath = outputDirectory.toPath().resolve(source.getRelativePath());
          System.out.println("  " + targetPath);
        }
      } else {
        // Write sources
        for (GeneratedSource source : sources) {
          source.writeTo(outputDirectory.toPath());
          if (verbose) {
            System.out.println("  Generated: " + source.getQualifiedName());
          }
        }
      }

      System.out.println("Generated " + sources.size() + " Java source files");
      return 0;

    } catch (BindgenException e) {
      System.err.println("Error: " + e.getMessage());
      if (verbose && e.getCause() != null) {
        e.getCause().printStackTrace(System.err);
      }
      return 1;
    } catch (Exception e) {
      System.err.println("Unexpected error: " + e.getMessage());
      if (verbose) {
        e.printStackTrace(System.err);
      }
      return 2;
    }
  }

  private List<Path> validateSources(final List<File> sources, final String extension) {
    List<Path> validPaths = new ArrayList<>();

    if (sources == null) {
      return validPaths;
    }

    for (File source : sources) {
      if (!source.exists()) {
        System.err.println("Warning: Source not found: " + source);
        continue;
      }

      if (source.isDirectory()) {
        // Collect all matching files from directory
        File[] files = source.listFiles((dir, name) -> name.endsWith(extension));
        if (files != null) {
          for (File file : files) {
            validPaths.add(file.toPath());
          }
        }
      } else if (source.getName().endsWith(extension)) {
        validPaths.add(source.toPath());
      } else {
        System.err.println("Warning: Skipping file with unexpected extension: " + source);
      }
    }

    return validPaths;
  }

  private void printConfiguration(final BindgenConfig config) {
    System.out.println("Configuration:");
    System.out.println("  Package: " + config.getPackageName());
    System.out.println("  Style: " + config.getCodeStyle());
    System.out.println("  Output: " + config.getOutputDirectory());
    System.out.println("  WIT sources: " + config.getWitSources().size());
    System.out.println("  WASM sources: " + config.getWasmSources().size());
    System.out.println("  Generate Javadoc: " + config.isGenerateJavadoc());
    System.out.println("  Generate Builders: " + config.isGenerateBuilders());
    System.out.println();
  }

  /**
   * Main entry point.
   *
   * @param args command-line arguments
   */
  public static void main(final String[] args) {
    int exitCode =
        new CommandLine(new BindgenCli()).setCaseInsensitiveEnumValuesAllowed(true).execute(args);
    System.exit(exitCode);
  }
}
