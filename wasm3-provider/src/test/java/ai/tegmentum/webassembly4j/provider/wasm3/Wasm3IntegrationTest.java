package ai.tegmentum.webassembly4j.provider.wasm3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.tegmentum.webassembly4j.api.DefaultLinkingContext;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Global;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.Memory;
import ai.tegmentum.webassembly4j.api.Module;
import ai.tegmentum.webassembly4j.api.ValueType;
import ai.tegmentum.webassembly4j.api.exception.WebAssemblyException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/** End-to-end tests for the wasm3 provider, gated on the native runtime being available. */
class Wasm3IntegrationTest {

    static boolean runtimeAvailable() {
        return new Wasm3Provider().availability().available();
    }

    // (func (export "add") (param i32 i32) (result i32) (i32.add (local.get 0) (local.get 1)))
    private static final byte[] ADD_MODULE = {
        0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00,
        0x01, 0x07, 0x01, 0x60, 0x02, 0x7F, 0x7F, 0x01, 0x7F,
        0x03, 0x02, 0x01, 0x00,
        0x07, 0x07, 0x01, 0x03, 0x61, 0x64, 0x64, 0x00, 0x00,
        0x0A, 0x09, 0x01, 0x07, 0x00, 0x20, 0x00, 0x20, 0x01, 0x6A, 0x0B
    };

    // Exports memory, store(i32 addr, i32 val), load(i32 addr)->i32
    private static final byte[] MEMORY_MODULE = {
        0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00, 0x01, 0x0b, 0x02, 0x60,
        0x02, 0x7f, 0x7f, 0x00, 0x60, 0x01, 0x7f, 0x01, 0x7f, 0x03, 0x03, 0x02,
        0x00, 0x01, 0x05, 0x03, 0x01, 0x00, 0x01, 0x07, 0x19, 0x03, 0x06, 0x6d,
        0x65, 0x6d, 0x6f, 0x72, 0x79, 0x02, 0x00, 0x05, 0x73, 0x74, 0x6f, 0x72,
        0x65, 0x00, 0x00, 0x04, 0x6c, 0x6f, 0x61, 0x64, 0x00, 0x01, 0x0a, 0x13,
        0x02, 0x09, 0x00, 0x20, 0x00, 0x20, 0x01, 0x36, 0x02, 0x00, 0x0b, 0x07,
        0x00, 0x20, 0x00, 0x28, 0x02, 0x00, 0x0b
    };

    // add(i32,i32)->i32 plus two mutable i32 globals g_a=10, g_b=20
    private static final byte[] GLOBALS_MODULE = {
        0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00,
        0x01, 0x07, 0x01, 0x60, 0x02, 0x7F, 0x7F, 0x01, 0x7F,
        0x03, 0x02, 0x01, 0x00,
        0x06, 0x0B, 0x02,
        0x7F, 0x01, 0x41, 0x0A, 0x0B,
        0x7F, 0x01, 0x41, 0x14, 0x0B,
        0x07, 0x13, 0x03,
        0x03, 0x61, 0x64, 0x64, 0x00, 0x00,
        0x03, 0x67, 0x5F, 0x61, 0x03, 0x00,
        0x03, 0x67, 0x5F, 0x62, 0x03, 0x01,
        0x0A, 0x09, 0x01, 0x07, 0x00, 0x20, 0x00, 0x20, 0x01, 0x6A, 0x0B
    };

    // Imports env.add_offset (i32)->i32; exports call_host (i32)->i32 calling the import.
    private static final byte[] IMPORT_MODULE = {
        0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00, 0x01, 0x06, 0x01, 0x60,
        0x01, 0x7f, 0x01, 0x7f, 0x02, 0x12, 0x01, 0x03, 0x65, 0x6e, 0x76, 0x0a,
        0x61, 0x64, 0x64, 0x5f, 0x6f, 0x66, 0x66, 0x73, 0x65, 0x74, 0x00, 0x00,
        0x03, 0x02, 0x01, 0x00, 0x07, 0x0d, 0x01, 0x09, 0x63, 0x61, 0x6c, 0x6c,
        0x5f, 0x68, 0x6f, 0x73, 0x74, 0x00, 0x01, 0x0a, 0x08, 0x01, 0x06, 0x00,
        0x20, 0x00, 0x10, 0x00, 0x0b
    };

    private static final byte[] INVALID_MODULE = {0x00, 0x01, 0x02, 0x03};

    @Test
    @EnabledIf("runtimeAvailable")
    void addFunction() {
        try (Engine engine = new Wasm3Provider().create(null);
                Module module = engine.loadModule(ADD_MODULE)) {
            final Instance instance = module.instantiate();
            final Function add = instance.function("add").orElseThrow();
            assertEquals(7, add.invoke(3, 4));
            assertEquals(ValueType.I32, add.parameterTypes()[0]);
            assertEquals(ValueType.I32, add.resultTypes()[0]);
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void selectsAnEngine() {
        try (Engine engine = new Wasm3Provider().create(null)) {
            assertEquals("wasm3", engine.info().engineId());
            assertTrue(engine.capabilities().supportsCoreModules());
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void memoryReadWrite() {
        try (Engine engine = new Wasm3Provider().create(null);
                Module module = engine.loadModule(MEMORY_MODULE)) {
            final Instance instance = module.instantiate();
            instance.function("store").orElseThrow().invoke(0, 12345);
            final Memory memory = instance.memory("memory").orElseThrow();
            final byte[] bytes = memory.read(0, 4);
            final int value = (bytes[0] & 0xff) | ((bytes[1] & 0xff) << 8)
                    | ((bytes[2] & 0xff) << 16) | ((bytes[3] & 0xff) << 24);
            assertEquals(12345, value);
            assertEquals(12345, instance.function("load").orElseThrow().invoke(0));
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void globalGetSet() {
        try (Engine engine = new Wasm3Provider().create(null);
                Module module = engine.loadModule(GLOBALS_MODULE)) {
            final Instance instance = module.instantiate();
            final Global gA = instance.global("g_a").orElseThrow();
            assertEquals(10, gA.get());
            gA.set(99);
            assertEquals(99, gA.get());
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void hostFunctionLinking() {
        try (Engine engine = new Wasm3Provider().create(null);
                Module module = engine.loadModule(IMPORT_MODULE)) {
            final DefaultLinkingContext ctx = DefaultLinkingContext.builder()
                    .addHostFunction(
                            "env",
                            "add_offset",
                            new ValueType[] {ValueType.I32},
                            new ValueType[] {ValueType.I32},
                            args -> new Object[] {((Number) args[0]).intValue() + 100})
                    .build();
            final Instance instance = module.instantiate(ctx);
            assertEquals(142, instance.function("call_host").orElseThrow().invoke(42));
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void invalidModuleThrows() {
        try (Engine engine = new Wasm3Provider().create(null)) {
            assertThrows(WebAssemblyException.class, () -> engine.loadModule(INVALID_MODULE));
        }
    }
}
