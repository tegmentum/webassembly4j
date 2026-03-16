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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WitNamingTest {

    @Test
    void toKebabCaseCamelCase() {
        assertEquals("get-value", WitNaming.toKebabCase("getValue"));
        assertEquals("my-field", WitNaming.toKebabCase("myField"));
        assertEquals("simple", WitNaming.toKebabCase("simple"));
    }

    @Test
    void toKebabCasePascalCase() {
        assertEquals("http-request", WitNaming.toKebabCase("HttpRequest"));
        assertEquals("my-component", WitNaming.toKebabCase("MyComponent"));
        assertEquals("greeter", WitNaming.toKebabCase("Greeter"));
    }

    @Test
    void toKebabCaseAcronyms() {
        assertEquals("xml-parser", WitNaming.toKebabCase("XMLParser"));
        assertEquals("simple-url", WitNaming.toKebabCase("simpleURL"));
    }

    @Test
    void toKebabCaseSnakeCase() {
        assertEquals("my-field", WitNaming.toKebabCase("my_field"));
        assertEquals("upper-snake", WitNaming.toKebabCase("UPPER_SNAKE"));
    }

    @Test
    void toKebabCaseNullAndEmpty() {
        assertNull(WitNaming.toKebabCase(null));
        assertEquals("", WitNaming.toKebabCase(""));
    }

    @Test
    void toCamelCase() {
        assertEquals("getValue", WitNaming.toCamelCase("get-value"));
        assertEquals("httpRequest", WitNaming.toCamelCase("http-request"));
        assertEquals("simple", WitNaming.toCamelCase("simple"));
    }

    @Test
    void toPascalCase() {
        assertEquals("HttpRequest", WitNaming.toPascalCase("http-request"));
        assertEquals("MyComponent", WitNaming.toPascalCase("my-component"));
        assertEquals("Simple", WitNaming.toPascalCase("simple"));
    }

    @Test
    void toCamelCaseNullAndEmpty() {
        assertNull(WitNaming.toCamelCase(null));
        assertEquals("", WitNaming.toCamelCase(""));
    }

    @Test
    void roundTrip() {
        // camelCase -> kebab-case -> camelCase
        assertEquals("getValue", WitNaming.toCamelCase(WitNaming.toKebabCase("getValue")));
        assertEquals("myField", WitNaming.toCamelCase(WitNaming.toKebabCase("myField")));
    }
}
