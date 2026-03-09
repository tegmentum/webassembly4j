package ai.tegmentum.webassembly4j.spring;

import ai.tegmentum.webassembly4j.api.Component;
import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.EngineCapabilities;
import ai.tegmentum.webassembly4j.api.EngineInfo;
import ai.tegmentum.webassembly4j.api.Module;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebAssemblyInfoEndpointTest {

    @Test
    @SuppressWarnings("unchecked")
    void infoContainsEngineDetails() {
        Engine engine = stubEngine();
        WebAssemblyInfoEndpoint endpoint = new WebAssemblyInfoEndpoint(engine);
        Map<String, Object> result = endpoint.info();

        assertNotNull(result.get("info"));
        Map<String, String> info = (Map<String, String>) result.get("info");
        assertEquals("wasmtime", info.get("engineId"));
        assertEquals("wasmtime4j", info.get("providerId"));
        assertEquals("42.0.1", info.get("engineVersion"));
        assertEquals("1.0.0", info.get("providerVersion"));
        assertEquals("11", info.get("minimumJavaVersion"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void infoContainsCapabilities() {
        Engine engine = stubEngine();
        WebAssemblyInfoEndpoint endpoint = new WebAssemblyInfoEndpoint(engine);
        Map<String, Object> result = endpoint.info();

        assertNotNull(result.get("capabilities"));
        Map<String, Boolean> caps = (Map<String, Boolean>) result.get("capabilities");
        assertTrue(caps.get("coreModules"));
        assertTrue(caps.get("wasi"));
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
            @Override public EngineCapabilities capabilities() {
                return new EngineCapabilities() {
                    @Override public boolean supportsCoreModules() { return true; }
                    @Override public boolean supportsComponents() { return true; }
                    @Override public boolean supportsWasi() { return true; }
                    @Override public boolean supportsFuel() { return true; }
                    @Override public boolean supportsEpochInterruption() { return false; }
                    @Override public boolean supportsThreads() { return false; }
                    @Override public boolean supportsGc() { return false; }
                    @Override public boolean supportsReferenceTypes() { return true; }
                    @Override public boolean supportsMultiMemory() { return false; }
                    @Override public boolean supportsNativeInterop() { return true; }
                };
            }
            @Override public Module loadModule(byte[] bytes) { return null; }
            @Override public Component loadComponent(byte[] bytes) { return null; }
            @Override public <T> Optional<T> extension(Class<T> t) { return Optional.empty(); }
            @Override public <T> Optional<T> unwrap(Class<T> t) { return Optional.empty(); }
            @Override public void close() {}
        };
    }
}
