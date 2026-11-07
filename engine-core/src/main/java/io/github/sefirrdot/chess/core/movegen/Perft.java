package io.github.sefirrdot.chess.core.movegen;

import io.github.sefirrdot.chess.core.board.Board;
import io.github.sefirrdot.chess.core.move.Move;
import io.github.sefirrdot.chess.core.move.MoveList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Performance test: counts the leaf nodes of the legal move tree to a fixed depth.
 *
 * <p>Perft numbers for well-known positions are published, so any mismatch proves a move generation or make/unmake bug.
 * {@link #divide} breaks the count down per root move, which is how a mismatch is narrowed down to one move.
 */
public final class Perft {

    private Perft() {
    }

    public static long perft(Board board, int depth) {
        if (depth == 0) {
            return 1;
        }
        var moves = new MoveList();
        MoveGenerator.generateLegal(board, moves);
        if (depth == 1) {
            return moves.size();
        }
        long nodes = 0;
        for (int i = 0; i < moves.size(); i++) {
            board.makeMove(moves.get(i));
            nodes += perft(board, depth - 1);
            board.unmakeMove();
        }
        return nodes;
    }

    /** Leaf count below each root move, keyed by UCI move, in generation order. */
    public static Map<String, Long> divide(Board board, int depth) {
        var moves = new MoveList();
        MoveGenerator.generateLegal(board, moves);
        Map<String, Long> result = new LinkedHashMap<>();
        for (int i = 0; i < moves.size(); i++) {
            board.makeMove(moves.get(i));
            result.put(Move.toUci(moves.get(i)), perft(board, depth - 1));
            board.unmakeMove();
        }
        return result;
    }
}
