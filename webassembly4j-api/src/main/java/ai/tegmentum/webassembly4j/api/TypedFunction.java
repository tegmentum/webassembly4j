package ai.tegmentum.webassembly4j.api;

import java.util.Objects;

/**
 * Type-safe wrappers around {@link Function} that eliminate boxing overhead
 * for common signatures. Obtain instances via {@link Function#typed(Class)}
 * or the static factory methods on this class.
 *
 * <p>Example usage:
 * <pre>{@code
 * Function fn = instance.function("add").orElseThrow();
 *
 * // Typed wrapper — no boxing on the hot path
 * TypedFunction.I32_I32_I32 add = fn.typed(TypedFunction.I32_I32_I32.class);
 * int sum = add.call(3, 4);
 * }</pre>
 */
public final class TypedFunction {

    private TypedFunction() {}

    // ─── 0-arg signatures ───

    @FunctionalInterface
    public interface Void_Void {
        void call();
    }

    @FunctionalInterface
    public interface Void_I32 {
        int call();
    }

    @FunctionalInterface
    public interface Void_I64 {
        long call();
    }

    @FunctionalInterface
    public interface Void_F32 {
        float call();
    }

    @FunctionalInterface
    public interface Void_F64 {
        double call();
    }

    // ─── 1-arg I32 signatures ───

    @FunctionalInterface
    public interface I32_Void {
        void call(int a);
    }

    @FunctionalInterface
    public interface I32_I32 {
        int call(int a);
    }

    @FunctionalInterface
    public interface I32_I64 {
        long call(int a);
    }

    @FunctionalInterface
    public interface I32_F64 {
        double call(int a);
    }

    // ─── 1-arg I64 signatures ───

    @FunctionalInterface
    public interface I64_Void {
        void call(long a);
    }

    @FunctionalInterface
    public interface I64_I64 {
        long call(long a);
    }

    @FunctionalInterface
    public interface I64_I32 {
        int call(long a);
    }

    // ─── 1-arg F32 signatures ───

    @FunctionalInterface
    public interface F32_F32 {
        float call(float a);
    }

    // ─── 1-arg F64 signatures ───

    @FunctionalInterface
    public interface F64_F64 {
        double call(double a);
    }

    @FunctionalInterface
    public interface F64_I32 {
        int call(double a);
    }

    // ─── 2-arg signatures ───

    @FunctionalInterface
    public interface I32_I32_Void {
        void call(int a, int b);
    }

    @FunctionalInterface
    public interface I32_I32_I32 {
        int call(int a, int b);
    }

    @FunctionalInterface
    public interface I32_I32_I64 {
        long call(int a, int b);
    }

    @FunctionalInterface
    public interface I64_I64_I64 {
        long call(long a, long b);
    }

    @FunctionalInterface
    public interface F32_F32_F32 {
        float call(float a, float b);
    }

    @FunctionalInterface
    public interface F64_F64_F64 {
        double call(double a, double b);
    }

    @FunctionalInterface
    public interface I32_I32_F64 {
        double call(int a, int b);
    }

    // ─── 3-arg signatures ───

    @FunctionalInterface
    public interface I32_I32_I32_I32 {
        int call(int a, int b, int c);
    }

    @FunctionalInterface
    public interface I32_I32_I32_Void {
        void call(int a, int b, int c);
    }

    @FunctionalInterface
    public interface I64_I64_I64_I64 {
        long call(long a, long b, long c);
    }

    // ─── Factory methods ───

    /**
     * Creates a typed wrapper for the given function. The wrapper type must be
     * one of the functional interfaces defined in this class. The function's
     * actual signature is not validated at wrap time — a mismatch will produce
     * a {@link ClassCastException} or wrong result at call time.
     *
     * @param fn   the WASM function to wrap
     * @param type the desired typed interface
     * @param <T>  the typed function interface
     * @return a typed wrapper
     * @throws IllegalArgumentException if the type is not a known typed function interface
     */
    @SuppressWarnings("unchecked")
    public static <T> T wrap(Function fn, Class<T> type) {
        Objects.requireNonNull(fn, "fn");
        Objects.requireNonNull(type, "type");

        // 0-arg
        if (type == Void_Void.class) {
            return (T) (Void_Void) () -> fn.invoke();
        }
        if (type == Void_I32.class) {
            return (T) (Void_I32) () -> ((Number) fn.invoke()).intValue();
        }
        if (type == Void_I64.class) {
            return (T) (Void_I64) () -> ((Number) fn.invoke()).longValue();
        }
        if (type == Void_F32.class) {
            return (T) (Void_F32) () -> ((Number) fn.invoke()).floatValue();
        }
        if (type == Void_F64.class) {
            return (T) (Void_F64) () -> ((Number) fn.invoke()).doubleValue();
        }

        // 1-arg I32
        if (type == I32_Void.class) {
            return (T) (I32_Void) a -> fn.invoke(a);
        }
        if (type == I32_I32.class) {
            return (T) (I32_I32) a -> ((Number) fn.invoke(a)).intValue();
        }
        if (type == I32_I64.class) {
            return (T) (I32_I64) a -> ((Number) fn.invoke(a)).longValue();
        }
        if (type == I32_F64.class) {
            return (T) (I32_F64) a -> ((Number) fn.invoke(a)).doubleValue();
        }

        // 1-arg I64
        if (type == I64_Void.class) {
            return (T) (I64_Void) a -> fn.invoke(a);
        }
        if (type == I64_I64.class) {
            return (T) (I64_I64) a -> ((Number) fn.invoke(a)).longValue();
        }
        if (type == I64_I32.class) {
            return (T) (I64_I32) a -> ((Number) fn.invoke(a)).intValue();
        }

        // 1-arg F32
        if (type == F32_F32.class) {
            return (T) (F32_F32) a -> ((Number) fn.invoke(a)).floatValue();
        }

        // 1-arg F64
        if (type == F64_F64.class) {
            return (T) (F64_F64) a -> ((Number) fn.invoke(a)).doubleValue();
        }
        if (type == F64_I32.class) {
            return (T) (F64_I32) a -> ((Number) fn.invoke(a)).intValue();
        }

        // 2-arg
        if (type == I32_I32_Void.class) {
            return (T) (I32_I32_Void) (a, b) -> fn.invoke(a, b);
        }
        if (type == I32_I32_I32.class) {
            return (T) (I32_I32_I32) (a, b) -> ((Number) fn.invoke(a, b)).intValue();
        }
        if (type == I32_I32_I64.class) {
            return (T) (I32_I32_I64) (a, b) -> ((Number) fn.invoke(a, b)).longValue();
        }
        if (type == I64_I64_I64.class) {
            return (T) (I64_I64_I64) (a, b) -> ((Number) fn.invoke(a, b)).longValue();
        }
        if (type == F32_F32_F32.class) {
            return (T) (F32_F32_F32) (a, b) -> ((Number) fn.invoke(a, b)).floatValue();
        }
        if (type == F64_F64_F64.class) {
            return (T) (F64_F64_F64) (a, b) -> ((Number) fn.invoke(a, b)).doubleValue();
        }
        if (type == I32_I32_F64.class) {
            return (T) (I32_I32_F64) (a, b) -> ((Number) fn.invoke(a, b)).doubleValue();
        }

        // 3-arg
        if (type == I32_I32_I32_I32.class) {
            return (T) (I32_I32_I32_I32) (a, b, c) -> ((Number) fn.invoke(a, b, c)).intValue();
        }
        if (type == I32_I32_I32_Void.class) {
            return (T) (I32_I32_I32_Void) (a, b, c) -> fn.invoke(a, b, c);
        }
        if (type == I64_I64_I64_I64.class) {
            return (T) (I64_I64_I64_I64) (a, b, c) -> ((Number) fn.invoke(a, b, c)).longValue();
        }

        throw new IllegalArgumentException("Unknown typed function interface: " + type.getName());
    }
}
