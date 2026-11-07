package io.github.sefirrdot.chess.core.eval;

import io.github.sefirrdot.chess.core.board.Bitboard;
import io.github.sefirrdot.chess.core.board.Board;
import io.github.sefirrdot.chess.core.board.Color;
import io.github.sefirrdot.chess.core.board.PieceType;

/**
 * Static evaluation in centipawns, from the side to move's point of view (positive = good for the side to move).
 *
 * <p>Material plus piece-square tables from Tomasz Michniewski's "Simplified Evaluation Function"
 * (https://www.chessprogramming.org/Simplified_Evaluation_Function). The king blends a middlegame table (stay sheltered)
 * and an endgame table (centralise) according to the remaining non-pawn material ("tapered evaluation").
 *
 * <p>Tables are written as seen from White, rank 8 on the first row, so they read like a diagram.
 */
public final class Evaluator {

    /** Indexed by {@link PieceType}. The king's value is irrelevant: both sides always have one. */
    public static final int[] PIECE_VALUE = {100, 320, 330, 500, 900, 0};

    /** Game phase contribution of each piece type; 24 = all minor and major pieces on the board. */
    private static final int[] PHASE_WEIGHT = {0, 1, 1, 2, 4, 0};
    private static final int MAX_PHASE = 24;

    private static final int[] PAWN_TABLE = {
         0,   0,   0,   0,   0,   0,   0,   0,
        50,  50,  50,  50,  50,  50,  50,  50,
        10,  10,  20,  30,  30,  20,  10,  10,
         5,   5,  10,  25,  25,  10,   5,   5,
         0,   0,   0,  20,  20,   0,   0,   0,
         5,  -5, -10,   0,   0, -10,  -5,   5,
         5,  10,  10, -20, -20,  10,  10,   5,
         0,   0,   0,   0,   0,   0,   0,   0,
    };

    private static final int[] KNIGHT_TABLE = {
        -50, -40, -30, -30, -30, -30, -40, -50,
        -40, -20,   0,   0,   0,   0, -20, -40,
        -30,   0,  10,  15,  15,  10,   0, -30,
        -30,   5,  15,  20,  20,  15,   5, -30,
        -30,   0,  15,  20,  20,  15,   0, -30,
        -30,   5,  10,  15,  15,  10,   5, -30,
        -40, -20,   0,   5,   5,   0, -20, -40,
        -50, -40, -30, -30, -30, -30, -40, -50,
    };

    private static final int[] BISHOP_TABLE = {
        -20, -10, -10, -10, -10, -10, -10, -20,
        -10,   0,   0,   0,   0,   0,   0, -10,
        -10,   0,   5,  10,  10,   5,   0, -10,
        -10,   5,   5,  10,  10,   5,   5, -10,
        -10,   0,  10,  10,  10,  10,   0, -10,
        -10,  10,  10,  10,  10,  10,  10, -10,
        -10,   5,   0,   0,   0,   0,   5, -10,
        -20, -10, -10, -10, -10, -10, -10, -20,
    };

    private static final int[] ROOK_TABLE = {
         0,   0,   0,   0,   0,   0,   0,   0,
         5,  10,  10,  10,  10,  10,  10,   5,
        -5,   0,   0,   0,   0,   0,   0,  -5,
        -5,   0,   0,   0,   0,   0,   0,  -5,
        -5,   0,   0,   0,   0,   0,   0,  -5,
        -5,   0,   0,   0,   0,   0,   0,  -5,
        -5,   0,   0,   0,   0,   0,   0,  -5,
         0,   0,   0,   5,   5,   0,   0,   0,
    };

    private static final int[] QUEEN_TABLE = {
        -20, -10, -10,  -5,  -5, -10, -10, -20,
        -10,   0,   0,   0,   0,   0,   0, -10,
        -10,   0,   5,   5,   5,   5,   0, -10,
         -5,   0,   5,   5,   5,   5,   0,  -5,
          0,   0,   5,   5,   5,   5,   0,  -5,
        -10,   5,   5,   5,   5,   5,   0, -10,
        -10,   0,   5,   0,   0,   0,   0, -10,
        -20, -10, -10,  -5,  -5, -10, -10, -20,
    };

    private static final int[] KING_MIDDLEGAME_TABLE = {
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -20, -30, -30, -40, -40, -30, -30, -20,
        -10, -20, -20, -20, -20, -20, -20, -10,
         20,  20,   0,   0,   0,   0,  20,  20,
         20,  30,  10,   0,   0,  10,  30,  20,
    };

    private static final int[] KING_ENDGAME_TABLE = {
        -50, -40, -30, -20, -20, -30, -40, -50,
        -30, -20, -10,   0,   0, -10, -20, -30,
        -30, -10,  20,  30,  30,  20, -10, -30,
        -30, -10,  30,  40,  40,  30, -10, -30,
        -30, -10,  30,  40,  40,  30, -10, -30,
        -30, -10,  20,  30,  30,  20, -10, -30,
        -30, -30,   0,   0,   0,   0, -30, -30,
        -50, -30, -30, -30, -30, -30, -30, -50,
    };

    private static final int[][] PIECE_TABLES = {PAWN_TABLE, KNIGHT_TABLE, BISHOP_TABLE, ROOK_TABLE, QUEEN_TABLE};

    private Evaluator() {
    }

    public static int evaluate(Board board) {
        int score = 0;
        int phase = 0;
        int kingMiddlegame = 0;
        int kingEndgame = 0;

        for (int color = 0; color < Color.COUNT; color++) {
            int sign = color == Color.WHITE ? 1 : -1;
            for (int type = PieceType.PAWN; type <= PieceType.QUEEN; type++) {
                int[] table = PIECE_TABLES[type];
                for (long bb = board.pieces(color, type); bb != 0; bb = Bitboard.clearLsb(bb)) {
                    score += sign * (PIECE_VALUE[type] + table[tableIndex(color, Bitboard.lsb(bb))]);
                    phase += PHASE_WEIGHT[type];
                }
            }
            int king = tableIndex(color, board.kingSquare(color));
            kingMiddlegame += sign * KING_MIDDLEGAME_TABLE[king];
            kingEndgame += sign * KING_ENDGAME_TABLE[king];
        }

        phase = Math.min(phase, MAX_PHASE);  // early promotions can push it above the starting material
        score += (kingMiddlegame * phase + kingEndgame * (MAX_PHASE - phase)) / MAX_PHASE;
        return board.sideToMove() == Color.WHITE ? score : -score;
    }

    /**
     * Tables are stored rank 8 first, so a1 (square 0) is at index 56: flip the rank for White. Black uses the table
     * mirrored vertically, which cancels out the flip: square index as is.
     */
    private static int tableIndex(int color, int square) {
        return color == Color.WHITE ? square ^ 56 : square;
    }
}
