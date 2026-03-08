package ai.tegmentum.webassembly4j.api;

public interface Function {

    ValueType[] parameterTypes();

    ValueType[] resultTypes();

    Object invoke(Object... args);
}
