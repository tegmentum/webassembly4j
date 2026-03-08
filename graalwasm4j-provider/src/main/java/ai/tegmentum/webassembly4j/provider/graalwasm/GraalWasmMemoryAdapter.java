package ai.tegmentum.webassembly4j.provider.graalwasm;

import ai.tegmentum.webassembly4j.api.Memory;
import org.graalvm.polyglot.Value;

import java.nio.ByteBuffer;
import java.util.Optional;

final class GraalWasmMemoryAdapter implements Memory {

    private final Value nativeMemory;

    GraalWasmMemoryAdapter(Value nativeMemory) {
        this.nativeMemory = nativeMemory;
    }

    @Override
    public long byteSize() {
        return nativeMemory.getBufferSize();
    }

    @Override
    public ByteBuffer asByteBuffer() {
        throw new UnsupportedOperationException(
                "GraalWasm does not expose memory as a direct ByteBuffer");
    }

    @Override
    public void write(long offset, byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            nativeMemory.writeBufferByte(offset + i, bytes[i]);
        }
    }

    @Override
    public byte[] read(long offset, int length) {
        byte[] result = new byte[length];
        for (int i = 0; i < length; i++) {
            result[i] = nativeMemory.readBufferByte(offset + i);
        }
        return result;
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
