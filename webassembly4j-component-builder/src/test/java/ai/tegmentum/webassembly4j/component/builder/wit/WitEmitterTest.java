/*
 * Copyright 2025 Tegmentum AI. All rights reserved.
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
package ai.tegmentum.webassembly4j.component.builder.wit;

import ai.tegmentum.webassembly4j.component.builder.scan.ScannedComponent;
import ai.tegmentum.webassembly4j.component.builder.scan.ScannedFunction;
import ai.tegmentum.webassembly4j.component.builder.scan.ScannedInterface;
import ai.tegmentum.webassembly4j.component.builder.scan.ScannedType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WitEmitterTest {

    @Test
    void emitsPackageDeclaration() {
        ScannedComponent component = new ScannedComponent(
                "myorg:mypackage", "0.1.0", "my-component", Collections.emptyList());

        String wit = WitEmitter.emit(component);

        assertTrue(wit.startsWith("package myorg:mypackage@0.1.0;\n"));
    }

    @Test
    void emitsPackageWithoutVersion() {
        ScannedComponent component = new ScannedComponent(
                "myorg:mypackage", null, "my-component", Collections.emptyList());

        String wit = WitEmitter.emit(component);

        assertTrue(wit.startsWith("package myorg:mypackage;\n"));
    }

    @Test
    void emitsSimpleInterface() {
        Map<String, ScannedType> params = new LinkedHashMap<>();
        params.put("name", ScannedType.primitive("string", "String"));

        ScannedFunction greet = new ScannedFunction(
                "greet", "greet", params,
                ScannedType.primitive("string", "String"));

        ScannedInterface greeter = new ScannedInterface(
                "Greeter", "greeter", true,
                Collections.singletonList(greet), Collections.emptyList());

        ScannedComponent component = new ScannedComponent(
                "myorg:mypackage", "0.1.0", "my-component",
                Collections.singletonList(greeter));

        String wit = WitEmitter.emit(component);

        assertTrue(wit.contains("interface greeter {"));
        assertTrue(wit.contains("    greet: func(name: string) -> string;"));
        assertTrue(wit.contains("}"));
    }

    @Test
    void emitsWorld() {
        ScannedInterface export = new ScannedInterface(
                "Greeter", "greeter", true,
                Collections.emptyList(), Collections.emptyList());

        ScannedInterface imp = new ScannedInterface(
                "Logger", "logger", false,
                Collections.emptyList(), Collections.emptyList());

        ScannedComponent component = new ScannedComponent(
                "myorg:mypackage", "0.1.0", "my-component",
                Arrays.asList(export, imp));

        String wit = WitEmitter.emit(component);

        assertTrue(wit.contains("world my-component {"));
        assertTrue(wit.contains("    import logger;"));
        assertTrue(wit.contains("    export greeter;"));
    }

    @Test
    void emitsVoidFunction() {
        ScannedFunction voidFunc = new ScannedFunction(
                "doWork", "do-work",
                Collections.emptyMap(), null);

        ScannedInterface iface = new ScannedInterface(
                "Worker", "worker", true,
                Collections.singletonList(voidFunc), Collections.emptyList());

        ScannedComponent component = new ScannedComponent(
                "test:pkg", null, "test-world",
                Collections.singletonList(iface));

        String wit = WitEmitter.emit(component);

        assertTrue(wit.contains("    do-work: func();"));
    }

    @Test
    void emitsMultipleParameters() {
        Map<String, ScannedType> params = new LinkedHashMap<>();
        params.put("x", ScannedType.primitive("s32", "int"));
        params.put("y", ScannedType.primitive("s32", "int"));

        ScannedFunction addFunc = new ScannedFunction(
                "add", "add", params,
                ScannedType.primitive("s32", "int"));

        ScannedInterface iface = new ScannedInterface(
                "Math", "math", true,
                Collections.singletonList(addFunc), Collections.emptyList());

        ScannedComponent component = new ScannedComponent(
                "test:pkg", null, "test-world",
                Collections.singletonList(iface));

        String wit = WitEmitter.emit(component);

        assertTrue(wit.contains("    add: func(x: s32, y: s32) -> s32;"));
    }

    @Test
    void emitsRecordTypeDefinition() {
        Map<String, ScannedType> fields = new LinkedHashMap<>();
        fields.put("name", ScannedType.primitive("string", "String"));
        fields.put("age", ScannedType.primitive("s32", "int"));
        ScannedType recordType = ScannedType.record("person", fields);

        ScannedInterface iface = new ScannedInterface(
                "Api", "api", true,
                Collections.emptyList(), Collections.singletonList(recordType));

        ScannedComponent component = new ScannedComponent(
                "test:pkg", null, "test-world",
                Collections.singletonList(iface));

        String wit = WitEmitter.emit(component);

        assertTrue(wit.contains("    record person {"));
        assertTrue(wit.contains("        name: string,"));
        assertTrue(wit.contains("        age: s32,"));
    }

    @Test
    void emitsEnumTypeDefinition() {
        ScannedType enumType = ScannedType.enumType("color",
                Arrays.asList("red", "green", "blue"));

        ScannedInterface iface = new ScannedInterface(
                "Api", "api", true,
                Collections.emptyList(), Collections.singletonList(enumType));

        ScannedComponent component = new ScannedComponent(
                "test:pkg", null, "test-world",
                Collections.singletonList(iface));

        String wit = WitEmitter.emit(component);

        assertTrue(wit.contains("    enum color {"));
        assertTrue(wit.contains("        red,"));
        assertTrue(wit.contains("        green,"));
        assertTrue(wit.contains("        blue,"));
    }

    @Test
    void emitsToFile(@TempDir Path tempDir) throws IOException {
        ScannedComponent component = new ScannedComponent(
                "myorg:mypackage", "0.1.0", "my-component",
                Collections.emptyList());

        Path witFile = WitEmitter.emitToFile(component, tempDir);

        assertTrue(Files.exists(witFile));
        assertEquals("my-component.wit", witFile.getFileName().toString());
        String content = Files.readString(witFile);
        assertTrue(content.contains("package myorg:mypackage@0.1.0;"));
    }

    @Test
    void emitsCompleteExample() {
        Map<String, ScannedType> greetParams = new LinkedHashMap<>();
        greetParams.put("name", ScannedType.primitive("string", "String"));

        ScannedFunction greet = new ScannedFunction(
                "greet", "greet", greetParams,
                ScannedType.primitive("string", "String"));

        ScannedInterface greeter = new ScannedInterface(
                "Greeter", "greeter", true,
                Collections.singletonList(greet), Collections.emptyList());

        ScannedComponent component = new ScannedComponent(
                "myorg:mypackage", "0.1.0", "my-component",
                Collections.singletonList(greeter));

        String wit = WitEmitter.emit(component);

        String expected =
                "package myorg:mypackage@0.1.0;\n" +
                "\n" +
                "interface greeter {\n" +
                "    greet: func(name: string) -> string;\n" +
                "}\n" +
                "\n" +
                "world my-component {\n" +
                "    export greeter;\n" +
                "}\n";

        assertEquals(expected, wit);
    }
}
