package ai.tegmentum.wasmos.runtime.generated;

import java.util.Optional;

public final class Capability {
  private final String name;

  private final Optional<String> scope;

  public Capability(String name, Optional<String> scope) {
    this.name = name;
    this.scope = scope;
  }

  public String name() {
    return this.name;
  }

  public Optional<String> scope() {
    return this.scope;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    Capability that = (Capability) obj;
    return java.util.Objects.equals(this.name, that.name) && java.util.Objects.equals(this.scope, that.scope);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(name, scope);
  }

  @Override
  public String toString() {
    return "Capability[name=" + name + ", scope=" + scope + "]";
  }
}
