package ai.tegmentum.webassembly4j.benchmarks;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.WebAssembly;

public final class BenchmarkSupport {

    private BenchmarkSupport() {
    }

    public static Engine createEngine(EngineVariant variant) {
        if (variant.systemProperty() != null) {
            System.setProperty(variant.systemProperty(), variant.propertyValue());
        }
        try {
            return WebAssembly.builder().engine(variant.engineId()).build();
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
            Engine engine = WebAssembly.builder().engine(variant.engineId()).build();
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
