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
package ai.tegmentum.webassembly4j.component.builder.tool;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalToolTest {

    @Test
    void basicProperties() {
        Path path = Paths.get("/usr/bin/wasm-tools");
        ExternalTool tool = new ExternalTool("wasm-tools", path, "1.0.0");

        assertEquals("wasm-tools", tool.getName());
        assertEquals(path, tool.getExecutablePath());
        assertTrue(tool.getVersion().isPresent());
        assertEquals("1.0.0", tool.getVersion().get());
    }

    @Test
    void nullVersion() {
        Path path = Paths.get("/usr/bin/tool");
        ExternalTool tool = new ExternalTool("tool", path, null);

        assertFalse(tool.getVersion().isPresent());
    }

    @Test
    void toStringIncludesInfo() {
        Path path = Paths.get("/usr/bin/native-image");
        ExternalTool tool = new ExternalTool("native-image", path, "25.0");

        String str = tool.toString();
        assertTrue(str.contains("native-image"));
        assertTrue(str.contains("25.0"));
        assertTrue(str.contains("/usr/bin/native-image"));
    }

    @Test
    void toStringWithoutVersion() {
        Path path = Paths.get("/usr/bin/tool");
        ExternalTool tool = new ExternalTool("tool", path, null);

        String str = tool.toString();
        assertTrue(str.contains("unknown version"));
    }
}
