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
import ai.tegmentum.webassembly4j.component.builder.annotation.WitExport;
import ai.tegmentum.webassembly4j.component.builder.annotation.WitResource;
import ai.tegmentum.webassembly4j.component.builder.wit.WitEmitter;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceTypeTest {

    @Test
    void mapsResourceType() {
        ScannedType type = TypeMapper.mapType(FileHandle.class);
        assertEquals(ScannedType.Kind.RESOURCE, type.getKind());
        assertEquals("file-handle", type.getWitType());
    }

    @Test
    void resourceHasConstructor() {
        ScannedType type = TypeMapper.mapType(FileHandle.class);
        assertTrue(type.getResourceMethods().stream()
                        .anyMatch(m -> m.getName().equals("[constructor]")),
                "Should have a constructor method");
    }

    @Test
    void resourceConstructorHasParameters() {
        ScannedType type = TypeMapper.mapType(FileHandle.class);
        ScannedFunction ctor = type.getResourceMethods().stream()
                .filter(m -> m.getName().equals("[constructor]"))
                .findFirst()
                .orElseThrow();

        assertFalse(ctor.getParameters().isEmpty(),
                "Constructor should have parameters");
        assertTrue(ctor.getParameters().values().stream()
                        .anyMatch(t -> t.getWitType().equals("string")),
                "Constructor should have a string parameter");
    }

    @Test
    void resourceHasInstanceMethods() {
        ScannedType type = TypeMapper.mapType(FileHandle.class);
        List<ScannedFunction> methods = type.getResourceMethods();

        assertTrue(methods.stream().anyMatch(m -> m.getWitName().equals("read")),
                "Should have read method");
        assertTrue(methods.stream().anyMatch(m -> m.getWitName().equals("write")),
                "Should have write method");
        assertTrue(methods.stream().anyMatch(m -> m.getWitName().equals("close")),
                "Should have close method");
    }

    @Test
    void resourceMethodsHaveCorrectSignatures() {
        ScannedType type = TypeMapper.mapType(FileHandle.class);

        // read(len: s32) -> list<u8>
        ScannedFunction read = type.getResourceMethods().stream()
                .filter(m -> m.getWitName().equals("read"))
                .findFirst()
                .orElseThrow();
        assertEquals("s32", read.getParameters().values().iterator().next().getWitType());
        assertEquals("list<u8>", read.getReturnType().getWitType());

        // close() -> void
        ScannedFunction close = type.getResourceMethods().stream()
                .filter(m -> m.getWitName().equals("close"))
                .findFirst()
                .orElseThrow();
        assertTrue(close.getParameters().isEmpty());
        assertEquals(null, close.getReturnType());
    }

    @Test
    void resourceCollectedAsReferencedType() {
        JavaInterfaceScanner scanner = new JavaInterfaceScanner(
                Thread.currentThread().getContextClassLoader());

        ScannedComponent component = scanner.scanClasses(Arrays.asList(
                ResourceTestComponent.class,
                FileApi.class));

        ScannedInterface export = component.getExports().get(0);
        assertTrue(export.getTypes().stream()
                        .anyMatch(t -> t.getKind() == ScannedType.Kind.RESOURCE
                                && t.getWitType().equals("file-handle")),
                "Should collect FileHandle resource as inline type");
    }

    @Test
    void resourceEmittedAsWit() {
        JavaInterfaceScanner scanner = new JavaInterfaceScanner(
                Thread.currentThread().getContextClassLoader());

        ScannedComponent component = scanner.scanClasses(Arrays.asList(
                ResourceTestComponent.class,
                FileApi.class));

        String wit = WitEmitter.emit(component);

        assertTrue(wit.contains("resource file-handle {"),
                "Should emit resource block in:\n" + wit);
        assertTrue(wit.contains("        constructor("),
                "Should emit constructor in:\n" + wit);
        assertTrue(wit.contains("        read: func("),
                "Should emit read method in:\n" + wit);
        assertTrue(wit.contains("        write: func("),
                "Should emit write method in:\n" + wit);
        assertTrue(wit.contains("        close: func()"),
                "Should emit close method in:\n" + wit);
        assertTrue(wit.contains(") -> list<u8>;"),
                "Should emit return type for read in:\n" + wit);
    }

    @Test
    void resourceWithNoConstructor() {
        ScannedType type = TypeMapper.mapType(OpaqueHandle.class);
        assertEquals(ScannedType.Kind.RESOURCE, type.getKind());
        // Default constructor maps to parameterless constructor
        assertTrue(type.getResourceMethods().stream()
                        .anyMatch(m -> m.getName().equals("[constructor]")),
                "Should still have default constructor");
    }

    // Test fixtures

    @WitComponent(packageName = "test:resource")
    public static class ResourceTestComponent {}

    @WitExport
    public interface FileApi {
        FileHandle open(String path);
    }

    @WitResource
    public static class FileHandle {
        public FileHandle(String path) {}
        public byte[] read(int len) { return null; }
        public void write(byte[] data) {}
        public void close() {}
    }

    @WitResource
    public static class OpaqueHandle {
        public void doWork() {}
    }
}
