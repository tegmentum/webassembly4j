package ai.tegmentum.webassembly4j.api.config;

import java.util.List;
import java.util.Map;

public interface WasiConfig {

    List<String> args();

    Map<String, String> env();

    boolean inheritStdin();

    boolean inheritStdout();

    boolean inheritStderr();
}
