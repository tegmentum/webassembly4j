package ai.tegmentum.webassembly4j.api;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface WasiContext {

    default List<String> args() {
        return Collections.emptyList();
    }

    default Map<String, String> env() {
        return Collections.emptyMap();
    }

    default boolean inheritStdin() {
        return false;
    }

    default boolean inheritStdout() {
        return false;
    }

    default boolean inheritStderr() {
        return false;
    }

    default List<String> preopenDirs() {
        return Collections.emptyList();
    }

    /**
     * The subset of {@link #preopenDirs()} that is granted read-only. A directory listed here is
     * preopened with read permissions only — the guest cannot create, write, or delete within it.
     * Directories not listed are read-write (the default), preserving prior behaviour.
     */
    default List<String> readOnlyPreopenDirs() {
        return Collections.emptyList();
    }

    /**
     * Optional host→guest path remaps for preopens. For a host directory in {@link #preopenDirs()},
     * the value here is the path the guest sees it at; absent means the guest sees the host path
     * unchanged (host == guest). Lets a policy expose, say, host {@code /var/data/app} as guest
     * {@code /data} without leaking the host layout.
     */
    default Map<String, String> preopenGuestPaths() {
        return Collections.emptyMap();
    }

    /**
     * Whether the guest may open outbound network connections. Default {@code false} (deny-by-default);
     * only true when a policy grants egress. Even when true, {@link #egressRules()} is the allow-list
     * that gates which endpoints are reachable — an empty rule set with {@code allowNetwork()==true}
     * denies everything.
     */
    default boolean allowNetwork() {
        return false;
    }

    /**
     * The network egress allow-list: outbound connections are permitted only to an endpoint matching one
     * of these rules. Empty ⇒ deny-all. The provider must additionally deny every bind/listen use, so
     * these rules can only ever grant <em>egress</em>, never ingress.
     */
    default List<NetworkEgressRule> egressRules() {
        return Collections.emptyList();
    }
}
