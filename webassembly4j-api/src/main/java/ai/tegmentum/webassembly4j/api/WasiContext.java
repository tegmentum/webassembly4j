package ai.tegmentum.webassembly4j.api;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface WasiContext {

    default List<String> args() {
        return Collections.emptyList();
    }

    default Map<String, String> env() {
        return Collections.emptyMap();
    }

    default boolean inheritStdin() {
        return false;
    }

    default boolean inheritStdout() {
        return false;
    }

    default boolean inheritStderr() {
        return false;
    }

    default List<String> preopenDirs() {
        return Collections.emptyList();
    }
}
