package ai.tegmentum.webassembly4j.api.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class WasiHttpConfigTest {

    @Test
    void defaultsAreNull() {
        WasiHttpConfig config = WasiHttpConfig.defaults();

        assertNull(config.connectTimeout());
        assertNull(config.readTimeout());
        assertNull(config.maxConnections());
        assertNull(config.maxRequestBodySize());
        assertNull(config.maxResponseBodySize());
    }

    @Test
    void builderSetsValues() {
        WasiHttpConfig config = WasiHttpConfig.builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(60))
                .maxConnections(100)
                .maxRequestBodySize(1024 * 1024)
                .maxResponseBodySize(10 * 1024 * 1024)
                .build();

        assertEquals(Duration.ofSeconds(30), config.connectTimeout());
        assertEquals(Duration.ofSeconds(60), config.readTimeout());
        assertEquals(100, config.maxConnections());
        assertEquals(1024 * 1024, config.maxRequestBodySize());
        assertEquals(10 * 1024 * 1024, config.maxResponseBodySize());
    }

    @Test
    void wasiConfigDefaultHttpConfigIsNull() {
        WasiConfig wasiConfig = new WasiConfig() {
            @Override
            public java.util.List<String> args() {
                return java.util.Collections.emptyList();
            }

            @Override
            public java.util.Map<String, String> env() {
                return java.util.Collections.emptyMap();
            }

            @Override
            public boolean inheritStdin() {
                return false;
            }

            @Override
            public boolean inheritStdout() {
                return false;
            }

            @Override
            public boolean inheritStderr() {
                return false;
            }
        };
        assertNull(wasiConfig.httpConfig());
    }
}
