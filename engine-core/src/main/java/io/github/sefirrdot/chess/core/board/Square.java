package io.github.sefirrdot.chess.core.board;

/**
 * Squares are plain {@code int}s in little-endian rank-file order: {@code a1 = 0, b1 = 1, ..., h1 = 7, a2 = 8, ..., h8 = 63}.
 *
 * <p>With this mapping, square {@code s} corresponds to bit {@code s} of a {@link Bitboard}, "one rank up" is {@code +8}
 * and "one file right" is {@code +1}.
 */
public final class Square {

    public static final int A1 = 0, B1 = 1, C1 = 2, D1 = 3, E1 = 4, F1 = 5, G1 = 6, H1 = 7;
    public static final int A2 = 8, B2 = 9, C2 = 10, D2 = 11, E2 = 12, F2 = 13, G2 = 14, H2 = 15;
    public static final int A3 = 16, B3 = 17, C3 = 18, D3 = 19, E3 = 20, F3 = 21, G3 = 22, H3 = 23;
    public static final int A4 = 24, B4 = 25, C4 = 26, D4 = 27, E4 = 28, F4 = 29, G4 = 30, H4 = 31;
    public static final int A5 = 32, B5 = 33, C5 = 34, D5 = 35, E5 = 36, F5 = 37, G5 = 38, H5 = 39;
    public static final int A6 = 40, B6 = 41, C6 = 42, D6 = 43, E6 = 44, F6 = 45, G6 = 46, H6 = 47;
    public static final int A7 = 48, B7 = 49, C7 = 50, D7 = 51, E7 = 52, F7 = 53, G7 = 54, H7 = 55;
    public static final int A8 = 56, B8 = 57, C8 = 58, D8 = 59, E8 = 60, F8 = 61, G8 = 62, H8 = 63;

    public static final int NONE = -1;
    public static final int COUNT = 64;

    private Square() {
    }

    /** @param file 0 (a) to 7 (h); @param rank 0 (rank 1) to 7 (rank 8) */
    public static int of(int file, int rank) {
        return (rank << 3) | file;
    }

    public static int file(int square) {
        return square & 7;
    }

    public static int rank(int square) {
        return square >>> 3;
    }

    public static boolean isValid(int square) {
        return (square & ~63) == 0;
    }

    /** Algebraic name, e.g. {@code "e4"}. */
    public static String name(int square) {
        if (!isValid(square)) {
            throw new IllegalArgumentException("Invalid square index: " + square);
        }
        return new String(new char[] {(char) ('a' + file(square)), (char) ('1' + rank(square))});
    }

    /** Parses an algebraic name such as {@code "e4"}. */
    public static int parse(String name) {
        if (name == null || name.length() != 2) {
            throw new IllegalArgumentException("Invalid square name: " + name);
        }
        int file = name.charAt(0) - 'a';
        int rank = name.charAt(1) - '1';
        if (file < 0 || file > 7 || rank < 0 || rank > 7) {
            throw new IllegalArgumentException("Invalid square name: " + name);
        }
        return of(file, rank);
    }
}
