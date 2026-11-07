package io.github.sefirrdot.chess.core.move;

import io.github.sefirrdot.chess.core.board.PieceType;
import io.github.sefirrdot.chess.core.board.Square;

/**
 * A move packed into the low 16 bits of an {@code int}:
 *
 * <pre>
 *  15  12 11       6 5        0
 * +------+----------+----------+
 * | flag |    to    |   from   |
 * +------+----------+----------+
 * </pre>
 *
 * <p>Flag layout (from the Chess Programming Wiki "from-to based" encoding):
 *
 * <pre>
 * bit 3 = promotion, bit 2 = capture, bits 0-1 = special / promotion piece
 *  0 quiet               4 capture              8 promo knight    12 promo-capture knight
 *  1 double pawn push    5 en passant capture   9 promo bishop    13 promo-capture bishop
 *  2 king castle                               10 promo rook      14 promo-capture rook
 *  3 queen castle                              11 promo queen     15 promo-capture queen
 * </pre>
 *
 * <p>Value {@code 0} (a1a1 quiet) can never be a legal move, so it serves as {@link #NONE}.
 */
public final class Move {

    public static final int NONE = 0;

    public static final int QUIET = 0;
    public static final int DOUBLE_PAWN_PUSH = 1;
    public static final int KING_CASTLE = 2;
    public static final int QUEEN_CASTLE = 3;
    public static final int CAPTURE = 4;
    public static final int EN_PASSANT = 5;
    public static final int PROMO_KNIGHT = 8;
    public static final int PROMO_BISHOP = 9;
    public static final int PROMO_ROOK = 10;
    public static final int PROMO_QUEEN = 11;
    public static final int PROMO_CAPTURE_KNIGHT = 12;
    public static final int PROMO_CAPTURE_BISHOP = 13;
    public static final int PROMO_CAPTURE_ROOK = 14;
    public static final int PROMO_CAPTURE_QUEEN = 15;

    private static final int CAPTURE_BIT = 0b0100;
    private static final int PROMOTION_BIT = 0b1000;
    private static final String PROMOTION_CHARS = "nbrq";

    private Move() {
    }

    public static int of(int from, int to, int flag) {
        return from | (to << 6) | (flag << 12);
    }

    public static int from(int move) {
        return move & 0x3F;
    }

    public static int to(int move) {
        return (move >>> 6) & 0x3F;
    }

    public static int flag(int move) {
        return (move >>> 12) & 0xF;
    }

    public static boolean isCapture(int move) {
        return (flag(move) & CAPTURE_BIT) != 0;
    }

    public static boolean isPromotion(int move) {
        return (flag(move) & PROMOTION_BIT) != 0;
    }

    public static boolean isCastle(int move) {
        int flag = flag(move);
        return flag == KING_CASTLE || flag == QUEEN_CASTLE;
    }

    /** Piece type a pawn promotes to. Only meaningful when {@link #isPromotion(int)}. */
    public static int promotionType(int move) {
        return PieceType.KNIGHT + (flag(move) & 0b11);
    }

    /** Long algebraic notation used by UCI, e.g. {@code e2e4}, {@code e7e8q}; {@code 0000} for {@link #NONE}. */
    public static String toUci(int move) {
        if (move == NONE) {
            return "0000";
        }
        String uci = Square.name(from(move)) + Square.name(to(move));
        return isPromotion(move) ? uci + PROMOTION_CHARS.charAt(flag(move) & 0b11) : uci;
    }
}
