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

import ai.tegmentum.webassembly4j.component.builder.annotation.WitComponent;
import ai.tegmentum.webassembly4j.component.builder.annotation.WitEnum;
import ai.tegmentum.webassembly4j.component.builder.annotation.WitExport;
import ai.tegmentum.webassembly4j.component.builder.annotation.WitRecord;
import ai.tegmentum.webassembly4j.component.builder.wit.WitEmitter;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InlineTypeCollectionTest {

    @Test
    void collectsRecordTypeFromParameter() {
        JavaInterfaceScanner scanner = new JavaInterfaceScanner(
                Thread.currentThread().getContextClassLoader());

        ScannedComponent component = scanner.scanClasses(Arrays.asList(
                TypeTestComponent.class,
                TypeTestExport.class));

        ScannedInterface export = component.getExports().get(0);
        assertFalse(export.getTypes().isEmpty(),
                "Should have collected referenced types");

        // Should find the Person record type
        assertTrue(export.getTypes().stream()
                        .anyMatch(t -> t.getKind() == ScannedType.Kind.RECORD
                                && t.getWitType().equals("person")),
                "Should have collected Person record type");
    }

    @Test
    void collectsEnumTypeFromParameter() {
        JavaInterfaceScanner scanner = new JavaInterfaceScanner(
                Thread.currentThread().getContextClassLoader());

        ScannedComponent component = scanner.scanClasses(Arrays.asList(
                TypeTestComponent.class,
                TypeTestExport.class));

        ScannedInterface export = component.getExports().get(0);

        assertTrue(export.getTypes().stream()
                        .anyMatch(t -> t.getKind() == ScannedType.Kind.ENUM
                                && t.getWitType().equals("status")),
                "Should have collected Status enum type");
    }

    @Test
    void collectsTypeFromReturnType() {
        JavaInterfaceScanner scanner = new JavaInterfaceScanner(
                Thread.currentThread().getContextClassLoader());

        ScannedComponent component = scanner.scanClasses(Arrays.asList(
                TypeTestComponent.class,
                TypeTestExport.class));

        ScannedInterface export = component.getExports().get(0);

        // Person appears in both parameter and return, should be deduplicated
        long personCount = export.getTypes().stream()
                .filter(t -> t.getWitType().equals("person"))
                .count();
        assertEquals(1, personCount, "Person type should appear exactly once (deduplicated)");
    }

    @Test
    void collectsTypeFromListParameter() {
        JavaInterfaceScanner scanner = new JavaInterfaceScanner(
                Thread.currentThread().getContextClassLoader());

        ScannedComponent component = scanner.scanClasses(Arrays.asList(
                TypeTestComponent.class,
                TypeTestExport.class));

        ScannedInterface export = component.getExports().get(0);

        // Person is also in List<Person> parameter, should still be collected
        assertTrue(export.getTypes().stream()
                        .anyMatch(t -> t.getWitType().equals("person")),
                "Should collect Person from List<Person> parameter");
    }

    @Test
    void collectsTypeFromOptionalReturn() {
        JavaInterfaceScanner scanner = new JavaInterfaceScanner(
                Thread.currentThread().getContextClassLoader());

        ScannedComponent component = scanner.scanClasses(Arrays.asList(
                TypeTestComponent.class,
                TypeTestExport.class));

        ScannedInterface export = component.getExports().get(0);

        // Status is also in Optional<Status> return, should still be collected
        assertTrue(export.getTypes().stream()
                        .anyMatch(t -> t.getWitType().equals("status")),
                "Should collect Status from Optional<Status> return");
    }

    @Test
    void emitsInlineTypeDefinitionsInInterface() {
        JavaInterfaceScanner scanner = new JavaInterfaceScanner(
                Thread.currentThread().getContextClassLoader());

        ScannedComponent component = scanner.scanClasses(Arrays.asList(
                TypeTestComponent.class,
                TypeTestExport.class));

        String wit = WitEmitter.emit(component);

        // Type definitions should appear inside the interface block
        assertTrue(wit.contains("interface type-test-export {"),
                "Should have the interface in:\n" + wit);
        assertTrue(wit.contains("    record person {"),
                "Person record should be inline in interface in:\n" + wit);
        assertTrue(wit.contains("        name: string,"),
                "Person fields should be in record in:\n" + wit);
        assertTrue(wit.contains("    enum status {"),
                "Status enum should be inline in interface in:\n" + wit);
    }

    @Test
    void doesNotCollectPrimitiveTypes() {
        JavaInterfaceScanner scanner = new JavaInterfaceScanner(
                Thread.currentThread().getContextClassLoader());

        ScannedComponent component = scanner.scanClasses(Arrays.asList(
                TypeTestComponent.class,
                TypeTestExport.class));

        ScannedInterface export = component.getExports().get(0);

        // No primitive types should appear in the types list
        assertTrue(export.getTypes().stream()
                        .noneMatch(t -> t.getKind() == ScannedType.Kind.PRIMITIVE),
                "Should not collect primitive types");
    }

    // Test fixtures

    @WitComponent(packageName = "test:types", version = "1.0.0")
    public static class TypeTestComponent {}

    @WitExport
    public interface TypeTestExport {
        Person createPerson(String name, int age);
        void updateStatus(Person person, Status status);
        List<Person> listPeople();
        Optional<Status> getStatus();
    }

    @WitRecord
    public static class Person {
        public String name;
        public int age;
    }

    @WitEnum
    public enum Status {
        ACTIVE, INACTIVE, PENDING
    }
}
