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
package ai.tegmentum.webassembly4j.component.builder.wit;

import ai.tegmentum.webassembly4j.component.builder.scan.ScannedComponent;
import ai.tegmentum.webassembly4j.component.builder.scan.ScannedFunction;
import ai.tegmentum.webassembly4j.component.builder.scan.ScannedInterface;
import ai.tegmentum.webassembly4j.component.builder.scan.ScannedType;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Emits WIT text from a scanned component model.
 */
public final class WitEmitter {

    private WitEmitter() {}

    /**
     * Emits WIT text for the given component.
     *
     * @param component the scanned component
     * @return the WIT text
     */
    public static String emit(ScannedComponent component) {
        StringBuilder sb = new StringBuilder();

        // Package declaration
        sb.append("package ").append(component.getPackageName());
        if (component.getVersion() != null && !component.getVersion().isEmpty()) {
            sb.append("@").append(component.getVersion());
        }
        sb.append(";\n");

        // Interface definitions
        for (ScannedInterface iface : component.getInterfaces()) {
            sb.append("\n");
            emitInterface(sb, iface);
        }

        // World definition
        sb.append("\n");
        sb.append("world ").append(component.getWorldName()).append(" {\n");

        for (ScannedInterface iface : component.getImports()) {
            sb.append("    import ").append(iface.getWitName()).append(";\n");
        }
        for (ScannedInterface iface : component.getExports()) {
            sb.append("    export ").append(iface.getWitName()).append(";\n");
        }

        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Emits WIT text to a file.
     *
     * @param component the scanned component
     * @param outputDir the output directory
     * @return the path to the written file
     * @throws IOException if writing fails
     */
    public static Path emitToFile(ScannedComponent component, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        String fileName = component.getWorldName() + ".wit";
        Path outputFile = outputDir.resolve(fileName);

        try (Writer writer = Files.newBufferedWriter(outputFile)) {
            writer.write(emit(component));
        }

        return outputFile;
    }

    private static void emitInterface(StringBuilder sb, ScannedInterface iface) {
        sb.append("interface ").append(iface.getWitName()).append(" {\n");

        // Emit record/enum/variant/flags type definitions used by this interface
        for (ScannedType type : iface.getTypes()) {
            emitTypeDefinition(sb, type);
        }

        // Emit functions
        for (ScannedFunction function : iface.getFunctions()) {
            emitFunction(sb, function);
        }

        sb.append("}\n");
    }

    private static void emitFunction(StringBuilder sb, ScannedFunction function) {
        sb.append("    ").append(function.getWitName()).append(": func(");

        // Parameters
        boolean first = true;
        for (Map.Entry<String, ScannedType> param : function.getParameters().entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(param.getKey()).append(": ").append(param.getValue().getWitType());
            first = false;
        }

        sb.append(")");

        // Return type
        if (function.getReturnType() != null) {
            sb.append(" -> ").append(function.getReturnType().getWitType());
        }

        sb.append(";\n");
    }

    private static void emitResourceMethod(StringBuilder sb, ScannedFunction method) {
        if (method.getName().equals("[constructor]")) {
            // WIT constructor syntax
            sb.append("        constructor(");
            boolean first = true;
            for (Map.Entry<String, ScannedType> param : method.getParameters().entrySet()) {
                if (!first) sb.append(", ");
                sb.append(param.getKey()).append(": ").append(param.getValue().getWitType());
                first = false;
            }
            sb.append(");\n");
        } else {
            // Regular resource method
            sb.append("        ").append(method.getWitName()).append(": func(");
            boolean first = true;
            for (Map.Entry<String, ScannedType> param : method.getParameters().entrySet()) {
                if (!first) sb.append(", ");
                sb.append(param.getKey()).append(": ").append(param.getValue().getWitType());
                first = false;
            }
            sb.append(")");
            if (method.getReturnType() != null) {
                sb.append(" -> ").append(method.getReturnType().getWitType());
            }
            sb.append(";\n");
        }
    }

    private static void emitTypeDefinition(StringBuilder sb, ScannedType type) {
        switch (type.getKind()) {
            case RECORD:
                sb.append("    record ").append(type.getWitType()).append(" {\n");
                for (Map.Entry<String, ScannedType> field : type.getFields().entrySet()) {
                    sb.append("        ").append(field.getKey()).append(": ")
                            .append(field.getValue().getWitType()).append(",\n");
                }
                sb.append("    }\n\n");
                break;

            case ENUM:
                sb.append("    enum ").append(type.getWitType()).append(" {\n");
                for (String caseName : type.getCases()) {
                    sb.append("        ").append(caseName).append(",\n");
                }
                sb.append("    }\n\n");
                break;

            case VARIANT:
                sb.append("    variant ").append(type.getWitType()).append(" {\n");
                for (String caseName : type.getCases()) {
                    sb.append("        ").append(caseName).append(",\n");
                }
                sb.append("    }\n\n");
                break;

            case FLAGS:
                sb.append("    flags ").append(type.getWitType()).append(" {\n");
                for (String caseName : type.getCases()) {
                    sb.append("        ").append(caseName).append(",\n");
                }
                sb.append("    }\n\n");
                break;

            case RESOURCE:
                sb.append("    resource ").append(type.getWitType()).append(" {\n");
                for (ScannedFunction method : type.getResourceMethods()) {
                    emitResourceMethod(sb, method);
                }
                sb.append("    }\n\n");
                break;

            default:
                break;
        }
    }
}
