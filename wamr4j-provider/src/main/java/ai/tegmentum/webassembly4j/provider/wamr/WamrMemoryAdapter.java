package ai.tegmentum.webassembly4j.provider.wamr;

import ai.tegmentum.wamr4j.WebAssemblyMemory;
import ai.tegmentum.webassembly4j.api.Memory;

import java.nio.ByteBuffer;
import java.util.Optional;

final class WamrMemoryAdapter implements Memory {

    private final WebAssemblyMemory nativeMemory;

    WamrMemoryAdapter(WebAssemblyMemory nativeMemory) {
        this.nativeMemory = nativeMemory;
    }

    @Override
    public long byteSize() {
        return nativeMemory.size();
    }

    @Override
    public ByteBuffer asByteBuffer() {
        return nativeMemory.asByteBuffer();
    }

    @Override
    public void write(long offset, byte[] bytes) {
        try {
            nativeMemory.write((int) offset, bytes);
        } catch (ai.tegmentum.wamr4j.exception.WasmRuntimeException e) {
            throw new ai.tegmentum.webassembly4j.api.exception.ExecutionException(
                    "Failed to write to WAMR memory", e);
        }
    }

    @Override
    public byte[] read(long offset, int length) {
        try {
            return nativeMemory.read((int) offset, length);
        } catch (ai.tegmentum.wamr4j.exception.WasmRuntimeException e) {
            throw new ai.tegmentum.webassembly4j.api.exception.ExecutionException(
                    "Failed to read from WAMR memory", e);
        }
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
