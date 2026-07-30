package ai.tegmentum.wasmos.runtime.generated;

import ai.tegmentum.webassembly4j.bindgen.wit.WitResult;
import java.util.List;

/**
 * Resource type: component
 *
 * <p>This resource should be closed after use.
 */
public class Component implements AutoCloseable {
  private final long handle;

  public Component(long handle) {
    this.handle = handle;
  }

  public long handle() {
    return this.handle;
  }

  @Override
  public void close() {
    WasmosRuntimeRegistry.runtime().componentClose(this.handle);
  }

  public static WitResult<Component, Error> fromBytes(Runtime runtime, List<Byte> bytes) {
    return WasmosRuntimeRegistry.runtime().componentFromBytes(runtime, bytes);
  }

  public List<String> exportNames() {
    return WasmosRuntimeRegistry.runtime().componentExportNames(this.handle);
  }
}
