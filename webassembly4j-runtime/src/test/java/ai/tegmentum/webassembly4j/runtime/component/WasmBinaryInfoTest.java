package ai.tegmentum.webassembly4j.runtime.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WasmBinaryInfoTest {

    // Core module: \0asm\1\0\0\0
    private static final byte[] CORE_MODULE = new byte[] {
            0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00
    };

    // Component: \0asm\r\0\1\0 (layer 0x0d)
    private static final byte[] COMPONENT = new byte[] {
            0x00, 0x61, 0x73, 0x6d, 0x0d, 0x00, 0x01, 0x00
    };

    @Test
    void detectCoreModule() {
        assertTrue(WasmBinaryInfo.isCoreModule(CORE_MODULE));
        assertFalse(WasmBinaryInfo.isComponent(CORE_MODULE));
    }

    @Test
    void detectComponent() {
        assertTrue(WasmBinaryInfo.isComponent(COMPONENT));
        assertFalse(WasmBinaryInfo.isCoreModule(COMPONENT));
    }

    @Test
    void nullAndShortBytes() {
        assertFalse(WasmBinaryInfo.isComponent(null));
        assertFalse(WasmBinaryInfo.isCoreModule(null));
        assertFalse(WasmBinaryInfo.isComponent(new byte[4]));
        assertFalse(WasmBinaryInfo.isCoreModule(new byte[4]));
    }

    @Test
    void garbageBytes() {
        assertFalse(WasmBinaryInfo.isComponent(new byte[] {1, 2, 3, 4, 5, 6, 7, 8}));
        assertFalse(WasmBinaryInfo.isCoreModule(new byte[] {1, 2, 3, 4, 5, 6, 7, 8}));
    }

    @Test
    void coreModuleWithExtendedBytes() {
        // Core module header followed by type section
        byte[] extended = new byte[] {
                0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00,
                0x01, 0x07, 0x01, 0x60, 0x02, 0x7F, 0x7F, 0x01, 0x7F
        };
        assertTrue(WasmBinaryInfo.isCoreModule(extended));
        assertFalse(WasmBinaryInfo.isComponent(extended));
    }
}
