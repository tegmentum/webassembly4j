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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a type discovered during scanning, mapped to a WIT type.
 */
public final class ScannedType {

    /**
     * The kind of WIT type this maps to.
     */
    public enum Kind {
        PRIMITIVE,
        LIST,
        OPTION,
        RECORD,
        ENUM,
        VARIANT,
        FLAGS,
        RESOURCE
    }

    private final Kind kind;
    private final String witType;
    private final String javaType;
    private final ScannedType elementType;
    private final Map<String, ScannedType> fields;
    private final List<String> cases;
    private final List<ScannedFunction> resourceMethods;

    private ScannedType(Kind kind, String witType, String javaType,
                        ScannedType elementType, Map<String, ScannedType> fields,
                        List<String> cases, List<ScannedFunction> resourceMethods) {
        this.kind = Objects.requireNonNull(kind);
        this.witType = Objects.requireNonNull(witType);
        this.javaType = Objects.requireNonNull(javaType);
        this.elementType = elementType;
        this.fields = fields != null ? Collections.unmodifiableMap(fields) : Collections.emptyMap();
        this.cases = cases != null ? Collections.unmodifiableList(cases) : Collections.emptyList();
        this.resourceMethods = resourceMethods != null
                ? Collections.unmodifiableList(resourceMethods) : Collections.emptyList();
    }

    private ScannedType(Kind kind, String witType, String javaType,
                        ScannedType elementType, Map<String, ScannedType> fields,
                        List<String> cases) {
        this(kind, witType, javaType, elementType, fields, cases, null);
    }

    public static ScannedType primitive(String witType, String javaType) {
        return new ScannedType(Kind.PRIMITIVE, witType, javaType, null, null, null);
    }

    public static ScannedType list(ScannedType elementType) {
        return new ScannedType(Kind.LIST, "list<" + elementType.getWitType() + ">",
                "List<" + elementType.getJavaType() + ">", elementType, null, null);
    }

    public static ScannedType option(ScannedType elementType) {
        return new ScannedType(Kind.OPTION, "option<" + elementType.getWitType() + ">",
                "Optional<" + elementType.getJavaType() + ">", elementType, null, null);
    }

    public static ScannedType record(String name, Map<String, ScannedType> fields) {
        return new ScannedType(Kind.RECORD, name, name, null, fields, null);
    }

    public static ScannedType enumType(String name, List<String> cases) {
        return new ScannedType(Kind.ENUM, name, name, null, null, cases);
    }

    public static ScannedType variant(String name, List<String> cases) {
        return new ScannedType(Kind.VARIANT, name, name, null, null, cases);
    }

    public static ScannedType flags(String name, List<String> cases) {
        return new ScannedType(Kind.FLAGS, name, name, null, null, cases);
    }

    public static ScannedType resource(String name, List<ScannedFunction> methods) {
        return new ScannedType(Kind.RESOURCE, name, name, null, null, null, methods);
    }

    public Kind getKind() {
        return kind;
    }

    public String getWitType() {
        return witType;
    }

    public String getJavaType() {
        return javaType;
    }

    public ScannedType getElementType() {
        return elementType;
    }

    public Map<String, ScannedType> getFields() {
        return fields;
    }

    public List<String> getCases() {
        return cases;
    }

    public List<ScannedFunction> getResourceMethods() {
        return resourceMethods;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScannedType that)) return false;
        return kind == that.kind && witType.equals(that.witType);
    }

    @Override
    public int hashCode() {
        return 31 * kind.hashCode() + witType.hashCode();
    }
}
