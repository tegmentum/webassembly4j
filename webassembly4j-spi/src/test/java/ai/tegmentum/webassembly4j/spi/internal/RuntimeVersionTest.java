package ai.tegmentum.webassembly4j.spi.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeVersionTest {

    @Test
    void currentJavaVersionIsPositive() {
        int version = RuntimeVersion.currentJavaVersion();
        assertTrue(version > 0, "Java version should be positive, got: " + version);
    }

    @Test
    void currentJavaVersionIsAtLeast11() {
        int version = RuntimeVersion.currentJavaVersion();
        assertTrue(version >= 11, "Expected Java 11+, got: " + version);
    }
}
