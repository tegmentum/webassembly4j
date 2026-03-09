package ai.tegmentum.webassembly4j.runtime.spi;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Instance;

public interface WasmBindingProvider {

    boolean supports(Class<?> iface);

    <T> T create(Class<T> iface, Instance instance,
                 ai.tegmentum.webassembly4j.api.Module module,
                 Engine engine);
}
