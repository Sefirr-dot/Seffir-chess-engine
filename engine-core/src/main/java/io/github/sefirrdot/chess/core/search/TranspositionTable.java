package io.github.sefirrdot.chess.core.search;

import java.util.Arrays;

/**
 * Hash table of search results, indexed by Zobrist hash.
 *
 * <p>The same position is reached through many move orders. Storing what a search found about it — score, depth,
 * whether the score is exact or only a bound, and the best move — lets later visits either return immediately or at
 * least try the stored best move first, which is what makes alpha-beta prune well.
 *
 * <p>Entries are two parallel {@code long} arrays (no objects, no garbage): the full key, to reject index collisions,
 * and a packed data word:
 *
 * <pre>
 *  63    62..42   41..40  39..32   31..16           15..0
 * +-----+--------+-------+--------+----------------+--------+
 * |valid| unused | bound | depth  | score (16 bit) | move   |
 * +-----+--------+-------+--------+----------------+--------+
 * </pre>
 *
 * <p>Replacement is "always replace", keeping the old best move when the new search found none. Mate scores must be
 * stored relative to the node, not the root: see {@link #scoreToTable} / {@link #scoreFromTable}.
 */
public final class TranspositionTable {

    public static final int EXACT = 0;
    /** The real score is at least the stored one (the search failed high). */
    public static final int LOWER_BOUND = 1;
    /** The real score is at most the stored one (the search failed low). */
    public static final int UPPER_BOUND = 2;

    public static final long NO_ENTRY = 0;
    public static final int DEFAULT_MEGABYTES = 64;

    private static final long VALID = 1L << 63;
    private static final int ENTRY_BYTES = 16;

    private long[] keys;
    private long[] data;
    private int mask;

    public TranspositionTable(int megabytes) {
        resize(megabytes);
    }

    /** Reallocates to the largest power-of-two entry count fitting in {@code megabytes}; the content is lost. */
    public void resize(int megabytes) {
        long bytes = Math.max(1, megabytes) * 1024L * 1024L;
        int entries = Integer.highestOneBit((int) Math.min(Integer.MAX_VALUE, bytes / ENTRY_BYTES));
        keys = new long[entries];
        data = new long[entries];
        mask = entries - 1;
    }

    public int capacity() {
        return keys.length;
    }

    public void clear() {
        Arrays.fill(keys, 0);
        Arrays.fill(data, 0);
    }

    /** @return the packed entry for {@code key}, or {@link #NO_ENTRY} */
    public long probe(long key) {
        int index = (int) key & mask;
        return keys[index] == key ? data[index] : NO_ENTRY;
    }

    public void store(long key, int move, int score, int depth, int bound) {
        int index = (int) key & mask;
        if (move == 0 && keys[index] == key) {
            move = move(data[index]);
        }
        keys[index] = key;
        data[index] = VALID
                | ((long) (bound & 3) << 40)
                | ((long) (Math.max(0, Math.min(255, depth))) << 32)
                | ((long) (score & 0xFFFF) << 16)
                | (move & 0xFFFF);
    }

    public static int move(long entry) {
        return (int) (entry & 0xFFFF);
    }

    public static int score(long entry) {
        return (short) (entry >>> 16);
    }

    public static int depth(long entry) {
        return (int) ((entry >>> 32) & 0xFF);
    }

    public static int bound(long entry) {
        return (int) ((entry >>> 40) & 3);
    }

    /** Permille of the first 1000 slots in use, as reported by UCI {@code hashfull}. */
    public int hashfull() {
        int sample = Math.min(1000, data.length);
        int used = 0;
        for (int i = 0; i < sample; i++) {
            if ((data[i] & VALID) != 0) {
                used++;
            }
        }
        return used * 1000 / sample;
    }

    /**
     * Mate scores count plies from the root ({@code MATE - plies}). A table entry may be reached at another ply, so
     * store "mate in n plies from this node" instead.
     */
    public static int scoreToTable(int score, int ply) {
        if (score >= Search.MATE - Search.MAX_PLY) {
            return score + ply;
        }
        if (score <= -Search.MATE + Search.MAX_PLY) {
            return score - ply;
        }
        return score;
    }

    public static int scoreFromTable(int score, int ply) {
        if (score >= Search.MATE - Search.MAX_PLY) {
            return score - ply;
        }
        if (score <= -Search.MATE + Search.MAX_PLY) {
            return score + ply;
        }
        return score;
    }
}
