package ai.tegmentum.webassembly4j.runtime.spi;

import ai.tegmentum.webassembly4j.api.Component;
import ai.tegmentum.webassembly4j.api.ComponentInstance;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Instance;

public interface WasmBindingProvider {

    boolean supports(Class<?> iface);

    <T> T create(Class<T> iface, Instance instance,
                 ai.tegmentum.webassembly4j.api.Module module,
                 Engine engine);

    /**
     * Returns whether this provider supports component model binding
     * in addition to core module binding.
     */
    default boolean supportsComponentBinding() {
        return false;
    }

    /**
     * Creates a binding from a native component instance.
     * Only called when {@link #supportsComponentBinding()} returns true.
     */
    default <T> T createFromComponent(Class<T> iface, ComponentInstance componentInstance,
                                       Component component, Engine engine) {
        throw new UnsupportedOperationException(
                "This binding provider does not support component model binding");
    }
}
