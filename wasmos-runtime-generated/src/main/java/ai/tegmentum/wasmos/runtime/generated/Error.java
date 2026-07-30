package ai.tegmentum.wasmos.runtime.generated;

public final class Error {
  private final ErrorCategory category;

  private final String message;

  public Error(ErrorCategory category, String message) {
    this.category = category;
    this.message = message;
  }

  public ErrorCategory category() {
    return this.category;
  }

  public String message() {
    return this.message;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    Error that = (Error) obj;
    return java.util.Objects.equals(this.category, that.category) && java.util.Objects.equals(this.message, that.message);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(category, message);
  }

  @Override
  public String toString() {
    return "Error[category=" + category + ", message=" + message + "]";
  }
}
