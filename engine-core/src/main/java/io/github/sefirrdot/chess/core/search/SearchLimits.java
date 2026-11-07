package io.github.sefirrdot.chess.core.search;

/**
 * What bounds a search, mirroring the UCI {@code go} parameters. Unset values are {@code 0} (or {@code false}).
 *
 * @param depth        maximum depth in plies, 0 = unlimited
 * @param nodes        maximum nodes, 0 = unlimited
 * @param moveTime     exact time for this move in ms, 0 = not set
 * @param whiteTime    White's remaining clock in ms, 0 = not set
 * @param blackTime    Black's remaining clock in ms, 0 = not set
 * @param whiteIncrement White's increment per move in ms
 * @param blackIncrement Black's increment per move in ms
 * @param movesToGo    moves until the next time control, 0 = sudden death
 * @param infinite     search until stopped
 */
public record SearchLimits(
        int depth,
        long nodes,
        long moveTime,
        long whiteTime,
        long blackTime,
        long whiteIncrement,
        long blackIncrement,
        int movesToGo,
        boolean infinite) {

    public static final SearchLimits NONE = new SearchLimits(0, 0, 0, 0, 0, 0, 0, 0, false);

    public static SearchLimits ofDepth(int depth) {
        return NONE.withDepth(depth);
    }

    public static SearchLimits ofMoveTime(long millis) {
        return new SearchLimits(0, 0, millis, 0, 0, 0, 0, 0, false);
    }

    public static SearchLimits infiniteSearch() {
        return new SearchLimits(0, 0, 0, 0, 0, 0, 0, 0, true);
    }

    public SearchLimits withDepth(int value) {
        return new SearchLimits(value, nodes, moveTime, whiteTime, blackTime, whiteIncrement, blackIncrement,
                movesToGo, infinite);
    }

    public SearchLimits withNodes(long value) {
        return new SearchLimits(depth, value, moveTime, whiteTime, blackTime, whiteIncrement, blackIncrement,
                movesToGo, infinite);
    }

    public SearchLimits withClock(long wtime, long btime, long winc, long binc, int movesToGo) {
        return new SearchLimits(depth, nodes, moveTime, wtime, btime, winc, binc, movesToGo, infinite);
    }

    public boolean hasClock() {
        return whiteTime > 0 || blackTime > 0;
    }
}
