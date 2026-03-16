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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a function discovered during scanning.
 */
public final class ScannedFunction {

    private final String name;
    private final String witName;
    private final Map<String, ScannedType> parameters;
    private final ScannedType returnType;

    public ScannedFunction(String name, String witName,
                           Map<String, ScannedType> parameters,
                           ScannedType returnType) {
        this.name = Objects.requireNonNull(name);
        this.witName = Objects.requireNonNull(witName);
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        this.returnType = returnType;
    }

    public String getName() {
        return name;
    }

    public String getWitName() {
        return witName;
    }

    public Map<String, ScannedType> getParameters() {
        return parameters;
    }

    public ScannedType getReturnType() {
        return returnType;
    }
}
