package ai.tegmentum.wasmos.embedder.generated;

/**
 * Resource type: host-provider
 *
 * <p>This resource should be closed after use.
 */
public class HostProvider implements AutoCloseable {
  private final long handle;

  protected HostProvider(long handle) {
    this.handle = handle;
  }

  public long handle() {
    return this.handle;
  }

  @Override
  public void close() {
    // Resource cleanup - to be implemented by runtime
  }

  public static HostProvider create(String interfaceName, int numFuncs) {
    throw new UnsupportedOperationException("wasmos:host/embedder constructor dispatch not yet wired");
  }
}
