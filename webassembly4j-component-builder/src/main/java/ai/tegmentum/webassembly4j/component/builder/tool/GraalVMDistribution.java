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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;

/**
 * Manages downloading and caching a GraalVM distribution with standalone WASM support.
 * <p>
 * The distribution is downloaded from GitHub releases on first use and cached in
 * {@code ~/.webassembly4j/graalvm/}. Subsequent calls use the cached version.
 */
public final class GraalVMDistribution {

    private static final String RELEASE_TAG = "v25.1.0-standalone-wasm-1";
    private static final String RELEASE_BASE_URL =
            "https://github.com/tegmentum/graal/releases/download/" + RELEASE_TAG;

    private static final Path CACHE_DIR = Path.of(
            System.getProperty("user.home"), ".webassembly4j", "graalvm", RELEASE_TAG);

    private GraalVMDistribution() {}

    /**
     * Returns the path to the web-image tool, downloading the distribution if needed.
     *
     * @return the resolved tool
     * @throws ComponentBuilderException if download or extraction fails
     */
    public static ExternalTool resolveWebImage() {
        Path webImage = findCachedWebImage();
        if (webImage != null) {
            String version = ToolResolver.getToolVersionSafe(webImage, "--version");
            return new ExternalTool("web-image", webImage, version);
        }

        download();

        webImage = findCachedWebImage();
        if (webImage == null) {
            throw new ComponentBuilderException(
                    "Failed to locate web-image after downloading GraalVM distribution");
        }
        String version = ToolResolver.getToolVersionSafe(webImage, "--version");
        return new ExternalTool("web-image", webImage, version);
    }

    private static Path findCachedWebImage() {
        if (!Files.isDirectory(CACHE_DIR)) {
            return null;
        }

        // The tarball extracts to a directory like graalvm-*/Contents/Home/bin/web-image (macOS)
        // or graalvm-*/bin/web-image (Linux)
        try (var stream = Files.walk(CACHE_DIR, 5)) {
            Optional<Path> found = stream
                    .filter(p -> p.getFileName().toString().equals("web-image"))
                    .filter(Files::isExecutable)
                    .findFirst();
            return found.orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static void download() {
        String platform = detectPlatform();
        String filename = "graalvm-standalone-wasm-25.1.0-dev-" + platform + ".tar.gz";
        String url = RELEASE_BASE_URL + "/" + filename;

        try {
            Files.createDirectories(CACHE_DIR);
            Path tarball = CACHE_DIR.resolve(filename);

            if (!Files.exists(tarball)) {
                System.out.println("[webassembly4j] Downloading GraalVM standalone WASM distribution...");
                System.out.println("[webassembly4j] " + url);

                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();

                HttpResponse<InputStream> response = client.send(request,
                        HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    throw new ComponentBuilderException(
                            "Failed to download GraalVM distribution: HTTP " + response.statusCode() +
                            "\nURL: " + url +
                            "\nNo pre-built binary available for platform '" + platform + "'." +
                            "\nSet GRAALVM_HOME to a local GraalVM build with standalone WASM support.");
                }

                try (InputStream body = response.body()) {
                    Files.copy(body, tarball, StandardCopyOption.REPLACE_EXISTING);
                }

                System.out.println("[webassembly4j] Downloaded " +
                        (Files.size(tarball) / 1024 / 1024) + " MB");
            }

            // Extract
            System.out.println("[webassembly4j] Extracting to " + CACHE_DIR);
            ProcessRunner.Result result = ProcessRunner.run(
                    java.util.List.of("tar", "xzf", tarball.toString()),
                    CACHE_DIR, 120);

            if (!result.isSuccess()) {
                throw new ComponentBuilderException(
                        "Failed to extract GraalVM distribution: " + result.getStderr());
            }

            // Clean up tarball after extraction
            Files.deleteIfExists(tarball);

            System.out.println("[webassembly4j] GraalVM standalone WASM ready");

        } catch (ComponentBuilderException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            throw new ComponentBuilderException(
                    "Failed to download GraalVM distribution from " + url, e);
        }
    }

    /**
     * Detects the current platform in the format used by release artifact names.
     *
     * @return platform string like "darwin-aarch64" or "linux-x64"
     */
    static String detectPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);

        String osName;
        if (os.contains("mac") || os.contains("darwin")) {
            osName = "darwin";
        } else if (os.contains("linux")) {
            osName = "linux";
        } else if (os.contains("windows")) {
            osName = "windows";
        } else {
            osName = os.replaceAll("\\s+", "-");
        }

        String archName;
        if (arch.equals("aarch64") || arch.equals("arm64")) {
            archName = "aarch64";
        } else if (arch.equals("amd64") || arch.equals("x86_64")) {
            archName = "x64";
        } else {
            archName = arch;
        }

        return osName + "-" + archName;
    }

    /**
     * Returns the cache directory for the current release.
     */
    public static Path getCacheDir() {
        return CACHE_DIR;
    }
}
