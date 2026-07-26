package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.wasmtime4j.WasmValue;
import ai.tegmentum.wasmtime4j.WasmValueType;
import ai.tegmentum.webassembly4j.api.DefaultLinkingContext;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.ExternImportDefinition;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.FunctionImport;
import ai.tegmentum.webassembly4j.api.Global;
import ai.tegmentum.webassembly4j.api.GlobalImport;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.LinkingContext;
import ai.tegmentum.webassembly4j.api.Memory;
import ai.tegmentum.webassembly4j.api.MemoryImport;
import ai.tegmentum.webassembly4j.api.Module;
import ai.tegmentum.webassembly4j.api.Table;
import ai.tegmentum.webassembly4j.api.TableImport;
import ai.tegmentum.webassembly4j.api.ValueType;
import ai.tegmentum.webassembly4j.api.exception.LinkingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link LinkingContext#externImports()} wiring on the wasmtime
 * provider. Covers each variant declared by the api layer:
 * {@link MemoryImport}, {@link TableImport}, {@link GlobalImport}, and the
 * intentionally-unsupported {@link FunctionImport}.
 */
class WasmtimeLinkingContextExternImportsTest {

    // (module
    //   (import "env" "memory" (memory 1))
    //   (func (export "store_i32") (param i32 i32) local.get 0 local.get 1 i32.store)
    //   (func (export "load_i32")  (param i32) (result i32) local.get 0 i32.load))
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

    // (module
    //   (type $i32_ret (func (result i32)))
    //   (import "env" "table" (table 4 funcref))
    //   (func (export "call_slot") (param i32) (result i32)
    //     (call_indirect (type $i32_ret) (local.get 0))))
    private static final byte[] IMPORT_TABLE_MODULE = new byte[] {
        0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00, 0x01, 0x0a, 0x02, 0x60,
        0x00, 0x01, 0x7f, 0x60, 0x01, 0x7f, 0x01, 0x7f, 0x02, 0x0f, 0x01, 0x03,
        0x65, 0x6e, 0x76, 0x05, 0x74, 0x61, 0x62, 0x6c, 0x65, 0x01, 0x70, 0x00,
        0x04, 0x03, 0x02, 0x01, 0x01, 0x07, 0x0d, 0x01, 0x09, 0x63, 0x61, 0x6c,
        0x6c, 0x5f, 0x73, 0x6c, 0x6f, 0x74, 0x00, 0x00, 0x0a, 0x09, 0x01, 0x07,
        0x00, 0x20, 0x00, 0x11, 0x00, 0x00, 0x0b
    };

    // (module
    //   (import "env" "counter" (global $g (mut i32)))
    //   (func (export "read") (result i32) global.get $g)
    //   (func (export "bump")
    //     global.get $g i32.const 1 i32.add global.set $g))
    //
    // bump is 0-arg to sidestep a pre-existing i32-in/void-out fast-path
    // shortcut in WasmtimeFunctionAdapter (out of scope for this charter).
    private static final byte[] IMPORT_GLOBAL_MODULE = new byte[] {
        0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x02, 0x60,
        0x00, 0x01, 0x7f, 0x60, 0x00, 0x00, 0x02, 0x10, 0x01, 0x03, 0x65, 0x6e,
        0x76, 0x07, 0x63, 0x6f, 0x75, 0x6e, 0x74, 0x65, 0x72, 0x03, 0x7f, 0x01,
        0x03, 0x03, 0x02, 0x00, 0x01, 0x07, 0x0f, 0x02, 0x04, 0x72, 0x65, 0x61,
        0x64, 0x00, 0x00, 0x04, 0x62, 0x75, 0x6d, 0x70, 0x00, 0x01, 0x0a, 0x10,
        0x02, 0x04, 0x00, 0x23, 0x00, 0x0b, 0x09, 0x00, 0x23, 0x00, 0x41, 0x01,
        0x6a, 0x24, 0x00, 0x0b
    };

    static boolean runtimeAvailable() {
        return new WasmtimeProvider().availability().available();
    }

    // ---------- api-layer builder + accessor -----------------------------------------

    @Test
    void builderCollectsAllFourVariants() {
        Memory memory = new StubMemory();
        Table table = new StubTable();
        Global global = new StubGlobal();
        Function function = new StubFunction();

        DefaultLinkingContext ctx = DefaultLinkingContext.builder()
                .addMemoryImport("env", "memory", memory)
                .addTableImport("env", "table", table)
                .addGlobalImport("env", "counter", global)
                .addFunctionImport("env", "log", function)
                .build();

        List<ExternImportDefinition> imports = ctx.externImports();
        assertEquals(4, imports.size(), "all four typed variants land in externImports()");
        assertTrue(imports.get(0) instanceof MemoryImport);
        assertTrue(imports.get(1) instanceof TableImport);
        assertTrue(imports.get(2) instanceof GlobalImport);
        assertTrue(imports.get(3) instanceof FunctionImport);

        MemoryImport mi = (MemoryImport) imports.get(0);
        assertEquals("env", mi.moduleName());
        assertEquals("memory", mi.name());
        assertSame(memory, mi.memory());
    }

    @Test
    void builderAcceptsPreBuiltExternImport() {
        MemoryImport pre = new MemoryImport("env", "memory", new StubMemory());
        DefaultLinkingContext ctx = DefaultLinkingContext.builder()
                .addExternImport(pre)
                .build();
        assertEquals(1, ctx.externImports().size());
        assertSame(pre, ctx.externImports().get(0));
    }

    @Test
    void defaultLinkingContextReturnsEmptyExternImportsByDefault() {
        // Anonymous impl reveals the interface default is the empty list.
        LinkingContext ctx = new LinkingContext() { };
        assertEquals(0, ctx.externImports().size());
    }

    // ---------- provider wiring: end-to-end shared imports on same store -------------

    @Test
    @EnabledIf("runtimeAvailable")
    void memoryImportSharedOnSameStore() {
        try (Engine engine = WasmtimeEngineAdapter.create(null);
             Module module = engine.loadModule(IMPORT_MEMORY_MODULE)) {

            // Manufacture a WasmMemory bound to the module's own native store so
            // wasmtime accepts it as a valid import at instantiate time.
            ai.tegmentum.wasmtime4j.Store nativeStore = ((WasmtimeModuleAdapter) module).store();
            ai.tegmentum.wasmtime4j.WasmMemory nativeMemory;
            try {
                nativeMemory = nativeStore.createMemory(1, 1);
            } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
                throw new AssertionError("createMemory failed", e);
            }
            Memory sharedMemory = new WasmtimeMemoryAdapter(nativeMemory);

            LinkingContext ctx = DefaultLinkingContext.builder()
                    .addMemoryImport("env", "memory", sharedMemory)
                    .build();

            Instance instance = module.instantiate(ctx);
            Function storeFn = instance.function("store_i32").orElseThrow();
            Function loadFn = instance.function("load_i32").orElseThrow();

            storeFn.invoke(0, 0xdeadbeef);
            assertEquals(0xdeadbeef, loadFn.invoke(0));

            // Host-side write is visible to the guest through the same memory.
            sharedMemory.write(4, new byte[] { 0x11, 0x22, 0x33, 0x44 });
            assertEquals(0x44332211, loadFn.invoke(4));
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void tableImportSharedOnSameStore() {
        try (Engine engine = WasmtimeEngineAdapter.create(null);
             Module module = engine.loadModule(IMPORT_TABLE_MODULE)) {

            ai.tegmentum.wasmtime4j.Store nativeStore = ((WasmtimeModuleAdapter) module).store();
            ai.tegmentum.wasmtime4j.WasmTable nativeTable;
            try {
                nativeTable = nativeStore.createTable(WasmValueType.FUNCREF, 4, 4);
            } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
                throw new AssertionError("createTable failed", e);
            }
            Table sharedTable = new WasmtimeTableAdapter(nativeTable);

            LinkingContext ctx = DefaultLinkingContext.builder()
                    .addTableImport("env", "table", sharedTable)
                    .build();

            Instance instance = module.instantiate(ctx);
            // The instantiate itself is the observable success signal; the
            // imported table is empty (null funcrefs) so calling call_slot
            // would trap. Assert the table handle was actually wired by
            // checking size flows through the api-layer adapter.
            assertNotNull(instance);
            assertEquals(4, sharedTable.size());
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void globalImportSharedOnSameStore() {
        try (Engine engine = WasmtimeEngineAdapter.create(null);
             Module module = engine.loadModule(IMPORT_GLOBAL_MODULE)) {

            ai.tegmentum.wasmtime4j.Store nativeStore = ((WasmtimeModuleAdapter) module).store();
            ai.tegmentum.wasmtime4j.WasmGlobal nativeGlobal;
            try {
                nativeGlobal = nativeStore.createMutableGlobal(WasmValueType.I32, WasmValue.i32(41));
            } catch (ai.tegmentum.wasmtime4j.exception.WasmException e) {
                throw new AssertionError("createMutableGlobal failed", e);
            }
            Global sharedGlobal = new WasmtimeGlobalAdapter(nativeGlobal);

            LinkingContext ctx = DefaultLinkingContext.builder()
                    .addGlobalImport("env", "counter", sharedGlobal)
                    .build();

            Instance instance = module.instantiate(ctx);
            Function read = instance.function("read").orElseThrow();
            Function bump = instance.function("bump").orElseThrow();

            assertEquals(41, read.invoke());
            bump.invoke();
            assertEquals(42, read.invoke());

            // Guest-side mutation is visible on the host global handle.
            assertEquals(42, sharedGlobal.get());

            // Host-side mutation is visible in the guest.
            sharedGlobal.set(100);
            assertEquals(100, read.invoke());
        }
    }

    // ---------- provider wiring: error paths ------------------------------------------

    @Test
    @EnabledIf("runtimeAvailable")
    void functionImportRaisesUnsupportedOperation() {
        try (Engine engine = WasmtimeEngineAdapter.create(null);
             Module module = engine.loadModule(IMPORT_MEMORY_MODULE)) {

            LinkingContext ctx = DefaultLinkingContext.builder()
                    .addFunctionImport("env", "log", new StubFunction())
                    .build();

            UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class,
                    () -> module.instantiate(ctx));
            assertTrue(e.getMessage().contains("FunctionImport"),
                    "message identifies the variant");
            assertTrue(e.getMessage().contains("env"),
                    "message includes the moduleName");
            assertTrue(e.getMessage().contains("log"),
                    "message includes the import name");
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void memoryImportFromWrongProviderRaisesLinkingException() {
        try (Engine engine = WasmtimeEngineAdapter.create(null);
             Module module = engine.loadModule(IMPORT_MEMORY_MODULE)) {

            LinkingContext ctx = DefaultLinkingContext.builder()
                    .addMemoryImport("env", "memory", new StubMemory())
                    .build();

            LinkingException e = assertThrows(LinkingException.class,
                    () -> module.instantiate(ctx));
            assertTrue(e.getMessage().contains("env"));
            assertTrue(e.getMessage().contains("memory"));
            assertTrue(e.getMessage().contains("wasmtime4j-backed"));
        }
    }

    // ---------- stubs for non-native cases -------------------------------------------

    private static final class StubMemory implements Memory {
        @Override public long byteSize() { return 0; }
        @Override public java.nio.ByteBuffer asByteBuffer() {
            return java.nio.ByteBuffer.allocate(0);
        }
        @Override public void write(long offset, byte[] bytes) { }
        @Override public byte[] read(long offset, int length) { return new byte[length]; }
        @Override public <T> Optional<T> unwrap(Class<T> nativeType) { return Optional.empty(); }
    }

    private static final class StubTable implements Table {
        @Override public int size() { return 0; }
        @Override public <T> Optional<T> unwrap(Class<T> nativeType) { return Optional.empty(); }
    }

    private static final class StubGlobal implements Global {
        @Override public ValueType type() { return ValueType.I32; }
        @Override public Object get() { return 0; }
        @Override public void set(Object value) { }
        @Override public boolean mutable() { return false; }
        @Override public <T> Optional<T> unwrap(Class<T> nativeType) { return Optional.empty(); }
    }

    private static final class StubFunction implements Function {
        @Override public ValueType[] parameterTypes() { return new ValueType[0]; }
        @Override public ValueType[] resultTypes() { return new ValueType[0]; }
        @Override public Object invoke(Object... args) { return null; }
    }
}
