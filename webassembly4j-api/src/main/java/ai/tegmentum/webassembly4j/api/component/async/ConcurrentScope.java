package ai.tegmentum.webassembly4j.api.component.async;

/**
 * Scope for concurrent component model function invocations.
 * Within a concurrent scope, multiple async calls can be made that
 * execute concurrently.
 */
public interface ConcurrentScope {

    /**
     * Invokes a component function concurrently within this scope.
     *
     * @param functionName the exported function name
     * @param args the function arguments
     * @return the function result, or null for void functions
     */
    Object callConcurrent(String functionName, Object... args);
}
