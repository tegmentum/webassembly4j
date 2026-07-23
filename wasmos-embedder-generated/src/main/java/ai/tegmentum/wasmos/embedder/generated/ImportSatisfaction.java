package ai.tegmentum.wasmos.embedder.generated;

public final class ImportSatisfaction {
  private final String interfaceName;

  private final HostProvider provider;

  public ImportSatisfaction(String interfaceName, HostProvider provider) {
    this.interfaceName = interfaceName;
    this.provider = provider;
  }

  public String interfaceName() {
    return this.interfaceName;
  }

  public HostProvider provider() {
    return this.provider;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    ImportSatisfaction that = (ImportSatisfaction) obj;
    return java.util.Objects.equals(this.interfaceName, that.interfaceName) && java.util.Objects.equals(this.provider, that.provider);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(interfaceName, provider);
  }

  @Override
  public String toString() {
    return "ImportSatisfaction[interfaceName=" + interfaceName + ", provider=" + provider + "]";
  }
}
