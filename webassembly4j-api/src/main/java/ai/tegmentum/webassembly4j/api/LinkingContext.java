package ai.tegmentum.webassembly4j.api;

import java.util.Collections;
import java.util.List;

public interface LinkingContext {

    default List<HostFunctionDefinition> hostFunctions() {
        return Collections.emptyList();
    }

    default WasiContext wasiContext() {
        return null;
    }
}
