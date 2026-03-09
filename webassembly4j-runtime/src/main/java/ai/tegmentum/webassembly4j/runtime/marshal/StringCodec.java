package ai.tegmentum.webassembly4j.runtime.marshal;

import ai.tegmentum.webassembly4j.api.Memory;

import java.nio.charset.StandardCharsets;

/**
 * Encodes and decodes strings to/from WebAssembly linear memory using UTF-8.
 *
 * <p>Follows the Component Model Canonical ABI convention where strings are represented
 * as a (pointer, byte_length) pair in linear memory.
 */
public final class StringCodec {

    private StringCodec() {
    }

    /**
     * Encodes a string into WebAssembly linear memory.
     *
     * @param value the string to encode
     * @param memory the linear memory to write to
     * @param allocator the allocator to use for the string data
     * @return a two-element array containing [pointer, byte_length]
     */
    public static int[] encode(String value, Memory memory, MemoryAllocator allocator) {
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        int ptr = allocator.allocate(utf8.length, 1);
        memory.write(ptr, utf8);
        return new int[]{ptr, utf8.length};
    }

    /**
     * Decodes a string from WebAssembly linear memory.
     *
     * @param memory the linear memory to read from
     * @param ptr the pointer to the start of the string data
     * @param byteLen the length of the string data in bytes
     * @return the decoded string
     */
    public static String decode(Memory memory, int ptr, int byteLen) {
        byte[] utf8 = memory.read(ptr, byteLen);
        return new String(utf8, StandardCharsets.UTF_8);
    }
}
