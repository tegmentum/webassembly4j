package ai.tegmentum.webassembly4j.spi.internal;

import ai.tegmentum.webassembly4j.spi.ValidationResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DefaultValidationResult implements ValidationResult {

    private final boolean valid;
    private final List<String> errors;
    private final List<String> warnings;

    private DefaultValidationResult(boolean valid, List<String> errors, List<String> warnings) {
        this.valid = valid;
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    public static ValidationResult ok() {
        return new DefaultValidationResult(true, Collections.emptyList(), Collections.emptyList());
    }

    public static ValidationResult withWarnings(List<String> warnings) {
        return new DefaultValidationResult(true, Collections.emptyList(), warnings);
    }

    public static ValidationResult invalid(List<String> errors) {
        return new DefaultValidationResult(false, errors, Collections.emptyList());
    }

    public static ValidationResult invalid(List<String> errors, List<String> warnings) {
        return new DefaultValidationResult(false, errors, warnings);
    }

    @Override
    public boolean valid() {
        return valid;
    }

    @Override
    public List<String> errors() {
        return errors;
    }

    @Override
    public List<String> warnings() {
        return warnings;
    }
}
