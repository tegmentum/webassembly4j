package ai.tegmentum.webassembly4j.spring;

import ai.tegmentum.webassembly4j.api.Component;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.EngineCapabilities;
import ai.tegmentum.webassembly4j.api.EngineInfo;
import ai.tegmentum.webassembly4j.api.Module;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WebAssemblyHealthIndicatorTest {

    @Test
    void healthyEngine() {
        Engine engine = stubEngine();
        WebAssemblyHealthIndicator indicator = new WebAssemblyHealthIndicator(engine);
        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("wasmtime", health.getDetails().get("engine"));
        assertEquals("wasmtime4j", health.getDetails().get("provider"));
    }

    @Test
    void unhealthyEngine() {
        Engine engine = createFailingEngine();
        WebAssemblyHealthIndicator indicator = new WebAssemblyHealthIndicator(engine);
        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertNotNull(health.getDetails().get("error"));
    }

    private static Engine stubEngine() {
        return new Engine() {
            @Override public EngineInfo info() {
                return new EngineInfo() {
                    @Override public String engineId() { return "wasmtime"; }
                    @Override public String providerId() { return "wasmtime4j"; }
                    @Override public String providerVersion() { return "1.0.0"; }
                    @Override public String engineVersion() { return "42.0.1"; }
                    @Override public int minimumJavaVersion() { return 11; }
                };
            }
            @Override public EngineCapabilities capabilities() { return null; }
            @Override public Module loadModule(byte[] bytes) { return null; }
            @Override public Component loadComponent(byte[] bytes) { return null; }
            @Override public <T> Optional<T> extension(Class<T> t) { return Optional.empty(); }
            @Override public <T> Optional<T> unwrap(Class<T> t) { return Optional.empty(); }
            @Override public void close() {}
        };
    }

    private static Engine createFailingEngine() {
        return new Engine() {
            @Override public EngineInfo info() { throw new RuntimeException("Engine failed"); }
            @Override public EngineCapabilities capabilities() { return null; }
            @Override public Module loadModule(byte[] bytes) { return null; }
            @Override public Component loadComponent(byte[] bytes) { return null; }
            @Override public <T> Optional<T> extension(Class<T> t) { return Optional.empty(); }
            @Override public <T> Optional<T> unwrap(Class<T> t) { return Optional.empty(); }
            @Override public void close() {}
        };
    }
}
