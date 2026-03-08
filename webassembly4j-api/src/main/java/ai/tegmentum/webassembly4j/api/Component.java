package ai.tegmentum.webassembly4j.api;

public interface Component extends AutoCloseable {

    Instance instantiate();

    Instance instantiate(LinkingContext linkingContext);

    @Override
    void close();
}
