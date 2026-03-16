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

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompilationResultTest {

    @Test
    void successResult() {
        Path wasm = Paths.get("/out/component.wasm");
        Path js = Paths.get("/out/component.js");
        Path wat = Paths.get("/out/component.wat");
        Path component = Paths.get("/out/component.component.wasm");

        CompilationResult result = CompilationResult.builder()
                .success(true)
                .outputWasm(wasm)
                .outputJs(js)
                .outputWat(wat)
                .componentWasm(component)
                .build();

        assertTrue(result.isSuccess());
        assertTrue(result.getOutputWasm().isPresent());
        assertEquals(wasm, result.getOutputWasm().get());
        assertTrue(result.getOutputJs().isPresent());
        assertEquals(js, result.getOutputJs().get());
        assertTrue(result.getOutputWat().isPresent());
        assertEquals(wat, result.getOutputWat().get());
        assertTrue(result.getComponentWasm().isPresent());
        assertEquals(component, result.getComponentWasm().get());
        assertFalse(result.getErrorMessage().isPresent());
    }

    @Test
    void failureResult() {
        CompilationResult result = CompilationResult.builder()
                .success(false)
                .errorMessage("compilation failed: missing symbol")
                .build();

        assertFalse(result.isSuccess());
        assertFalse(result.getOutputWasm().isPresent());
        assertFalse(result.getOutputJs().isPresent());
        assertFalse(result.getOutputWat().isPresent());
        assertFalse(result.getComponentWasm().isPresent());
        assertTrue(result.getErrorMessage().isPresent());
        assertEquals("compilation failed: missing symbol", result.getErrorMessage().get());
    }

    @Test
    void partialResult() {
        CompilationResult result = CompilationResult.builder()
                .success(true)
                .outputWasm(Paths.get("/out/core.wasm"))
                .build();

        assertTrue(result.isSuccess());
        assertTrue(result.getOutputWasm().isPresent());
        assertFalse(result.getOutputJs().isPresent());
        assertFalse(result.getComponentWasm().isPresent());
    }
}
