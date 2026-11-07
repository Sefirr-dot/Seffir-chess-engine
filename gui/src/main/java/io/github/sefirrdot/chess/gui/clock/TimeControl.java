package io.github.sefirrdot.chess.gui.clock;

import java.util.List;

/**
 * Base time plus Fischer increment per move. {@link #UNTIMED} disables the clock; engines then think for a fixed time
 * per move.
 */
public record TimeControl(long baseMillis, long incrementMillis) {

    public static final TimeControl UNTIMED = new TimeControl(0, 0);

    public static final List<TimeControl> PRESETS = List.of(
            minutes(1, 0), minutes(3, 2), minutes(5, 3), minutes(10, 5), minutes(15, 10), minutes(30, 0), UNTIMED);

    public static TimeControl minutes(int minutes, int incrementSeconds) {
        return new TimeControl(minutes * 60_000L, incrementSeconds * 1000L);
    }

    public boolean isTimed() {
        return baseMillis > 0;
    }

    @Override
    public String toString() {
        if (!isTimed()) {
            return "Unlimited";
        }
        String base = baseMillis % 60_000 == 0 ? String.valueOf(baseMillis / 60_000) : String.valueOf(baseMillis / 60_000.0);
        return base + " + " + incrementMillis / 1000;
    }
}
