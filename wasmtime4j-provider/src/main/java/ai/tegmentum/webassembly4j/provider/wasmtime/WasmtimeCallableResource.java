package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.wasmtime4j.component.ComponentInstance;
import ai.tegmentum.wasmtime4j.exception.WasmException;
import ai.tegmentum.wasmtime4j.wit.WitResource;
import ai.tegmentum.wasmtime4j.wit.WitValue;
import ai.tegmentum.webassembly4j.api.WitCallableResource;
import ai.tegmentum.webassembly4j.api.exception.ExecutionException;

import java.util.Objects;

/**
 * Provider-neutral callable-resource surface for wasmtime — binds a wasmtime {@link WitResource}
 * to the {@link ComponentInstance} it came from, so downstream code can dispatch a method on
 * the resource without threading the receiver + instance pair by hand.
 *
 * <p>Instantiated via {@link WasmtimeComponentInstanceAdapter#asCallableResource(Object)}.
 */
final class WasmtimeCallableResource implements WitCallableResource {

    private final WitResource resource;
    private final ComponentInstance instance;
    private volatile boolean closed;

    WasmtimeCallableResource(final WitResource resource, final ComponentInstance instance) {
        this.resource = Objects.requireNonNull(resource, "resource");
        this.instance = Objects.requireNonNull(instance, "instance");
    }

    @Override
    public String resourceTypeName() {
        return resource.getResourceTypeName();
    }

    @Override
    public Object invokeMethod(final String methodExportName, final Object... args) {
        ensureOpen();
        try {
            return instance.invokeResourceMethod(resource, methodExportName, marshalArgs(args));
        } catch (final WasmException e) {
            throw new ExecutionException(
                    "Failed to invoke resource method '" + methodExportName + "'", e);
        }
    }

    @Override
    public Object invokeMethodWit(final String methodExportName, final Object... args) {
        ensureOpen();
        try {
            final WitValue result =
                    instance.invokeResourceMethodWit(resource, methodExportName, marshalArgs(args));
            return result;
        } catch (final WasmException e) {
            throw new ExecutionException(
                    "Failed to invoke resource method '" + methodExportName + "'", e);
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            instance.dropResource(resource);
        } catch (final UnsupportedOperationException e) {
            // A pure-Java WitResource has no native backing; drop is a no-op — we already
            // flipped `closed`, so further invokeMethod calls will throw the usual "closed"
            // error rather than silently continuing to talk to a phantom native handle.
        } catch (final WasmException e) {
            throw new ExecutionException("Failed to drop resource", e);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Resource has been closed");
        }
    }

    /**
     * Reuse the adapter's argument marshaller so an {@link Object} arg that isn't already a
     * {@code WitValue} is coerced by the same rules as {@link
     * WasmtimeComponentInstanceAdapter#invoke(String, Object...)}.
     */
    private static Object[] marshalArgs(final Object[] args) {
        if (args == null || args.length == 0) {
            return new Object[0];
        }
        final Object[] witArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            try {
                witArgs[i] = WasmtimeComponentInstanceAdapter.toWitValue(args[i]);
            } catch (final WasmException e) {
                throw new ExecutionException(
                        "Argument " + i + " is not a valid WIT value", e);
            }
        }
        return witArgs;
    }
}
