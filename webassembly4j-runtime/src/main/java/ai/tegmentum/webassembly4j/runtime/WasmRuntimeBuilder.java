package ai.tegmentum.webassembly4j.runtime;

import ai.tegmentum.webassembly4j.api.DefaultLinkingContext;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.HostFunctionDefinition;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.LinkingContext;
import ai.tegmentum.webassembly4j.api.WebAssembly;
import ai.tegmentum.webassembly4j.api.WebAssemblyBuilder;
import ai.tegmentum.webassembly4j.api.config.EngineConfig;
import ai.tegmentum.webassembly4j.runtime.proxy.HostFunctionScanner;
import ai.tegmentum.webassembly4j.runtime.proxy.ProxyFactory;

import java.util.ArrayList;
import java.util.List;

public final class WasmRuntimeBuilder {

    private String engineId;
    private EngineConfig engineConfig;
    private final List<Object> hostObjects = new ArrayList<>();

    WasmRuntimeBuilder() {
    }

    public WasmRuntimeBuilder engine(String engineId) {
        this.engineId = engineId;
        return this;
    }

    public WasmRuntimeBuilder engineConfig(EngineConfig config) {
        this.engineConfig = config;
        return this;
    }

    public WasmRuntimeBuilder hostObjects(Object... hostObjects) {
        for (Object obj : hostObjects) {
            this.hostObjects.add(obj);
        }
        return this;
    }

    public <T extends AutoCloseable> T load(Class<T> iface, byte[] wasmBytes) {
        Engine engine = buildEngine();
        try {
            ai.tegmentum.webassembly4j.api.Module module = engine.loadModule(wasmBytes);
            LinkingContext ctx = buildLinkingContext();
            Instance instance = ctx != null
                    ? module.instantiate(ctx) : module.instantiate();
            return ProxyFactory.create(iface, engine, module, instance);
        } catch (Exception e) {
            engine.close();
            throw e;
        }
    }

    public WasmModule compile(byte[] wasmBytes) {
        Engine engine = buildEngine();
        try {
            ai.tegmentum.webassembly4j.api.Module module = engine.loadModule(wasmBytes);
            LinkingContext ctx = buildLinkingContext();
            return new WasmModule(engine, module, ctx);
        } catch (Exception e) {
            engine.close();
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    public <R> R call(byte[] wasmBytes, String functionName,
                      Class<R> returnType, Object... args) {
        try (Engine engine = buildEngine()) {
            ai.tegmentum.webassembly4j.api.Module module = engine.loadModule(wasmBytes);
            try {
                LinkingContext ctx = buildLinkingContext();
                Instance instance = ctx != null
                        ? module.instantiate(ctx) : module.instantiate();
                ai.tegmentum.webassembly4j.api.Function fn = instance.function(functionName)
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

    public void callVoid(byte[] wasmBytes, String functionName, Object... args) {
        try (Engine engine = buildEngine()) {
            ai.tegmentum.webassembly4j.api.Module module = engine.loadModule(wasmBytes);
            try {
                LinkingContext ctx = buildLinkingContext();
                Instance instance = ctx != null
                        ? module.instantiate(ctx) : module.instantiate();
                ai.tegmentum.webassembly4j.api.Function fn = instance.function(functionName)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "No exported function: " + functionName));
                fn.invoke(args);
            } finally {
                module.close();
            }
        }
    }

    private Engine buildEngine() {
        WebAssemblyBuilder builder = WebAssembly.builder();
        if (engineId != null) {
            builder.engine(engineId);
        }
        if (engineConfig != null) {
            builder.engineConfig(engineConfig);
        }
        return builder.build();
    }

    private LinkingContext buildLinkingContext() {
        if (hostObjects.isEmpty()) {
            return null;
        }
        List<HostFunctionDefinition> definitions =
                HostFunctionScanner.scan(hostObjects.toArray());
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
