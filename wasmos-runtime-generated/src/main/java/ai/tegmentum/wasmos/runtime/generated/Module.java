package ai.tegmentum.wasmos.runtime.generated;

import ai.tegmentum.webassembly4j.bindgen.wit.WitResult;
import java.util.List;

/**
 * Resource type: module
 *
 * <p>This resource should be closed after use.
 */
public class Module implements AutoCloseable {
  private final long handle;

  public Module(long handle) {
    this.handle = handle;
  }

  public long handle() {
    return this.handle;
  }

  @Override
  public void close() {
    WasmosRuntimeRegistry.runtime().moduleClose(this.handle);
  }

  public static WitResult<Module, Error> fromBytes(Runtime runtime, List<Byte> bytes) {
    return WasmosRuntimeRegistry.runtime().moduleFromBytes(runtime, bytes);
  }

  public List<String> exportNames() {
    return WasmosRuntimeRegistry.runtime().moduleExportNames(this.handle);
  }
}
