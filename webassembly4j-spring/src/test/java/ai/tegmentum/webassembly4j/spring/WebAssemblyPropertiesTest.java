package ai.tegmentum.webassembly4j.spring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebAssemblyPropertiesTest {

    @Test
    void defaultValues() {
        WebAssemblyProperties props = new WebAssemblyProperties();
        assertTrue(props.isEnabled());
        assertNull(props.getEngine());
        assertNull(props.getProvider());
        assertNotNull(props.getPool());
    }

    @Test
    void setEngine() {
        WebAssemblyProperties props = new WebAssemblyProperties();
        props.setEngine("wasmtime");
        assertEquals("wasmtime", props.getEngine());
    }

    @Test
    void setProvider() {
        WebAssemblyProperties props = new WebAssemblyProperties();
        props.setProvider("wasmtime4j");
        assertEquals("wasmtime4j", props.getProvider());
    }

    @Test
    void setEnabled() {
        WebAssemblyProperties props = new WebAssemblyProperties();
        props.setEnabled(false);
        assertFalse(props.isEnabled());
    }

    @Test
    void poolDefaults() {
        WebAssemblyProperties.Pool pool = new WebAssemblyProperties.Pool();
        assertFalse(pool.isEnabled());
        assertEquals(0, pool.getMinSize());
        assertEquals(8, pool.getMaxSize());
        assertEquals(300_000, pool.getMaxIdleMillis());
        assertEquals(30_000, pool.getBorrowTimeoutMillis());
    }

    @Test
    void poolCustomValues() {
        WebAssemblyProperties.Pool pool = new WebAssemblyProperties.Pool();
        pool.setEnabled(true);
        pool.setMinSize(2);
        pool.setMaxSize(16);
        pool.setMaxIdleMillis(60_000);
        pool.setBorrowTimeoutMillis(5_000);

        assertTrue(pool.isEnabled());
        assertEquals(2, pool.getMinSize());
        assertEquals(16, pool.getMaxSize());
        assertEquals(60_000, pool.getMaxIdleMillis());
        assertEquals(5_000, pool.getBorrowTimeoutMillis());
    }
}
