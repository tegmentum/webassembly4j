package ai.tegmentum.webassembly4j.testing;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.Module;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * JUnit 5 extension that provides {@link Engine}, {@link Module}, and
 * {@link Instance} parameters to test methods annotated with {@link WasmTest}.
 *
 * <p>The extension creates a separate test invocation for each available
 * WebAssembly engine on the classpath. Engines and modules are automatically
 * closed after each test.
 */
public class WasmTestExtension implements TestTemplateInvocationContextProvider,
        ParameterResolver, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(WasmTestExtension.class);

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestMethod()
                .map(m -> m.isAnnotationPresent(WasmTest.class))
                .orElse(false);
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
            ExtensionContext context) {
        List<WasmEngineSource.EngineFactory> engines = WasmEngineSource.discoverEngines();
        if (engines.isEmpty()) {
            throw new IllegalStateException(
                    "No WebAssembly engines found on the classpath. "
                    + "Add a provider dependency (e.g., wasmtime4j-provider, chicory4j-provider).");
        }
        return engines.stream().map(factory -> createInvocationContext(factory, context));
    }

    private TestTemplateInvocationContext createInvocationContext(
            WasmEngineSource.EngineFactory factory, ExtensionContext rootContext) {
        return new TestTemplateInvocationContext() {
            @Override
            public String getDisplayName(int invocationIndex) {
                return "[" + factory.displayName() + "]";
            }

            @Override
            public List<org.junit.jupiter.api.extension.Extension> getAdditionalExtensions() {
                return Collections.singletonList(
                        new EngineParameterResolver(factory, rootContext));
            }
        };
    }

    @Override
    public boolean supportsParameter(ParameterContext paramCtx, ExtensionContext extCtx) {
        Class<?> type = paramCtx.getParameter().getType();
        return type == Engine.class || type == Module.class || type == Instance.class;
    }

    @Override
    public Object resolveParameter(ParameterContext paramCtx, ExtensionContext extCtx) {
        // Delegated to EngineParameterResolver in invocation contexts
        return null;
    }

    @Override
    public void afterEach(ExtensionContext context) {
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        Instance instance = store.remove("instance", Instance.class);
        Module module = store.remove("module", Module.class);
        Engine engine = store.remove("engine", Engine.class);
        // Close in reverse order
        if (module != null) {
            module.close();
        }
        if (engine != null) {
            engine.close();
        }
    }

    private static class EngineParameterResolver implements ParameterResolver, AfterEachCallback {

        private final WasmEngineSource.EngineFactory factory;
        private final ExtensionContext rootContext;

        EngineParameterResolver(WasmEngineSource.EngineFactory factory,
                                ExtensionContext rootContext) {
            this.factory = factory;
            this.rootContext = rootContext;
        }

        @Override
        public boolean supportsParameter(ParameterContext paramCtx, ExtensionContext extCtx) {
            Class<?> type = paramCtx.getParameter().getType();
            return type == Engine.class || type == Module.class || type == Instance.class;
        }

        @Override
        public Object resolveParameter(ParameterContext paramCtx, ExtensionContext extCtx) {
            Class<?> type = paramCtx.getParameter().getType();
            ExtensionContext.Store store = extCtx.getStore(NAMESPACE);

            if (type == Engine.class) {
                return getOrCreateEngine(store);
            }
            if (type == Module.class) {
                Engine engine = getOrCreateEngine(store);
                return getOrCreateModule(store, engine, extCtx);
            }
            if (type == Instance.class) {
                Engine engine = getOrCreateEngine(store);
                Module module = getOrCreateModule(store, engine, extCtx);
                return getOrCreateInstance(store, module);
            }
            throw new IllegalArgumentException("Unsupported parameter type: " + type);
        }

        private Engine getOrCreateEngine(ExtensionContext.Store store) {
            Engine engine = store.get("engine", Engine.class);
            if (engine == null) {
                engine = factory.create();
                store.put("engine", engine);
            }
            return engine;
        }

        private Module getOrCreateModule(ExtensionContext.Store store, Engine engine,
                                         ExtensionContext extCtx) {
            Module module = store.get("module", Module.class);
            if (module != null) {
                return module;
            }
            WasmModule annotation = findWasmModuleAnnotation(extCtx);
            if (annotation == null) {
                throw new IllegalStateException(
                        "Module parameter requested but no @WasmModule annotation found "
                        + "on the test method or class.");
            }
            byte[] bytes = loadClasspathResource(annotation.value());
            module = engine.loadModule(bytes);
            store.put("module", module);
            return module;
        }

        private Instance getOrCreateInstance(ExtensionContext.Store store, Module module) {
            Instance instance = store.get("instance", Instance.class);
            if (instance == null) {
                instance = module.instantiate();
                store.put("instance", instance);
            }
            return instance;
        }

        private WasmModule findWasmModuleAnnotation(ExtensionContext context) {
            WasmModule annotation = context.getTestMethod()
                    .map(m -> m.getAnnotation(WasmModule.class))
                    .orElse(null);
            if (annotation == null) {
                annotation = context.getTestClass()
                        .map(c -> c.getAnnotation(WasmModule.class))
                        .orElse(null);
            }
            return annotation;
        }

        private byte[] loadClasspathResource(String path) {
            try (InputStream is = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(path)) {
                if (is == null) {
                    throw new IllegalArgumentException(
                            "WASM resource not found on classpath: " + path);
                }
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) {
                    buffer.write(buf, 0, n);
                }
                return buffer.toByteArray();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load WASM resource: " + path, e);
            }
        }

        @Override
        public void afterEach(ExtensionContext context) {
            ExtensionContext.Store store = context.getStore(NAMESPACE);
            Module module = store.remove("module", Module.class);
            Engine engine = store.remove("engine", Engine.class);
            if (module != null) {
                module.close();
            }
            if (engine != null) {
                engine.close();
            }
        }
    }
}
