package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.webassembly4j.api.Caller;
import ai.tegmentum.webassembly4j.api.CallerAwareHostFunction;
import ai.tegmentum.webassembly4j.api.DefaultLinkingContext;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.LinkingContext;
import ai.tegmentum.webassembly4j.api.Module;
import ai.tegmentum.webassembly4j.api.Table;
import ai.tegmentum.webassembly4j.api.ValueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the caller-aware host function pipeline end-to-end against
 * the wasmtime4j provider. The api additions land at r.1-1 (Caller,
 * CallerAwareHostFunction, CallerAwareHostFunctionDefinition,
 * LinkingContext.callerAwareHostFunctions, DefaultLinkingContext.Builder
 * .addCallerAwareHostFunction); the provider bridge lands at r.1-2 +
 * r.1-3 (WasmtimeCallerAdapter + WasmtimeModuleAdapter caller-aware
 * dispatch). This test proves the wire end-to-end and confirms that the
 * generation-counter use-after-return safety propagates from the
 * wasmtime4j jni-side generation check to the api-side Caller handle.
 */
class WasmtimeCallerAwareHostFunctionTest {

    // Hand-authored wasm module (compiled from):
    //
    //   (module
    //     (import "env" "trigger_grow" (func $trigger_grow (param i32)))
    //     (memory (export "memory") 1)
    //     (table (export "table") 1 funcref)
    //     (func (export "run") (param i32)
    //       local.get 0
    //       call $trigger_grow))
    //
    // 88 bytes. Kept inline to match the pattern used by
    // WasmtimeLinkingContextExternImportsTest so the test needs no build-time
    // wasm-authoring toolchain.
    private static final byte[] CALLER_AWARE_MODULE = new byte[] {
        0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00, 0x01, 0x05, 0x01, 0x60,
        0x01, 0x7f, 0x00, 0x02, 0x14, 0x01, 0x03, 0x65, 0x6e, 0x76, 0x0c, 0x74,
        0x72, 0x69, 0x67, 0x67, 0x65, 0x72, 0x5f, 0x67, 0x72, 0x6f, 0x77, 0x00,
        0x00, 0x03, 0x02, 0x01, 0x00, 0x04, 0x04, 0x01, 0x70, 0x00, 0x01, 0x05,
        0x03, 0x01, 0x00, 0x01, 0x07, 0x18, 0x03, 0x06, 0x6d, 0x65, 0x6d, 0x6f,
        0x72, 0x79, 0x02, 0x00, 0x05, 0x74, 0x61, 0x62, 0x6c, 0x65, 0x01, 0x00,
        0x03, 0x72, 0x75, 0x6e, 0x00, 0x01, 0x0a, 0x08, 0x01, 0x06, 0x00, 0x20,
        0x00, 0x10, 0x00, 0x0b
    };

    static boolean runtimeAvailable() {
        return new WasmtimeProvider().availability().available();
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void callerAwareHostFunctionGrowsCallerTableFromCallback() {
        AtomicReference<Caller<?>> capturedCaller = new AtomicReference<>();
        AtomicInteger observedPrevSize = new AtomicInteger(-1);
        AtomicInteger observedGrowReturn = new AtomicInteger(Integer.MIN_VALUE);

        CallerAwareHostFunction<?> triggerGrow = (caller, args) -> {
            // Capture the caller so we can prove post-callback use throws.
            capturedCaller.set(caller);

            Table t = caller.getTable("table").orElseThrow(
                    () -> new AssertionError("callback failed to reach caller table"));
            observedPrevSize.set(t.size());
            int growBy = ((Number) args[0]).intValue();
            int prevSize = caller.growTable(t, growBy, null);
            observedGrowReturn.set(prevSize);
            return new Object[0];
        };

        try (Engine engine = WasmtimeEngineAdapter.create(null);
             Module module = engine.loadModule(CALLER_AWARE_MODULE)) {

            LinkingContext ctx = DefaultLinkingContext.builder()
                    .addCallerAwareHostFunction("env", "trigger_grow",
                            new ValueType[] { ValueType.I32 },
                            new ValueType[0],
                            triggerGrow)
                    .build();

            Instance instance = module.instantiate(ctx);
            Table tableBefore = instance.table("table").orElseThrow(
                    () -> new AssertionError("run: table export missing"));
            int sizeBefore = tableBefore.size();
            assertEquals(1, sizeBefore, "wasm-declared table starts at size 1");

            Function run = instance.function("run").orElseThrow(
                    () -> new AssertionError("run: run export missing"));

            // Guest calls trigger_grow(1) — callback grows the table by 1.
            run.invoke(1);

            // Callback executed and observed the pre-grow size.
            assertEquals(1, observedPrevSize.get(),
                    "callback saw pre-grow table size");
            assertEquals(1, observedGrowReturn.get(),
                    "growTable return value is previous size on success");

            // Post-callback: instance's table now reports the grown size.
            Table tableAfter = instance.table("table").orElseThrow();
            assertEquals(2, tableAfter.size(),
                    "table grew by 1 in the caller-scoped path");

            // Use-after-return: retained Caller handle now throws when
            // any scoped method is invoked. Any of the scoped ops would
            // do; growTable is convenient because it does not need
            // any additional native handles beyond the caller itself.
            Caller<?> stale = capturedCaller.get();
            assertNotNull(stale, "capturedCaller set inside the callback");

            IllegalStateException ise = assertThrows(IllegalStateException.class, () ->
                    stale.growTable(tableAfter, 1, null),
                    "wasmtime4j r.2 generation counter rejects use-after-return");
            assertTrue(ise.getMessage().toLowerCase().contains("caller"),
                    "message identifies the caller-generation issue: " + ise.getMessage());
        }
    }
}
