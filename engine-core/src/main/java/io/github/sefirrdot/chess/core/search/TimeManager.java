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
 * in sudden death) plus most of the increment. The soft limit is half the target, because the iteration still running
 * when it passes takes about as long again; the hard limit aborts an iteration that would still overshoot (at 1.25x the
 * target, keeping the previous iteration's move), and never exceeds a quarter of what is left. With {@code movetime},
 * both limits are the given time.
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
            // Each iteration costs several times the previous one, so starting one late overshoots the target badly.
            // Stop deepening at half the target; the iteration in flight then usually ends near the target itself.
            long hard = Math.min(usable / 4, target * 5 / 4);
            long soft = Math.min(target / 2, hard);
            return new TimeManager(Math.max(1, soft), Math.max(1, hard));
        }
        return new TimeManager(UNLIMITED, UNLIMITED);
    }
}
