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
import ai.tegmentum.webassembly4j.component.builder.annotation.WitEnum;
import ai.tegmentum.webassembly4j.component.builder.annotation.WitFlags;
import ai.tegmentum.webassembly4j.component.builder.annotation.WitRecord;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TypeMapperTest {

    @Test
    void mapsPrimitiveBoolean() {
        ScannedType type = TypeMapper.mapType(boolean.class);
        assertEquals("bool", type.getWitType());
        assertEquals(ScannedType.Kind.PRIMITIVE, type.getKind());
    }

    @Test
    void mapsBoxedBoolean() {
        ScannedType type = TypeMapper.mapType(Boolean.class);
        assertEquals("bool", type.getWitType());
    }

    @Test
    void mapsByte() {
        ScannedType type = TypeMapper.mapType(byte.class);
        assertEquals("u8", type.getWitType());
    }

    @Test
    void mapsShort() {
        ScannedType type = TypeMapper.mapType(short.class);
        assertEquals("s16", type.getWitType());
    }

    @Test
    void mapsInt() {
        ScannedType type = TypeMapper.mapType(int.class);
        assertEquals("s32", type.getWitType());
    }

    @Test
    void mapsBoxedInt() {
        ScannedType type = TypeMapper.mapType(Integer.class);
        assertEquals("s32", type.getWitType());
    }

    @Test
    void mapsLong() {
        ScannedType type = TypeMapper.mapType(long.class);
        assertEquals("s64", type.getWitType());
    }

    @Test
    void mapsFloat() {
        ScannedType type = TypeMapper.mapType(float.class);
        assertEquals("float32", type.getWitType());
    }

    @Test
    void mapsDouble() {
        ScannedType type = TypeMapper.mapType(double.class);
        assertEquals("float64", type.getWitType());
    }

    @Test
    void mapsChar() {
        ScannedType type = TypeMapper.mapType(char.class);
        assertEquals("char", type.getWitType());
    }

    @Test
    void mapsString() {
        ScannedType type = TypeMapper.mapType(String.class);
        assertEquals("string", type.getWitType());
    }

    @Test
    void mapsVoid() {
        ScannedType type = TypeMapper.mapType(void.class);
        assertEquals("void", type.getWitType());
    }

    @Test
    void mapsByteArray() {
        ScannedType type = TypeMapper.mapType(byte[].class);
        assertEquals("list<u8>", type.getWitType());
        assertEquals(ScannedType.Kind.LIST, type.getKind());
    }

    @Test
    void mapsListWithGenericType() throws Exception {
        // Use a helper method to capture the generic type
        Method method = TypeMapperTestHelper.class.getMethod("listOfStrings");
        Type genericReturn = method.getGenericReturnType();

        ScannedType type = TypeMapper.mapType(List.class, genericReturn);
        assertEquals("list<string>", type.getWitType());
        assertEquals(ScannedType.Kind.LIST, type.getKind());
    }

    @Test
    void mapsOptionalWithGenericType() throws Exception {
        Method method = TypeMapperTestHelper.class.getMethod("optionalInt");
        Type genericReturn = method.getGenericReturnType();

        ScannedType type = TypeMapper.mapType(Optional.class, genericReturn);
        assertEquals("option<s32>", type.getWitType());
        assertEquals(ScannedType.Kind.OPTION, type.getKind());
    }

    @Test
    void mapsAnnotatedRecord() {
        ScannedType type = TypeMapper.mapType(SampleRecord.class);
        assertEquals(ScannedType.Kind.RECORD, type.getKind());
        assertEquals("sample-record", type.getWitType());
        assertEquals(2, type.getFields().size());
        assertEquals("string", type.getFields().get("name").getWitType());
        assertEquals("s32", type.getFields().get("age").getWitType());
    }

    @Test
    void mapsAnnotatedEnum() {
        ScannedType type = TypeMapper.mapType(SampleEnum.class);
        assertEquals(ScannedType.Kind.ENUM, type.getKind());
        assertEquals("sample-enum", type.getWitType());
        assertEquals(3, type.getCases().size());
        assertEquals("red", type.getCases().get(0));
        assertEquals("green", type.getCases().get(1));
        assertEquals("blue", type.getCases().get(2));
    }

    @Test
    void mapsAnnotatedFlags() {
        ScannedType type = TypeMapper.mapType(SampleFlags.class);
        assertEquals(ScannedType.Kind.FLAGS, type.getKind());
        assertEquals("sample-flags", type.getWitType());
        assertEquals(3, type.getCases().size());
    }

    @Test
    void throwsForUnmappableType() {
        assertThrows(ComponentBuilderException.class, () -> TypeMapper.mapType(Object.class));
    }

    // Test helpers

    public static class TypeMapperTestHelper {
        public List<String> listOfStrings() { return null; }
        public Optional<Integer> optionalInt() { return null; }
    }

    @WitRecord
    public static class SampleRecord {
        public String name;
        public int age;
    }

    @WitEnum
    public enum SampleEnum {
        RED, GREEN, BLUE
    }

    @WitFlags
    public enum SampleFlags {
        READ, WRITE, EXECUTE
    }
}
