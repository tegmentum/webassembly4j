package ai.tegmentum.webassembly4j.api.config;

import java.util.OptionalLong;

public interface ResourceLimits {

    OptionalLong maxMemoryBytes();

    OptionalLong maxTableElements();

    OptionalLong maxInstances();

    OptionalLong maxExecutionTimeMillis();
}
