package io.github.sefirrdot.chess.core.search;

import io.github.sefirrdot.chess.core.move.Move;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Snapshot published after each completed iteration of iterative deepening.
 *
 * @param depth    completed depth in plies
 * @param score    centipawns from the side to move's view, or a mate score (see {@link Search#isMateScore})
 * @param nodes    nodes searched so far
 * @param millis   time elapsed since the search started
 * @param pv       principal variation, best move first
 */
public record SearchInfo(int depth, int score, long nodes, long millis, int[] pv) {

    public int bestMove() {
        return pv.length > 0 ? pv[0] : Move.NONE;
    }

    public long nodesPerSecond() {
        return millis > 0 ? nodes * 1000 / millis : nodes * 1000;
    }

    /** UCI score token: {@code cp 35}, or {@code mate 3} / {@code mate -2} (in moves, not plies). */
    public String uciScore() {
        if (Search.isMateScore(score)) {
            int plies = Search.MATE - Math.abs(score);
            int moves = (plies + 1) / 2;
            return "mate " + (score > 0 ? moves : -moves);
        }
        return "cp " + score;
    }

    public String uciPv() {
        return Arrays.stream(pv).mapToObj(Move::toUci).collect(Collectors.joining(" "));
    }

    // Records compare arrays by identity; compare the contents instead.
    @Override
    public boolean equals(Object o) {
        return o instanceof SearchInfo other && depth == other.depth && score == other.score && nodes == other.nodes
                && millis == other.millis && Arrays.equals(pv, other.pv);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(depth, score, nodes, millis) + Arrays.hashCode(pv);
    }

    @Override
    public String toString() {
        return "SearchInfo[depth=" + depth + ", score=" + uciScore() + ", nodes=" + nodes + ", millis=" + millis
                + ", pv=" + uciPv() + "]";
    }
}
