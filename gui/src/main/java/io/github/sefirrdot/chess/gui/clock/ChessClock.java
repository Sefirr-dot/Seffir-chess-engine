package io.github.sefirrdot.chess.gui.clock;

import io.github.sefirrdot.chess.core.board.Color;
import java.util.function.LongSupplier;

/**
 * Two-sided chess clock with Fischer increment.
 *
 * <p>Time is read from an injectable nanosecond source so tests can drive it. The clock does not tick by itself:
 * callers query {@link #remainingMillis} (e.g. from a UI timer) and detect a flag with {@link #hasFlagged}.
 */
public final class ChessClock {

    private final TimeControl control;
    private final LongSupplier nanoTime;
    private final long[] remainingNanos = new long[Color.COUNT];
    private int running = -1;
    private long runningSince;

    public ChessClock(TimeControl control) {
        this(control, System::nanoTime);
    }

    public ChessClock(TimeControl control, LongSupplier nanoTime) {
        this.control = control;
        this.nanoTime = nanoTime;
        remainingNanos[Color.WHITE] = control.baseMillis() * 1_000_000;
        remainingNanos[Color.BLACK] = control.baseMillis() * 1_000_000;
    }

    public TimeControl control() {
        return control;
    }

    /** Starts (or keeps) the clock of {@code color} running; the other side's clock is paused. */
    public void start(int color) {
        pause();
        running = color;
        runningSince = nanoTime.getAsLong();
    }

    /**
     * Called after {@code color} completes a move: its clock stops, it receives the increment, and the opponent's clock
     * starts.
     */
    public void moveMade(int color) {
        if (running == color) {
            pause();
        }
        remainingNanos[color] += control.incrementMillis() * 1_000_000;
        start(Color.opposite(color));
    }

    public void pause() {
        if (running >= 0) {
            long now = nanoTime.getAsLong();
            remainingNanos[running] -= now - runningSince;
            running = -1;
        }
    }

    /** Side whose clock is running, or -1. */
    public int running() {
        return running;
    }

    public long remainingMillis(int color) {
        long nanos = remainingNanos[color];
        if (running == color) {
            nanos -= nanoTime.getAsLong() - runningSince;
        }
        return Math.max(0, nanos / 1_000_000);
    }

    public boolean hasFlagged(int color) {
        return control.isTimed() && remainingMillis(color) <= 0;
    }

    /** {@code m:ss}, or {@code m:ss.t} under ten seconds; {@code ∞} for untimed games. */
    public String format(int color) {
        if (!control.isTimed()) {
            return "∞";
        }
        long millis = remainingMillis(color);
        long minutes = millis / 60_000;
        long seconds = (millis / 1000) % 60;
        if (millis < 10_000) {
            return String.format("%d:%02d.%d", minutes, seconds, (millis / 100) % 10);
        }
        if (minutes >= 60) {
            return String.format("%d:%02d:%02d", minutes / 60, minutes % 60, seconds);
        }
        return String.format("%d:%02d", minutes, seconds);
    }
}
