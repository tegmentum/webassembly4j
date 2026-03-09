package ai.tegmentum.webassembly4j.runtime;

import ai.tegmentum.webassembly4j.runtime.annotation.WasmExport;
import ai.tegmentum.webassembly4j.runtime.annotation.WasmImport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WasmRuntimeTest {

    // (func (export "add") (param i32 i32) (result i32) local.get 0 local.get 1 i32.add)
    private static final byte[] ADD_MODULE = new byte[] {
        0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00,
        0x01, 0x07, 0x01, 0x60, 0x02, 0x7F, 0x7F, 0x01, 0x7F,
        0x03, 0x02, 0x01, 0x00,
        0x07, 0x07, 0x01, 0x03, 0x61, 0x64, 0x64, 0x00, 0x00,
        0x0A, 0x09, 0x01, 0x07, 0x00, 0x20, 0x00, 0x20, 0x01, 0x6A, 0x0B
    };

    // (func (export "noop"))
    private static final byte[] VOID_MODULE = new byte[] {
        0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00,
        0x01, 0x04, 0x01, 0x60, 0x00, 0x00,
        0x03, 0x02, 0x01, 0x00,
        0x07, 0x08, 0x01, 0x04, 0x6E, 0x6F, 0x6F, 0x70, 0x00, 0x00,
        0x0A, 0x04, 0x01, 0x02, 0x00, 0x0B
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

    // Interface for proxy binding
    interface Calculator extends AutoCloseable {
        int add(int a, int b);
    }

    // Interface with annotation
    interface AnnotatedCalculator extends AutoCloseable {
        @WasmExport("add")
        int sum(int a, int b);
    }

    // Interface with void method
    interface VoidModule extends AutoCloseable {
        void noop();
    }

    // Interface for host function import
    interface HostCaller extends AutoCloseable {
        @WasmExport("call_host")
        int callHost(int value);
    }

    // Interface with nonexistent export
    interface BadInterface extends AutoCloseable {
        int nonexistent(int x);
    }

    // Host object providing add_offset import
    static class HostFunctions {
        @WasmImport(module = "env", name = "add_offset")
        public int addOffset(int value) {
            return value + 100;
        }
    }

    @Test
    void loadAndBindInterface() {
        try (Calculator calc = WasmRuntime.load(Calculator.class, ADD_MODULE)) {
            assertEquals(7, calc.add(3, 4));
            assertEquals(0, calc.add(0, 0));
            assertEquals(-1, calc.add(1, -2));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void loadWithAnnotatedExport() {
        try (AnnotatedCalculator calc = WasmRuntime.load(AnnotatedCalculator.class, ADD_MODULE)) {
            assertEquals(7, calc.sum(3, 4));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void loadVoidFunction() {
        try (VoidModule mod = WasmRuntime.load(VoidModule.class, VOID_MODULE)) {
            mod.noop(); // should not throw
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void oneShotCall() {
        int result = WasmRuntime.call(ADD_MODULE, "add", Integer.class, 10, 20);
        assertEquals(30, result);
    }

    @Test
    void oneShotCallVoid() {
        WasmRuntime.callVoid(VOID_MODULE, "noop");
    }

    @Test
    void oneShotCallNonexistentFunction() {
        assertThrows(IllegalArgumentException.class,
                () -> WasmRuntime.call(ADD_MODULE, "nonexistent", Integer.class));
    }

    @Test
    void compileAndReuse() {
        try (WasmModule module = WasmRuntime.compile(ADD_MODULE)) {
            assertEquals(7, module.call("add", Integer.class, 3, 4));
            assertEquals(15, module.call("add", Integer.class, 7, 8));
        }
    }

    @Test
    void compileAndBind() {
        try (WasmModule module = WasmRuntime.compile(ADD_MODULE)) {
            try (Calculator calc = module.bind(Calculator.class)) {
                assertEquals(7, calc.add(3, 4));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    void wasmModuleCallVoid() {
        try (WasmModule module = WasmRuntime.compile(VOID_MODULE)) {
            module.callVoid("noop");
        }
    }

    @Test
    void wasmModuleNewInstance() {
        try (WasmModule module = WasmRuntime.compile(ADD_MODULE)) {
            try (WasmInstance instance = module.newInstance()) {
                assertEquals(7, instance.call("add", Integer.class, 3, 4));
            }
        }
    }

    @Test
    void wasmInstanceCallVoid() {
        try (WasmModule module = WasmRuntime.compile(VOID_MODULE)) {
            try (WasmInstance instance = module.newInstance()) {
                instance.callVoid("noop");
            }
        }
    }

    @Test
    void wasmInstanceBind() {
        try (WasmModule module = WasmRuntime.compile(ADD_MODULE)) {
            try (WasmInstance instance = module.newInstance()) {
                try (Calculator calc = instance.bind(Calculator.class)) {
                    assertEquals(7, calc.add(3, 4));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Test
    void closedModuleThrows() {
        WasmModule module = WasmRuntime.compile(ADD_MODULE);
        module.close();
        assertThrows(IllegalStateException.class,
                () -> module.call("add", Integer.class, 3, 4));
    }

    @Test
    void loadWithHostFunctions() {
        HostFunctions host = new HostFunctions();
        try (HostCaller caller = WasmRuntime.load(HostCaller.class, IMPORT_MODULE, host)) {
            assertEquals(142, caller.callHost(42));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void builderWithEngineSelection() {
        int result = WasmRuntime.builder()
                .engine("chicory")
                .call(ADD_MODULE, "add", Integer.class, 3, 4);
        assertEquals(7, result);
    }

    @Test
    void builderLoadInterface() {
        try (Calculator calc = WasmRuntime.builder()
                .engine("chicory")
                .load(Calculator.class, ADD_MODULE)) {
            assertEquals(7, calc.add(3, 4));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void builderWithHostObjects() {
        HostFunctions host = new HostFunctions();
        try (HostCaller caller = WasmRuntime.builder()
                .hostObjects(host)
                .load(HostCaller.class, IMPORT_MODULE)) {
            assertEquals(142, caller.callHost(42));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void builderCompile() {
        try (WasmModule module = WasmRuntime.builder()
                .engine("chicory")
                .compile(ADD_MODULE)) {
            assertEquals(7, module.call("add", Integer.class, 3, 4));
        }
    }

    @Test
    void proxyToStringReturnsDescriptive() {
        try (Calculator calc = WasmRuntime.load(Calculator.class, ADD_MODULE)) {
            assertTrue(calc.toString().contains("Calculator"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void interfaceMissingExportThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> WasmRuntime.load(BadInterface.class, ADD_MODULE));
    }
}
