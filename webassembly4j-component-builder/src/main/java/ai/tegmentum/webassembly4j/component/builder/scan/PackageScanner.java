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
import ai.tegmentum.webassembly4j.component.builder.annotation.WitEnum;
import ai.tegmentum.webassembly4j.component.builder.annotation.WitExport;
import ai.tegmentum.webassembly4j.component.builder.annotation.WitFlags;
import ai.tegmentum.webassembly4j.component.builder.annotation.WitImport;
import ai.tegmentum.webassembly4j.component.builder.annotation.WitRecord;
import ai.tegmentum.webassembly4j.component.builder.annotation.WitResource;
import ai.tegmentum.webassembly4j.component.builder.annotation.WitVariant;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Scans a Java package on the classpath for classes with component builder annotations.
 */
public final class PackageScanner {

    private PackageScanner() {}

    /**
     * Finds all class names in the given package that have any component builder annotation.
     *
     * @param packageName the Java package to scan (e.g., "com.example.mycomponent")
     * @param classLoader the classloader to search
     * @return list of fully qualified class names with component builder annotations
     */
    public static List<String> findAnnotatedClasses(String packageName, ClassLoader classLoader) {
        String packagePath = packageName.replace('.', '/');
        List<String> classNames = new ArrayList<>();

        try {
            Enumeration<URL> resources = classLoader.getResources(packagePath);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                String protocol = resource.getProtocol();

                if ("file".equals(protocol)) {
                    scanDirectory(Paths.get(resource.toURI()), packageName, classLoader, classNames);
                } else if ("jar".equals(protocol)) {
                    scanJar(resource, packagePath, packageName, classLoader, classNames);
                }
            }
        } catch (Exception e) {
            throw new ComponentBuilderException(
                    "Failed to scan package: " + packageName, e);
        }

        return classNames;
    }

    /**
     * Finds annotated classes using classpath entries directly (for URLClassLoader scenarios).
     *
     * @param packageName the Java package to scan
     * @param classpathEntries directories and JARs to search
     * @param classLoader the classloader to use for loading discovered classes
     * @return list of fully qualified class names with component builder annotations
     */
    public static List<String> findAnnotatedClasses(String packageName,
                                                     List<Path> classpathEntries,
                                                     ClassLoader classLoader) {
        String packagePath = packageName.replace('.', '/');
        List<String> classNames = new ArrayList<>();

        for (Path entry : classpathEntries) {
            if (Files.isDirectory(entry)) {
                Path packageDir = entry.resolve(packagePath);
                if (Files.isDirectory(packageDir)) {
                    scanDirectory(packageDir, packageName, classLoader, classNames);
                }
            } else if (entry.toString().endsWith(".jar") && Files.isRegularFile(entry)) {
                scanJarFile(entry, packagePath, packageName, classLoader, classNames);
            }
        }

        return classNames;
    }

    private static void scanDirectory(Path dir, String packageName,
                                       ClassLoader classLoader, List<String> result) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();

                if (Files.isDirectory(file)) {
                    // Do not recurse — scan only the specified package
                } else if (fileName.endsWith(".class") && !fileName.contains("$")) {
                    // Skip inner classes (they'll be discovered via their enclosing class)
                    String className = packageName + "." +
                            fileName.substring(0, fileName.length() - ".class".length());
                    checkAndAdd(className, classLoader, result);
                }
            }
        } catch (IOException e) {
            // Directory may not be readable, skip it
        }
    }

    private static void scanJar(URL jarUrl, String packagePath, String packageName,
                                 ClassLoader classLoader, List<String> result) {
        try {
            JarURLConnection connection = (JarURLConnection) jarUrl.openConnection();
            try (JarFile jarFile = connection.getJarFile()) {
                scanJarEntries(jarFile, packagePath, packageName, classLoader, result);
            }
        } catch (IOException e) {
            // JAR may not be readable, skip it
        }
    }

    private static void scanJarFile(Path jarPath, String packagePath, String packageName,
                                     ClassLoader classLoader, List<String> result) {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            scanJarEntries(jarFile, packagePath, packageName, classLoader, result);
        } catch (IOException e) {
            // JAR may not be readable, skip it
        }
    }

    private static void scanJarEntries(JarFile jarFile, String packagePath, String packageName,
                                        ClassLoader classLoader, List<String> result) {
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String entryName = entry.getName();

            if (entryName.startsWith(packagePath + "/")
                    && entryName.endsWith(".class")
                    && !entryName.contains("$")) {
                String className = entryName
                        .substring(0, entryName.length() - ".class".length())
                        .replace('/', '.');
                checkAndAdd(className, classLoader, result);
            }
        }
    }

    private static void checkAndAdd(String className, ClassLoader classLoader, List<String> result) {
        try {
            Class<?> clazz = classLoader.loadClass(className);
            if (hasComponentAnnotation(clazz)) {
                result.add(className);
            }
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            // Class can't be loaded, skip it
        }
    }

    private static boolean hasComponentAnnotation(Class<?> clazz) {
        return clazz.isAnnotationPresent(WitComponent.class)
                || clazz.isAnnotationPresent(WitExport.class)
                || clazz.isAnnotationPresent(WitImport.class)
                || clazz.isAnnotationPresent(WitRecord.class)
                || clazz.isAnnotationPresent(WitEnum.class)
                || clazz.isAnnotationPresent(WitVariant.class)
                || clazz.isAnnotationPresent(WitFlags.class)
                || clazz.isAnnotationPresent(WitResource.class);
    }
}
