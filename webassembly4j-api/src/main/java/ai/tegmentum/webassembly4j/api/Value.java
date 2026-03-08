package ai.tegmentum.webassembly4j.api;

public interface Value {

    ValueType type();

    Object raw();
}
