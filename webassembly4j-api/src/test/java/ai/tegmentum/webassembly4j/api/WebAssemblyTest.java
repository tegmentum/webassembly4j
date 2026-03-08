package ai.tegmentum.webassembly4j.api;

import org.junit.jupiter.api.Test;
import ai.tegmentum.webassembly4j.api.exception.ProviderUnavailableException;

import static org.junit.jupiter.api.Assertions.*;

class WebAssemblyTest {

    @Test
    void builderReturnsNonNull() {
        WebAssemblyBuilder builder = WebAssembly.builder();
        assertNotNull(builder);
    }

    @Test
    void buildWithoutProviderThrowsProviderUnavailable() {
        WebAssemblyBuilder builder = WebAssembly.builder();
        assertThrows(ProviderUnavailableException.class, builder::build);
    }

    @Test
    void builderMethodsReturnSameBuilder() {
        WebAssemblyBuilder builder = WebAssembly.builder();
        assertSame(builder, builder.engine("wasmtime"));
        assertSame(builder, builder.provider("wasmtime-ffm"));
    }
}
