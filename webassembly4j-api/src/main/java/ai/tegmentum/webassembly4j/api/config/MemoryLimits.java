package ai.tegmentum.webassembly4j.api.config;

import java.util.OptionalLong;

public interface MemoryLimits {

    OptionalLong initialBytes();

    OptionalLong maximumBytes();
}
