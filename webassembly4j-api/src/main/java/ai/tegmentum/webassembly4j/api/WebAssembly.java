package ai.tegmentum.webassembly4j.api;

public final class WebAssembly {

    private WebAssembly() {
    }

    public static WebAssemblyBuilder builder() {
        return new DefaultWebAssemblyBuilder();
    }
}
