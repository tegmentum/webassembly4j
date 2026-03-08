package ai.tegmentum.webassembly4j.api;

public interface Module extends AutoCloseable {

    Instance instantiate();

    Instance instantiate(LinkingContext linkingContext);

    @Override
    void close();
}
