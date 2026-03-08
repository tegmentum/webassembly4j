package ai.tegmentum.webassembly4j.provider.wasmtime;

import ai.tegmentum.wasmtime4j.WasmMemory;
import ai.tegmentum.webassembly4j.api.Memory;

import java.nio.ByteBuffer;
import java.util.Optional;

final class WasmtimeMemoryAdapter implements Memory {

    private static final int PAGE_SIZE = 65536;

    private final WasmMemory nativeMemory;

    WasmtimeMemoryAdapter(WasmMemory nativeMemory) {
        this.nativeMemory = nativeMemory;
    }

    @Override
    public long byteSize() {
        return (long) nativeMemory.getSize() * PAGE_SIZE;
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
