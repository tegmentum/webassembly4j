package ai.tegmentum.webassembly4j.api;

import java.nio.ByteBuffer;
import java.util.Optional;

public interface Memory {

    long byteSize();

    ByteBuffer asByteBuffer();

    void write(long offset, byte[] bytes);

    byte[] read(long offset, int length);

    <T> Optional<T> unwrap(Class<T> nativeType);
}
