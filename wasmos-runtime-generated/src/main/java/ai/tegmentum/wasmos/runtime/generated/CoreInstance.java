package ai.tegmentum.wasmos.runtime.generated;

import ai.tegmentum.webassembly4j.bindgen.wit.WitResult;
import java.util.List;

/**
 * Resource type: core-instance
 *
 * <p>This resource should be closed after use.
 */
public class CoreInstance implements AutoCloseable {
  private final long handle;

  public CoreInstance(long handle) {
    this.handle = handle;
  }

  public long handle() {
    return this.handle;
  }

  @Override
  public void close() {
    WasmosRuntimeRegistry.runtime().coreInstanceClose(this.handle);
  }

  public static WitResult<CoreInstance, Error> fromModule(Runtime runtime, Module module,
      List<Capability> caps) {
    return WasmosRuntimeRegistry.runtime().coreInstanceFromModule(runtime, module, caps);
  }

  public WitResult<List<Value>, Error> invoke(String exportName, List<Value> args) {
    return WasmosRuntimeRegistry.runtime().coreInstanceInvoke(this.handle, exportName, args);
  }

  public WitResult<List<Byte>, Error> readMemory(int offset, int len) {
    return WasmosRuntimeRegistry.runtime().coreInstanceReadMemory(this.handle, offset, len);
  }

  public WitResult<Integer, Error> writeMemory(int offset, List<Byte> bytes) {
    return WasmosRuntimeRegistry.runtime().coreInstanceWriteMemory(this.handle, offset, bytes);
  }
}
