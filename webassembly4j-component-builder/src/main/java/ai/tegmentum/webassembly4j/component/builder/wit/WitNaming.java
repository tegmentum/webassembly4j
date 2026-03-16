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

import java.util.Locale;

/**
 * Utility class for converting Java names to WIT naming conventions.
 *
 * <p>WIT uses kebab-case for identifiers. This class converts Java camelCase
 * and PascalCase names to kebab-case, and vice versa.
 */
public final class WitNaming {

    private WitNaming() {}

    /**
     * Converts a Java name (camelCase or PascalCase) to WIT kebab-case.
     *
     * <p>Examples:
     * <ul>
     *   <li>"getValue" → "get-value"</li>
     *   <li>"HttpRequest" → "http-request"</li>
     *   <li>"myField" → "my-field"</li>
     *   <li>"XMLParser" → "xml-parser"</li>
     *   <li>"simpleURL" → "simple-url"</li>
     * </ul>
     *
     * @param javaName the Java name
     * @return the kebab-case name
     */
    public static String toKebabCase(String javaName) {
        if (javaName == null || javaName.isEmpty()) {
            return javaName;
        }

        // Handle snake_case input
        if (javaName.contains("_")) {
            return javaName.replace('_', '-').toLowerCase(Locale.ROOT);
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < javaName.length(); i++) {
            char c = javaName.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    // Insert hyphen before uppercase that follows lowercase
                    char prev = javaName.charAt(i - 1);
                    if (Character.isLowerCase(prev)) {
                        result.append('-');
                    } else if (i + 1 < javaName.length()
                            && Character.isLowerCase(javaName.charAt(i + 1))
                            && Character.isUpperCase(prev)) {
                        // Handle acronyms like "XMLParser" -> "xml-parser"
                        result.append('-');
                    }
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Converts a WIT kebab-case name to Java camelCase.
     *
     * <p>Examples:
     * <ul>
     *   <li>"get-value" → "getValue"</li>
     *   <li>"http-request" → "httpRequest"</li>
     * </ul>
     *
     * @param witName the WIT name
     * @return the camelCase name
     */
    public static String toCamelCase(String witName) {
        if (witName == null || witName.isEmpty()) {
            return witName;
        }

        String[] parts = witName.split("-");
        StringBuilder result = new StringBuilder(parts[0].toLowerCase(Locale.ROOT));
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    result.append(part.substring(1).toLowerCase(Locale.ROOT));
                }
            }
        }
        return result.toString();
    }

    /**
     * Converts a WIT kebab-case name to Java PascalCase.
     *
     * <p>Examples:
     * <ul>
     *   <li>"http-request" → "HttpRequest"</li>
     *   <li>"my-component" → "MyComponent"</li>
     * </ul>
     *
     * @param witName the WIT name
     * @return the PascalCase name
     */
    public static String toPascalCase(String witName) {
        String camelCase = toCamelCase(witName);
        if (camelCase == null || camelCase.isEmpty()) {
            return camelCase;
        }
        return Character.toUpperCase(camelCase.charAt(0)) + camelCase.substring(1);
    }
}
