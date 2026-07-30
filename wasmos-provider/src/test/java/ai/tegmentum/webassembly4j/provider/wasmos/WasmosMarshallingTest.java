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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Round-trip tests for {@link WasmosMarshalling} — Java object -> JSON -> Java
 * object.
 *
 * <p>The full end-to-end path is Java -> JSON -> Rust {@code JsonVal} -> {@code Val}
 * -> wasmtime -> {@code Val} -> {@code JsonVal} -> JSON -> Java. This test
 * covers the Java-side halves; the Rust-side round-trip is validated by the
 * {@code #[cfg(test)]} module in {@code wasmos-provider/native/src/lib.rs}.
 */
final class WasmosMarshallingTest {

    /**
     * Round-trip a single Java value through marshal(args) + unmarshal(results),
     * treating results as if it were the args JSON blob (they share the exact
     * same JsonVal-array schema). Returns the round-tripped Java object.
     */
    private static Object roundTrip(Object v) {
        final String json = WasmosMarshalling.marshalArgs(new Object[] { v });
        final List<Object> back = WasmosMarshalling.unmarshalResults(json);
        assertEquals(1, back.size(), () -> "expected exactly one result for " + json);
        return back.get(0);
    }

    @Test
    @DisplayName("primitive round-trips preserve type + value")
    void primitives() {
        assertEquals(Boolean.TRUE, roundTrip(Boolean.TRUE));
        assertEquals(Boolean.FALSE, roundTrip(Boolean.FALSE));
        assertEquals((byte) -7, roundTrip((byte) -7));
        assertEquals((short) 1234, roundTrip((short) 1234));
        assertEquals(-42, roundTrip(-42));
        assertEquals(1_000_000_000_000L, roundTrip(1_000_000_000_000L));
        assertEquals(1.5f, roundTrip(1.5f));
        assertEquals(-2.25, roundTrip(-2.25));
        assertEquals('Z', roundTrip('Z'));
        assertEquals("hello", roundTrip("hello"));
    }

    @Test
    @DisplayName("string round-trip handles quotes, backslashes, newlines, unicode BMP")
    void stringEscapes() {
        assertEquals("he said \"hi\"", roundTrip("he said \"hi\""));
        assertEquals("a\\b", roundTrip("a\\b"));
        assertEquals("line1\nline2", roundTrip("line1\nline2"));
        assertEquals("tab\there", roundTrip("tab\there"));
        assertEquals("Ω hello", roundTrip("Ω hello"));
    }

    @Test
    @DisplayName("byte[] round-trip preserves bytes (base64 wire form)")
    void bytesFastPath() {
        final byte[] input = { 0, 1, 2, 3, 127, -1, -128 };
        final Object round = roundTrip(input);
        assertInstanceOf(byte[].class, round);
        assertArrayEquals(input, (byte[]) round);
    }

    @Test
    @DisplayName("empty byte[] round-trips as empty byte[]")
    void bytesEmpty() {
        final Object round = roundTrip(new byte[0]);
        assertInstanceOf(byte[].class, round);
        assertEquals(0, ((byte[]) round).length);
    }

    @Test
    @DisplayName("unsigned wrapper types round-trip through their tagged forms")
    void unsignedWrappers() {
        assertEquals((short) 200, roundTrip(new WasmosMarshalling.WitU8(200)));
        assertEquals(60_000, roundTrip(new WasmosMarshalling.WitU16(60_000)));
        assertEquals(4_000_000_000L, roundTrip(new WasmosMarshalling.WitU32(4_000_000_000L)));
        // u64 uses unsigned string encoding — round-trip preserves the exact bit pattern.
        assertEquals(-1L, roundTrip(new WasmosMarshalling.WitU64(-1L))); // 0xFFFF...FFFF
    }

    @Test
    @DisplayName("Optional.of + Optional.empty round-trip")
    void optionRoundtrip() {
        final Object present = roundTrip(Optional.of(42));
        assertInstanceOf(Optional.class, present);
        assertEquals(Optional.of(42), present);

        final Object empty = roundTrip(Optional.empty());
        assertInstanceOf(Optional.class, empty);
        assertFalse(((Optional<?>) empty).isPresent());
    }

    @Test
    @DisplayName("null argument round-trips as Optional.empty")
    void nullBecomesEmptyOption() {
        final Object round = roundTrip(null);
        assertInstanceOf(Optional.class, round);
        assertFalse(((Optional<?>) round).isPresent());
    }

    @Test
    @DisplayName("List round-trip preserves element order + types")
    void listRoundtrip() {
        final List<Object> input = Arrays.asList("alpha", "beta", "gamma");
        final Object round = roundTrip(input);
        assertInstanceOf(List.class, round);
        assertEquals(input, round);
    }

    @Test
    @DisplayName("Record (Map) round-trip preserves insertion order")
    void recordRoundtrip() {
        final Map<String, Object> input = new LinkedHashMap<>();
        input.put("name", "alice");
        input.put("age", 30);
        input.put("greeting", "hi");
        final Object round = roundTrip(input);
        assertInstanceOf(Map.class, round);
        assertEquals(new ArrayList<>(input.keySet()),
                new ArrayList<>(((Map<?, ?>) round).keySet()),
                "record field iteration order should be preserved for wasmtime lookup");
        assertEquals(input, round);
    }

    @Test
    @DisplayName("Set round-trip as WIT flags")
    void flagsRoundtrip() {
        final Set<String> input = new LinkedHashSet<>();
        input.add("read");
        input.add("write");
        final Object round = roundTrip(input);
        assertInstanceOf(Set.class, round);
        assertEquals(input, round);
    }

    @Test
    @DisplayName("Result carriers round-trip both branches")
    void resultRoundtrip() {
        final Object ok = roundTrip(WasmosMarshalling.WitResult.ok(7));
        assertInstanceOf(WasmosMarshalling.WitResult.class, ok);
        assertTrue(((WasmosMarshalling.WitResult) ok).isOk);
        assertEquals(7, ((WasmosMarshalling.WitResult) ok).ok);

        final Object err = roundTrip(WasmosMarshalling.WitResult.err("bad"));
        assertInstanceOf(WasmosMarshalling.WitResult.class, err);
        assertFalse(((WasmosMarshalling.WitResult) err).isOk);
        assertEquals("bad", ((WasmosMarshalling.WitResult) err).err);

        // Unit-payload branches
        final Object okUnit = roundTrip(WasmosMarshalling.WitResult.ok(null));
        assertNull(((WasmosMarshalling.WitResult) okUnit).ok);
        assertTrue(((WasmosMarshalling.WitResult) okUnit).isOk);
    }

    @Test
    @DisplayName("Variant + enum carriers survive round-trip")
    void variantEnumRoundtrip() {
        final Object variant = roundTrip(new WasmosMarshalling.WitVariant("some", 42));
        assertInstanceOf(WasmosMarshalling.WitVariant.class, variant);
        assertEquals("some", ((WasmosMarshalling.WitVariant) variant).discriminant);
        assertEquals(42, ((WasmosMarshalling.WitVariant) variant).value);

        final Object bareVariant = roundTrip(new WasmosMarshalling.WitVariant("none", null));
        assertNull(((WasmosMarshalling.WitVariant) bareVariant).value);

        final Object enumVal = roundTrip(new WasmosMarshalling.WitEnum("red"));
        assertInstanceOf(WasmosMarshalling.WitEnum.class, enumVal);
        assertEquals("red", ((WasmosMarshalling.WitEnum) enumVal).discriminant);
    }

    @Test
    @DisplayName("empty args JSON is the literal '[]'")
    void emptyArgs() {
        assertEquals("[]", WasmosMarshalling.marshalArgs(null));
        assertEquals("[]", WasmosMarshalling.marshalArgs(new Object[0]));
    }

    @Test
    @DisplayName("empty results JSON parses to empty list")
    void emptyResults() {
        assertEquals(0, WasmosMarshalling.unmarshalResults("[]").size());
    }

    @Test
    @DisplayName("nested list-of-lists round-trip")
    void nestedList() {
        final List<Object> nested = Arrays.asList(
                Arrays.asList(1, 2, 3),
                Arrays.asList("a", "b"));
        final Object round = roundTrip(nested);
        assertInstanceOf(List.class, round);
        assertEquals(nested, round);
    }

    @Test
    @DisplayName("WitMap<Integer, String> round-trip preserves typed keys + order")
    void witMapWithIntegerKeys() {
        // Distinct from Record: keys are Integer (not String) so plain
        // Map<?,?> can't be used. WitMap forces the Val::Map branch.
        final Map<Object, Object> input = new LinkedHashMap<>();
        input.put(1, "one");
        input.put(2, "two");
        input.put(3, "three");
        final Object round = roundTrip(new WasmosMarshalling.WitMap(input));
        assertInstanceOf(Map.class, round);
        final Map<?, ?> back = (Map<?, ?>) round;
        assertEquals(input.size(), back.size());
        assertEquals("one", back.get(1));
        assertEquals("two", back.get(2));
        assertEquals("three", back.get(3));
        // Order preservation matters for anyone using WitMap as a
        // deterministic ordered container.
        assertEquals(new java.util.ArrayList<>(input.keySet()),
                new java.util.ArrayList<>(back.keySet()));
    }

    @Test
    @DisplayName("WitMap<String, byte[]> round-trips byte[] values through Bytes fast path")
    void witMapWithByteArrayValues() {
        final Map<Object, Object> input = new LinkedHashMap<>();
        input.put("first", new byte[] { 1, 2, 3 });
        input.put("second", new byte[] { -1, 0, 127 });
        final Object round = roundTrip(new WasmosMarshalling.WitMap(input));
        assertInstanceOf(Map.class, round);
        final Map<?, ?> back = (Map<?, ?>) round;
        assertArrayEquals(new byte[] { 1, 2, 3 }, (byte[]) back.get("first"));
        assertArrayEquals(new byte[] { -1, 0, 127 }, (byte[]) back.get("second"));
    }

    @Test
    @DisplayName("WitMap with empty pairs round-trips as empty Map")
    void witMapEmpty() {
        final Object round = roundTrip(new WasmosMarshalling.WitMap(new LinkedHashMap<>()));
        assertInstanceOf(Map.class, round);
        assertTrue(((Map<?, ?>) round).isEmpty());
    }

    @Test
    @DisplayName("WitResource round-trips tableId + typeIdentifier + owned")
    void witResourceRoundtrip() {
        // Owned handle — matches what a guest return of `own<T>` produces
        // on the Rust side. Round-trip must preserve every field byte-for-byte
        // because Java uses tableId to re-find the parked ResourceAny.
        final WasmosMarshalling.WitResource owned = new WasmosMarshalling.WitResource(
                42L, "ResourceType { kind: Guest { component: 0, resource: 3 } }", true);
        final Object roundOwned = roundTrip(owned);
        assertInstanceOf(WasmosMarshalling.WitResource.class, roundOwned);
        final WasmosMarshalling.WitResource ownedBack =
                (WasmosMarshalling.WitResource) roundOwned;
        assertEquals(42L, ownedBack.tableId());
        assertEquals("ResourceType { kind: Guest { component: 0, resource: 3 } }",
                ownedBack.typeIdentifier());
        assertTrue(ownedBack.owned());
        // Equals / hashCode consistency — WitResource is used as a value
        // handle so structural equality matters for anyone parking it in a
        // Set / Map.
        assertEquals(owned, ownedBack);
        assertEquals(owned.hashCode(), ownedBack.hashCode());
    }

    @Test
    @DisplayName("WitResource borrow-flavored round-trip preserves owned=false")
    void witResourceBorrowRoundtrip() {
        // Same shape as the owned case but with owned=false — matches a
        // WIT `borrow<T>` on the way OUT. Round-trip must not silently
        // upgrade to owned; the Rust side keys on this to decide
        // take-vs-peek on the way back IN.
        final WasmosMarshalling.WitResource borrow = new WasmosMarshalling.WitResource(
                7L, "my-type", false);
        final Object round = roundTrip(borrow);
        assertInstanceOf(WasmosMarshalling.WitResource.class, round);
        final WasmosMarshalling.WitResource back =
                (WasmosMarshalling.WitResource) round;
        assertEquals(7L, back.tableId());
        assertEquals("my-type", back.typeIdentifier());
        assertFalse(back.owned());
    }

    @Test
    @DisplayName("WitResource with large tableId (u64-adjacent) round-trips")
    void witResourceLargeTableId() {
        // Rust-side ids are u64; Java carries them as long. The wire form
        // must survive a u64-ish value without overflow — pick something
        // > Integer.MAX_VALUE.
        final long largeId = 9_999_999_999L;
        final WasmosMarshalling.WitResource wr = new WasmosMarshalling.WitResource(
                largeId, "T", true);
        final Object round = roundTrip(wr);
        assertEquals(largeId,
                ((WasmosMarshalling.WitResource) round).tableId());
    }

    @Test
    @DisplayName("WitResource in a nested List round-trips through container marshalling")
    void witResourceNestedInList() {
        // Resources can appear as elements of collections just like any
        // other WitVal — verify the Resource writer + reader arms compose
        // with List. This is the shape a `list<own<T>>` return would
        // produce.
        final List<WasmosMarshalling.WitResource> items = Arrays.asList(
                new WasmosMarshalling.WitResource(1L, "T", true),
                new WasmosMarshalling.WitResource(2L, "T", false),
                new WasmosMarshalling.WitResource(3L, "T", true));
        final Object round = roundTrip(items);
        assertInstanceOf(List.class, round);
        final List<?> back = (List<?>) round;
        assertEquals(3, back.size());
        assertEquals(new WasmosMarshalling.WitResource(1L, "T", true), back.get(0));
        assertEquals(new WasmosMarshalling.WitResource(2L, "T", false), back.get(1));
        assertEquals(new WasmosMarshalling.WitResource(3L, "T", true), back.get(2));
    }

    @Test
    @DisplayName("WitResource typeIdentifier with special JSON chars round-trips")
    void witResourceEscapedTypeIdentifier() {
        // Debug output for ResourceType contains braces and colons — those
        // don't need escaping in JSON strings, but quotes and backslashes
        // do. The Debug format is unlikely to contain quotes, but be
        // defensive so a future wasmtime change doesn't silently corrupt
        // the type identifier field on the wire.
        final WasmosMarshalling.WitResource wr = new WasmosMarshalling.WitResource(
                1L, "quoted \"value\" with \\ backslash", false);
        final Object round = roundTrip(wr);
        assertEquals("quoted \"value\" with \\ backslash",
                ((WasmosMarshalling.WitResource) round).typeIdentifier());
    }

    @Test
    @DisplayName("WitFuture round-trips tableId + typeIdentifier")
    void witFutureRoundtrip() {
        // Return-direction shape: a WitFuture parked by the Rust decoder,
        // handed to Java, then round-tripped through the marshalling layer.
        // Every field must survive byte-for-byte — tableId is what Java uses
        // to re-find the parked FutureAny (or call futureClose against it).
        final WasmosMarshalling.WitFuture wf = new WasmosMarshalling.WitFuture(
                7L, "FutureAny { id: TableId(3), ty: Guest(Future(u32)) }");
        final Object round = roundTrip(wf);
        assertInstanceOf(WasmosMarshalling.WitFuture.class, round);
        final WasmosMarshalling.WitFuture back = (WasmosMarshalling.WitFuture) round;
        assertEquals(7L, back.tableId());
        assertEquals("FutureAny { id: TableId(3), ty: Guest(Future(u32)) }",
                back.typeIdentifier());
        // Value-shape equality — a WitFuture might be parked in a Set / Map
        // by callers doing bookkeeping across multiple futures, so equals /
        // hashCode consistency matters.
        assertEquals(wf, back);
        assertEquals(wf.hashCode(), back.hashCode());
    }

    @Test
    @DisplayName("WitFuture with large tableId (u64-adjacent) round-trips")
    void witFutureLargeTableId() {
        // Rust-side ids are u64 monotonic; Java carries them as long. The
        // wire form must survive the >Integer.MAX_VALUE range without any
        // silent truncation.
        final long largeId = 12_345_678_901L;
        final WasmosMarshalling.WitFuture wf = new WasmosMarshalling.WitFuture(
                largeId, "F");
        final Object round = roundTrip(wf);
        assertEquals(largeId,
                ((WasmosMarshalling.WitFuture) round).tableId());
    }

    @Test
    @DisplayName("WitFuture in a nested List round-trips through container marshalling")
    void witFutureNestedInList() {
        // A guest returning `list<future<T>>` produces exactly this shape;
        // verify the Future writer + reader arms compose with List. Same
        // regression guarantee as the WitResource nested-list test — proves
        // the marshalling arm plays with the container arms cleanly.
        final List<WasmosMarshalling.WitFuture> items = Arrays.asList(
                new WasmosMarshalling.WitFuture(1L, "F"),
                new WasmosMarshalling.WitFuture(2L, "F"),
                new WasmosMarshalling.WitFuture(3L, "F"));
        final Object round = roundTrip(items);
        assertInstanceOf(List.class, round);
        final List<?> back = (List<?>) round;
        assertEquals(3, back.size());
        assertEquals(new WasmosMarshalling.WitFuture(1L, "F"), back.get(0));
        assertEquals(new WasmosMarshalling.WitFuture(2L, "F"), back.get(1));
        assertEquals(new WasmosMarshalling.WitFuture(3L, "F"), back.get(2));
    }

    @Test
    @DisplayName("WitFuture wire form uses \"Future\" tag (matches Rust JsonVal::Future)")
    void witFutureWireFormat() {
        // Explicit wire-format check — the Rust JsonVal enum is
        // #[serde(tag = "t", content = "v")] and the tag string must match
        // byte-for-byte. A tag rename here would silently break the JNI
        // round-trip; guard against it.
        final WasmosMarshalling.WitFuture wf = new WasmosMarshalling.WitFuture(
                42L, "type-hint");
        final String wire = WasmosMarshalling.marshalArgs(new Object[] { wf });
        assertTrue(wire.contains("\"Future\""),
                "WitFuture wire form must use tag=\"Future\"; wire=" + wire);
        assertTrue(wire.contains("\"table_id\":42"),
                "wire form must expose table_id verbatim; wire=" + wire);
        assertTrue(wire.contains("\"type_name\":\"type-hint\""),
                "wire form must expose type_name verbatim; wire=" + wire);
    }

    @Test
    @DisplayName("WitStream round-trips tableId + typeIdentifier")
    void witStreamRoundtrip() {
        // Return-direction shape: WitStream parked by the Rust decoder,
        // handed to Java, then round-tripped through the marshalling layer.
        // tableId must survive byte-for-byte — Java uses it to re-find the
        // parked StreamAny or to close it.
        final WasmosMarshalling.WitStream ws = new WasmosMarshalling.WitStream(
                9L, "StreamAny { id: TableId(5), ty: Guest(Stream(u32)) }");
        final Object round = roundTrip(ws);
        assertInstanceOf(WasmosMarshalling.WitStream.class, round);
        final WasmosMarshalling.WitStream back = (WasmosMarshalling.WitStream) round;
        assertEquals(9L, back.tableId());
        assertEquals("StreamAny { id: TableId(5), ty: Guest(Stream(u32)) }",
                back.typeIdentifier());
        assertEquals(ws, back);
        assertEquals(ws.hashCode(), back.hashCode());
    }

    @Test
    @DisplayName("WitStream with large tableId (u64-adjacent) round-trips")
    void witStreamLargeTableId() {
        final long largeId = 8_888_888_888L;
        final WasmosMarshalling.WitStream ws = new WasmosMarshalling.WitStream(largeId, "S");
        final Object round = roundTrip(ws);
        assertEquals(largeId,
                ((WasmosMarshalling.WitStream) round).tableId());
    }

    @Test
    @DisplayName("WitStream in a nested List round-trips through container marshalling")
    void witStreamNestedInList() {
        // list<stream<T>> return shape — validates the Stream writer +
        // reader arms compose with container arms.
        final List<WasmosMarshalling.WitStream> items = Arrays.asList(
                new WasmosMarshalling.WitStream(1L, "S"),
                new WasmosMarshalling.WitStream(2L, "S"),
                new WasmosMarshalling.WitStream(3L, "S"));
        final Object round = roundTrip(items);
        assertInstanceOf(List.class, round);
        final List<?> back = (List<?>) round;
        assertEquals(3, back.size());
        assertEquals(new WasmosMarshalling.WitStream(1L, "S"), back.get(0));
        assertEquals(new WasmosMarshalling.WitStream(2L, "S"), back.get(1));
        assertEquals(new WasmosMarshalling.WitStream(3L, "S"), back.get(2));
    }

    @Test
    @DisplayName("WitStream wire form uses \"Stream\" tag (matches Rust JsonVal::Stream)")
    void witStreamWireFormat() {
        // Explicit wire-format check — the Rust JsonVal enum is
        // #[serde(tag = "t", content = "v")] and the tag string must match
        // byte-for-byte. A tag rename here would silently break the JNI
        // round-trip.
        final WasmosMarshalling.WitStream ws = new WasmosMarshalling.WitStream(
                55L, "type-hint");
        final String wire = WasmosMarshalling.marshalArgs(new Object[] { ws });
        assertTrue(wire.contains("\"Stream\""),
                "WitStream wire form must use tag=\"Stream\"; wire=" + wire);
        assertTrue(wire.contains("\"table_id\":55"),
                "wire form must expose table_id verbatim; wire=" + wire);
        assertTrue(wire.contains("\"type_name\":\"type-hint\""),
                "wire form must expose type_name verbatim; wire=" + wire);
    }

    @Test
    @DisplayName("WitErrorContext round-trips tableId + rep")
    void witErrorContextRoundtrip() {
        final WasmosMarshalling.WitErrorContext we = new WasmosMarshalling.WitErrorContext(
                4L, 12345L);
        final Object round = roundTrip(we);
        assertInstanceOf(WasmosMarshalling.WitErrorContext.class, round);
        final WasmosMarshalling.WitErrorContext back =
                (WasmosMarshalling.WitErrorContext) round;
        assertEquals(4L, back.tableId());
        assertEquals(12345L, back.rep());
        assertEquals(we, back);
        assertEquals(we.hashCode(), back.hashCode());
    }

    @Test
    @DisplayName("WitErrorContext with u32-max rep round-trips (long-widened)")
    void witErrorContextLargeRep() {
        // wasmtime's rep is a u32; we widen to Java long on the wire.
        // The 0xFFFFFFFF max must survive without silent truncation.
        final long u32Max = 4_294_967_295L;
        final WasmosMarshalling.WitErrorContext we = new WasmosMarshalling.WitErrorContext(
                1L, u32Max);
        final Object round = roundTrip(we);
        assertEquals(u32Max,
                ((WasmosMarshalling.WitErrorContext) round).rep());
    }

    @Test
    @DisplayName("WitErrorContext in a nested List round-trips through container marshalling")
    void witErrorContextNestedInList() {
        // list<error-context> is an unusual but wire-valid shape — verify
        // the ErrorContext writer + reader arms compose with List. Same
        // regression guarantee as the WitFuture nested-list test.
        final List<WasmosMarshalling.WitErrorContext> items = Arrays.asList(
                new WasmosMarshalling.WitErrorContext(1L, 1L),
                new WasmosMarshalling.WitErrorContext(2L, 2L),
                new WasmosMarshalling.WitErrorContext(3L, 3L));
        final Object round = roundTrip(items);
        assertInstanceOf(List.class, round);
        final List<?> back = (List<?>) round;
        assertEquals(3, back.size());
        assertEquals(new WasmosMarshalling.WitErrorContext(1L, 1L), back.get(0));
        assertEquals(new WasmosMarshalling.WitErrorContext(2L, 2L), back.get(1));
        assertEquals(new WasmosMarshalling.WitErrorContext(3L, 3L), back.get(2));
    }

    @Test
    @DisplayName("WitErrorContext wire form uses \"ErrorContext\" tag (matches Rust JsonVal::ErrorContext)")
    void witErrorContextWireFormat() {
        // Explicit wire-format check — the Rust JsonVal enum is
        // #[serde(tag = "t", content = "v")] and the tag string must match
        // byte-for-byte. A tag rename here would silently break the JNI
        // round-trip.
        final WasmosMarshalling.WitErrorContext we = new WasmosMarshalling.WitErrorContext(
                7L, 99L);
        final String wire = WasmosMarshalling.marshalArgs(new Object[] { we });
        assertTrue(wire.contains("\"ErrorContext\""),
                "WitErrorContext wire form must use tag=\"ErrorContext\"; wire=" + wire);
        assertTrue(wire.contains("\"table_id\":7"),
                "wire form must expose table_id verbatim; wire=" + wire);
        assertTrue(wire.contains("\"rep\":99"),
                "wire form must expose rep verbatim; wire=" + wire);
    }

    @Test
    @DisplayName("plain Map<String, ?> still routes to Record (not Map)")
    void plainMapStillRoutesToRecord() {
        // Regression guard for the tie-breaking rule: only WitMap should
        // route to Map; a bare Map<String,?> stays Record so existing
        // record-shaped callers aren't disturbed.
        final Map<String, Object> input = new LinkedHashMap<>();
        input.put("k", "v");
        final Object round = roundTrip(input);
        assertInstanceOf(Map.class, round);
        // The unmarshalled shape is a LinkedHashMap regardless; the
        // distinction shows up on the wire as tag="Record" (unfortunately
        // opaque from unmarshalResults). Sanity-check the actual wire
        // string for the record tag.
        final String wire = WasmosMarshalling.marshalArgs(new Object[] { input });
        assertTrue(wire.contains("\"Record\""),
                "plain Map<String,?> must marshal as Record, not Map; wire=" + wire);
    }
}
