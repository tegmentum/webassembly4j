package ai.tegmentum.wasmos.runtime.generated;

import ai.tegmentum.webassembly4j.bindgen.wit.WitResult;
import java.util.List;

/**
 * Resource type: instance
 *
 * <p>This resource should be closed after use.
 */
public class Instance implements AutoCloseable {
  private final long handle;

  public Instance(long handle) {
    this.handle = handle;
  }

  public long handle() {
    return this.handle;
  }

  @Override
  public void close() {
    WasmosRuntimeRegistry.runtime().instanceClose(this.handle);
  }

  public static WitResult<Instance, Error> fromComponent(Runtime runtime, Component component,
      List<Capability> caps) {
    return WasmosRuntimeRegistry.runtime().instanceFromComponent(runtime, component, caps);
  }

  public WitResult<List<Value>, Error> invoke(String exportName, List<Value> args) {
    return WasmosRuntimeRegistry.runtime().instanceInvoke(this.handle, exportName, args);
  }
}
