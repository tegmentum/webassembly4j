package ai.tegmentum.webassembly4j.runtime.component;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ComponentLowererTest {

    // Minimal core module: (module)
    private static final byte[] MINIMAL_CORE = new byte[] {
            0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00
    };

    // Core module with an add function
    private static final byte[] ADD_CORE = new byte[] {
            0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00,
            0x01, 0x07, 0x01, 0x60, 0x02, 0x7F, 0x7F, 0x01, 0x7F,
            0x03, 0x02, 0x01, 0x00,
            0x07, 0x07, 0x01, 0x03, 0x61, 0x64, 0x64, 0x00, 0x00,
            0x0A, 0x09, 0x01, 0x07, 0x00, 0x20, 0x00, 0x20, 0x01, 0x6A, 0x0B
    };

    @Test
    void extractCoreModuleFromComponent() throws IOException {
        byte[] component = buildComponent(MINIMAL_CORE);
        byte[] extracted = ComponentLowerer.lower(component);
        assertArrayEquals(MINIMAL_CORE, extracted);
    }

    @Test
    void extractAddModuleFromComponent() throws IOException {
        byte[] component = buildComponent(ADD_CORE);
        byte[] extracted = ComponentLowerer.lower(component);
        assertArrayEquals(ADD_CORE, extracted);
    }

    @Test
    void skipNonModuleSections() throws IOException {
        // Component with a custom section (id=0) before the core module
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Component header
        out.write(new byte[] {0x00, 0x61, 0x73, 0x6d, 0x0d, 0x00, 0x01, 0x00});
        // Custom section (id=0, size=5, payload="hello")
        out.write(0x00); // section id
        writeLeb128(out, 5);
        out.write("hello".getBytes());
        // Core module section (id=1)
        out.write(0x01); // section id
        writeLeb128(out, MINIMAL_CORE.length);
        out.write(MINIMAL_CORE);

        byte[] extracted = ComponentLowerer.lower(out.toByteArray());
        assertArrayEquals(MINIMAL_CORE, extracted);
    }

    @Test
    void rejectsNonComponent() {
        assertThrows(IllegalArgumentException.class,
                () -> ComponentLowerer.lower(MINIMAL_CORE));
    }

    @Test
    void rejectsComponentWithoutModule() {
        // Component header only, no sections
        byte[] empty = new byte[] {0x00, 0x61, 0x73, 0x6d, 0x0d, 0x00, 0x01, 0x00};
        assertThrows(IllegalArgumentException.class,
                () -> ComponentLowerer.lower(empty));
    }

    /**
     * Builds a minimal component binary containing a single core module.
     */
    private static byte[] buildComponent(byte[] coreModule) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Component header: \0asm\x0d\x00\x01\x00
        out.write(new byte[] {0x00, 0x61, 0x73, 0x6d, 0x0d, 0x00, 0x01, 0x00});
        // Core module section (id=1)
        out.write(0x01);
        writeLeb128(out, coreModule.length);
        out.write(coreModule);
        return out.toByteArray();
    }

    private static void writeLeb128(ByteArrayOutputStream out, int value) {
        int remaining = value;
        do {
            int b = remaining & 0x7F;
            remaining >>>= 7;
            if (remaining != 0) {
                b |= 0x80;
            }
            out.write(b);
        } while (remaining != 0);
    }
}
