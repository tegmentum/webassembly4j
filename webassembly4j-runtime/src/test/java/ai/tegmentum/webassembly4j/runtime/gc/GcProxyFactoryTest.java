package ai.tegmentum.webassembly4j.runtime.gc;

import ai.tegmentum.webassembly4j.runtime.annotation.WasmExport;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GcProxyFactoryTest {

    @GcMapped
    static class Point {
        double x;
        double y;
    }

    interface PureNumeric extends AutoCloseable {
        int add(int a, int b);
        void noop();
    }

    interface WithGcMapped extends AutoCloseable {
        @WasmExport("transform_point")
        Point transform(Point p, double angle);
    }

    interface MixedTypes extends AutoCloseable {
        int getId(Point p);
    }

    interface WithUnsupportedType extends AutoCloseable {
        String process(int x);
    }

    interface WithDefault extends AutoCloseable {
        int compute(int x);
        default int doubled(int x) { return compute(x) * 2; }
    }

    static class NotAnInterface {
    }

    @Test
    void analyzesPureNumericInterface() {
        Map<Method, GcProxyFactory.MethodBinding> bindings =
                GcProxyFactory.analyzeInterface(PureNumeric.class);
        assertEquals(2, bindings.size());

        for (GcProxyFactory.MethodBinding binding : bindings.values()) {
            assertFalse(binding.anyGcMarshalling);
        }
    }

    @Test
    void analyzesGcMappedInterface() {
        Map<Method, GcProxyFactory.MethodBinding> bindings =
                GcProxyFactory.analyzeInterface(WithGcMapped.class);
        assertEquals(1, bindings.size());

        GcProxyFactory.MethodBinding binding = bindings.values().iterator().next();
        assertEquals("transform_point", binding.exportName);
        assertTrue(binding.anyGcMarshalling);
        assertTrue(binding.returnNeedsGcMarshalling);
        assertTrue(binding.paramNeedsGcMarshalling[0]); // Point p
        assertFalse(binding.paramNeedsGcMarshalling[1]); // double angle
    }

    @Test
    void analyzesMixedTypes() {
        Map<Method, GcProxyFactory.MethodBinding> bindings =
                GcProxyFactory.analyzeInterface(MixedTypes.class);
        assertEquals(1, bindings.size());

        GcProxyFactory.MethodBinding binding = bindings.values().iterator().next();
        assertTrue(binding.anyGcMarshalling);
        assertTrue(binding.paramNeedsGcMarshalling[0]); // Point p
        assertFalse(binding.returnNeedsGcMarshalling); // int return
    }

    @Test
    void rejectsUnsupportedTypes() {
        assertThrows(IllegalArgumentException.class,
                () -> GcProxyFactory.analyzeInterface(WithUnsupportedType.class));
    }

    @Test
    void rejectsNonInterface() {
        assertThrows(IllegalArgumentException.class,
                () -> GcProxyFactory.analyzeInterface(NotAnInterface.class));
    }

    @Test
    void skipsDefaultAndObjectMethods() {
        Map<Method, GcProxyFactory.MethodBinding> bindings =
                GcProxyFactory.analyzeInterface(WithDefault.class);
        assertEquals(1, bindings.size());
        assertEquals("compute", bindings.values().iterator().next().exportName);
    }

    @Test
    void usesMethodNameAsDefaultExportName() {
        Map<Method, GcProxyFactory.MethodBinding> bindings =
                GcProxyFactory.analyzeInterface(MixedTypes.class);
        GcProxyFactory.MethodBinding binding = bindings.values().iterator().next();
        assertEquals("getId", binding.exportName);
    }
}
