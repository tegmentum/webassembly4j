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
import ai.tegmentum.webassembly4j.component.builder.annotation.WitResource;
import ai.tegmentum.webassembly4j.component.builder.annotation.WitVariant;
import ai.tegmentum.webassembly4j.component.builder.wit.WitNaming;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Maps Java types to WIT types.
 */
public final class TypeMapper {

    private TypeMapper() {}

    /**
     * Maps a Java class to a {@link ScannedType}.
     *
     * @param type the Java type
     * @return the corresponding scanned type
     */
    public static ScannedType mapType(Class<?> type) {
        return mapType(type, null);
    }

    /**
     * Maps a Java type (possibly generic) to a {@link ScannedType}.
     *
     * @param type the raw Java class
     * @param genericType the generic type info, or null
     * @return the corresponding scanned type
     */
    public static ScannedType mapType(Class<?> type, Type genericType) {
        // Primitives and their boxed equivalents
        if (type == boolean.class || type == Boolean.class) {
            return ScannedType.primitive("bool", "boolean");
        }
        if (type == byte.class || type == Byte.class) {
            return ScannedType.primitive("u8", "byte");
        }
        if (type == short.class || type == Short.class) {
            return ScannedType.primitive("s16", "short");
        }
        if (type == int.class || type == Integer.class) {
            return ScannedType.primitive("s32", "int");
        }
        if (type == long.class || type == Long.class) {
            return ScannedType.primitive("s64", "long");
        }
        if (type == float.class || type == Float.class) {
            return ScannedType.primitive("float32", "float");
        }
        if (type == double.class || type == Double.class) {
            return ScannedType.primitive("float64", "double");
        }
        if (type == char.class || type == Character.class) {
            return ScannedType.primitive("char", "char");
        }
        if (type == String.class) {
            return ScannedType.primitive("string", "String");
        }
        if (type == void.class || type == Void.class) {
            return ScannedType.primitive("void", "void");
        }

        // byte[] -> list<u8>
        if (type == byte[].class) {
            return ScannedType.list(ScannedType.primitive("u8", "byte"));
        }

        // List<T> -> list<T>
        if (List.class.isAssignableFrom(type)) {
            ScannedType elementType = resolveGenericArgument(genericType, 0);
            return ScannedType.list(elementType);
        }

        // Optional<T> -> option<T>
        if (Optional.class.isAssignableFrom(type)) {
            ScannedType elementType = resolveGenericArgument(genericType, 0);
            return ScannedType.option(elementType);
        }

        // @WitRecord
        if (type.isAnnotationPresent(WitRecord.class)) {
            return mapRecord(type);
        }

        // @WitEnum
        if (type.isAnnotationPresent(WitEnum.class) && type.isEnum()) {
            return mapEnum(type);
        }

        // @WitFlags
        if (type.isAnnotationPresent(WitFlags.class) && type.isEnum()) {
            return mapFlags(type);
        }

        // @WitVariant
        if (type.isAnnotationPresent(WitVariant.class)) {
            return mapVariant(type);
        }

        // @WitResource
        if (type.isAnnotationPresent(WitResource.class)) {
            return mapResource(type);
        }

        throw new ComponentBuilderException("Cannot map Java type to WIT: " + type.getName());
    }

    private static ScannedType resolveGenericArgument(Type genericType, int index) {
        if (genericType instanceof ParameterizedType) {
            Type[] args = ((ParameterizedType) genericType).getActualTypeArguments();
            if (index < args.length) {
                Type arg = args[index];
                if (arg instanceof Class<?>) {
                    return mapType((Class<?>) arg);
                }
                if (arg instanceof ParameterizedType) {
                    return mapType((Class<?>) ((ParameterizedType) arg).getRawType(), arg);
                }
            }
        }
        return ScannedType.primitive("string", "String");
    }

    private static ScannedType mapRecord(Class<?> type) {
        String witName = WitNaming.toKebabCase(type.getSimpleName());
        Map<String, ScannedType> fields = new LinkedHashMap<>();

        // Try record components first (Java 16+)
        try {
            Method getComponents = type.getMethod("getRecordComponents");
            // This is a record - but we can't call getRecordComponents without Java 16+ APIs.
            // Fall through to field-based approach for broader compatibility.
        } catch (NoSuchMethodException ignored) {
            // Not a record or not available
        }

        // Use declared fields
        for (Field field : type.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            String fieldWitName = WitNaming.toKebabCase(field.getName());
            ScannedType fieldType = mapType(field.getType(), field.getGenericType());
            fields.put(fieldWitName, fieldType);
        }

        return ScannedType.record(witName, fields);
    }

    private static ScannedType mapEnum(Class<?> type) {
        String witName = WitNaming.toKebabCase(type.getSimpleName());
        List<String> cases = Arrays.stream(type.getEnumConstants())
                .map(c -> WitNaming.toKebabCase(((Enum<?>) c).name().toLowerCase()))
                .collect(Collectors.toList());
        return ScannedType.enumType(witName, cases);
    }

    private static ScannedType mapFlags(Class<?> type) {
        String witName = WitNaming.toKebabCase(type.getSimpleName());
        List<String> cases = Arrays.stream(type.getEnumConstants())
                .map(c -> WitNaming.toKebabCase(((Enum<?>) c).name().toLowerCase()))
                .collect(Collectors.toList());
        return ScannedType.flags(witName, cases);
    }

    private static ScannedType mapResource(Class<?> type) {
        String witName = WitNaming.toKebabCase(type.getSimpleName());
        List<ScannedFunction> methods = new ArrayList<>();

        // Map public constructors as WIT constructors
        for (Constructor<?> ctor : type.getConstructors()) {
            Map<String, ScannedType> params = new LinkedHashMap<>();
            for (Parameter param : ctor.getParameters()) {
                String paramName = WitNaming.toKebabCase(param.getName());
                ScannedType paramType = mapType(param.getType(), param.getParameterizedType());
                params.put(paramName, paramType);
            }
            // WIT constructors are named "constructor"
            methods.add(new ScannedFunction("[constructor]", "[constructor]" + witName, params, null));
        }

        // Map public instance methods
        for (Method method : type.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            // Skip Object methods
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }

            String methodWitName = WitNaming.toKebabCase(method.getName());
            Map<String, ScannedType> params = new LinkedHashMap<>();
            for (Parameter param : method.getParameters()) {
                String paramName = WitNaming.toKebabCase(param.getName());
                ScannedType paramType = mapType(param.getType(), param.getParameterizedType());
                params.put(paramName, paramType);
            }

            ScannedType returnType = null;
            if (method.getReturnType() != void.class) {
                returnType = mapType(method.getReturnType(), method.getGenericReturnType());
            }

            methods.add(new ScannedFunction(method.getName(), methodWitName, params, returnType));
        }

        return ScannedType.resource(witName, methods);
    }

    private static ScannedType mapVariant(Class<?> type) {
        String witName = WitNaming.toKebabCase(type.getSimpleName());
        List<String> cases = new ArrayList<>();

        // For sealed interfaces, get permitted subclasses
        Class<?>[] permitted = type.getPermittedSubclasses();
        if (permitted != null) {
            for (Class<?> subclass : permitted) {
                cases.add(WitNaming.toKebabCase(subclass.getSimpleName()));
            }
        }

        return ScannedType.variant(witName, cases);
    }
}
