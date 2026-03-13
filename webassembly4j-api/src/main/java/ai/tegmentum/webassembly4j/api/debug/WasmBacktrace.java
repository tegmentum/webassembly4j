package ai.tegmentum.webassembly4j.api.debug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A captured WebAssembly call stack trace.
 *
 * <p>Contains a list of {@link FrameInfo} entries representing the call stack
 * at the point of capture, ordered from innermost (most recent) to outermost frame.
 */
public final class WasmBacktrace {

    private final List<FrameInfo> frames;

    private WasmBacktrace(List<FrameInfo> frames) {
        this.frames = Collections.unmodifiableList(new ArrayList<>(frames));
    }

    /**
     * Returns the frames in this backtrace, from innermost to outermost.
     */
    public List<FrameInfo> frames() {
        return frames;
    }

    /**
     * Returns the number of frames.
     */
    public int frameCount() {
        return frames.size();
    }

    /**
     * Returns the frame at the given index.
     *
     * @param index the zero-based frame index (0 = innermost)
     */
    public FrameInfo frame(int index) {
        return frames.get(index);
    }

    /**
     * Creates a backtrace from the given frames.
     */
    public static WasmBacktrace of(List<FrameInfo> frames) {
        return new WasmBacktrace(frames);
    }

    /**
     * Returns an empty backtrace.
     */
    public static WasmBacktrace empty() {
        return new WasmBacktrace(Collections.emptyList());
    }

    @Override
    public String toString() {
        if (frames.isEmpty()) {
            return "<empty backtrace>";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < frames.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append("  ").append(i).append(": ").append(frames.get(i));
        }
        return sb.toString();
    }
}
