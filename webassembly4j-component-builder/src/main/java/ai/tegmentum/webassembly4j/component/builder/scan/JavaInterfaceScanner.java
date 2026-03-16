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
import ai.tegmentum.webassembly4j.component.builder.wit.WitNaming;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scans Java classes for component builder annotations and builds a component model.
 */
public final class JavaInterfaceScanner {

    private final ClassLoader classLoader;

    public JavaInterfaceScanner(List<Path> classpathEntries) {
        this.classLoader = createClassLoader(classpathEntries);
    }

    public JavaInterfaceScanner(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Scans the given class names for component annotations and builds a component model.
     *
     * @param classNames fully qualified class names to scan
     * @return the scanned component model
     */
    public ScannedComponent scan(List<String> classNames) {
        List<Class<?>> classes = loadClasses(classNames);
        return scanClasses(classes);
    }

    /**
     * Scans loaded classes directly.
     *
     * @param classes the classes to scan
     * @return the scanned component model
     */
    public ScannedComponent scanClasses(List<Class<?>> classes) {
        // Find the @WitComponent entry point
        Class<?> componentClass = null;
        for (Class<?> clazz : classes) {
            if (clazz.isAnnotationPresent(WitComponent.class)) {
                if (componentClass != null) {
                    throw new ComponentBuilderException(
                            "Multiple @WitComponent annotations found: " +
                                    componentClass.getName() + " and " + clazz.getName());
                }
                componentClass = clazz;
            }
        }

        if (componentClass == null) {
            throw new ComponentBuilderException(
                    "No @WitComponent annotation found in scanned classes");
        }

        WitComponent componentAnnotation = componentClass.getAnnotation(WitComponent.class);
        String packageName = componentAnnotation.packageName();
        if (packageName.isEmpty()) {
            packageName = derivePackageName(componentClass);
        }
        String version = componentAnnotation.version();

        // Find world name
        String worldName;
        WitWorld worldAnnotation = componentClass.getAnnotation(WitWorld.class);
        if (worldAnnotation != null && !worldAnnotation.name().isEmpty()) {
            worldName = worldAnnotation.name();
        } else {
            worldName = WitNaming.toKebabCase(componentClass.getSimpleName());
        }

        // Scan all interfaces
        List<ScannedInterface> interfaces = new ArrayList<>();
        for (Class<?> clazz : classes) {
            if (clazz.isAnnotationPresent(WitExport.class)) {
                interfaces.add(scanInterface(clazz, true));
            } else if (clazz.isAnnotationPresent(WitImport.class)) {
                interfaces.add(scanInterface(clazz, false));
            }
        }

        // Also scan inner classes/interfaces of the component class
        for (Class<?> inner : componentClass.getDeclaredClasses()) {
            if (inner.isAnnotationPresent(WitExport.class)) {
                interfaces.add(scanInterface(inner, true));
            } else if (inner.isAnnotationPresent(WitImport.class)) {
                interfaces.add(scanInterface(inner, false));
            }
        }

        return new ScannedComponent(packageName, version, worldName, interfaces);
    }

    private ScannedInterface scanInterface(Class<?> clazz, boolean exported) {
        String witName = WitNaming.toKebabCase(clazz.getSimpleName());
        List<ScannedFunction> functions = new ArrayList<>();
        Set<ScannedType> referencedTypes = new LinkedHashSet<>();

        for (Method method : clazz.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (!Modifier.isPublic(method.getModifiers()) && !clazz.isInterface()) {
                continue;
            }

            String methodWitName = WitNaming.toKebabCase(method.getName());

            // Map parameters
            Map<String, ScannedType> params = new LinkedHashMap<>();
            for (Parameter param : method.getParameters()) {
                String paramName = WitNaming.toKebabCase(param.getName());
                ScannedType paramType = TypeMapper.mapType(param.getType(), param.getParameterizedType());
                params.put(paramName, paramType);
                collectReferencedTypes(paramType, referencedTypes);
            }

            // Map return type
            ScannedType returnType = null;
            if (method.getReturnType() != void.class) {
                returnType = TypeMapper.mapType(method.getReturnType(), method.getGenericReturnType());
                collectReferencedTypes(returnType, referencedTypes);
            }

            functions.add(new ScannedFunction(method.getName(), methodWitName, params, returnType));
        }

        return new ScannedInterface(clazz.getSimpleName(), witName, exported,
                functions, new ArrayList<>(referencedTypes));
    }

    private void collectReferencedTypes(ScannedType type, Set<ScannedType> collected) {
        if (type == null) {
            return;
        }
        switch (type.getKind()) {
            case RECORD, ENUM, VARIANT, FLAGS, RESOURCE -> collected.add(type);
            case LIST, OPTION -> collectReferencedTypes(type.getElementType(), collected);
            default -> { /* primitives need no type definition */ }
        }
    }

    private List<Class<?>> loadClasses(List<String> classNames) {
        List<Class<?>> classes = new ArrayList<>();
        for (String className : classNames) {
            try {
                classes.add(classLoader.loadClass(className));
            } catch (ClassNotFoundException e) {
                throw new ComponentBuilderException("Class not found: " + className, e);
            }
        }
        return classes;
    }

    private String derivePackageName(Class<?> componentClass) {
        String javaPackage = componentClass.getPackageName();
        // Convert Java package to WIT package format: last two segments become namespace:name
        String[] parts = javaPackage.split("\\.");
        if (parts.length >= 2) {
            return parts[parts.length - 2] + ":" + parts[parts.length - 1];
        }
        return "local:" + javaPackage;
    }

    private static ClassLoader createClassLoader(List<Path> classpathEntries) {
        if (classpathEntries == null || classpathEntries.isEmpty()) {
            return Thread.currentThread().getContextClassLoader();
        }

        URL[] urls = classpathEntries.stream()
                .map(path -> {
                    try {
                        return path.toUri().toURL();
                    } catch (Exception e) {
                        throw new ComponentBuilderException(
                                "Invalid classpath entry: " + path, e);
                    }
                })
                .toArray(URL[]::new);

        return new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
    }
}
