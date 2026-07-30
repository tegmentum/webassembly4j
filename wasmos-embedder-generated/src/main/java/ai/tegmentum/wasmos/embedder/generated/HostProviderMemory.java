package ai.tegmentum.wasmos.embedder.generated;

public final class HostProviderMemory {
  private final long memory;

  private final long realloc;

  private final StringEncoding stringEncoding;

  public HostProviderMemory(long memory, long realloc, StringEncoding stringEncoding) {
    this.memory = memory;
    this.realloc = realloc;
    this.stringEncoding = stringEncoding;
  }

  public long memory() {
    return this.memory;
  }

  public long realloc() {
    return this.realloc;
  }

  public StringEncoding stringEncoding() {
    return this.stringEncoding;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    HostProviderMemory that = (HostProviderMemory) obj;
    return this.memory == that.memory && this.realloc == that.realloc && java.util.Objects.equals(this.stringEncoding, that.stringEncoding);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(memory, realloc, stringEncoding);
  }

  @Override
  public String toString() {
    return "HostProviderMemory[memory=" + memory + ", realloc=" + realloc + ", stringEncoding=" + stringEncoding + "]";
  }
}
