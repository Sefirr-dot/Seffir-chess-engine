package io.github.sefirrdot.chess.core.board;

/**
 * A bitboard is a {@code long} where bit {@code n} is set when square {@code n} (see {@link Square}) belongs to the set.
 *
 * <p>Set operations become single CPU instructions: union is {@code |}, intersection {@code &}, complement {@code ~},
 * and shifting the whole set one rank up is {@code << 8}.
 */
public final class Bitboard {

    public static final long EMPTY = 0L;
    public static final long ALL = ~0L;

    public static final long FILE_A = 0x0101_0101_0101_0101L;
    public static final long FILE_B = FILE_A << 1;
    public static final long FILE_G = FILE_A << 6;
    public static final long FILE_H = FILE_A << 7;

    public static final long RANK_1 = 0xFFL;
    public static final long RANK_2 = RANK_1 << 8;
    public static final long RANK_3 = RANK_1 << 16;
    public static final long RANK_4 = RANK_1 << 24;
    public static final long RANK_5 = RANK_1 << 32;
    public static final long RANK_6 = RANK_1 << 40;
    public static final long RANK_7 = RANK_1 << 48;
    public static final long RANK_8 = RANK_1 << 56;

    private Bitboard() {
    }

    public static long of(int square) {
        return 1L << square;
    }

    public static boolean contains(long bitboard, int square) {
        return ((bitboard >>> square) & 1L) != 0;
    }

    public static int count(long bitboard) {
        return Long.bitCount(bitboard);
    }

    /** Index of the least significant set bit. Undefined (returns 64) for an empty bitboard. */
    public static int lsb(long bitboard) {
        return Long.numberOfTrailingZeros(bitboard);
    }

    /** Index of the most significant set bit. Undefined (returns -1) for an empty bitboard. */
    public static int msb(long bitboard) {
        return 63 - Long.numberOfLeadingZeros(bitboard);
    }

    /** The same bitboard with its least significant set bit cleared. Used to iterate: {@code while (bb != 0) { sq = lsb(bb); bb = clearLsb(bb); }} */
    public static long clearLsb(long bitboard) {
        return bitboard & (bitboard - 1);
    }

    /** 8x8 grid, rank 8 on top, {@code 1} for set squares. Debugging aid. */
    public static String toString(long bitboard) {
        var sb = new StringBuilder(8 * 17);
        for (int rank = 7; rank >= 0; rank--) {
            for (int file = 0; file < 8; file++) {
                sb.append(contains(bitboard, Square.of(file, rank)) ? '1' : '.');
                sb.append(file < 7 ? ' ' : '\n');
            }
        }
        return sb.toString();
    }
}
