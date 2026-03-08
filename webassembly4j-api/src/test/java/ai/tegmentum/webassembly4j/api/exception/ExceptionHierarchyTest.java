package ai.tegmentum.webassembly4j.api.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionHierarchyTest {

    @Test
    void allExceptionsExtendWebAssemblyException() {
        assertInstanceOf(WebAssemblyException.class, new ConfigurationException("test"));
        assertInstanceOf(WebAssemblyException.class, new ProviderUnavailableException("test"));
        assertInstanceOf(WebAssemblyException.class, new UnsupportedFeatureException("test"));
        assertInstanceOf(WebAssemblyException.class, new ValidationException("test"));
        assertInstanceOf(WebAssemblyException.class, new LinkingException("test"));
        assertInstanceOf(WebAssemblyException.class, new InstantiationException("test"));
        assertInstanceOf(WebAssemblyException.class, new ExecutionException("test"));
    }

    @Test
    void trapExceptionExtendsExecutionException() {
        assertInstanceOf(ExecutionException.class, new TrapException("trap"));
    }

    @Test
    void webAssemblyExceptionIsRuntimeException() {
        assertInstanceOf(RuntimeException.class, new WebAssemblyException("test"));
    }

    @Test
    void exceptionPreservesMessage() {
        assertEquals("bad config", new ConfigurationException("bad config").getMessage());
    }

    @Test
    void exceptionPreservesCause() {
        Throwable cause = new RuntimeException("root");
        ExecutionException ex = new ExecutionException("failed", cause);
        assertSame(cause, ex.getCause());
    }
}
