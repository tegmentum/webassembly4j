package ai.tegmentum.wasmos.embedder.generated;

import ai.tegmentum.webassembly4j.bindgen.wit.WitResult;
import java.util.List;

/**
 * Resource type: runtime-instance
 *
 * <p>This resource should be closed after use.
 */
public class RuntimeInstance implements AutoCloseable {
  private final long handle;

  public RuntimeInstance(long handle) {
    this.handle = handle;
  }

  public long handle() {
    return this.handle;
  }

  @Override
  public void close() {
    EmbedderRuntimeRegistry.runtime().runtimeInstanceClose(this.handle);
  }

  public static WitResult<RuntimeInstance, Error> instantiate(List<Byte> componentBytes,
      List<ImportSatisfaction> imports) {
    return EmbedderRuntimeRegistry.runtime().runtimeInstanceInstantiate(componentBytes, imports);
  }

  public WitResult<List<Byte>, Error> callExport(String name, List<Byte> args) {
    return EmbedderRuntimeRegistry.runtime().runtimeInstanceCallExport(this.handle, name, args);
  }

  public String introspect() {
    return EmbedderRuntimeRegistry.runtime().runtimeInstanceIntrospect(this.handle);
  }

  public WitResult<Void, Error> verifyWorld(String expectedWit) {
    return EmbedderRuntimeRegistry.runtime().runtimeInstanceVerifyWorld(this.handle, expectedWit);
  }
}
