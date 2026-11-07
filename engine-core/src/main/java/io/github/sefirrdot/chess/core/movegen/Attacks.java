package io.github.sefirrdot.chess.core.movegen;

import io.github.sefirrdot.chess.core.board.Bitboard;
import io.github.sefirrdot.chess.core.board.Board;
import io.github.sefirrdot.chess.core.board.Color;
import io.github.sefirrdot.chess.core.board.PieceType;
import io.github.sefirrdot.chess.core.board.Square;

/**
 * Attack sets for every piece, as bitboards.
 *
 * <p><b>Leapers</b> (pawn, knight, king) attack the same squares whatever the occupancy, so their attacks are
 * precomputed once per square and answered with a single array lookup.
 *
 * <p><b>Sliders</b> (bishop, rook, queen) use the classical ray approach: for each of the 8 directions a precomputed
 * ray holds every square up to the board edge. The first blocker on the ray is the lowest set bit of
 * {@code ray & occupied} for directions that increase the square index, and the highest for those that decrease it;
 * removing the blocker's own ray from ours leaves exactly the attacked squares (blocker included).
 */
public final class Attacks {

    // Direction order: the first four increase the square index, the last four decrease it.
    private static final int NORTH = 0, NORTH_EAST = 1, EAST = 2, NORTH_WEST = 3;
    private static final int SOUTH = 4, SOUTH_WEST = 5, WEST = 6, SOUTH_EAST = 7;
    private static final int[] FILE_DELTA = {0, 1, 1, -1, 0, -1, -1, 1};
    private static final int[] RANK_DELTA = {1, 1, 0, 1, -1, -1, 0, -1};

    private static final long[] KNIGHT = new long[Square.COUNT];
    private static final long[] KING = new long[Square.COUNT];
    private static final long[][] PAWN = new long[Color.COUNT][Square.COUNT];
    private static final long[][] RAYS = new long[8][Square.COUNT];

    static {
        int[][] knightJumps = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
        for (int sq = 0; sq < Square.COUNT; sq++) {
            for (int[] jump : knightJumps) {
                KNIGHT[sq] |= offset(sq, jump[0], jump[1]);
            }
            for (int dir = 0; dir < 8; dir++) {
                KING[sq] |= offset(sq, FILE_DELTA[dir], RANK_DELTA[dir]);
                RAYS[dir][sq] = ray(sq, FILE_DELTA[dir], RANK_DELTA[dir]);
            }
            PAWN[Color.WHITE][sq] = offset(sq, -1, 1) | offset(sq, 1, 1);
            PAWN[Color.BLACK][sq] = offset(sq, -1, -1) | offset(sq, 1, -1);
        }
    }

    private Attacks() {
    }

    /** Squares a pawn of {@code color} on {@code square} attacks (diagonally forward), not its pushes. */
    public static long pawn(int color, int square) {
        return PAWN[color][square];
    }

    public static long knight(int square) {
        return KNIGHT[square];
    }

    public static long king(int square) {
        return KING[square];
    }

    public static long bishop(int square, long occupied) {
        return slide(NORTH_EAST, square, occupied) | slide(NORTH_WEST, square, occupied)
                | slide(SOUTH_EAST, square, occupied) | slide(SOUTH_WEST, square, occupied);
    }

    public static long rook(int square, long occupied) {
        return slide(NORTH, square, occupied) | slide(EAST, square, occupied)
                | slide(SOUTH, square, occupied) | slide(WEST, square, occupied);
    }

    public static long queen(int square, long occupied) {
        return bishop(square, occupied) | rook(square, occupied);
    }

    /**
     * Whether any piece of {@code byColor} attacks {@code square}.
     *
     * <p>Uses the symmetry of attacks: put a "super piece" on the target square and check whether it, moving like each
     * piece type, hits an enemy piece of that same type. One lookup per piece type instead of iterating all attackers.
     */
    public static boolean isSquareAttacked(Board board, int square, int byColor) {
        long occupied = board.occupied();
        long queens = board.pieces(byColor, PieceType.QUEEN);
        // A white pawn attacking `square` sits where a black pawn on `square` would attack, and vice versa.
        return (pawn(Color.opposite(byColor), square) & board.pieces(byColor, PieceType.PAWN)) != 0
                || (knight(square) & board.pieces(byColor, PieceType.KNIGHT)) != 0
                || (king(square) & board.pieces(byColor, PieceType.KING)) != 0
                || (bishop(square, occupied) & (board.pieces(byColor, PieceType.BISHOP) | queens)) != 0
                || (rook(square, occupied) & (board.pieces(byColor, PieceType.ROOK) | queens)) != 0;
    }

    private static long slide(int direction, int square, long occupied) {
        long ray = RAYS[direction][square];
        long blockers = ray & occupied;
        if (blockers == 0) {
            return ray;
        }
        int blocker = direction < SOUTH ? Bitboard.lsb(blockers) : Bitboard.msb(blockers);
        return ray ^ RAYS[direction][blocker];
    }

    /** Bitboard of the square at (file + df, rank + dr), or empty if that falls off the board. */
    private static long offset(int square, int df, int dr) {
        int file = Square.file(square) + df;
        int rank = Square.rank(square) + dr;
        return file >= 0 && file < 8 && rank >= 0 && rank < 8 ? Bitboard.of(Square.of(file, rank)) : Bitboard.EMPTY;
    }

    private static long ray(int square, int df, int dr) {
        long ray = Bitboard.EMPTY;
        int file = Square.file(square) + df;
        int rank = Square.rank(square) + dr;
        while (file >= 0 && file < 8 && rank >= 0 && rank < 8) {
            ray |= Bitboard.of(Square.of(file, rank));
            file += df;
            rank += dr;
        }
        return ray;
    }
}
