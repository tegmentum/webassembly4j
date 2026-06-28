package ai.tegmentum.webassembly4j.provider.wasm3;

import ai.tegmentum.wasm34j.WebAssemblyMemory;
import ai.tegmentum.webassembly4j.api.Memory;
import ai.tegmentum.webassembly4j.api.exception.ExecutionException;

import java.nio.ByteBuffer;
import java.util.Optional;

/** {@link Memory} backed by a wasm34j {@link WebAssemblyMemory}. */
final class Wasm3MemoryAdapter implements Memory {

    private final WebAssemblyMemory nativeMemory;

    Wasm3MemoryAdapter(final WebAssemblyMemory nativeMemory) {
        this.nativeMemory = nativeMemory;
    }

    @Override
    public long byteSize() {
        return nativeMemory.byteSize();
    }

    @Override
    public long pageCount() {
        return nativeMemory.pageCount();
    }

    @Override
    public ByteBuffer asByteBuffer() {
        return nativeMemory.asByteBuffer();
    }

    @Override
    public void write(final long offset, final byte[] bytes) {
        try {
            nativeMemory.write(offset, bytes);
        } catch (final ai.tegmentum.wasm34j.exception.WasmException e) {
            throw new ExecutionException("Failed to write to wasm3 memory", e);
        }
    }

    @Override
    public byte[] read(final long offset, final int length) {
        try {
            return nativeMemory.read(offset, length);
        } catch (final ai.tegmentum.wasm34j.exception.WasmException e) {
            throw new ExecutionException("Failed to read from wasm3 memory", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> unwrap(final Class<T> nativeType) {
        if (nativeType.isInstance(nativeMemory)) {
            return Optional.of((T) nativeMemory);
        }
        return Optional.empty();
    }
}
