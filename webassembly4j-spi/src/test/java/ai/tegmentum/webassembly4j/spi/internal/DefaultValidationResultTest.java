package ai.tegmentum.webassembly4j.spi.internal;

import org.junit.jupiter.api.Test;
import ai.tegmentum.webassembly4j.spi.ValidationResult;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class DefaultValidationResultTest {

    @Test
    void okResultIsValid() {
        ValidationResult result = DefaultValidationResult.ok();
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void withWarningsIsStillValid() {
        ValidationResult result = DefaultValidationResult.withWarnings(
                Collections.singletonList("deprecated option"));
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void invalidHasErrors() {
        ValidationResult result = DefaultValidationResult.invalid(
                Arrays.asList("missing field", "bad value"));
        assertFalse(result.valid());
        assertEquals(2, result.errors().size());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void invalidWithWarnings() {
        ValidationResult result = DefaultValidationResult.invalid(
                Collections.singletonList("error"),
                Collections.singletonList("warning"));
        assertFalse(result.valid());
        assertEquals(1, result.errors().size());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void listsAreUnmodifiable() {
        ValidationResult result = DefaultValidationResult.ok();
        assertThrows(UnsupportedOperationException.class,
                () -> result.errors().add("hack"));
        assertThrows(UnsupportedOperationException.class,
                () -> result.warnings().add("hack"));
    }
}
