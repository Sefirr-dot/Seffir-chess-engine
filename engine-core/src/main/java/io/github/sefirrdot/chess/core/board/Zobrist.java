package io.github.sefirrdot.chess.core.board;

/**
 * Random keys for Zobrist hashing.
 *
 * <p>A position's hash is the XOR of the keys of everything in it: one per (piece, square), one when black is to move,
 * one per castling-rights mask and one per en passant file. XOR is its own inverse, so making a move updates the hash
 * with a handful of XORs instead of recomputing it.
 *
 * <p>Keys come from a SplitMix64 generator with a fixed seed, so hashes are identical across runs and machines.
 */
final class Zobrist {

    static final long[][] PIECE_SQUARE = new long[Piece.COUNT][Square.COUNT];
    static final long[] CASTLING = new long[CastlingRights.ALL + 1];
    static final long[] EN_PASSANT_FILE = new long[8];
    static final long BLACK_TO_MOVE;

    private static final long SEED = 0x5EFF1C4E55L;
    private static long state = SEED;

    static {
        for (long[] squares : PIECE_SQUARE) {
            for (int sq = 0; sq < Square.COUNT; sq++) {
                squares[sq] = next();
            }
        }
        // No rights at all hashes to nothing, so a bare position's hash is just its pieces.
        for (int rights = 1; rights < CASTLING.length; rights++) {
            CASTLING[rights] = next();
        }
        for (int file = 0; file < 8; file++) {
            EN_PASSANT_FILE[file] = next();
        }
        BLACK_TO_MOVE = next();
    }

    private Zobrist() {
    }

    private static long next() {
        long z = (state += 0x9E37_79B9_7F4A_7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58_476D_1CE4_E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D0_49BB_1331_11EBL;
        return z ^ (z >>> 31);
    }
}
