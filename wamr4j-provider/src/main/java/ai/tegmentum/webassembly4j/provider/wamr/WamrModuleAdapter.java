package ai.tegmentum.webassembly4j.provider.wamr;

import ai.tegmentum.wamr4j.WebAssemblyModule;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.LinkingContext;
import ai.tegmentum.webassembly4j.api.Module;
import ai.tegmentum.webassembly4j.api.exception.InstantiationException;
import ai.tegmentum.webassembly4j.provider.wamr.config.WamrConfig;

final class WamrModuleAdapter implements Module {

    private final WebAssemblyModule nativeModule;
    private final WamrConfig wamrConfig;

    WamrModuleAdapter(WebAssemblyModule nativeModule, WamrConfig wamrConfig) {
        this.nativeModule = nativeModule;
        this.wamrConfig = wamrConfig;
    }

    @Override
    public Instance instantiate() {
        try {
            ai.tegmentum.wamr4j.WebAssemblyInstance nativeInstance;
            if (wamrConfig != null && hasExtendedConfig()) {
                nativeInstance = nativeModule.instantiateEx(
                        wamrConfig.defaultStackSize().orElse(65536),
                        wamrConfig.hostManagedHeapSize().orElse(0),
                        wamrConfig.maxMemoryPages().orElse(0));
            } else {
                nativeInstance = nativeModule.instantiate();
            }
            return new WamrInstanceAdapter(nativeInstance);
        } catch (ai.tegmentum.wamr4j.exception.WasmRuntimeException e) {
            throw new InstantiationException(
                    "Failed to instantiate WebAssembly module", e);
        }
    }

    private boolean hasExtendedConfig() {
        return wamrConfig.defaultStackSize().isPresent()
                || wamrConfig.hostManagedHeapSize().isPresent()
                || wamrConfig.maxMemoryPages().isPresent();
    }

    @Override
    public Instance instantiate(LinkingContext linkingContext) {
        return instantiate();
    }

    @Override
    public void close() {
        nativeModule.close();
    }
}
