package ai.tegmentum.wasmos.embedder.generated;

import ai.tegmentum.webassembly4j.bindgen.wit.WitResult;
import java.util.List;

/**
 * Runtime-provider SPI for the generated bindings.
 *
 * <p>The embedder implements this interface once and installs the implementation
 * via {@link EmbedderRuntimeRegistry#install(EmbedderRuntime)}. Generated resource method bodies dispatch
 * through {@link EmbedderRuntimeRegistry#runtime()} to reach the installed provider.
 *
 * <p>Emitted by webassembly4j-bindgen 2.0 (runtime-provider dispatch mode).
 */
public interface EmbedderRuntime {
  HostProvider hostProviderCreate(String interfaceName, int numFuncs);

  void hostProviderAttachMemory(long handle, HostProviderMemory mem);

  void hostProviderClose(long handle);

  WitResult<RuntimeInstance, Error> runtimeInstanceInstantiate(List<Byte> componentBytes,
      List<ImportSatisfaction> imports);

  WitResult<List<Byte>, Error> runtimeInstanceCallExport(long handle, String name, List<Byte> args);

  String runtimeInstanceIntrospect(long handle);

  WitResult<Void, Error> runtimeInstanceVerifyWorld(long handle, String expectedWit);

  void runtimeInstanceClose(long handle);
}
