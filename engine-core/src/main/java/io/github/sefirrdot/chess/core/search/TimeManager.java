package io.github.sefirrdot.chess.core.search;

import io.github.sefirrdot.chess.core.board.Color;

/**
 * Turns search limits into two deadlines, in milliseconds from the start of the search:
 *
 * <ul>
 *   <li><b>soft</b>: checked between iterations. Starting another iteration after it would probably not finish, so
 *       iterative deepening stops there.
 *   <li><b>hard</b>: checked during the search. Exceeding it aborts the current iteration.
 * </ul>
 *
 * <p>With a clock, the target is an equal share of the remaining time (assuming {@value #DEFAULT_MOVES_TO_GO} more moves
 * in sudden death) plus most of the increment; the hard limit allows overrunning it for a difficult iteration, but never
 * more than a fraction of what is left.
 */
public record TimeManager(long softMillis, long hardMillis) {

    public static final long UNLIMITED = Long.MAX_VALUE;

    /** Time reserved for process and GUI latency so the engine never flags. */
    static final long MOVE_OVERHEAD = 30;
    static final int DEFAULT_MOVES_TO_GO = 30;

    public static TimeManager forLimits(SearchLimits limits, int sideToMove) {
        if (limits.infinite()) {
            return new TimeManager(UNLIMITED, UNLIMITED);
        }
        if (limits.moveTime() > 0) {
            long budget = Math.max(1, limits.moveTime() - MOVE_OVERHEAD);
            return new TimeManager(budget, budget);
        }
        if (limits.hasClock()) {
            boolean white = sideToMove == Color.WHITE;
            long remaining = white ? limits.whiteTime() : limits.blackTime();
            long increment = white ? limits.whiteIncrement() : limits.blackIncrement();
            int movesToGo = limits.movesToGo() > 0 ? limits.movesToGo() : DEFAULT_MOVES_TO_GO;

            long usable = Math.max(1, remaining - MOVE_OVERHEAD);
            long target = usable / movesToGo + increment * 3 / 4;
            long hard = Math.min(usable / 3, target * 4);
            long soft = Math.min(target, hard);
            return new TimeManager(Math.max(1, soft), Math.max(1, hard));
        }
        return new TimeManager(UNLIMITED, UNLIMITED);
    }
}
