package ai.tegmentum.wasmos.runtime.generated;

import ai.tegmentum.webassembly4j.bindgen.wit.WitResult;
import java.util.List;

/**
 * Runtime-provider SPI for the generated bindings.
 *
 * <p>The embedder implements this interface once and installs the implementation
 * via {@link WasmosRuntimeRegistry#install(WasmosRuntime)}. Generated resource method bodies dispatch
 * through {@link WasmosRuntimeRegistry#runtime()} to reach the installed provider.
 *
 * <p>Emitted by webassembly4j-bindgen 2.0 (runtime-provider dispatch mode).
 */
public interface WasmosRuntime {
  Runtime runtimeCreate(ResourceLimits limits);

  void runtimeClose(long handle);

  WitResult<Module, Error> moduleFromBytes(Runtime runtime, List<Byte> bytes);

  List<String> moduleExportNames(long handle);

  void moduleClose(long handle);

  WitResult<CoreInstance, Error> coreInstanceFromModule(Runtime runtime, Module module,
      List<Capability> caps);

  WitResult<List<Value>, Error> coreInstanceInvoke(long handle, String exportName,
      List<Value> args);

  WitResult<List<Byte>, Error> coreInstanceReadMemory(long handle, int offset, int len);

  WitResult<Integer, Error> coreInstanceWriteMemory(long handle, int offset, List<Byte> bytes);

  void coreInstanceClose(long handle);

  WitResult<Component, Error> componentFromBytes(Runtime runtime, List<Byte> bytes);

  List<String> componentExportNames(long handle);

  void componentClose(long handle);

  WitResult<Instance, Error> instanceFromComponent(Runtime runtime, Component component,
      List<Capability> caps);

  WitResult<List<Value>, Error> instanceInvoke(long handle, String exportName, List<Value> args);

  void instanceClose(long handle);
}
