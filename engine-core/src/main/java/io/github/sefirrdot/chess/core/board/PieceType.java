package io.github.sefirrdot.chess.core.board;

/** Colorless piece kind, {@code 0..5}. */
public final class PieceType {

    public static final int PAWN = 0;
    public static final int KNIGHT = 1;
    public static final int BISHOP = 2;
    public static final int ROOK = 3;
    public static final int QUEEN = 4;
    public static final int KING = 5;
    public static final int COUNT = 6;

    private PieceType() {
    }
}
