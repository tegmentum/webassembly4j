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

    private DefaultWasiContext(List<String> args, Map<String, String> env,
                                boolean inheritStdin, boolean inheritStdout,
                                boolean inheritStderr, List<String> preopenDirs,
                                List<String> readOnlyPreopenDirs) {
        this.args = Collections.unmodifiableList(new ArrayList<>(args));
        this.env = Collections.unmodifiableMap(new LinkedHashMap<>(env));
        this.inheritStdin = inheritStdin;
        this.inheritStdout = inheritStdout;
        this.inheritStderr = inheritStderr;
        this.preopenDirs = Collections.unmodifiableList(new ArrayList<>(preopenDirs));
        this.readOnlyPreopenDirs =
                Collections.unmodifiableList(new ArrayList<>(readOnlyPreopenDirs));
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

        public DefaultWasiContext build() {
            return new DefaultWasiContext(args, env, inheritStdin, inheritStdout,
                    inheritStderr, preopenDirs, readOnlyPreopenDirs);
        }
    }
}
