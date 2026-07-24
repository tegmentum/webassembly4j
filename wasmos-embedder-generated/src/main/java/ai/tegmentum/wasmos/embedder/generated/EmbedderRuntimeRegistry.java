package ai.tegmentum.wasmos.embedder.generated;

import java.util.Objects;

/**
 * Single-slot registry for the {@link EmbedderRuntime} runtime-provider SPI.
 *
 * <p>Call {@link #install(EmbedderRuntime)} once from the embedder's startup path, before
 * invoking any generated resource method. Generated bodies reach the installed
 * provider via {@link #runtime()}, which throws {@link IllegalStateException} if
 * nothing was installed.
 *
 * <p>Emitted by webassembly4j-bindgen 2.0.
 */
public final class EmbedderRuntimeRegistry {
  private static volatile EmbedderRuntime provider;

  private EmbedderRuntimeRegistry() {
  }

  public static void install(EmbedderRuntime provider) {
    EmbedderRuntimeRegistry.provider = Objects.requireNonNull(provider, "provider");
  }

  public static void uninstall() {
    EmbedderRuntimeRegistry.provider = null;
  }

  public static EmbedderRuntime runtime() {
    EmbedderRuntime r = provider;
    if (r == null) {
      throw new IllegalStateException("EmbedderRuntime not installed; call EmbedderRuntimeRegistry.install(...) before invoking generated bindings");
    }
    return r;
  }
}
