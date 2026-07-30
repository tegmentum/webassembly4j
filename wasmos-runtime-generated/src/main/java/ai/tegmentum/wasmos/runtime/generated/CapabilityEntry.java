package ai.tegmentum.wasmos.runtime.generated;

import java.util.Optional;

public final class CapabilityEntry {
  private final String name;

  private final boolean native_;

  private final boolean available;

  private final Optional<String> provider;

  public CapabilityEntry(String name, boolean native_, boolean available,
      Optional<String> provider) {
    this.name = name;
    this.native_ = native_;
    this.available = available;
    this.provider = provider;
  }

  public String name() {
    return this.name;
  }

  public boolean native_() {
    return this.native_;
  }

  public boolean available() {
    return this.available;
  }

  public Optional<String> provider() {
    return this.provider;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    CapabilityEntry that = (CapabilityEntry) obj;
    return java.util.Objects.equals(this.name, that.name) && this.native_ == that.native_ && this.available == that.available && java.util.Objects.equals(this.provider, that.provider);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(name, native_, available, provider);
  }

  @Override
  public String toString() {
    return "CapabilityEntry[name=" + name + ", native_=" + native_ + ", available=" + available + ", provider=" + provider + "]";
  }
}
