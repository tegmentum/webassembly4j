package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.webassembly4j.api.DefaultLinkingContext;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.LinkingContext;
import ai.tegmentum.webassembly4j.api.Memory;
import ai.tegmentum.webassembly4j.api.Module;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises {@link Engine#loadModule(byte[], Instance)} — the cross-module
 * store-sharing overload added by F-Webassembly4j-Cross-Module-Store-Sharing
 * charter r.1 (2026-07-26).
 *
 * <p>Contrast with {@link WasmtimeLinkingContextExternImportsTest} which uses
 * provider escape ({@code ((WasmtimeModuleAdapter) module).store()}) to
 * manufacture same-store memory/table objects. This test uses the api-layer
 * overload only — no provider escape.
 *
 * <p>Scenario: OUTER module exports memory; CONSUMER module (loaded via the
 * new overload against OUTER's instance) imports env.memory and provides
 * store/load helpers. Consumer's helpers operate on OUTER's memory — proves
 * cross-module store sharing works end-to-end through the api layer.
 */
class WasmtimeCrossModuleStoreSharingTest {

    // (module (memory (export "memory") 1))
    private static final byte[] PRODUCER_MEMORY_MODULE = new byte[] {
        (byte)0x00, (byte)0x61, (byte)0x73, (byte)0x6d, (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00,
        (byte)0x05, (byte)0x03, (byte)0x01, (byte)0x00, (byte)0x01, (byte)0x07, (byte)0x0a, (byte)0x01,
        (byte)0x06, (byte)0x6d, (byte)0x65, (byte)0x6d, (byte)0x6f, (byte)0x72, (byte)0x79, (byte)0x02,
        (byte)0x00
    };

    // (module
    //   (import "env" "memory" (memory 1))
    //   (func (export "store_i32") (param i32 i32) local.get 0 local.get 1 i32.store)
    //   (func (export "load_i32")  (param i32) (result i32) local.get 0 i32.load))
    // Copied byte-identical from WasmtimeLinkingContextExternImportsTest.
    private static final byte[] IMPORT_MEMORY_MODULE = new byte[] {
        0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00, 0x01, 0x0b, 0x02, 0x60,
        0x02, 0x7f, 0x7f, 0x00, 0x60, 0x01, 0x7f, 0x01, 0x7f, 0x02, 0x0f, 0x01,
        0x03, 0x65, 0x6e, 0x76, 0x06, 0x6d, 0x65, 0x6d, 0x6f, 0x72, 0x79, 0x02,
        0x00, 0x01, 0x03, 0x03, 0x02, 0x00, 0x01, 0x07, 0x18, 0x02, 0x09, 0x73,
        0x74, 0x6f, 0x72, 0x65, 0x5f, 0x69, 0x33, 0x32, 0x00, 0x00, 0x08, 0x6c,
        0x6f, 0x61, 0x64, 0x5f, 0x69, 0x33, 0x32, 0x00, 0x01, 0x0a, 0x13, 0x02,
        0x09, 0x00, 0x20, 0x00, 0x20, 0x01, 0x36, 0x02, 0x00, 0x0b, 0x07, 0x00,
        0x20, 0x00, 0x28, 0x02, 0x00, 0x0b
    };

    static boolean runtimeAvailable() {
        try {
            try (Engine e = WasmtimeEngineAdapter.create(null)) {
                return true;
            }
        } catch (Throwable t) {
            return false;
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void crossModuleStoreSharingViaEngineLoadModuleOverload() {
        try (Engine engine = WasmtimeEngineAdapter.create(null);
             Module producer = engine.loadModule(PRODUCER_MEMORY_MODULE)) {

            // Instantiate producer to get an Instance whose store the consumer will share.
            Instance producerInstance = producer.instantiate();
            Memory sharedMemory = producerInstance.memory("memory")
                    .orElseThrow(() -> new AssertionError("producer memory export missing"));

            // Load consumer via the new engine-agnostic overload — reuses producer's store.
            try (Module consumer = engine.loadModule(IMPORT_MEMORY_MODULE, producerInstance)) {
                LinkingContext ctx = DefaultLinkingContext.builder()
                        .addMemoryImport("env", "memory", sharedMemory)
                        .build();

                Instance consumerInstance = consumer.instantiate(ctx);
                Function storeFn = consumerInstance.function("store_i32").orElseThrow();
                Function loadFn = consumerInstance.function("load_i32").orElseThrow();

                storeFn.invoke(0, 0xdeadbeef);
                assertEquals(0xdeadbeef, ((Number) loadFn.invoke(0)).intValue(),
                        "consumer store/load round-trips through PRODUCER's memory");

                // Host-side write through the shared api-layer Memory is also visible.
                sharedMemory.write(4, new byte[] { 0x11, 0x22, 0x33, 0x44 });
                assertEquals(0x44332211, ((Number) loadFn.invoke(4)).intValue(),
                        "host-side write to shared memory visible to consumer");
            }
        }
    }

    @Test
    void rejectsNullShareStoreWith() {
        try (Engine engine = WasmtimeEngineAdapter.create(null)) {
            assertThrows(IllegalArgumentException.class,
                    () -> engine.loadModule(PRODUCER_MEMORY_MODULE, null));
        }
    }
}
