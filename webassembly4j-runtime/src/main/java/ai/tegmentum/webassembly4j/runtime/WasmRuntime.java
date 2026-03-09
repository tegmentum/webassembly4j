package ai.tegmentum.webassembly4j.runtime;

import ai.tegmentum.webassembly4j.api.DefaultLinkingContext;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.HostFunctionDefinition;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.LinkingContext;
import ai.tegmentum.webassembly4j.api.WebAssembly;
import ai.tegmentum.webassembly4j.runtime.proxy.HostFunctionScanner;
import ai.tegmentum.webassembly4j.runtime.proxy.ProxyFactory;

import java.util.List;

public final class WasmRuntime {

    private WasmRuntime() {
    }

    public static <T extends AutoCloseable> T load(Class<T> iface, byte[] wasmBytes) {
        Engine engine = WebAssembly.builder().build();
        try {
            ai.tegmentum.webassembly4j.api.Module module = engine.loadModule(wasmBytes);
            Instance instance = module.instantiate();
            return ProxyFactory.create(iface, engine, module, instance);
        } catch (Exception e) {
            engine.close();
            throw e;
        }
    }

    public static <T extends AutoCloseable> T load(Class<T> iface, byte[] wasmBytes,
                                                    Object... hostObjects) {
        Engine engine = WebAssembly.builder().build();
        try {
            ai.tegmentum.webassembly4j.api.Module module = engine.loadModule(wasmBytes);
            LinkingContext ctx = buildLinkingContext(hostObjects);
            Instance instance = ctx != null
                    ? module.instantiate(ctx) : module.instantiate();
            return ProxyFactory.create(iface, engine, module, instance);
        } catch (Exception e) {
            engine.close();
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    public static <R> R call(byte[] wasmBytes, String functionName,
                              Class<R> returnType, Object... args) {
        try (Engine engine = WebAssembly.builder().build()) {
            ai.tegmentum.webassembly4j.api.Module module = engine.loadModule(wasmBytes);
            try {
                Instance instance = module.instantiate();
                Function fn = instance.function(functionName)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "No exported function: " + functionName));
                Object result = fn.invoke(args);
                if (returnType == void.class || returnType == Void.class) {
                    return null;
                }
                return (R) result;
            } finally {
                module.close();
            }
        }
    }

    public static void callVoid(byte[] wasmBytes, String functionName, Object... args) {
        try (Engine engine = WebAssembly.builder().build()) {
            ai.tegmentum.webassembly4j.api.Module module = engine.loadModule(wasmBytes);
            try {
                Instance instance = module.instantiate();
                Function fn = instance.function(functionName)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "No exported function: " + functionName));
                fn.invoke(args);
            } finally {
                module.close();
            }
        }
    }

    public static WasmModule compile(byte[] wasmBytes) {
        Engine engine = WebAssembly.builder().build();
        try {
            ai.tegmentum.webassembly4j.api.Module module = engine.loadModule(wasmBytes);
            return new WasmModule(engine, module, null);
        } catch (Exception e) {
            engine.close();
            throw e;
        }
    }

    public static WasmRuntimeBuilder builder() {
        return new WasmRuntimeBuilder();
    }

    private static LinkingContext buildLinkingContext(Object[] hostObjects) {
        if (hostObjects == null || hostObjects.length == 0) {
            return null;
        }
        List<HostFunctionDefinition> definitions = HostFunctionScanner.scan(hostObjects);
        if (definitions.isEmpty()) {
            return null;
        }
        DefaultLinkingContext.Builder ctxBuilder = DefaultLinkingContext.builder();
        for (HostFunctionDefinition def : definitions) {
            ctxBuilder.addHostFunction(def);
        }
        return ctxBuilder.build();
    }
}
