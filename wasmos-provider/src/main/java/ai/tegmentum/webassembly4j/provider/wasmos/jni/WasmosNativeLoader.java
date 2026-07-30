/*
 * Copyright 2026 Tegmentum AI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package ai.tegmentum.webassembly4j.provider.wasmos.jni;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads the {@code libwasmos_provider.{so,dylib,dll}} native library out of
 * the packaged jar's {@code /natives/{os}-{arch}/} directory.
 *
 * <p>Deliberately self-contained (no dependency on wasmtime4j's
 * {@code wasmtime4j-native-loader}) so wasmos-provider stands alone with
 * no wasmtime4j coupling — per the F-Webassembly4j-Wasmos-Provider r.1
 * architectural constraint.
 *
 * <p>The layout matches wasmtime4j-native's convention:
 * <pre>
 *   /natives/darwin-aarch64/libwasmos_provider.dylib
 *   /natives/darwin-x86_64/libwasmos_provider.dylib
 *   /natives/linux-x86_64/libwasmos_provider.so
 *   /natives/linux-aarch64/libwasmos_provider.so
 *   /natives/windows-x86_64/wasmos_provider.dll
 * </pre>
 *
 * <p>System properties for troubleshooting:
 * <ul>
 *   <li>{@code webassembly4j.wasmos.native.path} — absolute path to a prebuilt
 *       dylib. Bypasses jar extraction. Useful during development when
 *       cargo builds outside the Maven cycle.
 *   <li>{@code webassembly4j.wasmos.native.debug} — set to {@code true} to
 *       log resolution attempts to {@code System.err}.
 * </ul>
 */
final class WasmosNativeLoader {

    private static final String PROP_OVERRIDE = "webassembly4j.wasmos.native.path";
    private static final String PROP_DEBUG = "webassembly4j.wasmos.native.debug";
    private static final String LIB_BASENAME = "wasmos_provider";

    // Loaded exactly once per classloader; a second call is a no-op (System.load
    // would throw UnsatisfiedLinkError on double-load of the same absolute path,
    // and the singleton fingerprint gives idempotency in the face of static-init
    // races across parallel threads).
    private static volatile boolean loaded = false;

    private WasmosNativeLoader() {}

    static synchronized void load() {
        if (loaded) {
            return;
        }
        final String override = System.getProperty(PROP_OVERRIDE);
        if (override != null && !override.isEmpty()) {
            debug("using override path: " + override);
            System.load(override);
            loaded = true;
            return;
        }

        final String platform = detectPlatform();
        final String fileName = libraryFileName(platform);
        final String resourcePath = "/natives/" + platform + "/" + fileName;
        debug("looking up " + resourcePath);

        try (InputStream in = WasmosNativeLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new UnsatisfiedLinkError(
                        "wasmos-provider native library not found on classpath at " + resourcePath
                        + " — either the wasmos-provider jar was built without the platform's"
                        + " native library, or the current platform (" + platform + ") is not"
                        + " supported. Override with -D" + PROP_OVERRIDE + "=/absolute/path.");
            }
            final Path tmp = Files.createTempFile("wasmos-provider-", fileName);
            tmp.toFile().deleteOnExit();
            // TRUNCATE_EXISTING: createTempFile made an empty file we're
            // about to fill; the option keeps us defensive if the file
            // ever gets prefixed by unrelated content between calls.
            try (OutputStream out = Files.newOutputStream(
                    tmp, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                copy(in, out);
            }
            debug("extracted to " + tmp);
            System.load(tmp.toAbsolutePath().toString());
            loaded = true;
        } catch (IOException e) {
            throw new UnsatisfiedLinkError(
                    "failed to extract wasmos-provider native library: " + e.getMessage());
        }
    }

    private static String detectPlatform() {
        final String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        final String archRaw = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
        final String arch;
        switch (archRaw) {
            case "aarch64":
            case "arm64":
                arch = "aarch64";
                break;
            case "x86_64":
            case "amd64":
                arch = "x86_64";
                break;
            default:
                arch = archRaw;
        }
        final String osFamily;
        if (os.contains("mac") || os.contains("darwin")) {
            osFamily = "darwin";
        } else if (os.contains("linux")) {
            osFamily = "linux";
        } else if (os.contains("win")) {
            osFamily = "windows";
        } else {
            osFamily = os;
        }
        return osFamily + "-" + arch;
    }

    private static String libraryFileName(String platform) {
        if (platform.startsWith("windows-")) {
            return LIB_BASENAME + ".dll";
        }
        if (platform.startsWith("darwin-")) {
            return "lib" + LIB_BASENAME + ".dylib";
        }
        // linux + everything else — .so
        return "lib" + LIB_BASENAME + ".so";
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        final byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
    }

    private static void debug(String msg) {
        if (Boolean.getBoolean(PROP_DEBUG)) {
            System.err.println("[wasmos-provider-loader] " + msg);
        }
    }

}
