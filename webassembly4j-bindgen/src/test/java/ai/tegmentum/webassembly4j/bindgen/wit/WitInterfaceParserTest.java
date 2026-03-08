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
package ai.tegmentum.webassembly4j.bindgen.wit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.tegmentum.webassembly4j.bindgen.BindgenException;
import ai.tegmentum.webassembly4j.bindgen.wit.WitCompatibilityResult;
import ai.tegmentum.webassembly4j.bindgen.wit.WitInterfaceDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Comprehensive unit tests for WIT interface parser. */
class WitInterfaceParserTest {

  private WitInterfaceParser parser;

  @BeforeEach
  void setUp() {
    parser = new WitInterfaceParser();
  }

  @Test
  @DisplayName("Test simple interface parsing")
  void testSimpleInterfaceParsing() throws BindgenException {
    final String witText =
        "interface calculator {\n"
            + "    add: func(a: s32, b: s32) -> s32;\n"
            + "    subtract: func(a: s32, b: s32) -> s32;\n"
            + "}\n";

    final WitInterfaceDefinition definition = parser.parseInterface(witText, "math");

    assertEquals("calculator", definition.getName());
    assertEquals("math", definition.getPackageName());
    assertEquals("1.0", definition.getVersion());

    final var functionNames = definition.getFunctionNames();
    assertEquals(2, functionNames.size());
    assertTrue(functionNames.contains("add"));
    assertTrue(functionNames.contains("subtract"));

    final var exportNames = definition.getExportNames();
    assertEquals(2, exportNames.size());
    assertTrue(exportNames.contains("add"));
    assertTrue(exportNames.contains("subtract"));
  }

  @Test
  @DisplayName("Test interface with types parsing")
  void testInterfaceWithTypesParsing() throws BindgenException {
    final String witText =
        "interface shapes {\n"
            + "    type point = record {\n"
            + "        x: f64,\n"
            + "        y: f64\n"
            + "    };\n"
            + "\n"
            + "    type color = enum {\n"
            + "        red,\n"
            + "        green,\n"
            + "        blue\n"
            + "    };\n"
            + "\n"
            + "    create-point: func(x: f64, y: f64) -> point;\n"
            + "    get-distance: func(p1: point, p2: point) -> f64;\n"
            + "}\n";

    final WitInterfaceDefinition definition = parser.parseInterface(witText, "graphics");

    assertEquals("shapes", definition.getName());
    assertEquals("graphics", definition.getPackageName());

    final var typeNames = definition.getTypeNames();
    assertEquals(2, typeNames.size());
    assertTrue(typeNames.contains("point"));
    assertTrue(typeNames.contains("color"));

    final var functionNames = definition.getFunctionNames();
    assertEquals(2, functionNames.size());
    assertTrue(functionNames.contains("create-point"));
    assertTrue(functionNames.contains("get-distance"));
  }

  @Test
  @DisplayName("Test complex types parsing")
  void testComplexTypesParsing() throws BindgenException {
    final String witText =
        "interface advanced {\n"
            + "    type result-type = result<string, s32>;\n"
            + "    type optional-value = option<f64>;\n"
            + "    type string-list = list<string>;\n"
            + "    type permissions = flags {\n"
            + "        read,\n"
            + "        write,\n"
            + "        execute\n"
            + "    };\n"
            + "\n"
            + "    process-data: func(input: string-list, perms: permissions) -> result-type;\n"
            + "}\n";

    final WitInterfaceDefinition definition = parser.parseInterface(witText, "advanced");

    assertEquals("advanced", definition.getName());

    final var typeNames = definition.getTypeNames();
    assertEquals(4, typeNames.size());
    assertTrue(typeNames.contains("result-type"));
    assertTrue(typeNames.contains("optional-value"));
    assertTrue(typeNames.contains("string-list"));
    assertTrue(typeNames.contains("permissions"));

    final var functionNames = definition.getFunctionNames();
    assertEquals(1, functionNames.size());
    assertTrue(functionNames.contains("process-data"));
  }

  @Test
  @DisplayName("Test variant type parsing")
  void testVariantTypeParsing() throws BindgenException {
    final String witText =
        "interface messaging {\n"
            + "    type message = variant {\n"
            + "        text(string),\n"
            + "        binary(list<u8>),\n"
            + "        empty\n"
            + "    };\n"
            + "\n"
            + "    send-message: func(msg: message);\n"
            + "}\n";

    final WitInterfaceDefinition definition = parser.parseInterface(witText, "messaging");

    assertEquals("messaging", definition.getName());

    final var typeNames = definition.getTypeNames();
    assertTrue(typeNames.contains("message"));

    final var functionNames = definition.getFunctionNames();
    assertTrue(functionNames.contains("send-message"));
  }

  @Test
  @DisplayName("Test function with no parameters")
  void testFunctionWithNoParameters() throws BindgenException {
    final String witText =
        "interface service {\n"
            + "    get-status: func() -> string;\n"
            + "    reset: func();\n"
            + "}\n";

    final WitInterfaceDefinition definition = parser.parseInterface(witText, "service");

    assertEquals("service", definition.getName());

    final var functionNames = definition.getFunctionNames();
    assertEquals(2, functionNames.size());
    assertTrue(functionNames.contains("get-status"));
    assertTrue(functionNames.contains("reset"));
  }

  @Test
  @DisplayName("Test function with no return type")
  void testFunctionWithNoReturnType() throws BindgenException {
    final String witText =
        "interface logger {\n"
            + "    log-message: func(level: string, message: string);\n"
            + "    clear-log: func();\n"
            + "}\n";

    final WitInterfaceDefinition definition = parser.parseInterface(witText, "logging");

    assertEquals("logger", definition.getName());

    final var functionNames = definition.getFunctionNames();
    assertEquals(2, functionNames.size());
    assertTrue(functionNames.contains("log-message"));
    assertTrue(functionNames.contains("clear-log"));
  }

  @Test
  @DisplayName("Test nested record types")
  void testNestedRecordTypes() throws BindgenException {
    final String witText =
        "interface nested {\n"
            + "    type address = record {\n"
            + "        street: string,\n"
            + "        city: string,\n"
            + "        zip: string\n"
            + "    };\n"
            + "\n"
            + "    type person = record {\n"
            + "        name: string,\n"
            + "        age: u32,\n"
            + "        address: address\n"
            + "    };\n"
            + "\n"
            + "    create-person: func(name: string, age: u32, addr: address) -> person;\n"
            + "}\n";

    final WitInterfaceDefinition definition = parser.parseInterface(witText, "people");

    assertEquals("nested", definition.getName());

    final var typeNames = definition.getTypeNames();
    assertEquals(2, typeNames.size());
    assertTrue(typeNames.contains("address"));
    assertTrue(typeNames.contains("person"));
  }

  @Test
  @DisplayName("Test invalid interface format")
  void testInvalidInterfaceFormat() {
    final String invalidWitText = "this is not a valid WIT interface";

    assertThrows(BindgenException.class, () -> parser.parseInterface(invalidWitText, "invalid"));
  }

  @Test
  @DisplayName("Test empty interface")
  void testEmptyInterface() throws BindgenException {
    final String witText = "interface empty {\n" + "}\n";

    final WitInterfaceDefinition definition = parser.parseInterface(witText, "empty");

    assertEquals("empty", definition.getName());
    assertTrue(definition.getFunctionNames().isEmpty());
    assertTrue(definition.getTypeNames().isEmpty());
  }

  @Test
  @DisplayName("Test interface compatibility checking")
  void testInterfaceCompatibilityChecking() throws BindgenException {
    final String witText1 =
        "interface math {\n" + "    add: func(a: s32, b: s32) -> s32;\n" + "}\n";

    final String witText2 =
        "interface math {\n" + "    add: func(a: s32, b: s32) -> s32;\n" + "}\n";

    final WitInterfaceDefinition def1 = parser.parseInterface(witText1, "math");
    final WitInterfaceDefinition def2 = parser.parseInterface(witText2, "math");

    final WitCompatibilityResult result = def1.isCompatibleWith(def2);
    assertTrue(result.isCompatible());
    assertEquals("Interfaces are compatible", result.getDetails());
  }

  @Test
  @DisplayName("Test interface incompatibility")
  void testInterfaceIncompatibility() throws BindgenException {
    final String witText1 =
        "interface math {\n" + "    add: func(a: s32, b: s32) -> s32;\n" + "}\n";

    final String witText2 =
        "interface calculator {\n" + "    multiply: func(x: f64, y: f64) -> f64;\n" + "}\n";

    final WitInterfaceDefinition def1 = parser.parseInterface(witText1, "math");
    final WitInterfaceDefinition def2 = parser.parseInterface(witText2, "calc");

    final WitCompatibilityResult result = def1.isCompatibleWith(def2);
    assertFalse(result.isCompatible());
    assertTrue(result.getDetails().contains("Interface names do not match"));
  }

  @Test
  @DisplayName("Test WIT text preservation")
  void testWitTextPreservation() throws BindgenException {
    final String originalWitText = "interface test {\n" + "    hello: func() -> string;\n" + "}\n";

    final WitInterfaceDefinition definition = parser.parseInterface(originalWitText, "test");
    assertEquals(originalWitText, definition.getWitText());
  }

  @Test
  @DisplayName("Test null parameter handling")
  void testNullParameterHandling() {
    assertThrows(NullPointerException.class, () -> parser.parseInterface(null, "test"));

    assertThrows(
        NullPointerException.class, () -> parser.parseInterface("interface test {}", null));
  }

  @Test
  @DisplayName("Test multiple primitive types")
  void testMultiplePrimitiveTypes() throws BindgenException {
    final String witText =
        "interface primitives {\n"
            + "    test-bool: func(value: bool) -> bool;\n"
            + "    test-u8: func(value: u8) -> u8;\n"
            + "    test-s8: func(value: s8) -> s8;\n"
            + "    test-u16: func(value: u16) -> u16;\n"
            + "    test-s16: func(value: s16) -> s16;\n"
            + "    test-u32: func(value: u32) -> u32;\n"
            + "    test-s32: func(value: s32) -> s32;\n"
            + "    test-u64: func(value: u64) -> u64;\n"
            + "    test-s64: func(value: s64) -> s64;\n"
            + "    test-float32: func(value: float32) -> float32;\n"
            + "    test-float64: func(value: float64) -> float64;\n"
            + "    test-char: func(value: char) -> char;\n"
            + "    test-string: func(value: string) -> string;\n"
            + "}\n";

    final WitInterfaceDefinition definition = parser.parseInterface(witText, "primitives");

    assertEquals("primitives", definition.getName());

    final var functionNames = definition.getFunctionNames();
    assertEquals(13, functionNames.size());

    // Verify all primitive type functions are present
    assertTrue(functionNames.contains("test-bool"));
    assertTrue(functionNames.contains("test-u8"));
    assertTrue(functionNames.contains("test-s8"));
    assertTrue(functionNames.contains("test-u16"));
    assertTrue(functionNames.contains("test-s16"));
    assertTrue(functionNames.contains("test-u32"));
    assertTrue(functionNames.contains("test-s32"));
    assertTrue(functionNames.contains("test-u64"));
    assertTrue(functionNames.contains("test-s64"));
    assertTrue(functionNames.contains("test-float32"));
    assertTrue(functionNames.contains("test-float64"));
    assertTrue(functionNames.contains("test-char"));
    assertTrue(functionNames.contains("test-string"));
  }

  @Test
  @DisplayName("Test complex function signatures")
  void testComplexFunctionSignatures() throws BindgenException {
    final String witText =
        "interface complex {\n"
            + "    type data = record {\n"
            + "        values: list<s32>,\n"
            + "        metadata: option<string>\n"
            + "    };\n"
            + "\n"
            + "    process: func(\n"
            + "        input: data,\n"
            + "        options: flags { verbose, debug, trace },\n"
            + "        callback: option<string>\n"
            + "    ) -> result<data, string>;\n"
            + "}\n";

    final WitInterfaceDefinition definition = parser.parseInterface(witText, "complex");

    assertEquals("complex", definition.getName());

    final var typeNames = definition.getTypeNames();
    assertTrue(typeNames.contains("data"));

    final var functionNames = definition.getFunctionNames();
    assertTrue(functionNames.contains("process"));
  }
}
