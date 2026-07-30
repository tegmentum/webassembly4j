/*
 * Copyright 2026 Tegmentum AI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package ai.tegmentum.webassembly4j.provider.wasmos;

import ai.tegmentum.webassembly4j.api.EngineInfo;

/**
 * Static {@link EngineInfo} for the wasmos provider. The engine version
 * tracks wasmos-runtime, not wasmtime — a wasmos-runtime version bump
 * reshuffles what's on the linker, whereas the underlying wasmtime bump
 * is a transitive detail.
 */
final class WasmosEngineInfo implements EngineInfo {

    @Override
    public String engineId() {
        return "wasmos";
    }

    @Override
    public String providerId() {
        return "wasmos";
    }

    @Override
    public String providerVersion() {
        return "1.0.0-SNAPSHOT";
    }

    @Override
    public String engineVersion() {
        // wasmos-runtime = 0.1.0 as of the initial provider scaffold; when we
        // move to a versioned crate release we should read this from a
        // manifest header instead of hardcoding.
        return "0.1.0";
    }

    @Override
    public int minimumJavaVersion() {
        return 11;
    }
}
