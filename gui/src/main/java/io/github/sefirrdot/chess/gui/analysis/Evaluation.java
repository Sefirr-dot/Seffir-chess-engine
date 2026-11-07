package io.github.sefirrdot.chess.gui.analysis;

import io.github.sefirrdot.chess.core.board.Color;
import io.github.sefirrdot.chess.gui.engine.UciInfo;
import java.util.Locale;

/**
 * An engine evaluation from White's point of view, the convention of every chess site: positive is good for White.
 *
 * @param centipawns score in centipawns, or {@code null} when a mate was found
 * @param mateIn     moves to mate (positive: White mates, negative: Black mates), or {@code null}
 */
public record Evaluation(Integer centipawns, Integer mateIn) {

    /** Engines report from the side to move; flip the sign when Black is to move. */
    public static Evaluation fromEngine(UciInfo info, int sideToMove) {
        int sign = sideToMove == Color.WHITE ? 1 : -1;
        return info.mateIn() != null
                ? new Evaluation(null, sign * info.mateIn())
                : new Evaluation(sign * info.scoreCp(), null);
    }

    /**
     * White's share of the evaluation bar, 0..1: Lichess's logistic win-probability curve over centipawns, and all or
     * nothing for a forced mate.
     */
    public double whiteShare() {
        if (mateIn != null) {
            return mateIn > 0 ? 1 : 0;
        }
        return 1 / (1 + Math.exp(-0.00368208 * centipawns));
    }

    /** Short form for the narrow evaluation bar, no sign (the bar shows who is ahead): {@code 0.4}, {@code M3}. */
    public String compactLabel() {
        if (mateIn != null) {
            return "M" + Math.abs(mateIn);
        }
        return String.format(Locale.ROOT, "%.1f", Math.abs(centipawns) / 100.0);
    }

    /** {@code +0.35}, {@code -1.20}, {@code #3}, {@code #-2}. */
    public String label() {
        if (mateIn != null) {
            return "#" + mateIn;
        }
        return String.format(Locale.ROOT, "%+.2f", centipawns / 100.0);
    }
}
