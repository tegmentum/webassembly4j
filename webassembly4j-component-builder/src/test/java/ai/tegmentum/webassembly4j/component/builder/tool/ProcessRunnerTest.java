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

import ai.tegmentum.webassembly4j.component.builder.ComponentBuilderException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessRunnerTest {

    @Test
    void runSuccessfulCommand() {
        ProcessRunner.Result result = ProcessRunner.run(
                Arrays.asList("echo", "hello"), null);

        assertTrue(result.isSuccess());
        assertEquals(0, result.getExitCode());
        assertEquals("hello", result.getStdout().trim());
    }

    @Test
    void runCommandWithNonZeroExit() {
        ProcessRunner.Result result = ProcessRunner.run(
                Arrays.asList("sh", "-c", "exit 42"), null);

        assertFalse(result.isSuccess());
        assertEquals(42, result.getExitCode());
    }

    @Test
    void runCommandCapturesStderr() {
        ProcessRunner.Result result = ProcessRunner.run(
                Arrays.asList("sh", "-c", "echo error >&2; exit 1"), null);

        assertFalse(result.isSuccess());
        assertEquals("error", result.getStderr().trim());
    }

    @Test
    void runCommandWithWorkingDirectory(@TempDir Path tempDir) {
        ProcessRunner.Result result = ProcessRunner.run(
                Arrays.asList("pwd"), tempDir);

        assertTrue(result.isSuccess());
        // The output should contain the temp dir path (realpath may resolve symlinks)
        assertTrue(result.getStdout().trim().endsWith(tempDir.getFileName().toString())
                || result.getStdout().trim().contains(tempDir.getFileName().toString()));
    }

    @Test
    void runCommandWithTimeout() {
        assertThrows(ComponentBuilderException.class, () ->
                ProcessRunner.run(
                        Arrays.asList("sleep", "60"), null, 1));
    }

    @Test
    void runNonExistentCommand() {
        assertThrows(ComponentBuilderException.class, () ->
                ProcessRunner.run(
                        List.of("nonexistent-command-xyz123"), null));
    }

    @Test
    void resultProperties() {
        ProcessRunner.Result result = new ProcessRunner.Result(0, "out", "err");

        assertEquals(0, result.getExitCode());
        assertEquals("out", result.getStdout());
        assertEquals("err", result.getStderr());
        assertTrue(result.isSuccess());
    }

    @Test
    void resultNotSuccess() {
        ProcessRunner.Result result = new ProcessRunner.Result(1, "", "fail");

        assertFalse(result.isSuccess());
    }
}
