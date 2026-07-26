package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.Module;
import ai.tegmentum.webassembly4j.api.Table;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the four spec-standard {@link Table} operations added by the
 * Table-Interface-Completion charter (r.1): {@code maxSize}, {@code get},
 * {@code set}, {@code grow}. These delegate to the native
 * {@code ai.tegmentum.wasmtime4j.WasmTable}.
 *
 * <p>The api-layer {@link Engine} intentionally exposes no WAT entry
 * point, so fixture modules are supplied as hand-encoded raw wasm; they
 * are small enough (three tables + one empty function) that this is more
 * legible than dragging a wasm-text tool through the test dependency
 * graph.
 */
class WasmtimeTableAdapterTest {

    static boolean runtimeAvailable() {
        return new WasmtimeProvider().availability().available();
    }

    // ---------------- Hand-encoded WASM fixtures ----------------
    //
    // Encoding a funcref table + one function is small enough to keep as
    // literal byte arrays. Encoded and cross-checked against wat2wasm:
    //   (module
    //     (table (export "t") $LIMITS funcref)
    //     (func $f (export "f")))
    //
    // Sections used:
    //   1 Type    : ()->()
    //   3 Function: [0]
    //   4 Table   : [limits, funcref]
    //   7 Export  : "t" (table 0), "f" (func 0)
    //  10 Code    : one empty function

    /** funcref table with min=3, max=10. */
    private static final byte[] TABLE_BOUNDED = new byte[] {
        0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00, // magic + version
        // Type: 1 * ()->()
        0x01, 0x04, 0x01, 0x60, 0x00, 0x00,
        // Function: 1 func with type 0
        0x03, 0x02, 0x01, 0x00,
        // Table: 1 * funcref, min=3, max=10
        0x04, 0x05, 0x01, 0x70, 0x01, 0x03, 0x0a,
        // Export: "t" (table 0), "f" (func 0)
        0x07, 0x09, 0x02,
            0x01, 0x74, 0x01, 0x00,
            0x01, 0x66, 0x00, 0x00,
        // Code: empty body
        0x0a, 0x04, 0x01, 0x02, 0x00, 0x0b
    };

    /** funcref table with min=2, no max. */
    private static final byte[] TABLE_UNBOUNDED = new byte[] {
        0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00,
        0x01, 0x04, 0x01, 0x60, 0x00, 0x00,
        0x03, 0x02, 0x01, 0x00,
        // Table: 1 * funcref, min=2, no max (flags=0x00)
        0x04, 0x04, 0x01, 0x70, 0x00, 0x02,
        0x07, 0x09, 0x02,
            0x01, 0x74, 0x01, 0x00,
            0x01, 0x66, 0x00, 0x00,
        0x0a, 0x04, 0x01, 0x02, 0x00, 0x0b
    };

    /** funcref table with min=1, max=1 (no room to grow). */
    private static final byte[] TABLE_MAX_ONE = new byte[] {
        0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00,
        0x01, 0x04, 0x01, 0x60, 0x00, 0x00,
        0x03, 0x02, 0x01, 0x00,
        // Table: 1 * funcref, min=1, max=1
        0x04, 0x05, 0x01, 0x70, 0x01, 0x01, 0x01,
        0x07, 0x09, 0x02,
            0x01, 0x74, 0x01, 0x00,
            0x01, 0x66, 0x00, 0x00,
        0x0a, 0x04, 0x01, 0x02, 0x00, 0x0b
    };

    // ---------------- Tests ----------------

    @Test
    @EnabledIf("runtimeAvailable")
    void sizeReportsInitialMinimum() {
        try (Engine engine = WasmtimeEngineAdapter.create(null);
             Module module = engine.loadModule(TABLE_BOUNDED)) {
            Instance instance = module.instantiate();
            Table table = instance.table("t").orElseThrow();
            assertEquals(3, table.size());
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void maxSizeBoundedReturnsDeclaredMax() {
        try (Engine engine = WasmtimeEngineAdapter.create(null);
             Module module = engine.loadModule(TABLE_BOUNDED)) {
            Instance instance = module.instantiate();
            Table table = instance.table("t").orElseThrow();
            OptionalInt max = table.maxSize();
            assertTrue(max.isPresent(), "bounded table must report a max");
            assertEquals(10, max.getAsInt());
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void maxSizeUnboundedReturnsEmpty() {
        try (Engine engine = WasmtimeEngineAdapter.create(null);
             Module module = engine.loadModule(TABLE_UNBOUNDED)) {
            Instance instance = module.instantiate();
            Table table = instance.table("t").orElseThrow();
            assertFalse(table.maxSize().isPresent(),
                    "unbounded table must report empty max");
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void getUninitializedSlotReturnsNull() {
        try (Engine engine = WasmtimeEngineAdapter.create(null);
             Module module = engine.loadModule(TABLE_BOUNDED)) {
            Instance instance = module.instantiate();
            Table table = instance.table("t").orElseThrow();
            // funcref slots default to null (uninitialized) until set
            assertNull(table.get(0));
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void setThenGetRoundTripsFuncref() {
        try (Engine engine = WasmtimeEngineAdapter.create(null);
             Module module = engine.loadModule(TABLE_BOUNDED)) {
            Instance instance = module.instantiate();
            Table table = instance.table("t").orElseThrow();

            // The api-layer Function does not expose unwrap; reach the native
            // WasmFunction (a legal funcref value) via the native Instance.
            ai.tegmentum.wasmtime4j.Instance nativeInstance = instance
                    .unwrap(ai.tegmentum.wasmtime4j.Instance.class)
                    .orElseThrow(() -> new AssertionError(
                            "Expected to unwrap ai.tegmentum.wasmtime4j.Instance"));
            Object funcref = nativeInstance.getFunction("f")
                    .orElseThrow(() -> new AssertionError(
                            "Expected exported function 'f'"));

            table.set(1, funcref);
            Object slot = table.get(1);
            assertNotNull(slot, "post-set slot must not be null");
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void growReturnsPreGrowSizeAndIncreasesSize() {
        try (Engine engine = WasmtimeEngineAdapter.create(null);
             Module module = engine.loadModule(TABLE_BOUNDED)) {
            Instance instance = module.instantiate();
            Table table = instance.table("t").orElseThrow();

            int preGrow = table.size();
            int returned = table.grow(2, null);
            assertEquals(preGrow, returned, "grow must return pre-grow size");
            assertEquals(preGrow + 2, table.size(),
                    "post-grow size must equal preGrow + delta");
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void growBeyondMaxReturnsMinusOne() {
        try (Engine engine = WasmtimeEngineAdapter.create(null);
             Module module = engine.loadModule(TABLE_MAX_ONE)) {
            Instance instance = module.instantiate();
            Table table = instance.table("t").orElseThrow();
            assertEquals(1, table.size());
            int returned = table.grow(5, null);
            assertEquals(-1, returned,
                    "grow past declared max must return -1");
            assertEquals(1, table.size(),
                    "failed grow must leave size unchanged");
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void getOutOfBoundsThrows() {
        try (Engine engine = WasmtimeEngineAdapter.create(null);
             Module module = engine.loadModule(TABLE_BOUNDED)) {
            Instance instance = module.instantiate();
            Table table = instance.table("t").orElseThrow();
            assertThrows(IndexOutOfBoundsException.class,
                    () -> table.get(table.size()));
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void setOutOfBoundsThrows() {
        try (Engine engine = WasmtimeEngineAdapter.create(null);
             Module module = engine.loadModule(TABLE_BOUNDED)) {
            Instance instance = module.instantiate();
            Table table = instance.table("t").orElseThrow();
            assertThrows(IndexOutOfBoundsException.class,
                    () -> table.set(table.size(), null));
        }
    }
}
