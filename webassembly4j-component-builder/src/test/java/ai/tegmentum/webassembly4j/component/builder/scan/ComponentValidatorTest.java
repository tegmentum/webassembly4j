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
package ai.tegmentum.webassembly4j.component.builder.scan;

import ai.tegmentum.webassembly4j.component.builder.ComponentBuilderException;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentValidatorTest {

    @Test
    void validComponentPasses() {
        ScannedInterface export = new ScannedInterface(
                "Greeter", "greeter", true,
                Collections.emptyList(), Collections.emptyList());

        ScannedComponent component = new ScannedComponent(
                "myorg:mypackage", "1.0.0", "my-world",
                Collections.singletonList(export));

        List<String> errors = ComponentValidator.validate(component);
        assertTrue(errors.isEmpty(), "Expected no errors, got: " + errors);
    }

    @Test
    void validateOrThrowDoesNotThrowForValid() {
        ScannedInterface export = new ScannedInterface(
                "Greeter", "greeter", true,
                Collections.emptyList(), Collections.emptyList());

        ScannedComponent component = new ScannedComponent(
                "myorg:mypackage", "1.0.0", "my-world",
                Collections.singletonList(export));

        assertDoesNotThrow(() -> ComponentValidator.validateOrThrow(component));
    }

    @Test
    void rejectsEmptyPackageName() {
        ScannedComponent component = new ScannedComponent(
                "", null, "my-world",
                Collections.singletonList(createExport("greeter")));

        List<String> errors = ComponentValidator.validate(component);
        assertTrue(errors.stream().anyMatch(e -> e.contains("package name is required")));
    }

    @Test
    void rejectsPackageWithoutColon() {
        ScannedComponent component = new ScannedComponent(
                "mypackage", null, "my-world",
                Collections.singletonList(createExport("greeter")));

        List<String> errors = ComponentValidator.validate(component);
        assertTrue(errors.stream().anyMatch(e -> e.contains("namespace:name")));
    }

    @Test
    void rejectsMissingExports() {
        ScannedComponent component = new ScannedComponent(
                "myorg:mypackage", null, "my-world",
                Collections.emptyList());

        List<String> errors = ComponentValidator.validate(component);
        assertTrue(errors.stream().anyMatch(e -> e.contains("export at least one")));
    }

    @Test
    void rejectsImportsOnlyWithNoExports() {
        ScannedInterface imp = new ScannedInterface(
                "Logger", "logger", false,
                Collections.emptyList(), Collections.emptyList());

        ScannedComponent component = new ScannedComponent(
                "myorg:mypackage", null, "my-world",
                Collections.singletonList(imp));

        List<String> errors = ComponentValidator.validate(component);
        assertTrue(errors.stream().anyMatch(e -> e.contains("export at least one")));
    }

    @Test
    void rejectsDuplicateInterfaceNames() {
        ScannedInterface export1 = createExport("greeter");
        ScannedInterface export2 = createExport("greeter");

        ScannedComponent component = new ScannedComponent(
                "myorg:mypackage", null, "my-world",
                Arrays.asList(export1, export2));

        List<String> errors = ComponentValidator.validate(component);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Duplicate interface")));
    }

    @Test
    void rejectsDuplicateFunctionNames() {
        Map<String, ScannedType> params = new LinkedHashMap<>();
        ScannedFunction func1 = new ScannedFunction("greet", "greet", params, null);
        ScannedFunction func2 = new ScannedFunction("greet", "greet", params, null);

        ScannedInterface export = new ScannedInterface(
                "Greeter", "greeter", true,
                Arrays.asList(func1, func2), Collections.emptyList());

        ScannedComponent component = new ScannedComponent(
                "myorg:mypackage", null, "my-world",
                Collections.singletonList(export));

        List<String> errors = ComponentValidator.validate(component);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Duplicate function")));
    }

    @Test
    void rejectsInvalidVersionFormat() {
        ScannedComponent component = new ScannedComponent(
                "myorg:mypackage", "bad-version", "my-world",
                Collections.singletonList(createExport("greeter")));

        List<String> errors = ComponentValidator.validate(component);
        assertTrue(errors.stream().anyMatch(e -> e.contains("semver")));
    }

    @Test
    void acceptsValidSemver() {
        ScannedComponent component = new ScannedComponent(
                "myorg:mypackage", "1.2.3", "my-world",
                Collections.singletonList(createExport("greeter")));

        List<String> errors = ComponentValidator.validate(component);
        assertTrue(errors.isEmpty());
    }

    @Test
    void acceptsSemverWithPrerelease() {
        ScannedComponent component = new ScannedComponent(
                "myorg:mypackage", "1.0.0-beta.1", "my-world",
                Collections.singletonList(createExport("greeter")));

        List<String> errors = ComponentValidator.validate(component);
        assertTrue(errors.isEmpty());
    }

    @Test
    void acceptsNullVersion() {
        ScannedComponent component = new ScannedComponent(
                "myorg:mypackage", null, "my-world",
                Collections.singletonList(createExport("greeter")));

        List<String> errors = ComponentValidator.validate(component);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validateOrThrowThrowsForInvalid() {
        ScannedComponent component = new ScannedComponent(
                "", null, "my-world",
                Collections.emptyList());

        ComponentBuilderException ex = assertThrows(
                ComponentBuilderException.class,
                () -> ComponentValidator.validateOrThrow(component));

        assertTrue(ex.getMessage().contains("validation failed"));
    }

    @Test
    void multipleErrors() {
        ScannedComponent component = new ScannedComponent(
                "badpackage", "not-a-version", "",
                Collections.emptyList());

        List<String> errors = ComponentValidator.validate(component);
        assertTrue(errors.size() >= 3,
                "Expected multiple errors, got: " + errors);
    }

    private ScannedInterface createExport(String witName) {
        return new ScannedInterface(
                witName, witName, true,
                Collections.emptyList(), Collections.emptyList());
    }
}
