package ai.tegmentum.webassembly4j.bindgen.wit;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Runtime representation of the WIT {@code result<ok, err>} type.
 *
 * <p>The bindgen code generator references this class by its fully-qualified
 * name (see {@link
 * ai.tegmentum.webassembly4j.bindgen.generator.TypeMappingRegistry}); it must
 * therefore exist as a real class on the consumer's classpath. The two
 * factory methods {@link #ok(Object)} and {@link #err(Object)} construct
 * either side; the resulting instance is immutable.
 *
 * <p>Generated code typically follows this pattern:
 *
 * <pre>{@code
 * WitResult<ParseResult, String> result = parser.parse(language, source);
 * if (result.isOk()) {
 *     ParseResult tree = result.unwrap();
 * } else {
 *     String msg = result.unwrapErr();
 * }
 * }</pre>
 *
 * @param <T> the success-payload type ({@link Void} when the WIT signature is
 *     {@code result<_, err>})
 * @param <E> the error-payload type ({@link Void} when the WIT signature is
 *     {@code result<ok>} or {@code result})
 */
public final class WitResult<T, E> {

  private final T ok;
  private final E err;
  private final boolean isOk;

  private WitResult(final T ok, final E err, final boolean isOk) {
    this.ok = ok;
    this.err = err;
    this.isOk = isOk;
  }

  /** Construct a successful result. */
  public static <T, E> WitResult<T, E> ok(final T value) {
    return new WitResult<>(value, null, true);
  }

  /** Construct an error result. */
  public static <T, E> WitResult<T, E> err(final E value) {
    return new WitResult<>(null, value, false);
  }

  /** True if this is a success result. */
  public boolean isOk() {
    return isOk;
  }

  /** True if this is an error result. */
  public boolean isErr() {
    return !isOk;
  }

  /** Returns the success payload or throws when {@link #isErr()}. */
  public T unwrap() {
    if (!isOk) {
      throw new NoSuchElementException("WitResult is err: " + err);
    }
    return ok;
  }

  /** Returns the error payload or throws when {@link #isOk()}. */
  public E unwrapErr() {
    if (isOk) {
      throw new NoSuchElementException("WitResult is ok: " + ok);
    }
    return err;
  }

  /** Optional view of the success side. */
  public Optional<T> ok() {
    return isOk ? Optional.ofNullable(ok) : Optional.empty();
  }

  /** Optional view of the error side. */
  public Optional<E> err() {
    return isOk ? Optional.empty() : Optional.ofNullable(err);
  }

  /** Map the success payload through {@code f}, leaving an error unchanged. */
  public <U> WitResult<U, E> map(final Function<? super T, ? extends U> f) {
    Objects.requireNonNull(f, "f");
    return isOk ? WitResult.ok(f.apply(ok)) : WitResult.err(err);
  }

  /** Map the error payload through {@code f}, leaving a success unchanged. */
  public <F> WitResult<T, F> mapErr(final Function<? super E, ? extends F> f) {
    Objects.requireNonNull(f, "f");
    return isOk ? WitResult.ok(ok) : WitResult.err(f.apply(err));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof WitResult)) {
      return false;
    }
    final WitResult<?, ?> other = (WitResult<?, ?>) o;
    return isOk == other.isOk
        && Objects.equals(ok, other.ok)
        && Objects.equals(err, other.err);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ok, err, isOk);
  }

  @Override
  public String toString() {
    return isOk ? "Ok(" + ok + ")" : "Err(" + err + ")";
  }
}
