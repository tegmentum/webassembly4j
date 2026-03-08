package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.Module;
import ai.tegmentum.webassembly4j.api.WebAssembly;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.*;

class WebAssemblyBuilderIntegrationTest {

    static boolean runtimeAvailable() {
        return new WasmtimeProvider().availability().available();
    }

    // Same ADD_MODULE constant
    private static final byte[] ADD_MODULE = new byte[] {
        0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00,
        0x01, 0x07, 0x01, 0x60, 0x02, 0x7F, 0x7F, 0x01, 0x7F,
        0x03, 0x02, 0x01, 0x00,
        0x07, 0x07, 0x01, 0x03, 0x61, 0x64, 0x64, 0x00, 0x00,
        0x0A, 0x09, 0x01, 0x07, 0x00, 0x20, 0x00, 0x20, 0x01, 0x6A, 0x0B
    };

    @Test
    @EnabledIf("runtimeAvailable")
    void builderDiscoverAndExecute() {
        try (Engine engine = WebAssembly.builder().build()) {
            assertNotNull(engine);
            assertNotNull(engine.info());

            Module module = engine.loadModule(ADD_MODULE);
            Instance instance = module.instantiate();
            Function add = instance.function("add").orElseThrow();
            Object result = add.invoke(3, 4);
            assertEquals(7, result);
            module.close();
        }
    }

    @Test
    @EnabledIf("runtimeAvailable")
    void builderWithEngineSelection() {
        try (Engine engine = WebAssembly.builder().engine("wasmtime").build()) {
            assertEquals("wasmtime", engine.info().engineId());

            Module module = engine.loadModule(ADD_MODULE);
            Instance instance = module.instantiate();
            Function add = instance.function("add").orElseThrow();
            assertEquals(7, add.invoke(3, 4));
            module.close();
        }
    }
}
