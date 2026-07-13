package ai.tegmentum.webassembly4j.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DefaultWasiContext implements WasiContext {

    private final List<String> args;
    private final Map<String, String> env;
    private final boolean inheritStdin;
    private final boolean inheritStdout;
    private final boolean inheritStderr;
    private final List<String> preopenDirs;
    private final List<String> readOnlyPreopenDirs;
    private final Map<String, String> preopenGuestPaths;
    private final boolean allowNetwork;
    private final List<NetworkEgressRule> egressRules;

    private DefaultWasiContext(List<String> args, Map<String, String> env,
                                boolean inheritStdin, boolean inheritStdout,
                                boolean inheritStderr, List<String> preopenDirs,
                                List<String> readOnlyPreopenDirs,
                                Map<String, String> preopenGuestPaths,
                                boolean allowNetwork, List<NetworkEgressRule> egressRules) {
        this.args = Collections.unmodifiableList(new ArrayList<>(args));
        this.env = Collections.unmodifiableMap(new LinkedHashMap<>(env));
        this.inheritStdin = inheritStdin;
        this.inheritStdout = inheritStdout;
        this.inheritStderr = inheritStderr;
        this.preopenDirs = Collections.unmodifiableList(new ArrayList<>(preopenDirs));
        this.readOnlyPreopenDirs =
                Collections.unmodifiableList(new ArrayList<>(readOnlyPreopenDirs));
        this.preopenGuestPaths =
                Collections.unmodifiableMap(new LinkedHashMap<>(preopenGuestPaths));
        this.allowNetwork = allowNetwork;
        this.egressRules = Collections.unmodifiableList(new ArrayList<>(egressRules));
    }

    @Override
    public List<String> args() {
        return args;
    }

    @Override
    public Map<String, String> env() {
        return env;
    }

    @Override
    public boolean inheritStdin() {
        return inheritStdin;
    }

    @Override
    public boolean inheritStdout() {
        return inheritStdout;
    }

    @Override
    public boolean inheritStderr() {
        return inheritStderr;
    }

    @Override
    public List<String> preopenDirs() {
        return preopenDirs;
    }

    @Override
    public List<String> readOnlyPreopenDirs() {
        return readOnlyPreopenDirs;
    }

    @Override
    public Map<String, String> preopenGuestPaths() {
        return preopenGuestPaths;
    }

    @Override
    public boolean allowNetwork() {
        return allowNetwork;
    }

    @Override
    public List<NetworkEgressRule> egressRules() {
        return egressRules;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final List<String> args = new ArrayList<>();
        private final Map<String, String> env = new LinkedHashMap<>();
        private boolean inheritStdin;
        private boolean inheritStdout;
        private boolean inheritStderr;
        private final List<String> preopenDirs = new ArrayList<>();
        private final List<String> readOnlyPreopenDirs = new ArrayList<>();
        private final Map<String, String> preopenGuestPaths = new LinkedHashMap<>();
        private boolean allowNetwork;
        private final List<NetworkEgressRule> egressRules = new ArrayList<>();

        private Builder() {
        }

        public Builder addArg(String arg) {
            this.args.add(arg);
            return this;
        }

        public Builder args(List<String> args) {
            this.args.clear();
            this.args.addAll(args);
            return this;
        }

        public Builder env(String key, String value) {
            this.env.put(key, value);
            return this;
        }

        public Builder inheritStdin(boolean inherit) {
            this.inheritStdin = inherit;
            return this;
        }

        public Builder inheritStdout(boolean inherit) {
            this.inheritStdout = inherit;
            return this;
        }

        public Builder inheritStderr(boolean inherit) {
            this.inheritStderr = inherit;
            return this;
        }

        public Builder inheritStdio(boolean inherit) {
            this.inheritStdin = inherit;
            this.inheritStdout = inherit;
            this.inheritStderr = inherit;
            return this;
        }

        /** Grant a read-write preopen (the default). */
        public Builder preopenDir(String dir) {
            return preopenDir(dir, true);
        }

        /**
         * Grant a preopen with an explicit access mode. {@code writable=false} preopens the
         * directory read-only — the guest can read but not create/write/delete within it.
         */
        public Builder preopenDir(String dir, boolean writable) {
            this.preopenDirs.add(dir);
            if (!writable) {
                this.readOnlyPreopenDirs.add(dir);
            }
            return this;
        }

        /**
         * Grant a preopen of host directory {@code hostDir} that the guest sees at {@code guestPath}
         * (host layout not leaked). {@code writable=false} preopens it read-only.
         */
        public Builder preopenDir(String hostDir, String guestPath, boolean writable) {
            this.preopenDirs.add(hostDir);
            this.preopenGuestPaths.put(hostDir, guestPath);
            if (!writable) {
                this.readOnlyPreopenDirs.add(hostDir);
            }
            return this;
        }

        /** Allow (or deny) the guest to open outbound network connections. Default deny. */
        public Builder allowNetwork(boolean allow) {
            this.allowNetwork = allow;
            return this;
        }

        /** Add an egress allow-list rule (implies {@link #allowNetwork(boolean) allowNetwork(true)}). */
        public Builder allowEgress(NetworkEgressRule rule) {
            this.egressRules.add(rule);
            this.allowNetwork = true;
            return this;
        }

        public DefaultWasiContext build() {
            return new DefaultWasiContext(args, env, inheritStdin, inheritStdout,
                    inheritStderr, preopenDirs, readOnlyPreopenDirs, preopenGuestPaths,
                    allowNetwork, egressRules);
        }
    }
}
