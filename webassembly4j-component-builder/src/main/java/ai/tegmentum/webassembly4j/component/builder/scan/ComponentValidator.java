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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates a scanned component model for correctness before WIT emission.
 */
public final class ComponentValidator {

    private ComponentValidator() {}

    /**
     * Validates the scanned component and returns a list of validation errors.
     *
     * @param component the component to validate
     * @return list of error messages, empty if valid
     */
    public static List<String> validate(ScannedComponent component) {
        List<String> errors = new ArrayList<>();

        // Package name must contain a colon (namespace:name format)
        String packageName = component.getPackageName();
        if (packageName == null || packageName.isEmpty()) {
            errors.add("Component package name is required");
        } else if (!packageName.contains(":")) {
            errors.add("Component package name must be in 'namespace:name' format, got: " + packageName);
        }

        // World name is required
        if (component.getWorldName() == null || component.getWorldName().isEmpty()) {
            errors.add("World name is required");
        }

        // Must have at least one export
        if (component.getExports().isEmpty()) {
            errors.add("Component must export at least one interface");
        }

        // Interface names must be unique
        Set<String> interfaceNames = new HashSet<>();
        for (ScannedInterface iface : component.getInterfaces()) {
            if (!interfaceNames.add(iface.getWitName())) {
                errors.add("Duplicate interface name: " + iface.getWitName());
            }
        }

        // Function names must be unique within each interface
        for (ScannedInterface iface : component.getInterfaces()) {
            Set<String> funcNames = new HashSet<>();
            for (ScannedFunction func : iface.getFunctions()) {
                if (!funcNames.add(func.getWitName())) {
                    errors.add("Duplicate function name '" + func.getWitName()
                            + "' in interface '" + iface.getWitName() + "'");
                }
            }
        }

        // Version format check (semver-like if present)
        String version = component.getVersion();
        if (version != null && !version.isEmpty()) {
            if (!version.matches("\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9.]+)?")) {
                errors.add("Version should follow semver format (e.g., '1.0.0'), got: " + version);
            }
        }

        return errors;
    }

    /**
     * Validates the component and throws if invalid.
     *
     * @param component the component to validate
     * @throws ComponentBuilderException if validation fails
     */
    public static void validateOrThrow(ScannedComponent component) {
        List<String> errors = validate(component);
        if (!errors.isEmpty()) {
            throw new ComponentBuilderException(
                    "Component validation failed:\n  - " + String.join("\n  - ", errors));
        }
    }
}
