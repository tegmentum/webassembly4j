package ai.tegmentum.webassembly4j.provider.wasmtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ai.tegmentum.wasmtime4j.wit.WitBool;
import ai.tegmentum.wasmtime4j.wit.WitFloat32;
import ai.tegmentum.wasmtime4j.wit.WitFloat64;
import ai.tegmentum.wasmtime4j.wit.WitList;
import ai.tegmentum.wasmtime4j.wit.WitS16;
import ai.tegmentum.wasmtime4j.wit.WitS32;
import ai.tegmentum.wasmtime4j.wit.WitS64;
import ai.tegmentum.wasmtime4j.wit.WitS8;
import ai.tegmentum.wasmtime4j.wit.WitString;
import ai.tegmentum.wasmtime4j.wit.WitValue;
import ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fix 10 unit test: the high-level {@code ComponentInstance.invoke} must marshal natural Java
 * argument types into {@link WitValue} (its javadoc contract), not require callers to pre-build
 * WitValue. Pure mapping test — no native library required.
 */
class WasmtimeComponentInstanceMarshalTest {

    @Test
    @DisplayName("scalars + string map to their natural WIT value types")
    void scalarsAndStrings() throws Exception {
        assertEquals(Boolean.TRUE,
                WasmtimeComponentInstanceAdapter.toWitValue(true) instanceof WitBool);
        assertEquals("hi", ((WitString) WasmtimeComponentInstanceAdapter.toWitValue("hi")).toJava());

        // Integer -> s32, Long -> s64 (signed defaults).
        assertEquals(WitS32.class, WasmtimeComponentInstanceAdapter.toWitValue(42).getClass());
        assertEquals(WitS64.class, WasmtimeComponentInstanceAdapter.toWitValue(42L).getClass());
        assertEquals(WitS8.class, WasmtimeComponentInstanceAdapter.toWitValue((byte) 1).getClass());
        assertEquals(WitS16.class,
                WasmtimeComponentInstanceAdapter.toWitValue((short) 1).getClass());
        assertEquals(WitFloat32.class,
                WasmtimeComponentInstanceAdapter.toWitValue(1.5f).getClass());
        assertEquals(WitFloat64.class,
                WasmtimeComponentInstanceAdapter.toWitValue(1.5d).getClass());
    }

    @Test
    @DisplayName("WitValue arguments pass through unchanged (escape hatch)")
    void witValuePassthrough() throws Exception {
        WitValue prebuilt = WitString.of("already-wit");
        assertSame(prebuilt, WasmtimeComponentInstanceAdapter.toWitValue(prebuilt));
    }

    @Test
    @DisplayName("byte[] and homogeneous lists become list values")
    void listsAndBytes() throws Exception {
        WitValue bytes = WasmtimeComponentInstanceAdapter.toWitValue(new byte[] {1, 2, 3});
        assertEquals(WitList.class, bytes.getClass());

        WitValue strings = WasmtimeComponentInstanceAdapter.toWitValue(List.of("a", "b"));
        assertEquals(WitList.class, strings.getClass());
    }

    @Test
    @DisplayName("unmarshallable / ambiguous types fail with guidance, not a cryptic native error")
    void unsupportedTypesAreRejected() {
        // Composite types whose WIT type can't be inferred from a bare Java value.
        assertThrows(UnsupportedFeatureException.class,
                () -> WasmtimeComponentInstanceAdapter.toWitValue(Map.of("k", "v")));
        // null (could be option none — needs an explicit type).
        assertThrows(UnsupportedFeatureException.class,
                () -> WasmtimeComponentInstanceAdapter.toWitValue((Object) null));
        // empty list — element type unknowable.
        assertThrows(UnsupportedFeatureException.class,
                () -> WasmtimeComponentInstanceAdapter.toWitValue(List.of()));
    }
}
