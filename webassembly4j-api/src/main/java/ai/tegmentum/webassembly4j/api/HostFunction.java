package ai.tegmentum.webassembly4j.api;

@FunctionalInterface
public interface HostFunction {

    Object[] execute(Object... args);
}
