package ai.tegmentum.webassembly4j.runtime.proxy;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.runtime.spi.WasmBindingProvider;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

public final class ProxyFactory {

    private static volatile List<WasmBindingProvider> cachedBindingProviders;

    private ProxyFactory() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T create(Class<T> iface, Engine engine,
                                ai.tegmentum.webassembly4j.api.Module module,
                                Instance instance) {
        // Try generated binding providers first
        for (WasmBindingProvider provider : getBindingProviders()) {
            if (provider.supports(iface)) {
                return provider.create(iface, instance, module, engine);
            }
        }

        // Fall back to proxy-based binding
        Map<Method, InterfaceAnalyzer.MethodBinding> bindings =
                InterfaceAnalyzer.analyzeExports(iface);
        Map<Method, Function> exports = new LinkedHashMap<>();

        for (Map.Entry<Method, InterfaceAnalyzer.MethodBinding> entry : bindings.entrySet()) {
            Function fn = instance.function(entry.getValue().exportName())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "WASM module does not export function: " + entry.getValue().exportName()));
            exports.put(entry.getKey(), fn);
        }

        WasmInvocationHandler handler = new WasmInvocationHandler(
                iface, engine, module, instance, exports, bindings);

        return (T) Proxy.newProxyInstance(
                iface.getClassLoader(),
                new Class<?>[]{iface},
                handler);
    }

    private static List<WasmBindingProvider> getBindingProviders() {
        List<WasmBindingProvider> providers = cachedBindingProviders;
        if (providers == null) {
            providers = discoverBindingProviders();
        }
        return providers;
    }

    private static synchronized List<WasmBindingProvider> discoverBindingProviders() {
        if (cachedBindingProviders != null) {
            return cachedBindingProviders;
        }
        List<WasmBindingProvider> providers = new ArrayList<>();
        for (WasmBindingProvider provider : ServiceLoader.load(WasmBindingProvider.class)) {
            providers.add(provider);
        }
        cachedBindingProviders = Collections.unmodifiableList(providers);
        return cachedBindingProviders;
    }
}
