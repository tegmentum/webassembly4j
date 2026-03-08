package ai.tegmentum.webassembly4j.provider.chicory;

import ai.tegmentum.webassembly4j.api.Memory;

import java.nio.ByteBuffer;
import java.util.Optional;

final class ChicoryMemoryAdapter implements Memory {

    private final com.dylibso.chicory.runtime.Memory nativeMemory;

    ChicoryMemoryAdapter(com.dylibso.chicory.runtime.Memory nativeMemory) {
        this.nativeMemory = nativeMemory;
    }

    @Override
    public long byteSize() {
        return (long) nativeMemory.pages() * com.dylibso.chicory.runtime.Memory.PAGE_SIZE;
    }

    @Override
    public ByteBuffer asByteBuffer() {
        throw new UnsupportedOperationException(
                "Chicory does not expose memory as a direct ByteBuffer");
    }

    @Override
    public void write(long offset, byte[] bytes) {
        nativeMemory.write((int) offset, bytes);
    }

    @Override
    public byte[] read(long offset, int length) {
        return nativeMemory.readBytes((int) offset, length);
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
