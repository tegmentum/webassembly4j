package ai.tegmentum.webassembly4j.runtime.component;

/**
 * Detects whether a WASM binary is a core module or a component.
 * <p>
 * Both use the {@code \0asm} magic prefix. Core modules have version bytes
 * {@code 01 00 00 00} at offset 4. Components have a layer byte of {@code 0x0d}
 * at offset 4 per the component model binary format.
 */
public final class WasmBinaryInfo {

    private WasmBinaryInfo() {}

    public static boolean isComponent(byte[] bytes) {
        return bytes != null && bytes.length >= 8
                && bytes[0] == 0x00 && bytes[1] == 0x61
                && bytes[2] == 0x73 && bytes[3] == 0x6d
                && (bytes[4] & 0xFF) == 0x0d;
    }

    public static boolean isCoreModule(byte[] bytes) {
        return bytes != null && bytes.length >= 8
                && bytes[0] == 0x00 && bytes[1] == 0x61
                && bytes[2] == 0x73 && bytes[3] == 0x6d
                && bytes[4] == 0x01 && bytes[5] == 0x00
                && bytes[6] == 0x00 && bytes[7] == 0x00;
    }
}
