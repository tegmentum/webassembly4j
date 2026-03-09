package ai.tegmentum.webassembly4j.runtime;

import ai.tegmentum.webassembly4j.runtime.annotation.WasmExport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for String and byte[] marshalling through the proxy path.
 *
 * <p>These tests use a hand-crafted WASM module that exports:
 * <ul>
 *   <li>{@code memory} — 1 page of linear memory</li>
 *   <li>{@code cabi_realloc(old_ptr, old_size, align, new_size) -> new_ptr} — bump allocator</li>
 *   <li>{@code echo_string(retptr, ptr, len)} — copies string and writes (ptr, len) to retptr</li>
 *   <li>{@code echo_bytes(retptr, ptr, len)} — copies bytes and writes (ptr, len) to retptr</li>
 *   <li>{@code string_length(ptr, len) -> i32} — returns the byte length of the string</li>
 * </ul>
 */
class WasmRuntimeMarshalTest {

    /**
     * Hand-crafted WASM module with memory, cabi_realloc, and echo functions.
     *
     * WAT equivalent:
     * <pre>
     * (module
     *   (memory (export "memory") 1)
     *   ;; Global for bump allocator, starts at 1024 to avoid stomping on test data
     *   (global $bump (mut i32) (i32.const 1024))
     *
     *   ;; cabi_realloc: simple bump allocator
     *   (func (export "cabi_realloc") (param $old_ptr i32) (param $old_size i32)
     *         (param $align i32) (param $new_size i32) (result i32)
     *     (local $ptr i32)
     *     ;; Align bump pointer: ptr = (bump + align - 1) & ~(align - 1)
     *     (local.set $ptr
     *       (i32.and
     *         (i32.add (global.get $bump) (i32.sub (local.get $align) (i32.const 1)))
     *         (i32.xor (i32.sub (local.get $align) (i32.const 1)) (i32.const -1))))
     *     ;; bump = ptr + new_size
     *     (global.set $bump (i32.add (local.get $ptr) (local.get $new_size)))
     *     (local.get $ptr))
     *
     *   ;; echo_string: copies input data to new alloc, writes (ptr, len) at retptr
     *   (func (export "echo_string") (param $retptr i32) (param $ptr i32) (param $len i32)
     *     (local $dst i32)
     *     ;; Allocate space for the copy
     *     (local.set $dst
     *       (call $cabi_realloc_internal (i32.const 0) (i32.const 0) (i32.const 1) (local.get $len)))
     *     ;; memory.copy dst, src, len
     *     (memory.copy (local.get $dst) (local.get $ptr) (local.get $len))
     *     ;; Write ptr at retptr
     *     (i32.store (local.get $retptr) (local.get $dst))
     *     ;; Write len at retptr+4
     *     (i32.store offset=4 (local.get $retptr) (local.get $len)))
     *
     *   ;; echo_bytes: same as echo_string
     *   (func (export "echo_bytes") (param $retptr i32) (param $ptr i32) (param $len i32)
     *     (local $dst i32)
     *     (local.set $dst
     *       (call $cabi_realloc_internal (i32.const 0) (i32.const 0) (i32.const 1) (local.get $len)))
     *     (memory.copy (local.get $dst) (local.get $ptr) (local.get $len))
     *     (i32.store (local.get $retptr) (local.get $dst))
     *     (i32.store offset=4 (local.get $retptr) (local.get $len)))
     *
     *   ;; string_length: returns the byte length
     *   (func (export "string_length") (param $ptr i32) (param $len i32) (result i32)
     *     (local.get $len))
     *
     *   ;; Internal version of cabi_realloc for calls within the module
     *   (func $cabi_realloc_internal (param $old_ptr i32) (param $old_size i32)
     *         (param $align i32) (param $new_size i32) (result i32)
     *     (local $ptr i32)
     *     (local.set $ptr
     *       (i32.and
     *         (i32.add (global.get $bump) (i32.sub (local.get $align) (i32.const 1)))
     *         (i32.xor (i32.sub (local.get $align) (i32.const 1)) (i32.const -1))))
     *     (global.set $bump (i32.add (local.get $ptr) (local.get $new_size)))
     *     (local.get $ptr))
     * )
     * </pre>
     */
    private static final byte[] MARSHAL_MODULE = buildMarshalModule();

    interface Greeter extends AutoCloseable {
        @WasmExport("echo_string")
        String greet(String name);
    }

    interface ByteProcessor extends AutoCloseable {
        @WasmExport("echo_bytes")
        byte[] process(byte[] input);
    }

    interface StringAnalyzer extends AutoCloseable {
        @WasmExport("string_length")
        int length(String input);
    }

    @Test
    void stringEcho() throws Exception {
        try (Greeter greeter = WasmRuntime.load(Greeter.class, MARSHAL_MODULE)) {
            assertNotNull(greeter);
            String result = greeter.greet("Hello, WebAssembly!");
            assertEquals("Hello, WebAssembly!", result);
        }
    }

    @Test
    void stringEchoUtf8() throws Exception {
        try (Greeter greeter = WasmRuntime.load(Greeter.class, MARSHAL_MODULE)) {
            String result = greeter.greet("\u00e9l\u00e8ve");
            assertEquals("\u00e9l\u00e8ve", result);
        }
    }

    @Test
    void byteArrayEcho() throws Exception {
        try (ByteProcessor processor = WasmRuntime.load(ByteProcessor.class, MARSHAL_MODULE)) {
            assertNotNull(processor);
            byte[] input = {1, 2, 3, 4, 5};
            byte[] result = processor.process(input);
            assertArrayEquals(input, result);
        }
    }

    @Test
    void stringLengthReturnsPrimitive() throws Exception {
        try (StringAnalyzer analyzer = WasmRuntime.load(StringAnalyzer.class, MARSHAL_MODULE)) {
            int len = analyzer.length("hello");
            assertEquals(5, len);
        }
    }

    @Test
    void stringLengthUtf8() throws Exception {
        try (StringAnalyzer analyzer = WasmRuntime.load(StringAnalyzer.class, MARSHAL_MODULE)) {
            // "élève" is 7 UTF-8 bytes
            int len = analyzer.length("\u00e9l\u00e8ve");
            assertEquals(7, len);
        }
    }

    /**
     * Builds the WASM module binary.
     *
     * This is a hand-assembled WASM binary with:
     * - 1 page memory (exported as "memory")
     * - 1 mutable global (bump pointer, starts at 1024)
     * - cabi_realloc (func 0, exported)
     * - echo_string (func 1, exported)
     * - echo_bytes (func 2, exported)
     * - string_length (func 3, exported)
     * - cabi_realloc_internal (func 4, internal)
     */
    @SuppressWarnings("checkstyle:MethodLength")
    private static byte[] buildMarshalModule() {
        // Type section: 3 type entries
        // type 0: (i32, i32, i32, i32) -> i32  (cabi_realloc)
        // type 1: (i32, i32, i32) -> ()         (echo_string, echo_bytes)
        // type 2: (i32, i32) -> i32             (string_length)
        byte[] typeSection = {
                0x01,                   // section id: Type
                0x13,                   // section size: 19 bytes
                0x03,                   // 3 types
                // type 0: (i32, i32, i32, i32) -> i32
                0x60, 0x04, 0x7f, 0x7f, 0x7f, 0x7f, 0x01, 0x7f,
                // type 1: (i32, i32, i32) -> ()
                0x60, 0x03, 0x7f, 0x7f, 0x7f, 0x00,
                // type 2: (i32, i32) -> i32
                0x60, 0x02, 0x7f, 0x7f, 0x01, 0x7f,
        };

        // Function section: 5 functions
        byte[] funcSection = {
                0x03,                   // section id: Function
                0x06,                   // section size: 6 bytes
                0x05,                   // 5 functions
                0x00,                   // func 0: type 0 (cabi_realloc)
                0x01,                   // func 1: type 1 (echo_string)
                0x01,                   // func 2: type 1 (echo_bytes)
                0x02,                   // func 3: type 2 (string_length)
                0x00,                   // func 4: type 0 (cabi_realloc_internal)
        };

        // Memory section: 1 memory, 1 page min
        byte[] memSection = {
                0x05,                   // section id: Memory
                0x03,                   // section size: 3 bytes
                0x01,                   // 1 memory
                0x00, 0x01,             // no max, 1 page min
        };

        // Global section: 1 mutable i32 global, init to 1024
        byte[] globalSection = {
                0x06,                   // section id: Global
                0x07,                   // section size: 7 bytes
                0x01,                   // 1 global
                0x7f, 0x01,             // i32, mutable
                0x41, (byte) 0x80, 0x08, // i32.const 1024
                0x0b,                   // end
        };

        // Export section: memory + 4 functions
        byte[] memExportName = "memory".getBytes();
        byte[] cabiReallocName = "cabi_realloc".getBytes();
        byte[] echoStringName = "echo_string".getBytes();
        byte[] echoBytesName = "echo_bytes".getBytes();
        byte[] stringLengthName = "string_length".getBytes();

        // Build export entries
        java.io.ByteArrayOutputStream exportStream = new java.io.ByteArrayOutputStream();
        exportStream.write(0x05); // 5 exports

        // memory export
        exportStream.write(memExportName.length);
        exportStream.write(memExportName, 0, memExportName.length);
        exportStream.write(0x02); // memory
        exportStream.write(0x00); // index 0

        // cabi_realloc export (func 0)
        exportStream.write(cabiReallocName.length);
        exportStream.write(cabiReallocName, 0, cabiReallocName.length);
        exportStream.write(0x00); // func
        exportStream.write(0x00); // index 0

        // echo_string export (func 1)
        exportStream.write(echoStringName.length);
        exportStream.write(echoStringName, 0, echoStringName.length);
        exportStream.write(0x00); // func
        exportStream.write(0x01); // index 1

        // echo_bytes export (func 2)
        exportStream.write(echoBytesName.length);
        exportStream.write(echoBytesName, 0, echoBytesName.length);
        exportStream.write(0x00); // func
        exportStream.write(0x02); // index 2

        // string_length export (func 3)
        exportStream.write(stringLengthName.length);
        exportStream.write(stringLengthName, 0, stringLengthName.length);
        exportStream.write(0x00); // func
        exportStream.write(0x03); // index 3

        byte[] exportPayload = exportStream.toByteArray();
        byte[] exportSection = new byte[2 + exportPayload.length];
        exportSection[0] = 0x07; // section id: Export
        exportSection[1] = (byte) exportPayload.length;
        System.arraycopy(exportPayload, 0, exportSection, 2, exportPayload.length);

        // Code section: 5 function bodies
        // Build each function body individually

        // func 0: cabi_realloc (exported)
        // Same logic as func 4 (internal version)
        byte[] cabiReallocBody = buildCabiReallocBody();

        // func 1: echo_string(retptr, ptr, len)
        byte[] echoStringBody = buildEchoBody();

        // func 2: echo_bytes(retptr, ptr, len) — same as echo_string
        byte[] echoBytesBody = buildEchoBody();

        // func 3: string_length(ptr, len) -> len
        byte[] stringLengthBody = {
                // body size, 1 local declaration groups
                0x04,                   // body size: 4 bytes
                0x00,                   // 0 local declaration groups
                0x20, 0x01,             // local.get 1 ($len)
                0x0b,                   // end
        };

        // func 4: cabi_realloc_internal (same as func 0)
        byte[] cabiReallocInternalBody = buildCabiReallocBody();

        // Assemble code section
        java.io.ByteArrayOutputStream codeStream = new java.io.ByteArrayOutputStream();
        codeStream.write(0x05); // 5 function bodies
        codeStream.write(cabiReallocBody, 0, cabiReallocBody.length);
        codeStream.write(echoStringBody, 0, echoStringBody.length);
        codeStream.write(echoBytesBody, 0, echoBytesBody.length);
        codeStream.write(stringLengthBody, 0, stringLengthBody.length);
        codeStream.write(cabiReallocInternalBody, 0, cabiReallocInternalBody.length);
        byte[] codePayload = codeStream.toByteArray();

        byte[] codeSectionHeader = encodeSectionHeader(0x0a, codePayload.length);

        // Data count section (required for memory.copy)
        byte[] dataCountSection = {
                0x0c,                   // section id: Data Count
                0x01,                   // section size: 1 byte
                0x00,                   // 0 data segments
        };

        // Assemble full module
        byte[] magic = {0x00, 0x61, 0x73, 0x6d}; // \0asm
        byte[] version = {0x01, 0x00, 0x00, 0x00}; // version 1

        java.io.ByteArrayOutputStream moduleStream = new java.io.ByteArrayOutputStream();
        moduleStream.write(magic, 0, magic.length);
        moduleStream.write(version, 0, version.length);
        moduleStream.write(typeSection, 0, typeSection.length);
        moduleStream.write(funcSection, 0, funcSection.length);
        moduleStream.write(memSection, 0, memSection.length);
        moduleStream.write(globalSection, 0, globalSection.length);
        moduleStream.write(exportSection, 0, exportSection.length);
        moduleStream.write(dataCountSection, 0, dataCountSection.length);
        moduleStream.write(codeSectionHeader, 0, codeSectionHeader.length);
        moduleStream.write(codePayload, 0, codePayload.length);

        return moduleStream.toByteArray();
    }

    /**
     * Builds the body for cabi_realloc:
     * <pre>
     * (local $ptr i32)
     * (local.set $ptr
     *   (i32.and
     *     (i32.add (global.get $bump) (i32.sub (local.get $align) (i32.const 1)))
     *     (i32.xor (i32.sub (local.get $align) (i32.const 1)) (i32.const -1))))
     * (global.set $bump (i32.add (local.get $ptr) (local.get $new_size)))
     * (local.get $ptr)
     * </pre>
     */
    private static byte[] buildCabiReallocBody() {
        byte[] code = {
                // 1 local: i32 ($ptr is local 4)
                0x01,                   // 1 local declaration group
                0x01, 0x7f,             // 1 x i32

                // local.set $ptr = (bump + align - 1) & ~(align - 1)
                // global.get $bump
                0x23, 0x00,
                // local.get $align (param 2)
                0x20, 0x02,
                // i32.const 1
                0x41, 0x01,
                // i32.sub
                0x6b,
                // i32.add
                0x6a,
                // local.get $align
                0x20, 0x02,
                // i32.const 1
                0x41, 0x01,
                // i32.sub
                0x6b,
                // i32.const -1
                0x41, 0x7f,
                // i32.xor
                0x73,
                // i32.and
                0x71,
                // local.set $ptr (local 4)
                0x21, 0x04,

                // global.set $bump = $ptr + $new_size
                // local.get $ptr
                0x20, 0x04,
                // local.get $new_size (param 3)
                0x20, 0x03,
                // i32.add
                0x6a,
                // global.set $bump
                0x24, 0x00,

                // local.get $ptr (return value)
                0x20, 0x04,
                // end
                0x0b,
        };

        int bodySize = code.length;
        byte[] body = new byte[1 + bodySize];
        body[0] = (byte) bodySize;
        System.arraycopy(code, 0, body, 1, bodySize);
        return body;
    }

    /**
     * Builds the body for echo_string/echo_bytes:
     * <pre>
     * (local $dst i32)
     * ;; allocate via internal cabi_realloc (func 4)
     * (local.set $dst (call 4 (i32.const 0) (i32.const 0) (i32.const 1) (local.get $len)))
     * ;; memory.copy dst, src, len
     * (memory.copy (local.get $dst) (local.get $ptr) (local.get $len))
     * ;; write (dst, len) at retptr
     * (i32.store (local.get $retptr) (local.get $dst))
     * (i32.store offset=4 (local.get $retptr) (local.get $len))
     * </pre>
     */
    private static byte[] buildEchoBody() {
        byte[] code = {
                // 1 local: i32 ($dst is local 3)
                0x01,                   // 1 local declaration group
                0x01, 0x7f,             // 1 x i32

                // call cabi_realloc_internal (func 4) with (0, 0, 1, len)
                0x41, 0x00,             // i32.const 0 (old_ptr)
                0x41, 0x00,             // i32.const 0 (old_size)
                0x41, 0x01,             // i32.const 1 (align)
                0x20, 0x02,             // local.get $len
                0x10, 0x04,             // call func 4
                0x21, 0x03,             // local.set $dst

                // memory.copy $dst, $ptr, $len
                0x20, 0x03,             // local.get $dst
                0x20, 0x01,             // local.get $ptr
                0x20, 0x02,             // local.get $len
                (byte) 0xfc, 0x0a,      // memory.copy
                0x00, 0x00,             // mem 0, mem 0

                // i32.store (retptr) = dst
                0x20, 0x00,             // local.get $retptr
                0x20, 0x03,             // local.get $dst
                0x36, 0x02, 0x00,       // i32.store align=4 offset=0

                // i32.store offset=4 (retptr) = len
                0x20, 0x00,             // local.get $retptr
                0x20, 0x02,             // local.get $len
                0x36, 0x02, 0x04,       // i32.store align=4 offset=4

                // end
                0x0b,
        };

        int bodySize = code.length;
        byte[] body = new byte[1 + bodySize];
        body[0] = (byte) bodySize;
        System.arraycopy(code, 0, body, 1, bodySize);
        return body;
    }

    private static byte[] encodeSectionHeader(int sectionId, int payloadSize) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(sectionId);
        // LEB128 encode payloadSize
        int value = payloadSize;
        do {
            int b = value & 0x7f;
            value >>= 7;
            if (value != 0) {
                b |= 0x80;
            }
            out.write(b);
        } while (value != 0);
        return out.toByteArray();
    }
}
