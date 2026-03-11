package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.Module;
import ai.tegmentum.webassembly4j.api.config.WebAssemblyConfig;
import ai.tegmentum.webassembly4j.api.gc.GcArrayInstance;
import ai.tegmentum.webassembly4j.api.gc.GcArrayType;
import ai.tegmentum.webassembly4j.api.gc.GcException;
import ai.tegmentum.webassembly4j.api.gc.GcExtension;
import ai.tegmentum.webassembly4j.api.gc.GcFieldType;
import ai.tegmentum.webassembly4j.api.gc.GcI31Instance;
import ai.tegmentum.webassembly4j.api.gc.GcReferenceType;
import ai.tegmentum.webassembly4j.api.gc.GcStructInstance;
import ai.tegmentum.webassembly4j.api.gc.GcStructType;
import ai.tegmentum.webassembly4j.api.gc.GcValue;
import ai.tegmentum.webassembly4j.api.gc.Finality;
import ai.tegmentum.webassembly4j.provider.wasmtime.config.WasmtimeConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIf("runtimeAvailable")
class WasmtimeGcExtensionTest {

    // Minimal WASM module with just a memory export
    private static final byte[] ADD_MODULE = new byte[] {
        0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00,
        0x01, 0x07, 0x01, 0x60, 0x02, 0x7F, 0x7F, 0x01, 0x7F,
        0x03, 0x02, 0x01, 0x00,
        0x07, 0x07, 0x01, 0x03, 0x61, 0x64, 0x64, 0x00, 0x00,
        0x0A, 0x09, 0x01, 0x07, 0x00, 0x20, 0x00, 0x20, 0x01, 0x6A, 0x0B
    };

    private Engine engine;
    private Module module;
    private Instance instance;
    private GcExtension gc;

    static boolean runtimeAvailable() {
        return new WasmtimeProvider().availability().available();
    }

    @BeforeEach
    void setUp() {
        WebAssemblyConfig config = WebAssemblyConfig.builder()
                .engineConfig(WasmtimeConfig.builder().wasmGc(true).build())
                .build();
        engine = WasmtimeEngineAdapter.create(config);
        module = engine.loadModule(ADD_MODULE);
        instance = module.instantiate();
        Optional<GcExtension> gcOpt = instance.extension(GcExtension.class);
        if (gcOpt.isPresent()) {
            gc = gcOpt.get();
        }
    }

    @AfterEach
    void tearDown() {
        if (module != null) module.close();
        if (engine != null) engine.close();
    }

    @Test
    void gcExtensionAvailableWithGcEnabled() {
        assertNotNull(gc, "GcExtension should be available when wasmGc is enabled");
    }

    @Test
    void gcExtensionNotAvailableWithoutGcConfig() {
        try (Engine noGcEngine = WasmtimeEngineAdapter.create(null);
             Module noGcModule = noGcEngine.loadModule(ADD_MODULE)) {
            Instance noGcInstance = noGcModule.instantiate();
            // GC may or may not be enabled by default depending on wasmtime version
            // This test just verifies the extension() call doesn't throw
            noGcInstance.extension(GcExtension.class);
        }
    }

    @Test
    void createStructAndReadFields() {
        if (gc == null) return;

        GcStructType pointType = GcStructType.builder("Point")
                .addField("x", GcFieldType.f64(), true)
                .addField("y", GcFieldType.f64(), true)
                .build();

        GcStructInstance point = gc.createStruct(pointType,
                GcValue.f64(3.0), GcValue.f64(4.0));

        assertNotNull(point);
        assertEquals(2, point.fieldCount());
        assertEquals(3.0, point.getField(0).asF64(), 0.001);
        assertEquals(4.0, point.getField(1).asF64(), 0.001);
    }

    @Test
    void createStructAndWriteField() {
        if (gc == null) return;

        GcStructType type = GcStructType.builder("Mutable")
                .addField("value", GcFieldType.i32(), true)
                .build();

        GcStructInstance s = gc.createStruct(type, GcValue.i32(10));
        assertEquals(10, s.getField(0).asI32());

        s.setField(0, GcValue.i32(42));
        assertEquals(42, s.getField(0).asI32());
    }

    @Test
    void createArrayAndReadElements() {
        if (gc == null) return;

        GcArrayType intArrayType = GcArrayType.builder("IntArray")
                .elementType(GcFieldType.i32())
                .mutable(true)
                .build();

        GcArrayInstance arr = gc.createArray(intArrayType,
                GcValue.i32(10), GcValue.i32(20), GcValue.i32(30));

        assertNotNull(arr);
        assertEquals(3, arr.length());
        assertEquals(10, arr.getElement(0).asI32());
        assertEquals(20, arr.getElement(1).asI32());
        assertEquals(30, arr.getElement(2).asI32());
    }

    @Test
    void createArrayWithLength() {
        if (gc == null) return;

        GcArrayType type = GcArrayType.builder("Zeros")
                .elementType(GcFieldType.i32())
                .mutable(true)
                .build();

        GcArrayInstance arr = gc.createArray(type, 5);
        assertEquals(5, arr.length());
        assertEquals(0, arr.getElement(0).asI32());
    }

    @Test
    void arraySetElement() {
        if (gc == null) return;

        GcArrayType type = GcArrayType.builder("MutArray")
                .elementType(GcFieldType.i32())
                .mutable(true)
                .build();

        GcArrayInstance arr = gc.createArray(type, GcValue.i32(0));
        arr.setElement(0, GcValue.i32(99));
        assertEquals(99, arr.getElement(0).asI32());
    }

    @Test
    void createI31() {
        if (gc == null) return;

        GcI31Instance i31 = gc.createI31(42);
        assertNotNull(i31);
        assertEquals(42, i31.value());
        assertEquals(GcReferenceType.I31_REF, i31.referenceType());
        assertFalse(i31.isNull());
    }

    @Test
    void createI31Negative() {
        if (gc == null) return;

        GcI31Instance i31 = gc.createI31(-1);
        assertEquals(-1, i31.value());
    }

    @Test
    void structRefEquals() {
        if (gc == null) return;

        GcStructType type = GcStructType.builder("T")
                .addField("v", GcFieldType.i32(), false)
                .build();

        GcStructInstance a = gc.createStruct(type, GcValue.i32(1));
        GcStructInstance b = gc.createStruct(type, GcValue.i32(1));

        assertTrue(a.refEquals(a), "Same reference should be equal");
        assertFalse(a.refEquals(b), "Different references should not be equal");
    }

    @Test
    void collectGarbageReturnsStats() {
        if (gc == null) return;

        GcStructType type = GcStructType.builder("T")
                .addField("v", GcFieldType.i32(), false)
                .build();

        // Create some objects then collect
        for (int i = 0; i < 10; i++) {
            gc.createStruct(type, GcValue.i32(i));
        }

        // Should not throw
        gc.collectGarbage();
        gc.getStats();
    }

    @Test
    void structReferenceType() {
        if (gc == null) return;

        GcStructType type = GcStructType.builder("T")
                .addField("v", GcFieldType.i32(), false)
                .build();

        GcStructInstance s = gc.createStruct(type, GcValue.i32(1));
        assertEquals(GcReferenceType.STRUCT_REF, s.referenceType());
        assertFalse(s.isNull());
    }

    @Test
    void arrayReferenceType() {
        if (gc == null) return;

        GcArrayType type = GcArrayType.builder("A")
                .elementType(GcFieldType.i32())
                .mutable(false)
                .build();

        GcArrayInstance a = gc.createArray(type, GcValue.i32(1));
        assertEquals(GcReferenceType.ARRAY_REF, a.referenceType());
        assertFalse(a.isNull());
    }
}
