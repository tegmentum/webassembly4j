package ai.tegmentum.wasmos.runtime.generated;

import java.util.Optional;

public final class ResourceLimits {
  private final Optional<Long> maxMemoryBytes;

  private final Optional<Long> cycleBudget;

  public ResourceLimits(Optional<Long> maxMemoryBytes, Optional<Long> cycleBudget) {
    this.maxMemoryBytes = maxMemoryBytes;
    this.cycleBudget = cycleBudget;
  }

  public Optional<Long> maxMemoryBytes() {
    return this.maxMemoryBytes;
  }

  public Optional<Long> cycleBudget() {
    return this.cycleBudget;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    ResourceLimits that = (ResourceLimits) obj;
    return java.util.Objects.equals(this.maxMemoryBytes, that.maxMemoryBytes) && java.util.Objects.equals(this.cycleBudget, that.cycleBudget);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(maxMemoryBytes, cycleBudget);
  }

  @Override
  public String toString() {
    return "ResourceLimits[maxMemoryBytes=" + maxMemoryBytes + ", cycleBudget=" + cycleBudget + "]";
  }
}
