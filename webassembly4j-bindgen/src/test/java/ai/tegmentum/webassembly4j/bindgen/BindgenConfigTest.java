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
package ai.tegmentum.webassembly4j.bindgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for BindgenConfig. */
class BindgenConfigTest {

  @Test
  void shouldCreateConfigWithDefaultValues() {
    BindgenConfig config =
        BindgenConfig.builder()
            .packageName("com.example")
            .outputDirectory(Path.of("target/generated"))
            .addWitSource(Path.of("src/main/wit"))
            .build();

    assertEquals(CodeStyle.MODERN, config.getCodeStyle());
    assertEquals("com.example", config.getPackageName());
    assertEquals(Path.of("target/generated"), config.getOutputDirectory());
    assertTrue(config.isGenerateJavadoc());
    assertTrue(config.isGenerateBuilders());
    assertTrue(config.hasWitSources());
    assertFalse(config.hasWasmSources());
  }

  @Test
  void shouldCreateConfigWithLegacyStyle() {
    BindgenConfig config =
        BindgenConfig.builder()
            .packageName("com.example")
            .outputDirectory(Path.of("target/generated"))
            .codeStyle(CodeStyle.LEGACY)
            .addWasmSource(Path.of("module.wasm"))
            .build();

    assertEquals(CodeStyle.LEGACY, config.getCodeStyle());
    assertTrue(config.hasWasmSources());
  }

  @Test
  void shouldValidateRequiredPackageName() {
    BindgenConfig config =
        BindgenConfig.builder()
            .outputDirectory(Path.of("target/generated"))
            .addWitSource(Path.of("src/main/wit"))
            .build();

    BindgenException exception = assertThrows(BindgenException.class, config::validate);
    assertTrue(
        exception.getMessage().contains("packageName is required"),
        "Expected message to contain: packageName is required");
  }

  @Test
  void shouldValidateRequiredOutputDirectory() {
    BindgenConfig config =
        BindgenConfig.builder()
            .packageName("com.example")
            .addWitSource(Path.of("src/main/wit"))
            .build();

    BindgenException exception = assertThrows(BindgenException.class, config::validate);
    assertTrue(
        exception.getMessage().contains("outputDirectory is required"),
        "Expected message to contain: outputDirectory is required");
  }

  @Test
  void shouldValidateAtLeastOneSource() {
    BindgenConfig config =
        BindgenConfig.builder()
            .packageName("com.example")
            .outputDirectory(Path.of("target/generated"))
            .build();

    BindgenException exception = assertThrows(BindgenException.class, config::validate);
    assertTrue(
        exception.getMessage().contains("At least one WIT or WASM source must be specified"),
        "Expected message to contain: At least one WIT or WASM source must be specified");
  }

  @Test
  void shouldPassValidationWithValidConfig() throws BindgenException {
    BindgenConfig config =
        BindgenConfig.builder()
            .packageName("com.example")
            .outputDirectory(Path.of("target/generated"))
            .addWitSource(Path.of("api.wit"))
            .build();

    // Should not throw
    config.validate();
  }

  @Test
  void shouldSupportMultipleSources() {
    BindgenConfig config =
        BindgenConfig.builder()
            .packageName("com.example")
            .outputDirectory(Path.of("target/generated"))
            .witSources(List.of(Path.of("api.wit"), Path.of("types.wit")))
            .wasmSources(List.of(Path.of("module1.wasm"), Path.of("module2.wasm")))
            .build();

    assertEquals(2, config.getWitSources().size());
    assertEquals(2, config.getWasmSources().size());
  }

  @Test
  void shouldImplementEqualsAndHashCode() {
    BindgenConfig config1 =
        BindgenConfig.builder()
            .packageName("com.example")
            .outputDirectory(Path.of("target/generated"))
            .addWitSource(Path.of("api.wit"))
            .build();

    BindgenConfig config2 =
        BindgenConfig.builder()
            .packageName("com.example")
            .outputDirectory(Path.of("target/generated"))
            .addWitSource(Path.of("api.wit"))
            .build();

    assertEquals(config2, config1);
    assertEquals(config2.hashCode(), config1.hashCode());
  }

  @Test
  void shouldImplementToString() {
    BindgenConfig config =
        BindgenConfig.builder()
            .packageName("com.example")
            .outputDirectory(Path.of("target/generated"))
            .addWitSource(Path.of("api.wit"))
            .build();

    String toString = config.toString();
    assertTrue(toString.contains("com.example"), "Expected toString to contain: com.example");
    assertTrue(toString.contains("MODERN"), "Expected toString to contain: MODERN");
  }
}
