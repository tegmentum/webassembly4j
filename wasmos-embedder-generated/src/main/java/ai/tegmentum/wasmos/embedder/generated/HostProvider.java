package ai.tegmentum.wasmos.embedder.generated;

/**
 * Resource type: host-provider
 *
 * <p>This resource should be closed after use.
 */
public class HostProvider implements AutoCloseable {
  private final long handle;

  public HostProvider(long handle) {
    this.handle = handle;
  }

  public long handle() {
    return this.handle;
  }

  @Override
  public void close() {
    EmbedderRuntimeRegistry.runtime().hostProviderClose(this.handle);
  }

  public static HostProvider create(String interfaceName, int numFuncs) {
    return EmbedderRuntimeRegistry.runtime().hostProviderCreate(interfaceName, numFuncs);
  }

  public void attachMemory(HostProviderMemory mem) {
    EmbedderRuntimeRegistry.runtime().hostProviderAttachMemory(this.handle, mem);
  }
}
