package ai.tegmentum.webassembly4j.api;

import ai.tegmentum.webassembly4j.api.exception.ExecutionException;

/**
 * A callable handle to a Component Model resource returned from a component invocation.
 *
 * <p>Component-model resource methods (a WIT {@code resource X { m: func(...) }}) surface as
 * ordinary exports whose first parameter is the resource handle. This interface hides that
 * mechanical detail — the receiver is bound; the caller writes {@code res.invokeMethod("m",
 * args...)} directly. Symmetric with {@link ComponentInstance#invoke(String, Object...)} on
 * the argument-shape side.
 *
 * <p>Ownership: the receiver is passed by <b>borrow</b> on every call — the resource is not
 * consumed and remains valid for further invocations until {@link #close()} drops it.
 * Close the handle exactly once per {@code own<T>} received from the component; leaking it
 * leaks a slot in the underlying store's resource table, and dropping it twice is an error.
 *
 * <p>Implements {@link AutoCloseable} for try-with-resources ergonomics.
 *
 * @since 2.4.0
 */
public interface WitCallableResource extends AutoCloseable {

    /**
     * Returns the resource type name (e.g. {@code "wasi:io/streams/input-stream"}), if the
     * provider tracks it. Providers may return an empty string if the type name was not
     * carried across the FFI boundary.
     *
     * @return the resource type name, or an empty string if unknown
     */
    String resourceTypeName();

    /**
     * Invokes a method on this resource with the receiver prepended as arg 0, unwrapping the
     * result via the same Java-shape convention as {@link ComponentInstance#invoke(String,
     * Object...)}.
     *
     * @param methodExportName the method's export path (a top-level
     *     {@code [method]<type>.<name>} or an interface-scoped
     *     {@code <interface>#[method]<type>.<name>})
     * @param args the additional args to pass after the receiver
     * @return the method result unwrapped to a natural Java shape, or {@code null} for void
     *     methods
     * @throws ExecutionException if invocation fails
     */
    Object invokeMethod(String methodExportName, Object... args);

    /**
     * Typed variant of {@link #invokeMethod} that returns the provider's WIT value tree
     * directly. Symmetric with {@link ComponentInstance#invokeWit(String, Object...)}.
     *
     * @param methodExportName the method's export path
     * @param args the additional args to pass after the receiver
     * @return the provider's WIT value, or {@code null} for void methods
     * @throws ExecutionException if invocation fails
     */
    Object invokeMethodWit(String methodExportName, Object... args);

    /**
     * Whether this resource has already been {@link #close() closed}. Further calls to
     * {@link #invokeMethod} / {@link #invokeMethodWit} on a closed resource throw.
     */
    boolean isClosed();

    /**
     * Drops this resource, releasing it in the underlying store and removing it from the
     * provider's resource registry. Idempotent — calling {@code close()} twice is a no-op on
     * the second call.
     */
    @Override
    void close();
}
