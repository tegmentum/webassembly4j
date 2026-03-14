package ai.tegmentum.webassembly4j.provider.wamr;

import ai.tegmentum.webassembly4j.api.DefaultLinkingContext;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.Memory;
import ai.tegmentum.webassembly4j.api.Module;
import ai.tegmentum.webassembly4j.api.ValueType;
import ai.tegmentum.webassembly4j.api.exception.WebAssemblyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WamrIntegrationTest {

    static boolean runtimeAvailable() {
        return new WamrProvider().availability().available();
    }

    private static final byte[] ADD_MODULE = new byte[] {
        0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00,
        0x01, 0x07, 0x01, 0x60, 0x02, 0x7F, 0x7F, 0x01, 0x7F,
        0x03, 0x02, 0x01, 0x00,
        0x07, 0x07, 0x01, 0x03, 0x61, 0x64, 0x64, 0x00, 0x00,
        0x0A, 0x09, 0x01, 0x07, 0x00, 0x20, 0x00, 0x20, 0x01, 0x6A, 0x0B
    };

    private static final byte[] VOID_MODULE = new byte[] {
        0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00,
        0x01, 0x04, 0x01, 0x60, 0x00, 0x00,
        0x03, 0x02, 0x01, 0x00,
        0x07, 0x08, 0x01, 0x04, 0x6E, 0x6F, 0x6F, 0x70, 0x00, 0x00,
        0x0A, 0x04, 0x01, 0x02, 0x00, 0x0B
    };

    private static final byte[] MEMORY_MODULE = new byte[] {
        0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00, 0x01, 0x0b, 0x02, 0x60,
        0x02, 0x7f, 0x7f, 0x00, 0x60, 0x01, 0x7f, 0x01, 0x7f, 0x03, 0x03, 0x02,
        0x00, 0x01, 0x05, 0x03, 0x01, 0x00, 0x01, 0x07, 0x19, 0x03, 0x06, 0x6d,
        0x65, 0x6d, 0x6f, 0x72, 0x79, 0x02, 0x00, 0x05, 0x73, 0x74, 0x6f, 0x72,
        0x65, 0x00, 0x00, 0x04, 0x6c, 0x6f, 0x61, 0x64, 0x00, 0x01, 0x0a, 0x13,
        0x02, 0x09, 0x00, 0x20, 0x00, 0x20, 0x01, 0x36, 0x02, 0x00, 0x0b, 0x07,
        0x00, 0x20, 0x00, 0x28, 0x02, 0x00, 0x0b
    };

    // Module importing env.add_offset (i32)->i32, exporting call_host (i32)->i32
    private static final byte[] IMPORT_MODULE = new byte[] {
        0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00, 0x01, 0x06, 0x01, 0x60,
        0x01, 0x7f, 0x01, 0x7f, 0x02, 0x12, 0x01, 0x03, 0x65, 0x6e, 0x76, 0x0a,
        0x61, 0x64, 0x64, 0x5f, 0x6f, 0x66, 0x66, 0x73, 0x65, 0x74, 0x00, 0x00,
        0x03, 0x02, 0x01, 0x00, 0x07, 0x0d, 0x01, 0x09, 0x63, 0x61, 0x6c, 0x6c,
        0x5f, 0x68, 0x6f, 0x73, 0x74, 0x00, 0x01, 0x0a, 0x08, 0x01, 0x06, 0x00,
        0x20, 0x00, 0x10, 0x00, 0x0b
    };

    // Module with add(i32,i32)->i32 + two mutable i32 globals (g_a=10, g_b=20)
    // WAT: (module
    //   (func (export "add") (param i32 i32) (result i32) (i32.add (local.get 0) (local.get 1)))
    //   (global (export "g_a") (mut i32) (i32.const 10))
    //   (global (export "g_b") (mut i32) (i32.const 20))
    // )
    private static final byte[] GLOBALS_MODULE = new byte[] {
        0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00, // header
        // Type section: 1 type (i32, i32) -> i32
        0x01, 0x07, 0x01, 0x60, 0x02, 0x7F, 0x7F, 0x01, 0x7F,
        // Function section: 1 function of type 0
        0x03, 0x02, 0x01, 0x00,
        // Global section: 2 mutable i32 globals
        0x06, 0x0B, 0x02,
        0x7F, 0x01, 0x41, 0x0A, 0x0B, // mut i32, i32.const 10, end
        0x7F, 0x01, 0x41, 0x14, 0x0B, // mut i32, i32.const 20, end
        // Export section: 3 exports (add, g_a, g_b)
        0x07, 0x13, 0x03,
        0x03, 0x61, 0x64, 0x64, 0x00, 0x00,       // "add" func 0
        0x03, 0x67, 0x5F, 0x61, 0x03, 0x00,       // "g_a" global 0
        0x03, 0x67, 0x5F, 0x62, 0x03, 0x01,       // "g_b" global 1
        // Code section: 1 function body
        0x0A, 0x09, 0x01, 0x07, 0x00, 0x20, 0x00, 0x20, 0x01, 0x6A, 0x0B
    };

    private static final byte[] INVALID_MODULE = new byte[] { 0x00, 0x01, 0x02, 0x03 };

    @Test
    @EnabledIf("runtimeAvailable")
    void addFunction() {
        try (Engine engine = WamrEngineAdapter.create(null)) {
            try (Module module = engine.loadModule(ADD_MODULE)) {
                Instance instance = module.instantiate();
                Function add = instance.function("add").orElseThrow();
                Object result = add.invoke(3, 4);
                assertEquals(7, result);
            }
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void voidFunction() {
        try (Engine engine = WamrEngineAdapter.create(null)) {
            try (Module module = engine.loadModule(VOID_MODULE)) {
                Instance instance = module.instantiate();
                Function noop = instance.function("noop").orElseThrow();
                Object result = noop.invoke();
                assertNull(result);
            }
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void invalidModuleThrows() {
        try (Engine engine = WamrEngineAdapter.create(null)) {
            assertThrows(WebAssemblyException.class, () -> engine.loadModule(INVALID_MODULE));
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void nonexistentFunctionReturnsEmpty() {
        try (Engine engine = WamrEngineAdapter.create(null)) {
            try (Module module = engine.loadModule(ADD_MODULE)) {
                Instance instance = module.instantiate();
                assertFalse(instance.function("nonexistent").isPresent());
            }
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void memoryReadWrite() {
        try (Engine engine = WamrEngineAdapter.create(null)) {
            try (Module module = engine.loadModule(MEMORY_MODULE)) {
                Instance instance = module.instantiate();
                Function store = instance.function("store").orElseThrow();
                Function load = instance.function("load").orElseThrow();
                store.invoke(0, 42);
                Object result = load.invoke(0);
                assertEquals(42, result);
            }
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void memoryDirectAccess() {
        try (Engine engine = WamrEngineAdapter.create(null)) {
            try (Module module = engine.loadModule(MEMORY_MODULE)) {
                Instance instance = module.instantiate();
                Memory memory = instance.memory("memory").orElseThrow();
                byte[] data = new byte[] { 1, 2, 3, 4 };
                memory.write(0, data);
                byte[] read = memory.read(0, 4);
                assertArrayEquals(data, read);
            }
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void batchGetGlobals() {
        try (Engine engine = WamrEngineAdapter.create(null)) {
            try (Module module = engine.loadModule(GLOBALS_MODULE)) {
                Instance instance = module.instantiate();

                Map<String, Object> globals = instance.getGlobals("g_a", "g_b");

                assertEquals(2, globals.size());
                assertEquals(10, globals.get("g_a"));
                assertEquals(20, globals.get("g_b"));

                // Verify ordering matches input
                String[] keys = globals.keySet().toArray(new String[0]);
                assertArrayEquals(new String[]{"g_a", "g_b"}, keys);
            }
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void batchSetGlobals() {
        try (Engine engine = WamrEngineAdapter.create(null)) {
            try (Module module = engine.loadModule(GLOBALS_MODULE)) {
                Instance instance = module.instantiate();

                Map<String, Object> newValues = new LinkedHashMap<>();
                newValues.put("g_a", 99);
                newValues.put("g_b", 88);
                instance.setGlobals(newValues);

                // Verify via individual reads
                assertEquals(99, instance.global("g_a").orElseThrow().get());
                assertEquals(88, instance.global("g_b").orElseThrow().get());
            }
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void batchGetGlobalsNonExistentThrows() {
        try (Engine engine = WamrEngineAdapter.create(null)) {
            try (Module module = engine.loadModule(GLOBALS_MODULE)) {
                Instance instance = module.instantiate();

                assertThrows(Exception.class,
                    () -> instance.getGlobals("g_a", "nonexistent"));
            }
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void batchGetGlobalsEmpty() {
        try (Engine engine = WamrEngineAdapter.create(null)) {
            try (Module module = engine.loadModule(GLOBALS_MODULE)) {
                Instance instance = module.instantiate();
                Map<String, Object> result = instance.getGlobals();
                assertNotNull(result);
                assertTrue(result.isEmpty());
            }
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void invokeMultipleBasic() {
        try (Engine engine = WamrEngineAdapter.create(null)) {
            try (Module module = engine.loadModule(GLOBALS_MODULE)) {
                Instance instance = module.instantiate();
                Function add = instance.function("add").orElseThrow();

                Object[] results = add.invokeMultiple(
                    new Object[]{1, 2},
                    new Object[]{10, 20},
                    new Object[]{100, 200}
                );

                assertEquals(3, results.length);
                assertEquals(3, results[0]);
                assertEquals(30, results[1]);
                assertEquals(300, results[2]);
            }
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void invokeMultipleEmpty() {
        try (Engine engine = WamrEngineAdapter.create(null)) {
            try (Module module = engine.loadModule(GLOBALS_MODULE)) {
                Instance instance = module.instantiate();
                Function add = instance.function("add").orElseThrow();

                Object[] results = add.invokeMultiple();
                assertNotNull(results);
                assertEquals(0, results.length);
            }
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void hostFunctionLinking() {
        try (Engine engine = WamrEngineAdapter.create(null)) {
            try (Module module = engine.loadModule(IMPORT_MODULE)) {
                DefaultLinkingContext ctx = DefaultLinkingContext.builder()
                        .addHostFunction("env", "add_offset",
                                new ValueType[] { ValueType.I32 },
                                new ValueType[] { ValueType.I32 },
                                args -> new Object[] { ((Number) args[0]).intValue() + 100 })
                        .build();

                Instance instance = module.instantiate(ctx);
                Function callHost = instance.function("call_host").orElseThrow();
                assertEquals(142, callHost.invoke(42));
            }
        }
    }
}
