package io.github.sefirrdot.chess.core.board;

/**
 * A colored piece, {@code color * 6 + type}: white pieces are {@code 0..5}, black pieces {@code 6..11}.
 *
 * <p>The ordering matches FEN letters {@code "PNBRQKpnbrqk"}, so the index doubles as a lookup into that string.
 */
public final class Piece {

    public static final int WHITE_PAWN = 0, WHITE_KNIGHT = 1, WHITE_BISHOP = 2,
            WHITE_ROOK = 3, WHITE_QUEEN = 4, WHITE_KING = 5;
    public static final int BLACK_PAWN = 6, BLACK_KNIGHT = 7, BLACK_BISHOP = 8,
            BLACK_ROOK = 9, BLACK_QUEEN = 10, BLACK_KING = 11;

    public static final int NONE = -1;
    public static final int COUNT = 12;

    private static final String FEN_CHARS = "PNBRQKpnbrqk";

    private Piece() {
    }

    public static int of(int color, int type) {
        return color * PieceType.COUNT + type;
    }

    public static int color(int piece) {
        return piece < PieceType.COUNT ? Color.WHITE : Color.BLACK;
    }

    public static int type(int piece) {
        return piece % PieceType.COUNT;
    }

    /** FEN letter: uppercase for white, lowercase for black. */
    public static char toFenChar(int piece) {
        return FEN_CHARS.charAt(piece);
    }

    /** @return the piece for a FEN letter, or {@link #NONE} if the letter is not a piece */
    public static int fromFenChar(char c) {
        return FEN_CHARS.indexOf(c);
    }
}
