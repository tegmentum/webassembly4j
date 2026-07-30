package ai.tegmentum.wasmos.runtime.generated;

import java.util.List;

public final class CapabilitySet {
  private final List<CapabilityEntry> entries;

  private final String source;

  public CapabilitySet(List<CapabilityEntry> entries, String source) {
    this.entries = entries;
    this.source = source;
  }

  public List<CapabilityEntry> entries() {
    return this.entries;
  }

  public String source() {
    return this.source;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    CapabilitySet that = (CapabilitySet) obj;
    return java.util.Objects.equals(this.entries, that.entries) && java.util.Objects.equals(this.source, that.source);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(entries, source);
  }

  @Override
  public String toString() {
    return "CapabilitySet[entries=" + entries + ", source=" + source + "]";
  }
}
