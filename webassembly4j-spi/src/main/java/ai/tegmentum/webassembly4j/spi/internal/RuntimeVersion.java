package ai.tegmentum.webassembly4j.spi.internal;

public final class RuntimeVersion {

    private RuntimeVersion() {
    }

    public static int currentJavaVersion() {
        String version = System.getProperty("java.specification.version");
        if (version.startsWith("1.")) {
            return Integer.parseInt(version.substring(2));
        }
        return Integer.parseInt(version);
    }
}
