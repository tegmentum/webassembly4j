package ai.tegmentum.webassembly4j.api.component.async;

/**
 * Result of a stream or future operation in the async component model.
 */
public enum StreamResult {
    COMPLETED,
    CANCELLED,
    DROPPED
}
