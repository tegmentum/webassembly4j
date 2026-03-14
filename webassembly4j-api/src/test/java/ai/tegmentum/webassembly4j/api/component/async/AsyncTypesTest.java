package ai.tegmentum.webassembly4j.api.component.async;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class AsyncTypesTest {

    @Test
    void streamResultValues() {
        assertEquals(3, StreamResult.values().length);
        assertNotNull(StreamResult.COMPLETED);
        assertNotNull(StreamResult.CANCELLED);
        assertNotNull(StreamResult.DROPPED);
    }

    @Test
    void guardedFutureClosesUnderlying() {
        AtomicBoolean closed = new AtomicBoolean(false);
        AsyncFuture future = new TestFuture(closed);

        GuardedFuture guard = new GuardedFuture(future);
        assertTrue(guard.isActive());
        assertSame(future, guard.getFuture());

        guard.close();
        assertFalse(guard.isActive());
        assertTrue(closed.get());
    }

    @Test
    void guardedFutureIntoFuturePreventsClose() {
        AtomicBoolean closed = new AtomicBoolean(false);
        AsyncFuture future = new TestFuture(closed);

        GuardedFuture guard = new GuardedFuture(future);
        AsyncFuture transferred = guard.intoFuture();
        assertSame(future, transferred);
        assertFalse(guard.isActive());

        guard.close(); // should be a no-op
        assertFalse(closed.get());
    }

    @Test
    void guardedFutureDoubleCloseIsIdempotent() {
        AtomicBoolean closed = new AtomicBoolean(false);
        AsyncFuture future = new TestFuture(closed);

        GuardedFuture guard = new GuardedFuture(future);
        guard.close();
        guard.close(); // should not throw
        assertTrue(closed.get());
    }

    @Test
    void guardedFutureIntoFutureTwiceThrows() {
        AsyncFuture future = new TestFuture(new AtomicBoolean());
        GuardedFuture guard = new GuardedFuture(future);
        guard.intoFuture();

        assertThrows(IllegalStateException.class, guard::intoFuture);
    }

    @Test
    void guardedFutureGetAfterTransferThrows() {
        AsyncFuture future = new TestFuture(new AtomicBoolean());
        GuardedFuture guard = new GuardedFuture(future);
        guard.intoFuture();

        assertThrows(IllegalStateException.class, guard::getFuture);
    }

    @Test
    void guardedFutureNullThrows() {
        assertThrows(NullPointerException.class, () -> new GuardedFuture(null));
    }

    @Test
    void guardedStreamClosesUnderlying() {
        AtomicBoolean closed = new AtomicBoolean(false);
        AsyncStream stream = new TestStream(closed);

        GuardedStream guard = new GuardedStream(stream);
        assertTrue(guard.isActive());
        assertSame(stream, guard.getStream());

        guard.close();
        assertFalse(guard.isActive());
        assertTrue(closed.get());
    }

    @Test
    void guardedStreamIntoStreamPreventsClose() {
        AtomicBoolean closed = new AtomicBoolean(false);
        AsyncStream stream = new TestStream(closed);

        GuardedStream guard = new GuardedStream(stream);
        AsyncStream transferred = guard.intoStream();
        assertSame(stream, transferred);
        assertFalse(guard.isActive());

        guard.close(); // should be a no-op
        assertFalse(closed.get());
    }

    @Test
    void guardedStreamDoubleCloseIsIdempotent() {
        AtomicBoolean closed = new AtomicBoolean(false);
        AsyncStream stream = new TestStream(closed);

        GuardedStream guard = new GuardedStream(stream);
        guard.close();
        guard.close(); // should not throw
        assertTrue(closed.get());
    }

    @Test
    void guardedStreamNullThrows() {
        assertThrows(NullPointerException.class, () -> new GuardedStream(null));
    }

    private static class TestFuture implements AsyncFuture {
        private final AtomicBoolean closed;
        private boolean valid = true;

        TestFuture(AtomicBoolean closed) {
            this.closed = closed;
        }

        @Override
        public boolean isValid() {
            return valid;
        }

        @Override
        public void close() {
            valid = false;
            closed.set(true);
        }
    }

    private static class TestStream implements AsyncStream {
        private final AtomicBoolean closed;
        private boolean valid = true;

        TestStream(AtomicBoolean closed) {
            this.closed = closed;
        }

        @Override
        public boolean isValid() {
            return valid;
        }

        @Override
        public void close() {
            valid = false;
            closed.set(true);
        }
    }
}
