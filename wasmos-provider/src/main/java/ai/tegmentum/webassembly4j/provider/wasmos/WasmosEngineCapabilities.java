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

import ai.tegmentum.webassembly4j.api.EngineCapabilities;

/**
 * Capability matrix for wasmos. Reflects the MVP surface — component-model
 * hosting + WASI preview 2 are on; core-module hosting, GC, fuel, epoch
 * interruption, threads are off (either because wasmos-runtime doesn't
 * expose them yet or because the wasmos-provider MVP hasn't wired them
 * through). A future pass can flip these once we plumb configuration
 * through the JNI shim.
 */
final class WasmosEngineCapabilities implements EngineCapabilities {

    @Override public boolean supportsCoreModules()      { return false; }
    @Override public boolean supportsComponents()       { return true; }
    @Override public boolean supportsWasi()             { return true; }
    @Override public boolean supportsFuel()             { return false; }
    @Override public boolean supportsEpochInterruption(){ return false; }
    @Override public boolean supportsThreads()          { return false; }
    @Override public boolean supportsGc()               { return false; }
    @Override public boolean supportsReferenceTypes()   { return false; }
    @Override public boolean supportsMultiMemory()      { return false; }
    @Override public boolean supportsNativeInterop()    { return true; }
    @Override public boolean supportsWasiHttp()         { return false; }

    /** wasmos-runtime uses async wasmtime under the hood — every instance
     *  goes through {@code instantiate_async} + {@code call_async} on the
     *  provider's persistent Tokio runtime. */
    @Override public boolean supportsAsyncComponents()  { return true; }
}
