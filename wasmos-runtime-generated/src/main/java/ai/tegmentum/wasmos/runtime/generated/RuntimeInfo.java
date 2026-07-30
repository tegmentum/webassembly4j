package ai.tegmentum.wasmos.runtime.generated;

import java.util.List;

public final class RuntimeInfo {
  private final String name;

  private final String version;

  private final List<String> features;

  public RuntimeInfo(String name, String version, List<String> features) {
    this.name = name;
    this.version = version;
    this.features = features;
  }

  public String name() {
    return this.name;
  }

  public String version() {
    return this.version;
  }

  public List<String> features() {
    return this.features;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    RuntimeInfo that = (RuntimeInfo) obj;
    return java.util.Objects.equals(this.name, that.name) && java.util.Objects.equals(this.version, that.version) && java.util.Objects.equals(this.features, that.features);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(name, version, features);
  }

  @Override
  public String toString() {
    return "RuntimeInfo[name=" + name + ", version=" + version + ", features=" + features + "]";
  }
}
