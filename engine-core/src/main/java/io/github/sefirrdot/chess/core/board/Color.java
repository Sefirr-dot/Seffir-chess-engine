package io.github.sefirrdot.chess.core.board;

/** Side to move / piece owner, encoded as {@code 0} (white) or {@code 1} (black) so it can index arrays directly. */
public final class Color {

    public static final int WHITE = 0;
    public static final int BLACK = 1;
    public static final int COUNT = 2;

    private Color() {
    }

    public static int opposite(int color) {
        return color ^ 1;
    }

    public static String name(int color) {
        return color == WHITE ? "white" : "black";
    }
}
