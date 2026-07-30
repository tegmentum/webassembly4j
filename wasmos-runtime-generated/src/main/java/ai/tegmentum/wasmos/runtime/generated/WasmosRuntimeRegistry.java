package ai.tegmentum.wasmos.runtime.generated;

import java.util.Objects;

/**
 * Single-slot registry for the {@link WasmosRuntime} runtime-provider SPI.
 *
 * <p>Call {@link #install(WasmosRuntime)} once from the embedder's startup path, before
 * invoking any generated resource method. Generated bodies reach the installed
 * provider via {@link #runtime()}, which throws {@link IllegalStateException} if
 * nothing was installed.
 *
 * <p>Emitted by webassembly4j-bindgen 2.0.
 */
public final class WasmosRuntimeRegistry {
  private static volatile WasmosRuntime provider;

  private WasmosRuntimeRegistry() {
  }

  public static void install(WasmosRuntime provider) {
    WasmosRuntimeRegistry.provider = Objects.requireNonNull(provider, "provider");
  }

  public static void uninstall() {
    WasmosRuntimeRegistry.provider = null;
  }

  public static WasmosRuntime runtime() {
    WasmosRuntime r = provider;
    if (r == null) {
      throw new IllegalStateException("WasmosRuntime not installed; call WasmosRuntimeRegistry.install(...) before invoking generated bindings");
    }
    return r;
  }
}
