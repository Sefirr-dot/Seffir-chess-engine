package io.github.sefirrdot.chess.core.board;

/** Castling availability as a 4-bit mask, in FEN order {@code KQkq}. */
public final class CastlingRights {

    public static final int NONE = 0;
    public static final int WHITE_KINGSIDE = 1;
    public static final int WHITE_QUEENSIDE = 2;
    public static final int BLACK_KINGSIDE = 4;
    public static final int BLACK_QUEENSIDE = 8;
    public static final int ALL = 15;

    private static final String FEN_CHARS = "KQkq";

    private CastlingRights() {
    }

    public static boolean has(int rights, int right) {
        return (rights & right) != 0;
    }

    /** FEN castling field, {@code "-"} when no rights remain. */
    public static String toFen(int rights) {
        if (rights == NONE) {
            return "-";
        }
        var sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            if (has(rights, 1 << i)) {
                sb.append(FEN_CHARS.charAt(i));
            }
        }
        return sb.toString();
    }

    /** Parses a FEN castling field. Letters must appear in {@code KQkq} order, without repetition. */
    public static int fromFen(String field) {
        if ("-".equals(field)) {
            return NONE;
        }
        if (field.isEmpty()) {
            throw new IllegalArgumentException("Empty castling field");
        }
        int rights = NONE;
        int lastIndex = -1;
        for (int i = 0; i < field.length(); i++) {
            int index = FEN_CHARS.indexOf(field.charAt(i));
            if (index <= lastIndex) {
                throw new IllegalArgumentException("Invalid castling field: " + field);
            }
            rights |= 1 << index;
            lastIndex = index;
        }
        return rights;
    }
}
