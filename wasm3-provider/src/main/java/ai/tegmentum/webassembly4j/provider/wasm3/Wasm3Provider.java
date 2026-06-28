package ai.tegmentum.webassembly4j.provider.wasm3;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.spi.EngineProvider;
import ai.tegmentum.webassembly4j.spi.ProviderAvailability;
import ai.tegmentum.webassembly4j.spi.ProviderDescriptor;
import ai.tegmentum.webassembly4j.spi.ProviderPriority;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * webassembly4j {@link EngineProvider} for the wasm3 interpreter, backed by the standalone
 * {@code wasm34j} library (JNI + Panama). Discovered via {@link java.util.ServiceLoader}.
 */
public final class Wasm3Provider implements EngineProvider {

    private static final String ENGINE_ID = "wasm3";
    private static final String PROVIDER_ID = "wasm3";
    private static final String VERSION = "1.0.0-SNAPSHOT";
    private static final int MIN_JAVA = 17;

    @Override
    public ProviderDescriptor descriptor() {
        return new ProviderDescriptor() {
            @Override
            public String engineId() {
                return ENGINE_ID;
            }

            @Override
            public String providerId() {
                return PROVIDER_ID;
            }

            @Override
            public String version() {
                return VERSION;
            }

            @Override
            public int minimumJavaVersion() {
                return MIN_JAVA;
            }

            @Override
            public Set<String> tags() {
                final Set<String> tags = new HashSet<>();
                tags.add("native");
                tags.add("interpreter");
                tags.add("lightweight");
                return Collections.unmodifiableSet(tags);
            }

            @Override
            public int priority() {
                return ProviderPriority.LOW;
            }
        };
    }

    @Override
    public ProviderAvailability availability() {
        try {
            ai.tegmentum.wasm34j.RuntimeFactory.create().close();
            return new ProviderAvailability() {
                @Override
                public boolean available() {
                    return true;
                }

                @Override
                public String message() {
                    return "wasm3 runtime available";
                }
            };
        } catch (final Throwable e) {
            final String msg = e.getClass().getName() + ": " + e.getMessage();
            return new ProviderAvailability() {
                @Override
                public boolean available() {
                    return false;
                }

                @Override
                public String message() {
                    return "wasm3 not available: " + msg;
                }
            };
        }
    }

    @Override
    public Engine create(final WebAssemblyConfig config) {
        return Wasm3EngineAdapter.create(config);
    }
}
