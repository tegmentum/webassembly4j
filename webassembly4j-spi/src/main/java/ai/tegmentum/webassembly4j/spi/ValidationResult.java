package ai.tegmentum.webassembly4j.spi;

import java.util.List;

public interface ValidationResult {

    boolean valid();

    List<String> errors();

    List<String> warnings();
}
