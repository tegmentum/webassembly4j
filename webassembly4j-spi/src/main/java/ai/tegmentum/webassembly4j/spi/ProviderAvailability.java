package ai.tegmentum.webassembly4j.spi;

public interface ProviderAvailability {

    boolean available();

    String message();
}
