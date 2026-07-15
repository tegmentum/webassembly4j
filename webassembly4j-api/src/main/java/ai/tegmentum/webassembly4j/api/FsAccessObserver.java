package ai.tegmentum.webassembly4j.api;

/**
 * Engine-neutral, observe-only callback notified when a component's path-based filesystem access is
 * DENIED on the capability-confined (WASI preopen) instantiation path.
 *
 * <p>This is a pure observability hook. It CANNOT change enforcement: by the time it fires the
 * runtime has already performed the real {@code open-at} / {@code stat-at} and produced the real
 * error, which flows back to the guest unchanged regardless of what this method does. Its sole
 * purpose is to surface the raw guest-supplied path together with the classified failure reason so
 * the host can log, meter, or alert on denied filesystem access that would otherwise be invisible on
 * the component/preopen path (where the runtime refuses opens internally, with no host callback on
 * the enforcement path itself).
 *
 * <p>The interface is intentionally engine-agnostic — it names only {@code java.lang} types and does
 * not depend on any particular WebAssembly runtime. A provider that supports a native denial hook
 * (for example the wasmtime provider, which bridges this to wasmtime4j's own observer) adapts this
 * to its runtime; providers without such a hook simply never invoke it.
 *
 * <p>Implementations are invoked synchronously on the guest's calling thread while the denied
 * operation is being serviced. They should be fast and must not assume they can influence the
 * outcome. A provider is expected to isolate the guest from any exception this method throws.
 *
 * @since 2.3.0
 */
@FunctionalInterface
public interface FsAccessObserver {

    /**
     * Called when a path-based filesystem operation was refused by WASI enforcement.
     *
     * @param path the raw guest-supplied path the operation targeted (relative to the descriptor it
     *     was resolved against; never {@code null})
     * @param operation the operation that was denied, e.g. {@code "open-at"} or {@code "stat-at"}
     * @param reason the classified failure reason: the kebab-case WASI {@code error-code} name, e.g.
     *     {@code "not-permitted"}, {@code "no-entry"}, {@code "access"}; {@code "unknown"} if the
     *     failure could not be classified to an error code
     * @param errorCode the numeric discriminant of {@code reason} within the WASI {@code error-code}
     *     enum, or {@code -1} when unclassified
     */
    void onDenied(String path, String operation, String reason, int errorCode);
}
