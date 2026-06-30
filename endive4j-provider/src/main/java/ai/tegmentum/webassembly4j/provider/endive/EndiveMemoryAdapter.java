package ai.tegmentum.webassembly4j.provider.endive;

import ai.tegmentum.webassembly4j.api.Memory;

import java.nio.ByteBuffer;
import java.util.Optional;

final class EndiveMemoryAdapter implements Memory {

    private final run.endive.runtime.Memory nativeMemory;

    EndiveMemoryAdapter(run.endive.runtime.Memory nativeMemory) {
        this.nativeMemory = nativeMemory;
    }

    @Override
    public long byteSize() {
        return (long) nativeMemory.pages() * run.endive.runtime.Memory.PAGE_SIZE;
    }

    @Override
    public long pageCount() {
        return nativeMemory.pages();
    }

    @Override
    public long maxPageCount() {
        return nativeMemory.maximumPages();
    }

    @Override
    public long grow(long pages) {
        return nativeMemory.grow((int) pages);
    }

    @Override
    public ByteBuffer asByteBuffer() {
        throw new UnsupportedOperationException(
                "Endive does not expose memory as a direct ByteBuffer");
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
