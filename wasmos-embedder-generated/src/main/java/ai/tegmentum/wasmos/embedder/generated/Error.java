package ai.tegmentum.wasmos.embedder.generated;

public final class Error {
  private final ErrorCode code;

  private final String message;

  public Error(ErrorCode code, String message) {
    this.code = code;
    this.message = message;
  }

  public ErrorCode code() {
    return this.code;
  }

  public String message() {
    return this.message;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    Error that = (Error) obj;
    return java.util.Objects.equals(this.code, that.code) && java.util.Objects.equals(this.message, that.message);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(code, message);
  }

  @Override
  public String toString() {
    return "Error[code=" + code + ", message=" + message + "]";
  }
}
