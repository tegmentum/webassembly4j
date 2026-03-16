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
package ai.tegmentum.webassembly4j.component.builder.it;

import ai.tegmentum.webassembly4j.component.builder.ComponentBuildPipeline;
import ai.tegmentum.webassembly4j.component.builder.ComponentBuilderConfig;
import ai.tegmentum.webassembly4j.component.builder.scan.ScannedComponent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for package scanning through the pipeline.
 */
class PackageScanIT {

    @Test
    void scanPackageDiscoverFixtureClasses() {
        ComponentBuilderConfig config = ComponentBuilderConfig.builder()
                .scanPackage("ai.tegmentum.webassembly4j.component.builder.it.fixtures")
                .witOutputDirectory(Path.of("/tmp"))
                .build();

        ComponentBuildPipeline pipeline = new ComponentBuildPipeline(config);
        ScannedComponent component = pipeline.scan();

        // Should discover GreeterComponent as the @WitComponent entry point
        assertEquals("example:greeter", component.getPackageName());
        assertEquals("greeter-world", component.getWorldName());

        // Should discover Greeter as @WitExport and Logger as @WitImport
        assertFalse(component.getExports().isEmpty(),
                "Should have discovered exported interfaces");
        assertFalse(component.getImports().isEmpty(),
                "Should have discovered imported interfaces");
    }

    @Test
    void scanPackageGeneratesWit(@TempDir Path tempDir) throws IOException {
        ComponentBuilderConfig config = ComponentBuilderConfig.builder()
                .scanPackage("ai.tegmentum.webassembly4j.component.builder.it.fixtures")
                .witOutputDirectory(tempDir)
                .build();

        ComponentBuildPipeline pipeline = new ComponentBuildPipeline(config);
        Path witFile = pipeline.generateWit();

        assertTrue(Files.exists(witFile));
        String content = Files.readString(witFile);

        assertTrue(content.contains("package example:greeter@0.1.0;"));
        assertTrue(content.contains("world greeter-world"));
        assertTrue(content.contains("export greeter;"));
        assertTrue(content.contains("import logger;"));
    }

    @Test
    void mixedScanPackageAndExplicitClasses() {
        // Provide scanPackage for discovery but also an explicit class
        ComponentBuilderConfig config = ComponentBuilderConfig.builder()
                .scanPackage("ai.tegmentum.webassembly4j.component.builder.it.fixtures")
                .witOutputDirectory(Path.of("/tmp"))
                .build();

        ComponentBuildPipeline pipeline = new ComponentBuildPipeline(config);
        ScannedComponent component = pipeline.scan();

        // Should still work — the package scan finds everything
        assertEquals("example:greeter", component.getPackageName());
    }
}
