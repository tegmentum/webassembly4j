package ai.tegmentum.webassembly4j.api.component.async;

/**
 * A task that executes within a {@link ConcurrentScope}, allowing
 * concurrent component model function invocations.
 *
 * @param <T> the result type of the task
 */
@FunctionalInterface
public interface ConcurrentTask<T> {

    /**
     * Executes the task within the given concurrent scope.
     *
     * @param scope the concurrent scope for making async calls
     * @return the task result
     * @throws Exception if the task fails
     */
    T execute(ConcurrentScope scope) throws Exception;
}
