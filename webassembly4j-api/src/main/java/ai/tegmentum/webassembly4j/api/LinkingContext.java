package ai.tegmentum.webassembly4j.api;

import java.util.Collections;
import java.util.List;

public interface LinkingContext {

    default List<HostFunctionDefinition> hostFunctions() {
        return Collections.emptyList();
    }

    /**
     * WIT-typed host functions to link into a component's imports. Providers may
     * ignore these when instantiating a core module. See {@link WitHostFunction}
     * for the value-type contract.
     */
    default List<WitHostFunctionDefinition> witHostFunctions() {
        return Collections.emptyList();
    }

    default WasiContext wasiContext() {
        return null;
    }
}
