package ai.tegmentum.webassembly4j.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TypedFunctionTest {

    private static Function stubFunction(Object returnValue) {
        return new Function() {
            @Override
            public ValueType[] parameterTypes() {
                return new ValueType[0];
            }

            @Override
            public ValueType[] resultTypes() {
                return new ValueType[0];
            }

            @Override
            public Object invoke(Object... args) {
                return returnValue;
            }
        };
    }

    private static Function recordingFunction() {
        return new Function() {
            @Override
            public ValueType[] parameterTypes() {
                return new ValueType[]{ValueType.I32, ValueType.I32};
            }

            @Override
            public ValueType[] resultTypes() {
                return new ValueType[]{ValueType.I32};
            }

            @Override
            public Object invoke(Object... args) {
                int a = ((Number) args[0]).intValue();
                int b = ((Number) args[1]).intValue();
                return a + b;
            }
        };
    }

    @Test
    void wrapVoidVoid() {
        Function fn = stubFunction(null);
        TypedFunction.Void_Void typed = TypedFunction.wrap(fn, TypedFunction.Void_Void.class);
        typed.call(); // should not throw
    }

    @Test
    void wrapVoidI32() {
        Function fn = stubFunction(42);
        TypedFunction.Void_I32 typed = TypedFunction.wrap(fn, TypedFunction.Void_I32.class);
        assertEquals(42, typed.call());
    }

    @Test
    void wrapVoidI64() {
        Function fn = stubFunction(100L);
        TypedFunction.Void_I64 typed = TypedFunction.wrap(fn, TypedFunction.Void_I64.class);
        assertEquals(100L, typed.call());
    }

    @Test
    void wrapVoidF32() {
        Function fn = stubFunction(3.14f);
        TypedFunction.Void_F32 typed = TypedFunction.wrap(fn, TypedFunction.Void_F32.class);
        assertEquals(3.14f, typed.call(), 0.001f);
    }

    @Test
    void wrapVoidF64() {
        Function fn = stubFunction(2.718);
        TypedFunction.Void_F64 typed = TypedFunction.wrap(fn, TypedFunction.Void_F64.class);
        assertEquals(2.718, typed.call(), 0.001);
    }

    @Test
    void wrapI32I32() {
        Function fn = new Function() {
            @Override
            public ValueType[] parameterTypes() {
                return new ValueType[]{ValueType.I32};
            }

            @Override
            public ValueType[] resultTypes() {
                return new ValueType[]{ValueType.I32};
            }

            @Override
            public Object invoke(Object... args) {
                return ((Number) args[0]).intValue() * 2;
            }
        };
        TypedFunction.I32_I32 typed = TypedFunction.wrap(fn, TypedFunction.I32_I32.class);
        assertEquals(10, typed.call(5));
    }

    @Test
    void wrapI32I32I32() {
        Function fn = recordingFunction();
        TypedFunction.I32_I32_I32 typed = TypedFunction.wrap(fn, TypedFunction.I32_I32_I32.class);
        assertEquals(7, typed.call(3, 4));
    }

    @Test
    void wrapI64I64I64() {
        Function fn = new Function() {
            @Override
            public ValueType[] parameterTypes() {
                return new ValueType[]{ValueType.I64, ValueType.I64};
            }

            @Override
            public ValueType[] resultTypes() {
                return new ValueType[]{ValueType.I64};
            }

            @Override
            public Object invoke(Object... args) {
                return ((Number) args[0]).longValue() + ((Number) args[1]).longValue();
            }
        };
        TypedFunction.I64_I64_I64 typed = TypedFunction.wrap(fn, TypedFunction.I64_I64_I64.class);
        assertEquals(30L, typed.call(10L, 20L));
    }

    @Test
    void wrapF64F64F64() {
        Function fn = new Function() {
            @Override
            public ValueType[] parameterTypes() {
                return new ValueType[]{ValueType.F64, ValueType.F64};
            }

            @Override
            public ValueType[] resultTypes() {
                return new ValueType[]{ValueType.F64};
            }

            @Override
            public Object invoke(Object... args) {
                return ((Number) args[0]).doubleValue() + ((Number) args[1]).doubleValue();
            }
        };
        TypedFunction.F64_F64_F64 typed = TypedFunction.wrap(fn, TypedFunction.F64_F64_F64.class);
        assertEquals(5.0, typed.call(2.0, 3.0), 0.001);
    }

    @Test
    void wrapI32Void() {
        final int[] captured = new int[1];
        Function fn = new Function() {
            @Override
            public ValueType[] parameterTypes() {
                return new ValueType[]{ValueType.I32};
            }

            @Override
            public ValueType[] resultTypes() {
                return new ValueType[0];
            }

            @Override
            public Object invoke(Object... args) {
                captured[0] = ((Number) args[0]).intValue();
                return null;
            }
        };
        TypedFunction.I32_Void typed = TypedFunction.wrap(fn, TypedFunction.I32_Void.class);
        typed.call(42);
        assertEquals(42, captured[0]);
    }

    @Test
    void wrapI32I32Void() {
        final int[] captured = new int[2];
        Function fn = new Function() {
            @Override
            public ValueType[] parameterTypes() {
                return new ValueType[]{ValueType.I32, ValueType.I32};
            }

            @Override
            public ValueType[] resultTypes() {
                return new ValueType[0];
            }

            @Override
            public Object invoke(Object... args) {
                captured[0] = ((Number) args[0]).intValue();
                captured[1] = ((Number) args[1]).intValue();
                return null;
            }
        };
        TypedFunction.I32_I32_Void typed = TypedFunction.wrap(fn, TypedFunction.I32_I32_Void.class);
        typed.call(10, 20);
        assertEquals(10, captured[0]);
        assertEquals(20, captured[1]);
    }

    @Test
    void wrapF32F32() {
        Function fn = new Function() {
            @Override
            public ValueType[] parameterTypes() {
                return new ValueType[]{ValueType.F32};
            }

            @Override
            public ValueType[] resultTypes() {
                return new ValueType[]{ValueType.F32};
            }

            @Override
            public Object invoke(Object... args) {
                return ((Number) args[0]).floatValue() * 2.0f;
            }
        };
        TypedFunction.F32_F32 typed = TypedFunction.wrap(fn, TypedFunction.F32_F32.class);
        assertEquals(6.0f, typed.call(3.0f), 0.001f);
    }

    @Test
    void wrapI32I32I32I32() {
        Function fn = new Function() {
            @Override
            public ValueType[] parameterTypes() {
                return new ValueType[]{ValueType.I32, ValueType.I32, ValueType.I32};
            }

            @Override
            public ValueType[] resultTypes() {
                return new ValueType[]{ValueType.I32};
            }

            @Override
            public Object invoke(Object... args) {
                return ((Number) args[0]).intValue()
                        + ((Number) args[1]).intValue()
                        + ((Number) args[2]).intValue();
            }
        };
        TypedFunction.I32_I32_I32_I32 typed = TypedFunction.wrap(fn, TypedFunction.I32_I32_I32_I32.class);
        assertEquals(6, typed.call(1, 2, 3));
    }

    @Test
    void typedMethodOnFunction() {
        Function fn = recordingFunction();
        TypedFunction.I32_I32_I32 typed = fn.typed(TypedFunction.I32_I32_I32.class);
        assertEquals(10, typed.call(3, 7));
    }

    @Test
    void unknownTypeThrows() {
        Function fn = stubFunction(null);
        assertThrows(IllegalArgumentException.class,
                () -> TypedFunction.wrap(fn, Runnable.class));
    }

    @Test
    void nullFunctionThrows() {
        assertThrows(NullPointerException.class,
                () -> TypedFunction.wrap(null, TypedFunction.Void_Void.class));
    }

    @Test
    void nullTypeThrows() {
        Function fn = stubFunction(null);
        assertThrows(NullPointerException.class,
                () -> TypedFunction.wrap(fn, null));
    }
}
