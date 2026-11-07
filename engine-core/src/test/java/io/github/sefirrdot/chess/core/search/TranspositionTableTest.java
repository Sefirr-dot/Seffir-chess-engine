package io.github.sefirrdot.chess.core.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sefirrdot.chess.core.board.Board;
import io.github.sefirrdot.chess.core.board.Fen;
import io.github.sefirrdot.chess.core.board.Square;
import io.github.sefirrdot.chess.core.move.Move;
import org.junit.jupiter.api.Test;

class TranspositionTableTest {

    @Test
    void storesAndUnpacksEveryField() {
        var tt = new TranspositionTable(1);
        int move = Move.of(Square.E7, Square.E8, Move.PROMO_QUEEN);
        for (int score : new int[] {0, 35, -35, 31_000, -31_000, -32_000}) {
            tt.store(42L, move, score, 17, TranspositionTable.LOWER_BOUND);
            long entry = tt.probe(42L);
            assertEquals(move, TranspositionTable.move(entry));
            assertEquals(score, TranspositionTable.score(entry));
            assertEquals(17, TranspositionTable.depth(entry));
            assertEquals(TranspositionTable.LOWER_BOUND, TranspositionTable.bound(entry));
        }
    }

    @Test
    void sizeIsAPowerOfTwoWithinTheBudget() {
        var tt = new TranspositionTable(1);
        assertEquals(65_536, tt.capacity(), "1 MB / 16 bytes per entry");
        tt.resize(3);
        assertEquals(131_072, tt.capacity(), "rounded down to a power of two");
    }

    @Test
    void indexCollisionsAreRejectedByTheFullKey() {
        var tt = new TranspositionTable(1);
        long a = 5;
        long b = 5 + tt.capacity();  // same slot, different key
        tt.store(a, Move.of(Square.E2, Square.E4, Move.DOUBLE_PAWN_PUSH), 10, 3, TranspositionTable.EXACT);
        assertEquals(TranspositionTable.NO_ENTRY, tt.probe(b));
        tt.store(b, 0, 20, 4, TranspositionTable.EXACT);
        assertEquals(TranspositionTable.NO_ENTRY, tt.probe(a), "always replace");
        assertEquals(20, TranspositionTable.score(tt.probe(b)));
    }

    @Test
    void storingWithoutAMoveKeepsThePreviousMoveForTheSamePosition() {
        var tt = new TranspositionTable(1);
        int move = Move.of(Square.G1, Square.F3, Move.QUIET);
        tt.store(7L, move, 10, 5, TranspositionTable.EXACT);
        tt.store(7L, Move.NONE, -3, 6, TranspositionTable.UPPER_BOUND);
        assertEquals(move, TranspositionTable.move(tt.probe(7L)));
        assertEquals(6, TranspositionTable.depth(tt.probe(7L)));
    }

    @Test
    void mateScoresAreStoredRelativeToTheNode() {
        int mateIn5PliesFromRoot = Search.MATE - 5;
        // Found at ply 3: from that node it is mate in 2 plies.
        int stored = TranspositionTable.scoreToTable(mateIn5PliesFromRoot, 3);
        assertEquals(Search.MATE - 2, stored);
        // Reached again at ply 7: mate in 9 plies from the root.
        assertEquals(Search.MATE - 9, TranspositionTable.scoreFromTable(stored, 7));
        assertEquals(-Search.MATE + 9, TranspositionTable.scoreFromTable(TranspositionTable.scoreToTable(-Search.MATE + 5, 3), 7));
        assertEquals(120, TranspositionTable.scoreFromTable(TranspositionTable.scoreToTable(120, 3), 7), "normal scores");
    }

    @Test
    void clearEmptiesTheTable() {
        var tt = new TranspositionTable(1);
        tt.store(9L, 0, 1, 1, TranspositionTable.EXACT);
        assertTrue(tt.hashfull() >= 0);
        tt.clear();
        assertEquals(TranspositionTable.NO_ENTRY, tt.probe(9L));
    }

    @Test
    void sharedTableMakesTheSecondSearchFasterAndKeepsTheResult() {
        Board board = Fen.parse("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");
        var tt = new TranspositionTable(16);
        SearchInfo first = new Search(board, tt, info -> { }).search(SearchLimits.ofDepth(6));
        SearchInfo second = new Search(board, tt, info -> { }).search(SearchLimits.ofDepth(6));
        assertTrue(second.nodes() < first.nodes() / 2, first.nodes() + " then " + second.nodes());
        assertEquals(first.bestMove(), second.bestMove());
    }
}
