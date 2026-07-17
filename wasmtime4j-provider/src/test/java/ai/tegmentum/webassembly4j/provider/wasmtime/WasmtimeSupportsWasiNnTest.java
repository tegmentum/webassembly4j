package ai.tegmentum.webassembly4j.provider.wasmtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.tegmentum.wasmtime4j.jni.JniComponentLinker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@link WasmtimeComponentAdapter#supportsWasiNn()} probe to the truth
 * reported by the loaded wasmtime4j native. The provider override delegates to
 * {@link JniComponentLinker#wasiNnAvailable()}, so the two must agree — a drift
 * would silently return a stale (probably {@code true}) answer and re-open the
 * regression this probe exists to close.
 *
 * <p>The test intentionally does not assert the boolean is {@code true} or
 * {@code false} in absolute terms: whether the native was built with the
 * {@code wasi-nn} cargo feature depends on the classifier the reactor
 * consumed, and this repo's default build ships without it.
 */
class WasmtimeSupportsWasiNnTest {

    @Test
    @DisplayName("provider probe returns the loaded native's wasi:nn truth")
    void supportsWasiNnMirrorsNative() {
        // The override is a straight delegation — no component / engine state is read
        // — so a null-inflated adapter is safe here and lets us exercise the real
        // override without paying for a compile+instantiate.
        WasmtimeComponentAdapter adapter =
                new WasmtimeComponentAdapter(null, null, null, null);

        // Compute the native truth defensively — an older cached wasmtime4j native
        // on this workstation's java.library.path may miss the nativeWasiNnAvailable
        // symbol; that surfaces as UnsatisfiedLinkError, which the reflection-based
        // provider override treats as "no support" (returns false). Either way the
        // adapter answer must match the raw JNI answer for the classpath in use.
        boolean nativeAnswer;
        try {
            nativeAnswer = JniComponentLinker.wasiNnAvailable();
        } catch (UnsatisfiedLinkError | ExceptionInInitializerError err) {
            nativeAnswer = false;
        }
        assertEquals(nativeAnswer, adapter.supportsWasiNn());
    }
}
