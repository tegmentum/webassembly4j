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
import java.util.stream.Collectors;

/**
 * Represents a complete component model built from scanning annotated Java classes.
 */
public final class ScannedComponent {

    private final String packageName;
    private final String version;
    private final String worldName;
    private final List<ScannedInterface> interfaces;

    public ScannedComponent(String packageName, String version, String worldName,
                            List<ScannedInterface> interfaces) {
        this.packageName = Objects.requireNonNull(packageName);
        this.version = version;
        this.worldName = Objects.requireNonNull(worldName);
        this.interfaces = Collections.unmodifiableList(interfaces);
    }

    public String getPackageName() {
        return packageName;
    }

    public String getVersion() {
        return version;
    }

    public String getWorldName() {
        return worldName;
    }

    public List<ScannedInterface> getInterfaces() {
        return interfaces;
    }

    public List<ScannedInterface> getExports() {
        return interfaces.stream()
                .filter(ScannedInterface::isExported)
                .collect(Collectors.toList());
    }

    public List<ScannedInterface> getImports() {
        return interfaces.stream()
                .filter(ScannedInterface::isImported)
                .collect(Collectors.toList());
    }
}
