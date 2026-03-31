package ai.tegmentum.webassembly4j.runtime.component;

import java.util.Arrays;

/**
 * Extracts the first core WASM module from a component model binary.
 * <p>
 * The component binary format ({@code \0asm\x0d\x00\x01\x00}) contains sections,
 * each with a 1-byte ID and LEB128-encoded size. Section ID 1 contains an
 * embedded core WASM module as a complete binary.
 * <p>
 * This is a pure Java implementation — no external tools required.
 */
public final class ComponentLowerer {

    /** Component binary format section ID for an embedded core module. */
    private static final int SECTION_CORE_MODULE = 1;

    /** Size of the component binary header: \0asm + 4 version bytes. */
    private static final int HEADER_SIZE = 8;

    private ComponentLowerer() {}

    /**
     * Extracts the first core module from a component binary.
     *
     * @param componentBytes the component model WASM binary
     * @return the core module bytes
     * @throws IllegalArgumentException if the bytes are not a valid component
     *         or contain no core module
     */
    public static byte[] lower(byte[] componentBytes) {
        if (!WasmBinaryInfo.isComponent(componentBytes)) {
            throw new IllegalArgumentException("Not a component model binary");
        }

        int offset = HEADER_SIZE;

        while (offset < componentBytes.length) {
            if (offset >= componentBytes.length) {
                break;
            }

            int sectionId = componentBytes[offset] & 0xFF;
            offset++;

            long[] sizeResult = readLeb128(componentBytes, offset);
            int sectionSize = (int) sizeResult[0];
            offset = (int) sizeResult[1];

            if (sectionId == SECTION_CORE_MODULE) {
                // The section payload is a complete core WASM module binary
                return Arrays.copyOfRange(componentBytes, offset, offset + sectionSize);
            }

            offset += sectionSize;
        }

        throw new IllegalArgumentException(
                "Component binary contains no embedded core module");
    }

    /**
     * Reads an unsigned LEB128 value from the byte array.
     *
     * @return a two-element array: [value, newOffset]
     */
    private static long[] readLeb128(byte[] bytes, int offset) {
        long result = 0;
        int shift = 0;
        int pos = offset;

        while (pos < bytes.length) {
            int b = bytes[pos] & 0xFF;
            pos++;
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return new long[]{result, pos};
            }
            shift += 7;
            if (shift >= 64) {
                throw new IllegalArgumentException("LEB128 value too large at offset " + offset);
            }
        }
        throw new IllegalArgumentException("Truncated LEB128 at offset " + offset);
    }
}
