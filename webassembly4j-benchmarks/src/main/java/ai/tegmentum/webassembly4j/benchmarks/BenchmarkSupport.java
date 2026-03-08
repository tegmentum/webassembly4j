package ai.tegmentum.webassembly4j.benchmarks;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.WebAssembly;
import ai.tegmentum.webassembly4j.api.WebAssemblyBuilder;
import ai.tegmentum.webassembly4j.api.config.EngineConfig;

public final class BenchmarkSupport {

    private BenchmarkSupport() {
    }

    public static Engine createEngine(EngineVariant variant) {
        if (variant.systemProperty() != null) {
            System.setProperty(variant.systemProperty(), variant.propertyValue());
        }
        try {
            WebAssemblyBuilder builder = WebAssembly.builder().engine(variant.engineId());
            EngineConfig config = variant.engineConfig();
            if (config != null) {
                builder.engineConfig(config);
            }
            return builder.build();
        } finally {
            if (variant.systemProperty() != null) {
                System.clearProperty(variant.systemProperty());
            }
        }
    }

    public static boolean isAvailable(EngineVariant variant) {
        if (variant.systemProperty() != null) {
            System.setProperty(variant.systemProperty(), variant.propertyValue());
        }
        try {
            WebAssemblyBuilder builder = WebAssembly.builder().engine(variant.engineId());
            EngineConfig config = variant.engineConfig();
            if (config != null) {
                builder.engineConfig(config);
            }
            Engine engine = builder.build();
            engine.close();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (variant.systemProperty() != null) {
                System.clearProperty(variant.systemProperty());
            }
        }
    }
}
