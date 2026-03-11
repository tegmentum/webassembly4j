package ai.tegmentum.webassembly4j.api;

import ai.tegmentum.webassembly4j.api.exception.UnsupportedFeatureException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ComponentInterfaceTest {

    @Test
    void defaultExportedInterfacesReturnsEmptyList() {
        Component component = createMinimalComponent();
        assertTrue(component.exportedInterfaces().isEmpty());
    }

    @Test
    void defaultImportedInterfacesReturnsEmptyList() {
        Component component = createMinimalComponent();
        assertTrue(component.importedInterfaces().isEmpty());
    }

    @Test
    void defaultExportsInterfaceReturnsFalse() {
        Component component = createMinimalComponent();
        assertFalse(component.exportsInterface("wasi:cli/run"));
    }

    @Test
    void defaultImportsInterfaceReturnsFalse() {
        Component component = createMinimalComponent();
        assertFalse(component.importsInterface("wasi:cli/stdout"));
    }

    @Test
    void defaultSerializeThrowsUnsupported() {
        Component component = createMinimalComponent();
        assertThrows(UnsupportedFeatureException.class, component::serialize);
    }

    @Test
    void defaultExtensionReturnsEmpty() {
        Component component = createMinimalComponent();
        Optional<String> ext = component.extension(String.class);
        assertFalse(ext.isPresent());
    }

    @Test
    void exportsInterfaceUsesExportedInterfacesList() {
        Component component = new Component() {
            @Override
            public ComponentInstance instantiate() { return null; }

            @Override
            public ComponentInstance instantiate(LinkingContext ctx) { return null; }

            @Override
            public List<String> exportedInterfaces() {
                return Arrays.asList("wasi:cli/run", "my:pkg/api");
            }

            @Override
            public void close() {}
        };

        assertTrue(component.exportsInterface("wasi:cli/run"));
        assertTrue(component.exportsInterface("my:pkg/api"));
        assertFalse(component.exportsInterface("nonexistent"));
    }

    @Test
    void importsInterfaceUsesImportedInterfacesList() {
        Component component = new Component() {
            @Override
            public ComponentInstance instantiate() { return null; }

            @Override
            public ComponentInstance instantiate(LinkingContext ctx) { return null; }

            @Override
            public List<String> importedInterfaces() {
                return Collections.singletonList("wasi:cli/stdout");
            }

            @Override
            public void close() {}
        };

        assertTrue(component.importsInterface("wasi:cli/stdout"));
        assertFalse(component.importsInterface("other"));
    }

    @Test
    void instantiateReturnsComponentInstance() {
        // Verify the return type is ComponentInstance
        Component component = createMinimalComponent();
        ComponentInstance instance = component.instantiate();
        assertNull(instance); // minimal impl returns null
    }

    private static Component createMinimalComponent() {
        return new Component() {
            @Override
            public ComponentInstance instantiate() { return null; }

            @Override
            public ComponentInstance instantiate(LinkingContext ctx) { return null; }

            @Override
            public void close() {}
        };
    }
}
