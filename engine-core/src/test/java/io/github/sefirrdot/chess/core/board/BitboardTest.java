package io.github.sefirrdot.chess.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BitboardTest {

    @Test
    void ofSetsExactlyOneBit() {
        for (int sq = 0; sq < Square.COUNT; sq++) {
            long bb = Bitboard.of(sq);
            assertEquals(1, Bitboard.count(bb));
            assertEquals(sq, Bitboard.lsb(bb));
            assertTrue(Bitboard.contains(bb, sq));
        }
    }

    @Test
    void filesAndRanksContainEightSquares() {
        for (long line : new long[] {Bitboard.FILE_A, Bitboard.FILE_H, Bitboard.RANK_1, Bitboard.RANK_8}) {
            assertEquals(8, Bitboard.count(line));
        }
        assertTrue(Bitboard.contains(Bitboard.FILE_A, Square.A8));
        assertTrue(Bitboard.contains(Bitboard.FILE_H, Square.H1));
        assertTrue(Bitboard.contains(Bitboard.RANK_8, Square.E8));
        assertFalse(Bitboard.contains(Bitboard.FILE_A, Square.B1));
    }

    @Test
    void iteratingWithLsbVisitsSquaresInAscendingOrder() {
        long bb = Bitboard.of(Square.H8) | Bitboard.of(Square.A1) | Bitboard.of(Square.E4);
        List<Integer> visited = new ArrayList<>();
        while (bb != 0) {
            visited.add(Bitboard.lsb(bb));
            bb = Bitboard.clearLsb(bb);
        }
        assertEquals(List.of(Square.A1, Square.E4, Square.H8), visited);
    }

    @Test
    void lsbAndMsbFindExtremeSquares() {
        long bb = Bitboard.of(Square.C3) | Bitboard.of(Square.F6) | Bitboard.of(Square.B7);
        assertEquals(Square.C3, Bitboard.lsb(bb));
        assertEquals(Square.B7, Bitboard.msb(bb));
        assertEquals(Square.H8, Bitboard.msb(Bitboard.ALL));
        assertEquals(Square.A1, Bitboard.msb(Bitboard.of(Square.A1)));
    }

    @Test
    void toStringPutsRankEightOnTop() {
        String expected = """
                . . . . . . . 1
                . . . . . . . .
                . . . . . . . .
                . . . . . . . .
                . . . . . . . .
                . . . . . . . .
                . . . . . . . .
                1 . . . . . . .
                """;
        assertEquals(expected, Bitboard.toString(Bitboard.of(Square.A1) | Bitboard.of(Square.H8)));
    }
}
