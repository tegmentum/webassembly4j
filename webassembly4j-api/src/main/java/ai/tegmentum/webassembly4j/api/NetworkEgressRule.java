package ai.tegmentum.webassembly4j.api;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/**
 * A provider-agnostic network egress allow-list rule: permit outbound connections to a literal IP or
 * CIDR range, on a given port (0 = any), over TCP or UDP. Hostnames are resolved to IPs by the policy
 * compiler before a rule is built, so a rule always matches on address, not name — keeping DNS out of
 * the guest. Matching is total and fail-closed: any address the rule cannot positively match yields
 * {@code false}.
 *
 * <p>Egress only by construction: this type expresses <em>connect</em> permission; the provider that
 * consumes it must deny every bind/listen use regardless of the rules.
 */
public final class NetworkEgressRule {

    private final String cidrOrIp;
    private final int prefixLen; // -1 ⇒ exact IP (no CIDR)
    private final byte[] network; // resolved network address bytes, or exact IP bytes
    private final int port; // 0 ⇒ any
    private final boolean tcp;

    private NetworkEgressRule(String cidrOrIp, int prefixLen, byte[] network, int port, boolean tcp) {
        this.cidrOrIp = cidrOrIp;
        this.prefixLen = prefixLen;
        this.network = network;
        this.port = port;
        this.tcp = tcp;
    }

    /**
     * Build a rule from a literal IP ({@code 93.184.216.34}) or CIDR ({@code 10.0.0.0/8}), a port
     * (0 = any), and a protocol. Throws {@link IllegalArgumentException} if the address/CIDR is
     * malformed — a compile-time error the policy layer surfaces as a hard failure (never a permissive
     * default).
     */
    public static NetworkEgressRule of(String cidrOrIp, int port, boolean tcp) {
        Objects.requireNonNull(cidrOrIp, "cidrOrIp");
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        String addrPart = cidrOrIp;
        int prefix = -1;
        int slash = cidrOrIp.indexOf('/');
        if (slash >= 0) {
            addrPart = cidrOrIp.substring(0, slash);
            try {
                prefix = Integer.parseInt(cidrOrIp.substring(slash + 1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("malformed CIDR prefix: " + cidrOrIp, e);
            }
        }
        byte[] bytes;
        try {
            bytes = InetAddress.getByName(addrPart).getAddress(); // numeric only (no DNS for a literal)
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("malformed IP/CIDR address: " + cidrOrIp, e);
        }
        if (slash >= 0 && (prefix < 0 || prefix > bytes.length * 8)) {
            throw new IllegalArgumentException("CIDR prefix out of range for address: " + cidrOrIp);
        }
        return new NetworkEgressRule(cidrOrIp, prefix, bytes, port, tcp);
    }

    /**
     * Whether an outbound {@code addr} over the given protocol is permitted by this rule. Proto must
     * match, port must match (or the rule is any-port), and the address must fall within the rule's
     * IP/CIDR. Returns {@code false} on any mismatch or resolution anomaly.
     */
    public boolean matches(InetSocketAddress addr, boolean isTcp) {
        if (addr == null || isTcp != tcp) {
            return false;
        }
        if (port != 0 && addr.getPort() != port) {
            return false;
        }
        InetAddress ia = addr.getAddress();
        if (ia == null) {
            return false; // unresolved — fail closed
        }
        byte[] target = ia.getAddress();
        if (target.length != network.length) {
            return false; // v4 vs v6 mismatch
        }
        int bits = prefixLen < 0 ? target.length * 8 : prefixLen;
        int fullBytes = bits / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (target[i] != network[i]) {
                return false;
            }
        }
        int remBits = bits % 8;
        if (remBits != 0) {
            int mask = 0xFF << (8 - remBits) & 0xFF;
            if ((target[fullBytes] & mask) != (network[fullBytes] & mask)) {
                return false;
            }
        }
        return true;
    }

    /** The protocol this rule permits: true = TCP, false = UDP. */
    public boolean tcp() {
        return tcp;
    }

    /** The port this rule permits, or 0 for any port. */
    public int port() {
        return port;
    }

    @Override
    public String toString() {
        return "NetworkEgressRule{" + cidrOrIp + ":" + (port == 0 ? "*" : port)
                + "/" + (tcp ? "tcp" : "udp") + "}";
    }
}
