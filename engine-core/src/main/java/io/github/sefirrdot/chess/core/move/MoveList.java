package io.github.sefirrdot.chess.core.move;

/**
 * Fixed-capacity list of encoded moves, reusable across positions to avoid allocation in search.
 *
 * <p>No reachable position has more than 218 legal moves; 256 leaves headroom for pseudo-legal ones.
 */
public final class MoveList {

    public static final int CAPACITY = 256;

    private final int[] moves = new int[CAPACITY];
    private int size;

    public void add(int move) {
        moves[size++] = move;
    }

    public int get(int index) {
        return moves[index];
    }

    public int size() {
        return size;
    }

    public void clear() {
        size = 0;
    }

    public boolean contains(int move) {
        for (int i = 0; i < size; i++) {
            if (moves[i] == move) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        var sb = new StringBuilder("[");
        for (int i = 0; i < size; i++) {
            sb.append(i == 0 ? "" : ", ").append(Move.toUci(moves[i]));
        }
        return sb.append(']').toString();
    }
}
