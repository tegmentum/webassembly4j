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
import java.util.Objects;

/**
 * Represents an interface discovered during scanning, mapped to a WIT interface.
 */
public final class ScannedInterface {

    private final String name;
    private final String witName;
    private final boolean exported;
    private final List<ScannedFunction> functions;
    private final List<ScannedType> types;

    public ScannedInterface(String name, String witName, boolean exported,
                            List<ScannedFunction> functions, List<ScannedType> types) {
        this.name = Objects.requireNonNull(name);
        this.witName = Objects.requireNonNull(witName);
        this.exported = exported;
        this.functions = Collections.unmodifiableList(functions);
        this.types = Collections.unmodifiableList(types);
    }

    public String getName() {
        return name;
    }

    public String getWitName() {
        return witName;
    }

    public boolean isExported() {
        return exported;
    }

    public boolean isImported() {
        return !exported;
    }

    public List<ScannedFunction> getFunctions() {
        return functions;
    }

    public List<ScannedType> getTypes() {
        return types;
    }
}
