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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackageScannerTest {

    @Test
    void findsAnnotatedClassesInFixturesPackage() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        List<String> classes = PackageScanner.findAnnotatedClasses(
                "ai.tegmentum.webassembly4j.component.builder.it.fixtures", cl);

        // Should find GreeterComponent, Greeter, Logger, Point, Color, MathComponent
        assertTrue(classes.size() >= 5,
                "Expected at least 5 annotated classes, found: " + classes);

        assertTrue(classes.stream().anyMatch(c -> c.endsWith("GreeterComponent")));
        assertTrue(classes.stream().anyMatch(c -> c.endsWith("Greeter")));
        assertTrue(classes.stream().anyMatch(c -> c.endsWith("Logger")));
        assertTrue(classes.stream().anyMatch(c -> c.endsWith("Point")));
        assertTrue(classes.stream().anyMatch(c -> c.endsWith("Color")));
    }

    @Test
    void returnsEmptyForUnannotatedPackage() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        List<String> classes = PackageScanner.findAnnotatedClasses(
                "java.lang", cl);

        assertTrue(classes.isEmpty());
    }

    @Test
    void returnsEmptyForNonexistentPackage() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        List<String> classes = PackageScanner.findAnnotatedClasses(
                "com.nonexistent.package.xyz123", cl);

        assertTrue(classes.isEmpty());
    }

    @Test
    void doesNotIncludeInnerClasses() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        List<String> classes = PackageScanner.findAnnotatedClasses(
                "ai.tegmentum.webassembly4j.component.builder.it.fixtures", cl);

        // MathComponent has an inner @WitExport interface Calculator
        // The scanner should find MathComponent but NOT MathComponent$Calculator
        assertFalse(classes.stream().anyMatch(c -> c.contains("$")),
                "Should not include inner classes: " + classes);
    }
}
