package ai.tegmentum.webassembly4j.spring;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.EngineCapabilities;
import ai.tegmentum.webassembly4j.api.EngineInfo;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Actuator endpoint that exposes WebAssembly engine info and capabilities.
 * Accessible at {@code /actuator/wasm}.
 */
@Endpoint(id = "wasm")
public class WebAssemblyInfoEndpoint {

    private final Engine engine;

    public WebAssemblyInfoEndpoint(Engine engine) {
        this.engine = engine;
    }

    @ReadOperation
    public Map<String, Object> info() {
        Map<String, Object> result = new LinkedHashMap<>();

        EngineInfo engineInfo = engine.info();
        Map<String, String> info = new LinkedHashMap<>();
        info.put("engineId", engineInfo.engineId());
        info.put("providerId", engineInfo.providerId());
        info.put("engineVersion", engineInfo.engineVersion());
        info.put("providerVersion", engineInfo.providerVersion());
        info.put("minimumJavaVersion", String.valueOf(engineInfo.minimumJavaVersion()));
        result.put("info", info);

        EngineCapabilities caps = engine.capabilities();
        Map<String, Boolean> capabilities = new LinkedHashMap<>();
        capabilities.put("coreModules", caps.supportsCoreModules());
        capabilities.put("components", caps.supportsComponents());
        capabilities.put("wasi", caps.supportsWasi());
        capabilities.put("fuel", caps.supportsFuel());
        capabilities.put("threads", caps.supportsThreads());
        capabilities.put("gc", caps.supportsGc());
        capabilities.put("referenceTypes", caps.supportsReferenceTypes());
        capabilities.put("multiMemory", caps.supportsMultiMemory());
        capabilities.put("nativeInterop", caps.supportsNativeInterop());
        result.put("capabilities", capabilities);

        return result;
    }
}
