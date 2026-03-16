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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Wraps {@link ProcessBuilder} with timeout, output capture, and error reporting.
 */
public final class ProcessRunner {

    private static final long DEFAULT_TIMEOUT_SECONDS = 300;

    private ProcessRunner() {}

    /**
     * Result of running an external process.
     */
    public static final class Result {
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        public Result(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        public int getExitCode() {
            return exitCode;
        }

        public String getStdout() {
            return stdout;
        }

        public String getStderr() {
            return stderr;
        }

        public boolean isSuccess() {
            return exitCode == 0;
        }
    }

    /**
     * Runs a command with the default timeout.
     *
     * @param command the command and arguments
     * @param workingDirectory the working directory, or null for current directory
     * @return the result
     */
    public static Result run(List<String> command, Path workingDirectory) {
        return run(command, workingDirectory, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Runs a command with a specified timeout.
     *
     * @param command the command and arguments
     * @param workingDirectory the working directory, or null for current directory
     * @param timeoutSeconds maximum seconds to wait
     * @return the result
     */
    public static Result run(List<String> command, Path workingDirectory, long timeoutSeconds) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            if (workingDirectory != null) {
                pb.directory(workingDirectory.toFile());
            }
            pb.redirectErrorStream(false);

            Process process = pb.start();

            // Read stdout and stderr in parallel threads to avoid blocking
            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    return reader.lines().collect(Collectors.joining("\n"));
                } catch (IOException e) {
                    return "";
                }
            });

            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    return reader.lines().collect(Collectors.joining("\n"));
                } catch (IOException e) {
                    return "";
                }
            });

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw new ComponentBuilderException(
                        "Process timed out after " + timeoutSeconds + " seconds: " +
                                String.join(" ", command));
            }

            String stdout = stdoutFuture.join();
            String stderr = stderrFuture.join();

            return new Result(process.exitValue(), stdout, stderr);

        } catch (ComponentBuilderException e) {
            throw e;
        } catch (IOException e) {
            throw new ComponentBuilderException(
                    "Failed to execute command: " + String.join(" ", command), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ComponentBuilderException(
                    "Interrupted while waiting for command: " + String.join(" ", command), e);
        }
    }
}
