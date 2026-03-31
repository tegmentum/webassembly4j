package ai.tegmentum.webassembly4j.runtime.proxy;

import ai.tegmentum.webassembly4j.api.Component;
import ai.tegmentum.webassembly4j.api.ComponentInstance;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.runtime.spi.WasmBindingProvider;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * Creates interface proxies bound to a native {@link ComponentInstance}.
 * <p>
 * For runtimes that support the component model natively (e.g. wasmtime),
 * this factory delegates method calls to {@link ComponentInstance#invoke(String, Object...)}
 * which handles WIT type conversion natively.
 */
public final class ComponentProxyFactory {

    private ComponentProxyFactory() {}

    @SuppressWarnings("unchecked")
    public static <T> T create(Class<T> iface, Engine engine,
                                Component component, ComponentInstance instance) {
        // Try generated binding providers first (component-aware)
        for (WasmBindingProvider provider : ProxyFactory.getBindingProviders()) {
            if (provider.supports(iface) && provider.supportsComponentBinding()) {
                return provider.createFromComponent(iface, instance, component, engine);
            }
        }

        // Fall back to dynamic proxy using ComponentInstance.invoke()
        Map<Method, InterfaceAnalyzer.MethodBinding> bindings =
                InterfaceAnalyzer.analyzeExports(iface);

        ComponentInvocationHandler handler = new ComponentInvocationHandler(
                iface, engine, component, instance, bindings);

        return (T) Proxy.newProxyInstance(
                iface.getClassLoader(),
                new Class<?>[]{iface},
                handler);
    }
}
