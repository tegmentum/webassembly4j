package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.wasmtime4j.WasmMemory;
import ai.tegmentum.webassembly4j.api.Memory;

import java.nio.ByteBuffer;
import java.util.Optional;

final class WasmtimeMemoryAdapter implements Memory {

    private static final int PAGE_SIZE = 65536;

    private final WasmMemory nativeMemory;
    /**
     * Non-null when this adapter was minted by {@code WasmtimeCallerAdapter.getMemory(name)}
     * from within a callback frame. Signals that the underlying native handle is a raw
     * {@code Box<wasmtime::Memory>} (not registry-wrapped), so
     * {@code WasmtimeCallerAdapter.defineCallerScopedExternImports} must route through the
     * caller-scoped {@code linkerDefineMemoryFromExport} path instead of the handle-based
     * path — the registry lookup would otherwise fail.
     */
    private final String callerExportName;

    WasmtimeMemoryAdapter(WasmMemory nativeMemory) {
        this(nativeMemory, null);
    }

    WasmtimeMemoryAdapter(WasmMemory nativeMemory, String callerExportName) {
        this.nativeMemory = nativeMemory;
        this.callerExportName = callerExportName;
    }

    String callerExportName() {
        return callerExportName;
    }

    @Override
    public long byteSize() {
        return (long) nativeMemory.getSize() * PAGE_SIZE;
    }

    @Override
    public long pageCount() {
        return nativeMemory.getSize();
    }

    @Override
    public long maxPageCount() {
        return nativeMemory.getMaxSize();
    }

    @Override
    public long grow(long pages) {
        return nativeMemory.grow((int) pages);
    }

    @Override
    public ByteBuffer asByteBuffer() {
        return nativeMemory.getBuffer();
    }

    @Override
    public void write(long offset, byte[] bytes) {
        nativeMemory.writeBytes((int) offset, bytes, 0, bytes.length);
    }

    @Override
    public byte[] read(long offset, int length) {
        byte[] dest = new byte[length];
        nativeMemory.readBytes((int) offset, dest, 0, length);
        return dest;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        if (nativeType.isInstance(nativeMemory)) {
            return Optional.of((T) nativeMemory);
        }
        return Optional.empty();
    }
}
