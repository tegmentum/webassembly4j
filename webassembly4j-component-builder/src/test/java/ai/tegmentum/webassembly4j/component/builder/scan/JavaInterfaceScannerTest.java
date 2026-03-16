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
import ai.tegmentum.webassembly4j.component.builder.annotation.WitComponent;
import ai.tegmentum.webassembly4j.component.builder.annotation.WitExport;
import ai.tegmentum.webassembly4j.component.builder.annotation.WitImport;
import ai.tegmentum.webassembly4j.component.builder.annotation.WitWorld;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaInterfaceScannerTest {

    @Test
    void scanFindsComponentAndExports() {
        JavaInterfaceScanner scanner = new JavaInterfaceScanner(
                Thread.currentThread().getContextClassLoader());

        List<Class<?>> classes = Arrays.asList(
                SampleComponent.class,
                GreeterExport.class);

        ScannedComponent component = scanner.scanClasses(classes);

        assertEquals("myorg:mypackage", component.getPackageName());
        assertEquals("0.1.0", component.getVersion());
        assertEquals("my-component", component.getWorldName());
        assertEquals(1, component.getExports().size());
        assertEquals(0, component.getImports().size());
    }

    @Test
    void scanFindsExportedFunctions() {
        JavaInterfaceScanner scanner = new JavaInterfaceScanner(
                Thread.currentThread().getContextClassLoader());

        List<Class<?>> classes = Arrays.asList(
                SampleComponent.class,
                GreeterExport.class);

        ScannedComponent component = scanner.scanClasses(classes);

        ScannedInterface export = component.getExports().get(0);
        assertEquals("greeter-export", export.getWitName());
        assertTrue(export.isExported());
        assertFalse(export.getFunctions().isEmpty());

        ScannedFunction greetFunc = export.getFunctions().stream()
                .filter(f -> f.getWitName().equals("greet"))
                .findFirst()
                .orElseThrow();

        assertEquals("string", greetFunc.getReturnType().getWitType());
        assertFalse(greetFunc.getParameters().isEmpty());
    }

    @Test
    void scanFindsImports() {
        JavaInterfaceScanner scanner = new JavaInterfaceScanner(
                Thread.currentThread().getContextClassLoader());

        List<Class<?>> classes = Arrays.asList(
                SampleComponent.class,
                GreeterExport.class,
                LoggerImport.class);

        ScannedComponent component = scanner.scanClasses(classes);

        assertEquals(1, component.getImports().size());
        ScannedInterface imp = component.getImports().get(0);
        assertEquals("logger-import", imp.getWitName());
        assertTrue(imp.isImported());
    }

    @Test
    void scanUsesWorldAnnotationName() {
        JavaInterfaceScanner scanner = new JavaInterfaceScanner(
                Thread.currentThread().getContextClassLoader());

        List<Class<?>> classes = Arrays.asList(
                CustomWorldComponent.class);

        ScannedComponent component = scanner.scanClasses(classes);
        assertEquals("custom-world", component.getWorldName());
    }

    @Test
    void scanThrowsWhenNoComponentAnnotation() {
        JavaInterfaceScanner scanner = new JavaInterfaceScanner(
                Thread.currentThread().getContextClassLoader());

        List<Class<?>> classes = Arrays.asList(GreeterExport.class);

        assertThrows(ComponentBuilderException.class, () -> scanner.scanClasses(classes));
    }

    @Test
    void scanThrowsWhenMultipleComponents() {
        JavaInterfaceScanner scanner = new JavaInterfaceScanner(
                Thread.currentThread().getContextClassLoader());

        List<Class<?>> classes = Arrays.asList(
                SampleComponent.class,
                CustomWorldComponent.class);

        assertThrows(ComponentBuilderException.class, () -> scanner.scanClasses(classes));
    }

    @Test
    void scanDerivesPackageNameWhenNotSpecified() {
        JavaInterfaceScanner scanner = new JavaInterfaceScanner(
                Thread.currentThread().getContextClassLoader());

        List<Class<?>> classes = Arrays.asList(MinimalComponent.class);

        ScannedComponent component = scanner.scanClasses(classes);
        // Should derive from Java package
        assertFalse(component.getPackageName().isEmpty());
    }

    // Test fixtures

    @WitComponent(packageName = "myorg:mypackage", version = "0.1.0")
    @WitWorld(name = "my-component")
    public static class SampleComponent {}

    @WitExport
    public interface GreeterExport {
        String greet(String name);
    }

    @WitImport
    public interface LoggerImport {
        void log(String message);
    }

    @WitComponent(packageName = "other:pkg")
    @WitWorld(name = "custom-world")
    public static class CustomWorldComponent {}

    @WitComponent
    public static class MinimalComponent {}
}
