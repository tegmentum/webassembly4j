package ai.tegmentum.webassembly4j.provider.endive;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wire codec for the {@code wasmcm_runtime_guest} argument / result frames.
 *
 * <p>Mirrors the tags and layout documented on {@code wasmcm_runtime_guest::codec}
 * (see {@code crates/wasmcm-runtime-guest/src/codec.rs} in the wasm-cm tree):
 *
 * <pre>
 * frame  := [u32 total_bytes_including_header][u32 count][value * count]
 * value  := [u32 tag][payload...]
 * </pre>
 *
 * <p>All integers are little-endian and unaligned.
 *
 * <p>The Java surface exchanges natural Java types with callers of
 * {@link EndiveComponentInstanceAdapter#invoke}: {@code Boolean, Byte, Short,
 * Integer, Long, Float, Double, Character, String, byte[], List, Map} — mapped
 * onto the tag arms below at encode time. Decoding produces the mirror shape.
 *
 * <p>Types the Java-side natural mapping cannot express unambiguously (unsigned
 * widths, variants, options, results, enums, flags, resources) accept a
 * pre-boxed {@link Boxed} sentinel that carries the wire tag alongside the
 * payload.
 */
final class WasmcmValueCodec {

    static final int TAG_BOOL = 0;
    static final int TAG_S8 = 1;
    static final int TAG_U8 = 2;
    static final int TAG_S16 = 3;
    static final int TAG_U16 = 4;
    static final int TAG_S32 = 5;
    static final int TAG_U32 = 6;
    static final int TAG_S64 = 7;
    static final int TAG_U64 = 8;
    static final int TAG_F32 = 9;
    static final int TAG_F64 = 10;
    static final int TAG_CHAR = 11;
    static final int TAG_STRING = 12;
    static final int TAG_LIST = 13;
    static final int TAG_RECORD = 14;
    static final int TAG_TUPLE = 15;
    static final int TAG_VARIANT = 16;
    static final int TAG_OPTION = 17;
    static final int TAG_RESULT = 18;
    static final int TAG_FLAGS = 19;
    static final int TAG_ENUM = 20;
    static final int TAG_OWN = 21;
    static final int TAG_BORROW = 22;

    private WasmcmValueCodec() {}

    /**
     * Envelope for a payload plus its explicit wire tag — the escape hatch for
     * WIT types the Java-native shape can't disambiguate on its own (unsigned
     * widths, variants, options, results, enums, flags, own/borrow handles).
     *
     * <p>The concrete payload shape per tag:
     * <ul>
     *   <li>{@link #TAG_U8}/{@link #TAG_U16}/{@link #TAG_U32}: {@link Integer}
     *       carrying the unsigned bit pattern.</li>
     *   <li>{@link #TAG_U64}: {@link Long} carrying the unsigned bit pattern.</li>
     *   <li>{@link #TAG_S8}: {@link Byte}. {@link #TAG_S16}: {@link Short}.
     *       {@link #TAG_S32}: {@link Integer}. {@link #TAG_S64}: {@link Long}.</li>
     *   <li>{@link #TAG_OPTION}: {@link Boxed} for {@code some(v)} carrying the
     *       inner value, or {@code null} payload for {@code none}.</li>
     *   <li>{@link #TAG_RESULT}: {@code Object[]{isOkBoolean, payloadOrNull}}.</li>
     *   <li>{@link #TAG_ENUM}: {@code Object[]{caseIndexInt, caseNameString}}.</li>
     *   <li>{@link #TAG_VARIANT}: {@code Object[]{caseIndexInt, caseNameString, payloadOrNull}}.</li>
     *   <li>{@link #TAG_FLAGS}: {@link List} of {@link String}.</li>
     *   <li>{@link #TAG_OWN}/{@link #TAG_BORROW}: {@link Integer} handle.</li>
     * </ul>
     */
    static final class Boxed {
        final int tag;
        final Object payload;

        Boxed(int tag, Object payload) {
            this.tag = tag;
            this.payload = payload;
        }

        int tag() {
            return tag;
        }

        Object payload() {
            return payload;
        }
    }

    // -----------------------------------------------------------------
    // Encode
    // -----------------------------------------------------------------

    static byte[] encodeFrame(Object[] values) {
        ByteArrayOutputStream inner = new ByteArrayOutputStream();
        writeU32(inner, values.length);
        for (Object v : values) {
            encodeValue(inner, v);
        }
        byte[] body = inner.toByteArray();
        int total = 4 + body.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream(total);
        writeU32(out, total);
        try {
            out.write(body);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("BAOS never throws", e);
        }
        return out.toByteArray();
    }

    static void encodeValue(ByteArrayOutputStream buf, Object v) {
        if (v instanceof Boxed) {
            Boxed b = (Boxed) v;
            encodeBoxed(buf, b);
            return;
        }
        if (v instanceof Boolean) {
            writeU32(buf, TAG_BOOL);
            buf.write(((Boolean) v) ? 1 : 0);
            return;
        }
        if (v instanceof Byte) {
            writeU32(buf, TAG_S8);
            buf.write(((Byte) v) & 0xFF);
            return;
        }
        if (v instanceof Short) {
            writeU32(buf, TAG_S16);
            writeI16(buf, (Short) v);
            return;
        }
        if (v instanceof Integer) {
            writeU32(buf, TAG_S32);
            writeI32(buf, (Integer) v);
            return;
        }
        if (v instanceof Long) {
            writeU32(buf, TAG_S64);
            writeI64(buf, (Long) v);
            return;
        }
        if (v instanceof Float) {
            writeU32(buf, TAG_F32);
            writeI32(buf, Float.floatToRawIntBits((Float) v));
            return;
        }
        if (v instanceof Double) {
            writeU32(buf, TAG_F64);
            writeI64(buf, Double.doubleToRawLongBits((Double) v));
            return;
        }
        if (v instanceof Character) {
            writeU32(buf, TAG_CHAR);
            writeU32(buf, (Character) v);
            return;
        }
        if (v instanceof String) {
            writeU32(buf, TAG_STRING);
            writeBytes(buf, ((String) v).getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (v instanceof byte[]) {
            byte[] bytes = (byte[]) v;
            writeU32(buf, TAG_LIST);
            writeU32(buf, bytes.length);
            for (byte b : bytes) {
                writeU32(buf, TAG_U8);
                buf.write(b & 0xFF);
            }
            return;
        }
        if (v instanceof List<?>) {
            List<?> list = (List<?>) v;
            writeU32(buf, TAG_LIST);
            writeU32(buf, list.size());
            for (Object item : list) {
                encodeValue(buf, item);
            }
            return;
        }
        if (v instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) v;
            writeU32(buf, TAG_RECORD);
            writeU32(buf, map.size());
            for (Map.Entry<?, ?> e : map.entrySet()) {
                writeBytes(buf, e.getKey().toString().getBytes(StandardCharsets.UTF_8));
                encodeValue(buf, e.getValue());
            }
            return;
        }
        throw new IllegalArgumentException(
                "cannot encode Java type "
                        + (v == null ? "null" : v.getClass().getName())
                        + " as a wasmcm ComponentValue; wrap in "
                        + Boxed.class.getName()
                        + " with an explicit wire tag");
    }

    private static void encodeBoxed(ByteArrayOutputStream buf, Boxed b) {
        switch (b.tag) {
            case TAG_BOOL:
                writeU32(buf, TAG_BOOL);
                buf.write(((Boolean) b.payload) ? 1 : 0);
                return;
            case TAG_S8:
                writeU32(buf, TAG_S8);
                buf.write(((Number) b.payload).byteValue() & 0xFF);
                return;
            case TAG_U8:
                writeU32(buf, TAG_U8);
                buf.write(((Number) b.payload).intValue() & 0xFF);
                return;
            case TAG_S16:
                writeU32(buf, TAG_S16);
                writeI16(buf, ((Number) b.payload).shortValue());
                return;
            case TAG_U16:
                writeU32(buf, TAG_U16);
                writeI16(buf, (short) (((Number) b.payload).intValue() & 0xFFFF));
                return;
            case TAG_S32:
                writeU32(buf, TAG_S32);
                writeI32(buf, ((Number) b.payload).intValue());
                return;
            case TAG_U32:
                writeU32(buf, TAG_U32);
                writeI32(buf, ((Number) b.payload).intValue());
                return;
            case TAG_S64:
                writeU32(buf, TAG_S64);
                writeI64(buf, ((Number) b.payload).longValue());
                return;
            case TAG_U64:
                writeU32(buf, TAG_U64);
                writeI64(buf, ((Number) b.payload).longValue());
                return;
            case TAG_F32:
                writeU32(buf, TAG_F32);
                writeI32(buf, Float.floatToRawIntBits(((Number) b.payload).floatValue()));
                return;
            case TAG_F64:
                writeU32(buf, TAG_F64);
                writeI64(buf, Double.doubleToRawLongBits(((Number) b.payload).doubleValue()));
                return;
            case TAG_CHAR:
                writeU32(buf, TAG_CHAR);
                writeU32(buf, (int) ((Character) b.payload).charValue());
                return;
            case TAG_STRING:
                writeU32(buf, TAG_STRING);
                writeBytes(buf, ((String) b.payload).getBytes(StandardCharsets.UTF_8));
                return;
            case TAG_LIST:
                writeU32(buf, TAG_LIST);
                {
                    List<?> list = (List<?>) b.payload;
                    writeU32(buf, list.size());
                    for (Object item : list) {
                        encodeValue(buf, item);
                    }
                }
                return;
            case TAG_RECORD:
                writeU32(buf, TAG_RECORD);
                {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) b.payload;
                    writeU32(buf, map.size());
                    for (Map.Entry<String, Object> e : map.entrySet()) {
                        writeBytes(buf, e.getKey().getBytes(StandardCharsets.UTF_8));
                        encodeValue(buf, e.getValue());
                    }
                }
                return;
            case TAG_TUPLE:
                writeU32(buf, TAG_TUPLE);
                {
                    List<?> list = (List<?>) b.payload;
                    writeU32(buf, list.size());
                    for (Object item : list) {
                        encodeValue(buf, item);
                    }
                }
                return;
            case TAG_VARIANT:
                writeU32(buf, TAG_VARIANT);
                {
                    Object[] parts = (Object[]) b.payload;
                    int caseIdx = ((Number) parts[0]).intValue();
                    String caseName = (String) parts[1];
                    Object innerPayload = parts.length > 2 ? parts[2] : null;
                    writeU32(buf, caseIdx);
                    writeBytes(buf, caseName.getBytes(StandardCharsets.UTF_8));
                    if (innerPayload == null) {
                        buf.write(0);
                    } else {
                        buf.write(1);
                        encodeValue(buf, innerPayload);
                    }
                }
                return;
            case TAG_OPTION:
                writeU32(buf, TAG_OPTION);
                if (b.payload == null) {
                    buf.write(0);
                } else {
                    buf.write(1);
                    encodeValue(buf, b.payload);
                }
                return;
            case TAG_RESULT:
                writeU32(buf, TAG_RESULT);
                {
                    Object[] parts = (Object[]) b.payload;
                    boolean isOk = ((Boolean) parts[0]);
                    Object inner = parts.length > 1 ? parts[1] : null;
                    buf.write(isOk ? 1 : 0);
                    if (inner == null) {
                        buf.write(0);
                    } else {
                        buf.write(1);
                        encodeValue(buf, inner);
                    }
                }
                return;
            case TAG_FLAGS:
                writeU32(buf, TAG_FLAGS);
                {
                    List<?> labels = (List<?>) b.payload;
                    writeU32(buf, labels.size());
                    for (Object label : labels) {
                        writeBytes(buf, ((String) label).getBytes(StandardCharsets.UTF_8));
                    }
                }
                return;
            case TAG_ENUM:
                writeU32(buf, TAG_ENUM);
                {
                    Object[] parts = (Object[]) b.payload;
                    int caseIdx = ((Number) parts[0]).intValue();
                    String caseName = (String) parts[1];
                    writeU32(buf, caseIdx);
                    writeBytes(buf, caseName.getBytes(StandardCharsets.UTF_8));
                }
                return;
            case TAG_OWN:
                writeU32(buf, TAG_OWN);
                writeU32(buf, ((Number) b.payload).intValue());
                return;
            case TAG_BORROW:
                writeU32(buf, TAG_BORROW);
                writeU32(buf, ((Number) b.payload).intValue());
                return;
            default:
                throw new IllegalArgumentException("unknown wire tag " + b.tag);
        }
    }

    // -----------------------------------------------------------------
    // Decode
    // -----------------------------------------------------------------

    static List<Object> decodeFrame(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return java.util.Collections.emptyList();
        }
        Cursor c = new Cursor(bytes, 0);
        readU32(c); // total_bytes — discard, callers already sized the array
        int count = readU32(c);
        List<Object> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(decodeValue(c));
        }
        return out;
    }

    static Object decodeValue(Cursor c) {
        int tag = readU32(c);
        switch (tag) {
            case TAG_BOOL:
                return c.readU8() != 0;
            case TAG_S8:
                return (byte) c.readU8();
            case TAG_U8:
                return c.readU8() & 0xFF;
            case TAG_S16:
                return (short) readI16(c);
            case TAG_U16:
                return readI16(c) & 0xFFFF;
            case TAG_S32:
                return readU32(c);
            case TAG_U32:
                // Represent unsigned u32 as a Long to keep the natural mapping non-lossy.
                return ((long) readU32(c)) & 0xFFFFFFFFL;
            case TAG_S64:
                return readI64(c);
            case TAG_U64:
                // No unsigned long in Java; caller can reinterpret bits.
                return readI64(c);
            case TAG_F32:
                return Float.intBitsToFloat(readU32(c));
            case TAG_F64:
                return Double.longBitsToDouble(readI64(c));
            case TAG_CHAR:
                return (char) readU32(c);
            case TAG_STRING:
                return new String(readBytes(c), StandardCharsets.UTF_8);
            case TAG_LIST: {
                int n = readU32(c);
                List<Object> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    list.add(decodeValue(c));
                }
                return list;
            }
            case TAG_RECORD: {
                int n = readU32(c);
                Map<String, Object> record = new LinkedHashMap<>();
                for (int i = 0; i < n; i++) {
                    String name = new String(readBytes(c), StandardCharsets.UTF_8);
                    Object value = decodeValue(c);
                    record.put(name, value);
                }
                return record;
            }
            case TAG_TUPLE: {
                int n = readU32(c);
                List<Object> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    list.add(decodeValue(c));
                }
                return new Boxed(TAG_TUPLE, list);
            }
            case TAG_VARIANT: {
                int caseIdx = readU32(c);
                String caseName = new String(readBytes(c), StandardCharsets.UTF_8);
                int hasPayload = c.readU8();
                Object payload = hasPayload == 0 ? null : decodeValue(c);
                return new Boxed(TAG_VARIANT, new Object[] {caseIdx, caseName, payload});
            }
            case TAG_OPTION: {
                int has = c.readU8();
                return has == 0 ? new Boxed(TAG_OPTION, null) : new Boxed(TAG_OPTION, decodeValue(c));
            }
            case TAG_RESULT: {
                boolean isOk = c.readU8() != 0;
                int has = c.readU8();
                Object payload = has == 0 ? null : decodeValue(c);
                return new Boxed(TAG_RESULT, new Object[] {isOk, payload});
            }
            case TAG_FLAGS: {
                int n = readU32(c);
                List<String> labels = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    labels.add(new String(readBytes(c), StandardCharsets.UTF_8));
                }
                return new Boxed(TAG_FLAGS, labels);
            }
            case TAG_ENUM: {
                int caseIdx = readU32(c);
                String caseName = new String(readBytes(c), StandardCharsets.UTF_8);
                return new Boxed(TAG_ENUM, new Object[] {caseIdx, caseName});
            }
            case TAG_OWN:
                return new Boxed(TAG_OWN, readU32(c));
            case TAG_BORROW:
                return new Boxed(TAG_BORROW, readU32(c));
            default:
                throw new IllegalStateException("unknown wire tag " + tag);
        }
    }

    // -----------------------------------------------------------------
    // Low-level LE readers / writers
    // -----------------------------------------------------------------

    static final class Cursor {
        final byte[] bytes;
        int off;

        Cursor(byte[] bytes, int off) {
            this.bytes = bytes;
            this.off = off;
        }

        int readU8() {
            require(1);
            return bytes[off++] & 0xFF;
        }

        void require(int n) {
            if (off + n > bytes.length) {
                throw new IllegalStateException(
                        "wire frame truncated: need " + n + " at " + off + " of " + bytes.length);
            }
        }
    }

    private static void writeU32(ByteArrayOutputStream buf, int v) {
        buf.write(v & 0xFF);
        buf.write((v >>> 8) & 0xFF);
        buf.write((v >>> 16) & 0xFF);
        buf.write((v >>> 24) & 0xFF);
    }

    private static void writeI16(ByteArrayOutputStream buf, int v) {
        buf.write(v & 0xFF);
        buf.write((v >>> 8) & 0xFF);
    }

    private static void writeI32(ByteArrayOutputStream buf, int v) {
        writeU32(buf, v);
    }

    private static void writeI64(ByteArrayOutputStream buf, long v) {
        buf.write((int) (v & 0xFF));
        buf.write((int) ((v >>> 8) & 0xFF));
        buf.write((int) ((v >>> 16) & 0xFF));
        buf.write((int) ((v >>> 24) & 0xFF));
        buf.write((int) ((v >>> 32) & 0xFF));
        buf.write((int) ((v >>> 40) & 0xFF));
        buf.write((int) ((v >>> 48) & 0xFF));
        buf.write((int) ((v >>> 56) & 0xFF));
    }

    private static void writeBytes(ByteArrayOutputStream buf, byte[] bytes) {
        writeU32(buf, bytes.length);
        try {
            buf.write(bytes);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("BAOS never throws", e);
        }
    }

    private static int readU32(Cursor c) {
        c.require(4);
        int v = (c.bytes[c.off] & 0xFF)
                | ((c.bytes[c.off + 1] & 0xFF) << 8)
                | ((c.bytes[c.off + 2] & 0xFF) << 16)
                | ((c.bytes[c.off + 3] & 0xFF) << 24);
        c.off += 4;
        return v;
    }

    private static int readI16(Cursor c) {
        c.require(2);
        int v = (c.bytes[c.off] & 0xFF) | ((c.bytes[c.off + 1] & 0xFF) << 8);
        c.off += 2;
        return v;
    }

    private static long readI64(Cursor c) {
        c.require(8);
        long v = (long) (c.bytes[c.off] & 0xFF)
                | ((long) (c.bytes[c.off + 1] & 0xFF) << 8)
                | ((long) (c.bytes[c.off + 2] & 0xFF) << 16)
                | ((long) (c.bytes[c.off + 3] & 0xFF) << 24)
                | ((long) (c.bytes[c.off + 4] & 0xFF) << 32)
                | ((long) (c.bytes[c.off + 5] & 0xFF) << 40)
                | ((long) (c.bytes[c.off + 6] & 0xFF) << 48)
                | ((long) (c.bytes[c.off + 7] & 0xFF) << 56);
        c.off += 8;
        return v;
    }

    private static byte[] readBytes(Cursor c) {
        int n = readU32(c);
        c.require(n);
        byte[] out = new byte[n];
        System.arraycopy(c.bytes, c.off, out, 0, n);
        c.off += n;
        return out;
    }
}
