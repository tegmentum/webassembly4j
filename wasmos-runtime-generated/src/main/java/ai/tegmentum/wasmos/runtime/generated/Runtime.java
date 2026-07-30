package ai.tegmentum.wasmos.runtime.generated;

/**
 * Resource type: runtime
 *
 * <p>This resource should be closed after use.
 */
public class Runtime implements AutoCloseable {
  private final long handle;

  public Runtime(long handle) {
    this.handle = handle;
  }

  public long handle() {
    return this.handle;
  }

  @Override
  public void close() {
    WasmosRuntimeRegistry.runtime().runtimeClose(this.handle);
  }

  public static Runtime create(ResourceLimits limits) {
    return WasmosRuntimeRegistry.runtime().runtimeCreate(limits);
  }
}
