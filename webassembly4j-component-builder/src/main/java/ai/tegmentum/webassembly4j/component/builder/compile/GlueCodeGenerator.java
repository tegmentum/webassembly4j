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
package ai.tegmentum.webassembly4j.component.builder.compile;

import ai.tegmentum.webassembly4j.component.builder.ComponentBuilderException;
import ai.tegmentum.webassembly4j.component.builder.scan.ScannedComponent;
import ai.tegmentum.webassembly4j.component.builder.scan.ScannedFunction;
import ai.tegmentum.webassembly4j.component.builder.scan.ScannedInterface;
import ai.tegmentum.webassembly4j.component.builder.scan.ScannedType;
import ai.tegmentum.webassembly4j.component.builder.wit.WitNaming;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates bridge classes that adapt WIT-style interfaces for GraalVM Web Image.
 *
 * <p>GraalVM Web Image uses {@code @JS.Export} annotations to expose Java methods
 * as WASM exports via the {@code globalThis} pattern. This generator creates bridge
 * classes with static methods annotated for export, which delegate to the user's
 * implementation classes.
 *
 * <p>For each exported interface, generates:
 * <ul>
 *   <li>A bridge class with static {@code @JS.Export} methods</li>
 *   <li>A static field holding the implementation instance</li>
 *   <li>An {@code init} method to wire the implementation</li>
 * </ul>
 */
public final class GlueCodeGenerator {

    private static final ClassName JS_EXPORT = ClassName.get(
            "org.graalvm.polyglot.js", "JS", "Export");

    private final String packageName;

    public GlueCodeGenerator(String packageName) {
        this.packageName = packageName;
    }

    /**
     * Generates glue code for the given component.
     *
     * @param component the scanned component model
     * @param outputDir the output directory for generated Java sources
     * @return the list of generated Java files
     */
    public List<GeneratedGlueFile> generate(ScannedComponent component, Path outputDir) {
        List<GeneratedGlueFile> files = new ArrayList<>();

        for (ScannedInterface iface : component.getExports()) {
            GeneratedGlueFile file = generateBridgeClass(iface, outputDir);
            files.add(file);
        }

        // Generate the main entry point that wires all bridges
        if (!component.getExports().isEmpty()) {
            GeneratedGlueFile mainFile = generateMainBridge(component, outputDir);
            files.add(mainFile);
        }

        return files;
    }

    private GeneratedGlueFile generateBridgeClass(ScannedInterface iface, Path outputDir) {
        String className = WitNaming.toPascalCase(iface.getWitName()) + "Bridge";
        String ifaceClassName = iface.getName();

        TypeSpec.Builder bridgeBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        // Static field for implementation instance
        bridgeBuilder.addField(
                ClassName.get(packageName, ifaceClassName),
                "impl",
                Modifier.PRIVATE, Modifier.STATIC);

        // Init method to set the implementation
        bridgeBuilder.addMethod(MethodSpec.methodBuilder("setImplementation")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(ClassName.get(packageName, ifaceClassName), "implementation")
                .addStatement("impl = implementation")
                .build());

        // Generate @JS.Export bridge methods
        for (ScannedFunction function : iface.getFunctions()) {
            bridgeBuilder.addMethod(generateBridgeMethod(function));
        }

        TypeSpec bridgeType = bridgeBuilder.build();
        JavaFile javaFile = JavaFile.builder(packageName + ".bridge", bridgeType)
                .indent("    ")
                .build();

        try {
            javaFile.writeTo(outputDir);
        } catch (IOException e) {
            throw new ComponentBuilderException(
                    "Failed to write bridge class: " + className, e);
        }

        return new GeneratedGlueFile(
                packageName + ".bridge." + className,
                outputDir.resolve(packageName.replace('.', '/')
                        + "/bridge/" + className + ".java"));
    }

    private MethodSpec generateBridgeMethod(ScannedFunction function) {
        String methodName = WitNaming.toCamelCase(function.getWitName());
        TypeName returnType = resolveTypeName(function.getReturnType());

        MethodSpec.Builder method = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addAnnotation(AnnotationSpec.builder(JS_EXPORT).build());

        if (function.getReturnType() != null
                && !"void".equals(function.getReturnType().getWitType())) {
            method.returns(returnType);
        }

        // Add parameters
        StringBuilder delegateArgs = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, ScannedType> param : function.getParameters().entrySet()) {
            String paramName = WitNaming.toCamelCase(param.getKey());
            TypeName paramType = resolveTypeName(param.getValue());
            method.addParameter(paramType, paramName);

            if (!first) {
                delegateArgs.append(", ");
            }
            delegateArgs.append(paramName);
            first = false;
        }

        // Delegate to impl
        if (function.getReturnType() != null
                && !"void".equals(function.getReturnType().getWitType())) {
            method.addStatement("return impl.$L($L)", methodName, delegateArgs);
        } else {
            method.addStatement("impl.$L($L)", methodName, delegateArgs);
        }

        return method.build();
    }

    private GeneratedGlueFile generateMainBridge(ScannedComponent component, Path outputDir) {
        String className = WitNaming.toPascalCase(component.getWorldName()) + "Main";

        TypeSpec.Builder mainBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        // Main method that initializes all bridges
        MethodSpec.Builder mainMethod = MethodSpec.methodBuilder("main")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(String[].class, "args")
                .addComment("Wire implementation instances to bridges here");

        for (ScannedInterface iface : component.getExports()) {
            String bridgeName = WitNaming.toPascalCase(iface.getWitName()) + "Bridge";
            mainMethod.addComment("$L.setImplementation(new $LImpl())",
                    bridgeName, iface.getName());
        }

        mainBuilder.addMethod(mainMethod.build());

        TypeSpec mainType = mainBuilder.build();
        JavaFile javaFile = JavaFile.builder(packageName + ".bridge", mainType)
                .indent("    ")
                .build();

        try {
            javaFile.writeTo(outputDir);
        } catch (IOException e) {
            throw new ComponentBuilderException(
                    "Failed to write main bridge class: " + className, e);
        }

        return new GeneratedGlueFile(
                packageName + ".bridge." + className,
                outputDir.resolve(packageName.replace('.', '/')
                        + "/bridge/" + className + ".java"));
    }

    private TypeName resolveTypeName(ScannedType type) {
        if (type == null) {
            return TypeName.VOID;
        }

        return switch (type.getWitType()) {
            case "bool" -> TypeName.BOOLEAN;
            case "u8" -> TypeName.BYTE;
            case "s16" -> TypeName.SHORT;
            case "s32" -> TypeName.INT;
            case "s64" -> TypeName.LONG;
            case "float32" -> TypeName.FLOAT;
            case "float64" -> TypeName.DOUBLE;
            case "char" -> TypeName.CHAR;
            case "string" -> ClassName.get(String.class);
            case "void" -> TypeName.VOID;
            default -> ClassName.get(String.class); // fallback for complex types
        };
    }

    /**
     * Represents a generated glue code file.
     */
    public static final class GeneratedGlueFile {
        private final String qualifiedName;
        private final Path filePath;

        public GeneratedGlueFile(String qualifiedName, Path filePath) {
            this.qualifiedName = qualifiedName;
            this.filePath = filePath;
        }

        public String getQualifiedName() {
            return qualifiedName;
        }

        public Path getFilePath() {
            return filePath;
        }
    }
}
