/*
 * Copyright 2026 Tegmentum AI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package ai.tegmentum.webassembly4j.provider.wasmos;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Cheap-and-cheerful JSON marshalling between Java-natural objects and the
 * on-the-wire {@code JsonVal} schema that
 * {@code wasmos-provider/native/src/lib.rs} defines.
 *
 * <p>Design principles:
 * <ul>
 *   <li>No third-party JSON dep — vanilla JVM, hand-rolled writer/reader.
 *       Kept minimal because the schema is closed (a fixed set of
 *       {@code JsonVal} variants).</li>
 *   <li>Symmetric with the Rust {@code JsonVal} enum. Tag key is {@code "t"},
 *       payload key is {@code "v"} — matches the {@code #[serde(tag = "t",
 *       content = "v")]} annotation.</li>
 *   <li>Fast path for {@code byte[]}: encoded as {@code {"t":"Bytes",
 *       "v":"...base64..."}}; the Rust side decodes to {@code Val::List<U8>}
 *       and detects the same pattern on the return path to hand it back as
 *       {@code Bytes} instead of an O(n) list of tagged u8s.</li>
 * </ul>
 *
 * <p>Java type mapping — mirrors the natural-shape convention documented on
 * {@code ComponentInstance.invoke} for symmetry with other providers:
 * <table>
 *   <tr><td>Boolean</td><td>Bool</td></tr>
 *   <tr><td>Byte</td><td>S8</td></tr>
 *   <tr><td>Short</td><td>S16</td></tr>
 *   <tr><td>Integer</td><td>S32</td></tr>
 *   <tr><td>Long</td><td>S64</td></tr>
 *   <tr><td>Float</td><td>F32</td></tr>
 *   <tr><td>Double</td><td>F64</td></tr>
 *   <tr><td>Character</td><td>Char</td></tr>
 *   <tr><td>String</td><td>String</td></tr>
 *   <tr><td>byte[]</td><td>Bytes(base64)</td></tr>
 *   <tr><td>List&lt;?&gt;</td><td>List</td></tr>
 *   <tr><td>Optional&lt;?&gt;/null</td><td>Option</td></tr>
 *   <tr><td>Map&lt;String,?&gt;</td><td>Record (field iteration order matters)</td></tr>
 *   <tr><td>{@link WitMap}</td><td>Map (arbitrary key types)</td></tr>
 *   <tr><td>Set&lt;String&gt;</td><td>Flags</td></tr>
 *   <tr><td>{@link WitResult}</td><td>Result</td></tr>
 *   <tr><td>{@link WitVariant}</td><td>Variant</td></tr>
 *   <tr><td>{@link WitEnum}</td><td>Enum</td></tr>
 *   <tr><td>{@link WitResource}</td><td>Resource (opaque host-parked handle)</td></tr>
 *   <tr><td>{@link WitFuture}</td><td>Future (opaque host-parked handle; awaiting is a wasmtime API gap)</td></tr>
 *   <tr><td>{@link WitStream}</td><td>Stream (opaque host-parked handle; reading is a wasmtime API gap)</td></tr>
 *   <tr><td>{@link WitErrorContext}</td><td>ErrorContext (opaque host-parked handle; rep is a wasmtime numeric hint)</td></tr>
 * </table>
 *
 * <p>Unsigned integers: the WIT spec has u8/u16/u32/u64 but Java doesn't;
 * callers who need those specific WIT widths must use {@link WitU8} / {@link
 * WitU16} / {@link WitU32} / {@link WitU64} wrappers (rare enough that we
 * don't try to guess). Plain Byte/Short/Integer/Long default to signed
 * widths — that's what the widest range of Java code produces naturally.
 */
final class WasmosMarshalling {

    private WasmosMarshalling() {}

    // Tag names — must match the Rust JsonVal enum exactly (serde tag = "t").
    private static final String TAG = "t";
    private static final String VAL = "v";

    // ---- Explicit-typed wrappers for unsigned / typed-result shapes -------

    /** Wrap a Java int as a WIT u8. Overflow at u8 boundary throws. */
    static final class WitU8 {
        final short value;

        WitU8(int v) {
            if (v < 0 || v > 0xFF) {
                throw new IllegalArgumentException("u8 out of range: " + v);
            }
            this.value = (short) v;
        }
    }

    static final class WitU16 {
        final int value;

        WitU16(int v) {
            if (v < 0 || v > 0xFFFF) {
                throw new IllegalArgumentException("u16 out of range: " + v);
            }
            this.value = v;
        }
    }

    static final class WitU32 {
        final long value;

        WitU32(long v) {
            if (v < 0 || v > 0xFFFFFFFFL) {
                throw new IllegalArgumentException("u32 out of range: " + v);
            }
            this.value = v;
        }
    }

    /** u64 via {@link java.math.BigInteger}-free Long — bit-pattern is
     *  reinterpreted on the wire. */
    static final class WitU64 {
        final long bits;

        WitU64(long bits) { this.bits = bits; }
    }

    /** WIT result carrier. Exactly one of {@link #ok} / {@link #err} is
     *  meaningful, selected by {@link #isOk}. */
    static final class WitResult {
        final boolean isOk;
        final Object ok;
        final Object err;

        private WitResult(boolean isOk, Object ok, Object err) {
            this.isOk = isOk;
            this.ok = ok;
            this.err = err;
        }

        static WitResult ok(Object v) { return new WitResult(true, v, null); }

        static WitResult err(Object v) { return new WitResult(false, null, v); }
    }

    /** WIT variant carrier — a named discriminant plus an optional payload. */
    static final class WitVariant {
        final String discriminant;
        final Object value;

        WitVariant(String discriminant, Object value) {
            this.discriminant = discriminant;
            this.value = value;
        }
    }

    /** WIT enum carrier — a named discriminant, no payload. Distinguishable
     *  from a plain String on the marshalling path. */
    static final class WitEnum {
        final String discriminant;

        WitEnum(String discriminant) { this.discriminant = discriminant; }
    }

    /**
     * WIT component-model resource handle. The actual guest resource lives
     * Rust-side in the wasmos-provider native crate's per-instance
     * {@code ResourceRegistry}; a {@code WitResource} is just a lightweight
     * carrier for the slot id + opaque type identity + ownership flag that
     * lets Java-side callers pass the handle back into another invoke.
     *
     * <p>Fields:
     * <ul>
     *   <li>{@code tableId} — the Rust-side slot number. Monotonically
     *       increasing per instance; never reused, so a stale WitResource
     *       (e.g. after an own-transfer) surfaces as a clean error on
     *       reuse rather than an aliased hit.</li>
     *   <li>{@code typeIdentifier} — the wasmtime {@code ResourceType}'s
     *       {@code Debug} rendering. Opaque to Java; useful only for
     *       equality checks between two WitResources.</li>
     *   <li>{@code owned} — on values RECEIVED from a guest return, this
     *       reflects the ResourceAny's own ownership status. On values
     *       PASSED IN to a call, this is the caller's intent: {@code true}
     *       transfers ownership (matches WIT {@code own<T>}) and consumes
     *       the WitResource; {@code false} lends a borrow view (matches
     *       WIT {@code borrow<T>}) and leaves the WitResource reusable.
     *       The caller is responsible for setting this to match the
     *       function's WIT signature.</li>
     * </ul>
     *
     * <p>Immutable — the fields are final and there are no setters. Same
     * {@code WitResource} can be threaded through multiple borrow-shaped
     * calls before a final own-shaped call consumes it.
     */
    static final class WitResource {
        private final long tableId;
        private final String typeIdentifier;
        private final boolean owned;

        WitResource(long tableId, String typeIdentifier, boolean owned) {
            this.tableId = tableId;
            this.typeIdentifier = typeIdentifier == null ? "" : typeIdentifier;
            this.owned = owned;
        }

        public long tableId() { return tableId; }
        public String typeIdentifier() { return typeIdentifier; }
        public boolean owned() { return owned; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof WitResource)) return false;
            final WitResource other = (WitResource) o;
            return tableId == other.tableId
                    && owned == other.owned
                    && typeIdentifier.equals(other.typeIdentifier);
        }

        @Override
        public int hashCode() {
            int h = Long.hashCode(tableId);
            h = 31 * h + typeIdentifier.hashCode();
            h = 31 * h + Boolean.hashCode(owned);
            return h;
        }

        @Override
        public String toString() {
            return "WitResource{tableId=" + tableId
                    + ", typeIdentifier='" + typeIdentifier
                    + "', owned=" + owned + '}';
        }
    }

    /**
     * WIT component-model {@code future<T>} handle. Same lightweight-carrier
     * shape as {@link WitResource}: the {@code FutureAny} lives Rust-side in
     * the wasmos-provider native crate's per-instance {@code FutureRegistry},
     * and Java only ever sees a stable slot id + an opaque Debug rendering
     * of the {@code FutureAny} for equality-adjacent introspection.
     *
     * <p>Unlike {@link WitResource}, {@code WitFuture} has no {@code owned}
     * bit. Wasmtime 47's {@code FutureAny} is already the "read end" of a
     * split future, and there is no borrow-shaped pass-in variant in the
     * component model — a future handle flows through a guest import
     * exactly once. The Rust-side decode arm always consumes (takes) the
     * parked entry.
     *
     * <p><b>Awaiting is currently unsupported.</b> Wasmtime 47's public
     * {@code FutureAny} API only exposes
     * {@code try_into_future_reader::<T>()} (compile-time typed) and
     * {@code close(store)}. There is no dynamic-payload-type await surface,
     * which means our JSON-based marshalling can't lift the resolved value
     * to a {@link Object}. See
     * {@link ai.tegmentum.webassembly4j.provider.wasmos.ext.WasmosAsyncExtension#awaitFuture}
     * for how the extension exposes this gap (returned
     * {@link java.util.concurrent.CompletableFuture} completes exceptionally
     * with a clear message). A parked {@code WitFuture} can still be:
     * <ul>
     *   <li>passed back into a guest import that consumes it (the pass-in
     *       path IS wired end-to-end and simply hands the parked
     *       {@code FutureAny} back to wasmtime);</li>
     *   <li>explicitly disposed of via
     *       {@link ai.tegmentum.webassembly4j.provider.wasmos.ext.WasmosAsyncExtension#closeFuture}.</li>
     * </ul>
     *
     * <p>Immutable — fields are final and there are no setters.
     */
    static final class WitFuture {
        private final long tableId;
        private final String typeIdentifier;

        WitFuture(long tableId, String typeIdentifier) {
            this.tableId = tableId;
            this.typeIdentifier = typeIdentifier == null ? "" : typeIdentifier;
        }

        public long tableId() { return tableId; }
        public String typeIdentifier() { return typeIdentifier; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof WitFuture)) return false;
            final WitFuture other = (WitFuture) o;
            return tableId == other.tableId
                    && typeIdentifier.equals(other.typeIdentifier);
        }

        @Override
        public int hashCode() {
            int h = Long.hashCode(tableId);
            h = 31 * h + typeIdentifier.hashCode();
            return h;
        }

        @Override
        public String toString() {
            return "WitFuture{tableId=" + tableId
                    + ", typeIdentifier='" + typeIdentifier + "'}";
        }
    }

    /**
     * WIT component-model {@code stream<T>} handle. Structural mirror of
     * {@link WitFuture} — the {@code StreamAny} lives Rust-side in the
     * wasmos-provider native crate's per-instance {@code StreamRegistry},
     * and Java only ever sees a stable slot id + an opaque Debug rendering
     * of the {@code StreamAny} for equality-adjacent introspection.
     *
     * <p>Same lifecycle as {@link WitFuture}: no {@code owned} bit
     * (streams have a read-vs-write split at the type level in wasmtime),
     * pass-in consumes on the Rust decode path, and explicit disposal is
     * via
     * {@link ai.tegmentum.webassembly4j.provider.wasmos.ext.WasmosAsyncExtension#closeStream}.
     *
     * <p><b>Reading is currently unsupported.</b> Wasmtime 47's public
     * {@code StreamAny} API only exposes
     * {@code try_into_stream_reader::<T>()} (compile-time typed) and
     * {@code close(store)}. There is no dynamic-payload-type read/poll
     * surface, which means our JSON-based marshalling can't lift stream
     * items to Java objects. See
     * {@link ai.tegmentum.webassembly4j.provider.wasmos.ext.WasmosAsyncExtension#readStream}
     * for how the extension exposes this gap (returned
     * {@link java.util.concurrent.CompletableFuture} completes exceptionally
     * with a clear message).
     *
     * <p>Immutable — fields are final and there are no setters.
     */
    static final class WitStream {
        private final long tableId;
        private final String typeIdentifier;

        WitStream(long tableId, String typeIdentifier) {
            this.tableId = tableId;
            this.typeIdentifier = typeIdentifier == null ? "" : typeIdentifier;
        }

        public long tableId() { return tableId; }
        public String typeIdentifier() { return typeIdentifier; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof WitStream)) return false;
            final WitStream other = (WitStream) o;
            return tableId == other.tableId
                    && typeIdentifier.equals(other.typeIdentifier);
        }

        @Override
        public int hashCode() {
            int h = Long.hashCode(tableId);
            h = 31 * h + typeIdentifier.hashCode();
            return h;
        }

        @Override
        public String toString() {
            return "WitStream{tableId=" + tableId
                    + ", typeIdentifier='" + typeIdentifier + "'}";
        }
    }

    /**
     * WIT component-model {@code error-context} handle. The underlying
     * wasmtime 47 {@code ErrorContextAny} is a placeholder type (see
     * wasmtime's {@code FIXME(#11161)}) that carries only an opaque
     * {@code pub(crate) u32} rep — no debug message, no backtrace, no
     * dispose surface. The Rust side still parks it in a per-instance
     * registry so a Java caller can pass it back into a guest import
     * that accepts an {@code error-context} argument; Java-visible
     * disposal is a pure Rust-side registry eviction via
     * {@link ai.tegmentum.webassembly4j.provider.wasmos.ext.WasmosAsyncExtension#closeErrorContext}.
     *
     * <p>Fields:
     * <ul>
     *   <li>{@code tableId} — Rust-side slot number, monotonically
     *       increasing, never reused. Used to look up the parked handle
     *       on pass-in and to target close.</li>
     *   <li>{@code rep} — parsed out of {@code ErrorContextAny}'s Debug
     *       rendering. Numeric identity hint only; not meaningful beyond
     *       equality-adjacent introspection.</li>
     * </ul>
     *
     * <p>Immutable. Structural equality; parking multiple WitErrorContexts
     * in a Set is safe.
     */
    static final class WitErrorContext {
        private final long tableId;
        private final long rep;

        WitErrorContext(long tableId, long rep) {
            this.tableId = tableId;
            this.rep = rep;
        }

        public long tableId() { return tableId; }
        public long rep() { return rep; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof WitErrorContext)) return false;
            final WitErrorContext other = (WitErrorContext) o;
            return tableId == other.tableId && rep == other.rep;
        }

        @Override
        public int hashCode() {
            return 31 * Long.hashCode(tableId) + Long.hashCode(rep);
        }

        @Override
        public String toString() {
            return "WitErrorContext{tableId=" + tableId + ", rep=" + rep + '}';
        }
    }

    /**
     * WIT map carrier. Distinguishes {@code map<k, v>} from
     * {@code record { ... }} at the marshalling boundary: a plain
     * {@code Map<String, ?>} routes to WIT record (field-name lookup);
     * a {@code WitMap<?, ?>} routes to WIT map (arbitrary key type).
     *
     * <p>Keys and values are marshalled by the same rules as any other
     * WitVal — nesting other typed carriers is supported. Iteration
     * order is preserved on the wire; the underlying map is treated as
     * a {@link LinkedHashMap} even if the caller hands in another
     * implementation.
     */
    static final class WitMap {
        final Map<Object, Object> pairs;

        WitMap(Map<?, ?> pairs) {
            // Copy into a fresh LinkedHashMap so downstream mutations to
            // the caller's map don't leak into the on-wire form.
            this.pairs = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : pairs.entrySet()) {
                this.pairs.put((Object) e.getKey(), (Object) e.getValue());
            }
        }
    }

    // ---- Public API --------------------------------------------------------

    /**
     * Marshal a Java args array to the JSON-array wire form. The empty-args
     * case ({@code args == null || args.length == 0}) returns the literal
     * {@code "[]"} without any allocation.
     */
    static String marshalArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        final StringBuilder sb = new StringBuilder(64);
        sb.append('[');
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(',');
            writeJsonVal(sb, args[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Unmarshal a JSON-array of {@code JsonVal}s into a list of Java-natural
     * objects. Empty results ({@code "[]"}) returns an empty list.
     */
    static List<Object> unmarshalResults(String json) {
        if (json == null) {
            return new ArrayList<>();
        }
        final Parser p = new Parser(json);
        p.skipWs();
        p.expect('[');
        p.skipWs();
        final List<Object> out = new ArrayList<>();
        if (p.peek() == ']') {
            p.consume();
            return out;
        }
        while (true) {
            p.skipWs();
            out.add(readJsonVal(p));
            p.skipWs();
            final char c = p.peek();
            if (c == ',') {
                p.consume();
                continue;
            }
            if (c == ']') {
                p.consume();
                return out;
            }
            throw new IllegalArgumentException(
                    "expected ',' or ']' at pos " + p.pos + " in: " + json);
        }
    }

    // ---- Writer ------------------------------------------------------------

    private static void writeJsonVal(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("{\"").append(TAG).append("\":\"Option\",\"").append(VAL).append("\":null}");
            return;
        }
        if (v instanceof Boolean) {
            sb.append("{\"").append(TAG).append("\":\"Bool\",\"").append(VAL).append("\":");
            sb.append(((Boolean) v) ? "true" : "false");
            sb.append('}');
            return;
        }
        if (v instanceof Byte) {
            writeTagged(sb, "S8", Byte.toString((Byte) v));
            return;
        }
        if (v instanceof Short) {
            writeTagged(sb, "S16", Short.toString((Short) v));
            return;
        }
        if (v instanceof Integer) {
            writeTagged(sb, "S32", Integer.toString((Integer) v));
            return;
        }
        if (v instanceof Long) {
            writeTagged(sb, "S64", Long.toString((Long) v));
            return;
        }
        if (v instanceof Float) {
            writeTagged(sb, "F32", Float.toString((Float) v));
            return;
        }
        if (v instanceof Double) {
            writeTagged(sb, "F64", Double.toString((Double) v));
            return;
        }
        if (v instanceof Character) {
            // Char is encoded as an int (u32 codepoint) — matches serde's
            // representation of Rust's `char` (single Unicode scalar value).
            writeTagged(sb, "Char", Integer.toString(((Character) v).charValue()));
            return;
        }
        if (v instanceof String) {
            sb.append("{\"").append(TAG).append("\":\"String\",\"").append(VAL).append("\":");
            writeJsonString(sb, (String) v);
            sb.append('}');
            return;
        }
        if (v instanceof byte[]) {
            final String b64 = Base64.getEncoder().encodeToString((byte[]) v);
            sb.append("{\"").append(TAG).append("\":\"Bytes\",\"").append(VAL).append("\":");
            writeJsonString(sb, b64);
            sb.append('}');
            return;
        }
        if (v instanceof WitU8) {
            writeTagged(sb, "U8", Short.toString(((WitU8) v).value));
            return;
        }
        if (v instanceof WitU16) {
            writeTagged(sb, "U16", Integer.toString(((WitU16) v).value));
            return;
        }
        if (v instanceof WitU32) {
            writeTagged(sb, "U32", Long.toString(((WitU32) v).value));
            return;
        }
        if (v instanceof WitU64) {
            // u64 is encoded as an unsigned integer literal. Java Long can't
            // represent the full u64 range with `Long.toString`; use the
            // unsigned formatter.
            writeTagged(sb, "U64", Long.toUnsignedString(((WitU64) v).bits));
            return;
        }
        if (v instanceof Optional) {
            final Optional<?> opt = (Optional<?>) v;
            sb.append("{\"").append(TAG).append("\":\"Option\",\"").append(VAL).append("\":");
            if (opt.isPresent()) {
                writeJsonVal(sb, opt.get());
            } else {
                sb.append("null");
            }
            sb.append('}');
            return;
        }
        if (v instanceof WitResult) {
            final WitResult r = (WitResult) v;
            sb.append("{\"").append(TAG).append("\":\"Result\",\"").append(VAL).append("\":{");
            sb.append("\"is_ok\":").append(r.isOk);
            if (r.isOk && r.ok != null) {
                sb.append(",\"ok\":");
                writeJsonVal(sb, r.ok);
            } else if (!r.isOk && r.err != null) {
                sb.append(",\"err\":");
                writeJsonVal(sb, r.err);
            }
            sb.append("}}");
            return;
        }
        if (v instanceof WitVariant) {
            final WitVariant wv = (WitVariant) v;
            sb.append("{\"").append(TAG).append("\":\"Variant\",\"").append(VAL).append("\":{");
            sb.append("\"discriminant\":");
            writeJsonString(sb, wv.discriminant);
            if (wv.value != null) {
                sb.append(",\"value\":");
                writeJsonVal(sb, wv.value);
            }
            sb.append("}}");
            return;
        }
        if (v instanceof WitEnum) {
            sb.append("{\"").append(TAG).append("\":\"Enum\",\"").append(VAL).append("\":");
            writeJsonString(sb, ((WitEnum) v).discriminant);
            sb.append('}');
            return;
        }
        if (v instanceof Set) {
            final Set<?> set = (Set<?>) v;
            sb.append("{\"").append(TAG).append("\":\"Flags\",\"").append(VAL).append("\":[");
            boolean first = true;
            for (Object flag : set) {
                if (!first) sb.append(',');
                first = false;
                writeJsonString(sb, String.valueOf(flag));
            }
            sb.append("]}");
            return;
        }
        if (v instanceof WitResource) {
            // Wire form: { "t":"Resource", "v": { "table_id": <long>,
            // "type_name": "<string>", "owned": <bool> } }. Field names
            // MUST match the JsonVal::Resource variant in Rust
            // (wasmos-provider/native/src/lib.rs) exactly — serde
            // deserialization is field-name-driven.
            final WitResource r = (WitResource) v;
            sb.append("{\"").append(TAG).append("\":\"Resource\",\"").append(VAL).append("\":{");
            sb.append("\"table_id\":").append(r.tableId);
            sb.append(",\"type_name\":");
            writeJsonString(sb, r.typeIdentifier);
            sb.append(",\"owned\":").append(r.owned);
            sb.append("}}");
            return;
        }
        if (v instanceof WitFuture) {
            // Wire form: { "t":"Future", "v": { "table_id": <long>,
            // "type_name": "<string>" } }. Field names MUST match the
            // JsonVal::Future variant in Rust (wasmos-provider/native/src/lib.rs).
            // No `owned` bit here — see WitFuture javadoc; futures always
            // take on the Rust decode path.
            final WitFuture wf = (WitFuture) v;
            sb.append("{\"").append(TAG).append("\":\"Future\",\"").append(VAL).append("\":{");
            sb.append("\"table_id\":").append(wf.tableId);
            sb.append(",\"type_name\":");
            writeJsonString(sb, wf.typeIdentifier);
            sb.append("}}");
            return;
        }
        if (v instanceof WitStream) {
            // Wire form: { "t":"Stream", "v": { "table_id": <long>,
            // "type_name": "<string>" } }. Structural mirror of WitFuture.
            final WitStream ws = (WitStream) v;
            sb.append("{\"").append(TAG).append("\":\"Stream\",\"").append(VAL).append("\":{");
            sb.append("\"table_id\":").append(ws.tableId);
            sb.append(",\"type_name\":");
            writeJsonString(sb, ws.typeIdentifier);
            sb.append("}}");
            return;
        }
        if (v instanceof WitErrorContext) {
            // Wire form: { "t":"ErrorContext", "v": { "table_id": <long>,
            // "rep": <number> } }. Field names MUST match the
            // JsonVal::ErrorContext variant in Rust — the wasmtime rep is
            // a u32, we widen to Java long so it survives the wire form.
            final WitErrorContext we = (WitErrorContext) v;
            sb.append("{\"").append(TAG).append("\":\"ErrorContext\",\"").append(VAL).append("\":{");
            sb.append("\"table_id\":").append(we.tableId);
            sb.append(",\"rep\":").append(we.rep);
            sb.append("}}");
            return;
        }
        if (v instanceof WitMap) {
            // WIT map<k,v>: keys are typed JsonVals, so `[key_json,
            // value_json]` pairs — same layout as Val::Map(Vec<(Val, Val)>)
            // on the Rust side. Distinct from Record because Record's
            // wire layout uses raw JSON strings for the key half.
            final WitMap wm = (WitMap) v;
            sb.append("{\"").append(TAG).append("\":\"Map\",\"").append(VAL).append("\":[");
            boolean first = true;
            for (Map.Entry<Object, Object> e : wm.pairs.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('[');
                writeJsonVal(sb, e.getKey());
                sb.append(',');
                writeJsonVal(sb, e.getValue());
                sb.append(']');
            }
            sb.append("]}");
            return;
        }
        if (v instanceof Map) {
            final Map<?, ?> m = (Map<?, ?>) v;
            sb.append("{\"").append(TAG).append("\":\"Record\",\"").append(VAL).append("\":[");
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('[');
                writeJsonString(sb, String.valueOf(e.getKey()));
                sb.append(',');
                writeJsonVal(sb, e.getValue());
                sb.append(']');
            }
            sb.append("]}");
            return;
        }
        if (v instanceof Collection) {
            final Collection<?> c = (Collection<?>) v;
            sb.append("{\"").append(TAG).append("\":\"List\",\"").append(VAL).append("\":[");
            boolean first = true;
            for (Object it : c) {
                if (!first) sb.append(',');
                first = false;
                writeJsonVal(sb, it);
            }
            sb.append("]}");
            return;
        }
        throw new IllegalArgumentException(
                "wasmos-provider marshalling: unsupported Java type "
                        + v.getClass().getName());
    }

    private static void writeTagged(StringBuilder sb, String tag, String val) {
        sb.append("{\"").append(TAG).append("\":\"").append(tag).append("\",\"")
                .append(VAL).append("\":").append(val).append('}');
    }

    private static void writeJsonString(StringBuilder sb, String s) {
        sb.append('"');
        final int n = s.length();
        for (int i = 0; i < n; i++) {
            final char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ---- Reader ------------------------------------------------------------

    /**
     * Read a single tagged JsonVal object from the parser at its current
     * position (after any leading whitespace). Returns a Java-natural object.
     */
    private static Object readJsonVal(Parser p) {
        p.expect('{');
        p.skipWs();
        String tag = null;
        String rawValue = null;
        int rawValueStart = -1;
        int rawValueEnd = -1;
        // Two-field object: {"t":"...", "v":...}. Values can be null.
        boolean sawT = false;
        boolean sawV = false;
        while (true) {
            p.skipWs();
            final String key = p.readString();
            p.skipWs();
            p.expect(':');
            p.skipWs();
            if (TAG.equals(key)) {
                tag = p.readString();
                sawT = true;
            } else if (VAL.equals(key)) {
                rawValueStart = p.pos;
                p.skipJsonValue();
                rawValueEnd = p.pos;
                rawValue = p.source.substring(rawValueStart, rawValueEnd);
                sawV = true;
            } else {
                // Unknown field — skip its value to stay forward-compatible.
                p.skipJsonValue();
            }
            p.skipWs();
            if (p.peek() == ',') {
                p.consume();
                continue;
            }
            p.expect('}');
            break;
        }
        if (!sawT) {
            throw new IllegalArgumentException("missing '" + TAG + "' at pos " + p.pos);
        }
        if (!sawV) {
            // No `v` field. Only meaningful for unit-payload variants — treat
            // as null.
            rawValue = "null";
        }
        return decodeTag(tag, rawValue);
    }

    private static Object decodeTag(String tag, String rawValue) {
        switch (tag) {
            case "Bool":
                return Boolean.valueOf("true".equalsIgnoreCase(rawValue.trim()));
            case "S8":
                return Byte.parseByte(rawValue.trim());
            case "S16":
                return Short.parseShort(rawValue.trim());
            case "S32":
                return Integer.parseInt(rawValue.trim());
            case "S64":
                return Long.parseLong(rawValue.trim());
            case "U8":
                return (short) Integer.parseInt(rawValue.trim());
            case "U16":
                return Integer.parseInt(rawValue.trim());
            case "U32":
                return Long.parseLong(rawValue.trim());
            case "U64":
                return Long.parseUnsignedLong(rawValue.trim());
            case "F32":
                return Float.parseFloat(rawValue.trim());
            case "F64":
                return Double.parseDouble(rawValue.trim());
            case "Char": {
                // Encoded as an integer codepoint.
                final int cp = Integer.parseInt(rawValue.trim());
                return (char) cp;
            }
            case "String": {
                final Parser p = new Parser(rawValue);
                p.skipWs();
                return p.readString();
            }
            case "Bytes": {
                final Parser p = new Parser(rawValue);
                p.skipWs();
                final String b64 = p.readString();
                return Base64.getDecoder().decode(b64);
            }
            case "List": {
                final Parser p = new Parser(rawValue);
                p.skipWs();
                return readJsonArray(p);
            }
            case "Option": {
                final String trimmed = rawValue.trim();
                if ("null".equals(trimmed)) {
                    return Optional.empty();
                }
                final Parser p = new Parser(rawValue);
                p.skipWs();
                return Optional.of(readJsonVal(p));
            }
            case "Result": {
                final Parser p = new Parser(rawValue);
                p.skipWs();
                p.expect('{');
                boolean isOk = false;
                Object ok = null;
                Object err = null;
                while (true) {
                    p.skipWs();
                    final String key = p.readString();
                    p.skipWs();
                    p.expect(':');
                    p.skipWs();
                    if ("is_ok".equals(key)) {
                        final String bool = p.readBareLiteral();
                        isOk = "true".equalsIgnoreCase(bool);
                    } else if ("ok".equals(key)) {
                        if (p.peek() == 'n') {
                            p.readBareLiteral(); // "null"
                        } else {
                            ok = readJsonVal(p);
                        }
                    } else if ("err".equals(key)) {
                        if (p.peek() == 'n') {
                            p.readBareLiteral();
                        } else {
                            err = readJsonVal(p);
                        }
                    } else {
                        p.skipJsonValue();
                    }
                    p.skipWs();
                    if (p.peek() == ',') {
                        p.consume();
                        continue;
                    }
                    p.expect('}');
                    break;
                }
                return isOk ? WitResult.ok(ok) : WitResult.err(err);
            }
            case "Record": {
                final Parser p = new Parser(rawValue);
                p.skipWs();
                p.expect('[');
                final Map<String, Object> out = new LinkedHashMap<>();
                p.skipWs();
                if (p.peek() == ']') {
                    p.consume();
                    return out;
                }
                while (true) {
                    p.skipWs();
                    p.expect('[');
                    p.skipWs();
                    final String key = p.readString();
                    p.skipWs();
                    p.expect(',');
                    p.skipWs();
                    final Object value = readJsonVal(p);
                    p.skipWs();
                    p.expect(']');
                    out.put(key, value);
                    p.skipWs();
                    if (p.peek() == ',') {
                        p.consume();
                        continue;
                    }
                    p.expect(']');
                    return out;
                }
            }
            case "Tuple": {
                final Parser p = new Parser(rawValue);
                p.skipWs();
                return readJsonArray(p);
            }
            case "Variant": {
                final Parser p = new Parser(rawValue);
                p.skipWs();
                p.expect('{');
                String disc = null;
                Object value = null;
                while (true) {
                    p.skipWs();
                    final String key = p.readString();
                    p.skipWs();
                    p.expect(':');
                    p.skipWs();
                    if ("discriminant".equals(key)) {
                        disc = p.readString();
                    } else if ("value".equals(key)) {
                        if (p.peek() == 'n') {
                            p.readBareLiteral();
                        } else {
                            value = readJsonVal(p);
                        }
                    } else {
                        p.skipJsonValue();
                    }
                    p.skipWs();
                    if (p.peek() == ',') {
                        p.consume();
                        continue;
                    }
                    p.expect('}');
                    break;
                }
                return new WitVariant(disc, value);
            }
            case "Enum": {
                final Parser p = new Parser(rawValue);
                p.skipWs();
                return new WitEnum(p.readString());
            }
            case "Map": {
                // Wire form: [[key_json, value_json], ...]. Keys are decoded
                // as full JsonVals rather than raw strings; we surface the
                // result as a plain LinkedHashMap so callers get natural
                // Java iteration semantics (the WitMap wrapper is only
                // needed on the outgoing side to disambiguate from Record).
                final Parser p = new Parser(rawValue);
                p.skipWs();
                p.expect('[');
                final Map<Object, Object> out = new LinkedHashMap<>();
                p.skipWs();
                if (p.peek() == ']') {
                    p.consume();
                    return out;
                }
                while (true) {
                    p.skipWs();
                    p.expect('[');
                    p.skipWs();
                    final Object key = readJsonVal(p);
                    p.skipWs();
                    p.expect(',');
                    p.skipWs();
                    final Object value = readJsonVal(p);
                    p.skipWs();
                    p.expect(']');
                    out.put(key, value);
                    p.skipWs();
                    if (p.peek() == ',') {
                        p.consume();
                        continue;
                    }
                    p.expect(']');
                    return out;
                }
            }
            case "Future": {
                // Object payload: { "table_id": <number>, "type_name": <str> }.
                // Fields may arrive in any order; unknown fields are skipped
                // for forward-compat. See the JsonVal::Future doc comment
                // for why there's no `owned` bit here.
                final Parser p = new Parser(rawValue);
                p.skipWs();
                p.expect('{');
                long tid = 0L;
                String typeName = "";
                boolean sawTableId = false;
                while (true) {
                    p.skipWs();
                    final String key = p.readString();
                    p.skipWs();
                    p.expect(':');
                    p.skipWs();
                    if ("table_id".equals(key)) {
                        tid = Long.parseLong(p.readBareLiteral().trim());
                        sawTableId = true;
                    } else if ("type_name".equals(key)) {
                        typeName = p.readString();
                    } else {
                        p.skipJsonValue();
                    }
                    p.skipWs();
                    if (p.peek() == ',') {
                        p.consume();
                        continue;
                    }
                    p.expect('}');
                    break;
                }
                if (!sawTableId) {
                    throw new IllegalArgumentException(
                            "Future JsonVal missing required 'table_id' field");
                }
                return new WitFuture(tid, typeName);
            }
            case "Stream": {
                // Structural mirror of the Future arm — same field names,
                // same forward-compat unknown-key skip, same required-field
                // set. Kept as a separate case (rather than sharing a helper)
                // so failure messages name the WIT shape the caller was
                // handed.
                final Parser p = new Parser(rawValue);
                p.skipWs();
                p.expect('{');
                long tid = 0L;
                String typeName = "";
                boolean sawTableId = false;
                while (true) {
                    p.skipWs();
                    final String key = p.readString();
                    p.skipWs();
                    p.expect(':');
                    p.skipWs();
                    if ("table_id".equals(key)) {
                        tid = Long.parseLong(p.readBareLiteral().trim());
                        sawTableId = true;
                    } else if ("type_name".equals(key)) {
                        typeName = p.readString();
                    } else {
                        p.skipJsonValue();
                    }
                    p.skipWs();
                    if (p.peek() == ',') {
                        p.consume();
                        continue;
                    }
                    p.expect('}');
                    break;
                }
                if (!sawTableId) {
                    throw new IllegalArgumentException(
                            "Stream JsonVal missing required 'table_id' field");
                }
                return new WitStream(tid, typeName);
            }
            case "ErrorContext": {
                // Object payload: { "table_id": <number>, "rep": <number> }.
                // The `rep` field is a u32 on the Rust side; Java carries it
                // as long so the whole u32 range survives without silent
                // truncation.
                final Parser p = new Parser(rawValue);
                p.skipWs();
                p.expect('{');
                long tid = 0L;
                long rep = 0L;
                boolean sawTableId = false;
                while (true) {
                    p.skipWs();
                    final String key = p.readString();
                    p.skipWs();
                    p.expect(':');
                    p.skipWs();
                    if ("table_id".equals(key)) {
                        tid = Long.parseLong(p.readBareLiteral().trim());
                        sawTableId = true;
                    } else if ("rep".equals(key)) {
                        rep = Long.parseLong(p.readBareLiteral().trim());
                    } else {
                        p.skipJsonValue();
                    }
                    p.skipWs();
                    if (p.peek() == ',') {
                        p.consume();
                        continue;
                    }
                    p.expect('}');
                    break;
                }
                if (!sawTableId) {
                    throw new IllegalArgumentException(
                            "ErrorContext JsonVal missing required 'table_id' field");
                }
                return new WitErrorContext(tid, rep);
            }
            case "Resource": {
                // Object payload: { "table_id": <number>, "type_name": <str>,
                // "owned": <bool> }. Fields may arrive in any order (serde
                // doesn't guarantee ordering across versions). Unknown fields
                // are skipped for forward-compat with future wire additions.
                final Parser p = new Parser(rawValue);
                p.skipWs();
                p.expect('{');
                long tid = 0L;
                String typeName = "";
                boolean owned = false;
                boolean sawTableId = false;
                while (true) {
                    p.skipWs();
                    final String key = p.readString();
                    p.skipWs();
                    p.expect(':');
                    p.skipWs();
                    if ("table_id".equals(key)) {
                        tid = Long.parseLong(p.readBareLiteral().trim());
                        sawTableId = true;
                    } else if ("type_name".equals(key)) {
                        typeName = p.readString();
                    } else if ("owned".equals(key)) {
                        owned = "true".equalsIgnoreCase(p.readBareLiteral().trim());
                    } else {
                        p.skipJsonValue();
                    }
                    p.skipWs();
                    if (p.peek() == ',') {
                        p.consume();
                        continue;
                    }
                    p.expect('}');
                    break;
                }
                if (!sawTableId) {
                    throw new IllegalArgumentException(
                            "Resource JsonVal missing required 'table_id' field");
                }
                return new WitResource(tid, typeName, owned);
            }
            case "Flags": {
                final Parser p = new Parser(rawValue);
                p.skipWs();
                p.expect('[');
                final List<String> out = new ArrayList<>();
                p.skipWs();
                if (p.peek() == ']') {
                    p.consume();
                    return new java.util.LinkedHashSet<>(out);
                }
                while (true) {
                    p.skipWs();
                    out.add(p.readString());
                    p.skipWs();
                    if (p.peek() == ',') {
                        p.consume();
                        continue;
                    }
                    p.expect(']');
                    return new java.util.LinkedHashSet<>(out);
                }
            }
            default:
                throw new IllegalArgumentException(
                        "wasmos-provider marshalling: unknown JsonVal tag '" + tag + "'");
        }
    }

    private static List<Object> readJsonArray(Parser p) {
        p.expect('[');
        p.skipWs();
        final List<Object> out = new ArrayList<>();
        if (p.peek() == ']') {
            p.consume();
            return out;
        }
        while (true) {
            p.skipWs();
            out.add(readJsonVal(p));
            p.skipWs();
            if (p.peek() == ',') {
                p.consume();
                continue;
            }
            p.expect(']');
            return out;
        }
    }

    // ---- Minimal JSON parser ----------------------------------------------

    /**
     * Hand-rolled JSON parser tuned for the tightly-scoped JsonVal schema.
     * Not a general-purpose JSON reader; skips validation that would be
     * dead code on Rust-produced input (we trust the sibling serde emitter).
     */
    private static final class Parser {

        final String source;
        int pos;

        Parser(String source) {
            this.source = source;
            this.pos = 0;
        }

        char peek() {
            if (pos >= source.length()) {
                throw new IllegalArgumentException("unexpected end of JSON at pos " + pos);
            }
            return source.charAt(pos);
        }

        void consume() { pos++; }

        void expect(char c) {
            if (pos >= source.length()) {
                throw new IllegalArgumentException(
                        "expected '" + c + "' at pos " + pos + " but hit end of input");
            }
            if (source.charAt(pos) != c) {
                throw new IllegalArgumentException(
                        "expected '" + c + "' at pos " + pos + " but got '"
                                + source.charAt(pos) + "'");
            }
            pos++;
        }

        void skipWs() {
            while (pos < source.length()) {
                final char c = source.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    return;
                }
            }
        }

        String readString() {
            if (source.charAt(pos) != '"') {
                throw new IllegalArgumentException(
                        "expected '\"' at pos " + pos + " but got '" + source.charAt(pos) + "'");
            }
            pos++;
            final StringBuilder sb = new StringBuilder();
            while (pos < source.length()) {
                final char c = source.charAt(pos);
                if (c == '"') {
                    pos++;
                    return sb.toString();
                }
                if (c == '\\') {
                    pos++;
                    final char esc = source.charAt(pos);
                    pos++;
                    switch (esc) {
                        case '"':  sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/':  sb.append('/'); break;
                        case 'b':  sb.append('\b'); break;
                        case 'f':  sb.append('\f'); break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
                        case 'u': {
                            final String hex = source.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                            break;
                        }
                        default:
                            throw new IllegalArgumentException(
                                    "invalid escape \\" + esc + " at pos " + pos);
                    }
                    continue;
                }
                sb.append(c);
                pos++;
            }
            throw new IllegalArgumentException("unterminated string at pos " + pos);
        }

        /** Read a bareword literal (true/false/null/number) up to a
         *  delimiter (comma / brace / bracket / whitespace / EOF). */
        String readBareLiteral() {
            final int start = pos;
            while (pos < source.length()) {
                final char c = source.charAt(pos);
                if (c == ',' || c == '}' || c == ']' || c == ' ' || c == '\t'
                        || c == '\n' || c == '\r') {
                    break;
                }
                pos++;
            }
            return source.substring(start, pos);
        }

        /**
         * Advance past a JSON value (string / number / object / array /
         * true / false / null) without decoding it. Used to slice out the
         * raw substring of a `"v"` field.
         */
        void skipJsonValue() {
            skipWs();
            if (pos >= source.length()) return;
            final char c = source.charAt(pos);
            if (c == '"') {
                readString();
                return;
            }
            if (c == '{') {
                pos++;
                int depth = 1;
                boolean inString = false;
                boolean esc = false;
                while (pos < source.length() && depth > 0) {
                    final char ch = source.charAt(pos);
                    if (inString) {
                        if (esc) {
                            esc = false;
                        } else if (ch == '\\') {
                            esc = true;
                        } else if (ch == '"') {
                            inString = false;
                        }
                    } else {
                        if (ch == '"') inString = true;
                        else if (ch == '{') depth++;
                        else if (ch == '}') depth--;
                    }
                    pos++;
                }
                return;
            }
            if (c == '[') {
                pos++;
                int depth = 1;
                boolean inString = false;
                boolean esc = false;
                while (pos < source.length() && depth > 0) {
                    final char ch = source.charAt(pos);
                    if (inString) {
                        if (esc) {
                            esc = false;
                        } else if (ch == '\\') {
                            esc = true;
                        } else if (ch == '"') {
                            inString = false;
                        }
                    } else {
                        if (ch == '"') inString = true;
                        else if (ch == '[') depth++;
                        else if (ch == ']') depth--;
                    }
                    pos++;
                }
                return;
            }
            readBareLiteral();
        }
    }

    // Kept for symmetry with the Rust side's exposure of raw bytes — unused
    // by the marshalling helpers themselves but handy for tests that want to
    // reconstruct the wire form byte-for-byte.
    static byte[] asUtf8(String s) {
        return s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8);
    }
}
