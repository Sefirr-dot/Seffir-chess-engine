package io.github.sefirrdot.chess.core.movegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sefirrdot.chess.core.board.Bitboard;
import io.github.sefirrdot.chess.core.board.Board;
import io.github.sefirrdot.chess.core.board.Color;
import io.github.sefirrdot.chess.core.board.Fen;
import io.github.sefirrdot.chess.core.board.Square;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class AttacksTest {

    private static long squares(int... squares) {
        long bb = 0;
        for (int sq : squares) {
            bb |= Bitboard.of(sq);
        }
        return bb;
    }

    // --- Leapers ---

    @Test
    void knightAttacks() {
        assertEquals(squares(Square.B3, Square.C2), Attacks.knight(Square.A1));
        assertEquals(squares(Square.D6, Square.F6, Square.G5, Square.G3, Square.F2, Square.D2, Square.C3, Square.C5),
                Attacks.knight(Square.E4));
        assertEquals(squares(Square.F7, Square.G6), Attacks.knight(Square.H8));
    }

    @Test
    void kingAttacks() {
        assertEquals(squares(Square.A2, Square.B2, Square.B1), Attacks.king(Square.A1));
        assertEquals(8, Bitboard.count(Attacks.king(Square.E4)));
        assertEquals(5, Bitboard.count(Attacks.king(Square.E1)));
    }

    @Test
    void pawnAttacksAreDiagonalForwardAndDoNotWrap() {
        assertEquals(squares(Square.D3, Square.F3), Attacks.pawn(Color.WHITE, Square.E2));
        assertEquals(squares(Square.B3), Attacks.pawn(Color.WHITE, Square.A2));
        assertEquals(squares(Square.G3), Attacks.pawn(Color.WHITE, Square.H2));
        assertEquals(squares(Square.D6, Square.F6), Attacks.pawn(Color.BLACK, Square.E7));
        assertEquals(squares(Square.G6), Attacks.pawn(Color.BLACK, Square.H7));
        assertEquals(0, Attacks.pawn(Color.WHITE, Square.E8));
    }

    @Test
    void leaperAttackCountsSumToKnownTotals() {
        long knight = 0;
        long king = 0;
        for (int sq = 0; sq < Square.COUNT; sq++) {
            knight += Bitboard.count(Attacks.knight(sq));
            king += Bitboard.count(Attacks.king(sq));
        }
        assertEquals(336, knight);
        assertEquals(420, king);
    }

    // --- Sliders ---

    @Test
    void rookOnEmptyBoardSeesFourteenSquares() {
        for (int sq = 0; sq < Square.COUNT; sq++) {
            assertEquals(14, Bitboard.count(Attacks.rook(sq, 0)));
        }
    }

    @Test
    void rookStopsAtFirstBlockerIncludingIt() {
        long occupied = squares(Square.D6, Square.B4, Square.D7, Square.A4);
        long attacks = Attacks.rook(Square.D4, occupied);
        assertTrue(Bitboard.contains(attacks, Square.D6));
        assertFalse(Bitboard.contains(attacks, Square.D7));
        assertTrue(Bitboard.contains(attacks, Square.B4));
        assertFalse(Bitboard.contains(attacks, Square.A4));
        assertTrue(Bitboard.contains(attacks, Square.D1));
        assertTrue(Bitboard.contains(attacks, Square.H4));
        assertFalse(Bitboard.contains(attacks, Square.D4));
    }

    @Test
    void bishopStopsAtFirstBlockerInDecreasingDirections() {
        long attacks = Attacks.bishop(Square.E5, squares(Square.C3, Square.G3));
        assertTrue(Bitboard.contains(attacks, Square.C3));
        assertFalse(Bitboard.contains(attacks, Square.B2));
        assertTrue(Bitboard.contains(attacks, Square.G3));
        assertFalse(Bitboard.contains(attacks, Square.H2));
        assertFalse(Bitboard.contains(attacks, Square.A1));
        assertTrue(Bitboard.contains(attacks, Square.H8));
        assertTrue(Bitboard.contains(attacks, Square.B8));
    }

    @Test
    void slidersMatchNaiveReferenceOnRandomOccupancies() {
        var random = new SplittableRandom(20261107);
        for (int i = 0; i < 2000; i++) {
            // Sparse and dense boards: AND-ing random longs lowers the density.
            long occupied = i % 2 == 0 ? random.nextLong() : random.nextLong() & random.nextLong() & random.nextLong();
            for (int sq = 0; sq < Square.COUNT; sq++) {
                assertEquals(naiveSlide(sq, occupied, BISHOP_DIRS), Attacks.bishop(sq, occupied));
                assertEquals(naiveSlide(sq, occupied, ROOK_DIRS), Attacks.rook(sq, occupied));
            }
        }
    }

    private static final int[][] BISHOP_DIRS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int[][] ROOK_DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Walks square by square until the edge or the first occupied square. Slow but obviously correct. */
    private static long naiveSlide(int square, long occupied, int[][] directions) {
        long attacks = 0;
        for (int[] d : directions) {
            int file = Square.file(square) + d[0];
            int rank = Square.rank(square) + d[1];
            while (file >= 0 && file < 8 && rank >= 0 && rank < 8) {
                int target = Square.of(file, rank);
                attacks |= Bitboard.of(target);
                if (Bitboard.contains(occupied, target)) {
                    break;
                }
                file += d[0];
                rank += d[1];
            }
        }
        return attacks;
    }

    // --- Square attacked ---

    @Test
    void squaresAttackedInStartingPosition() {
        Board board = Board.startingPosition();
        assertTrue(Attacks.isSquareAttacked(board, Square.E3, Color.WHITE));   // pawns d2, f2
        assertTrue(Attacks.isSquareAttacked(board, Square.F3, Color.WHITE));   // knight g1
        assertFalse(Attacks.isSquareAttacked(board, Square.E4, Color.WHITE));
        assertTrue(Attacks.isSquareAttacked(board, Square.F6, Color.BLACK));
        assertFalse(Attacks.isSquareAttacked(board, Square.E5, Color.BLACK));
        assertFalse(Attacks.isSquareAttacked(board, Square.E3, Color.BLACK));
    }

    @Test
    void slidersAttackThroughOpenLinesButNotThroughPieces() {
        // White: queen d1, king e1, rook a5. Black: king e8, pawn e7.
        Board board = Fen.parse("4k3/4p3/8/R7/8/8/8/3QK3 w - - 0 1");
        assertTrue(Attacks.isSquareAttacked(board, Square.H5, Color.WHITE));   // rook along the open 5th rank
        assertTrue(Attacks.isSquareAttacked(board, Square.A4, Color.WHITE));   // queen on the d1-a4 diagonal
        assertTrue(Attacks.isSquareAttacked(board, Square.D8, Color.WHITE));   // queen up the open d-file
        assertTrue(Attacks.isSquareAttacked(board, Square.E7, Color.BLACK));   // king defends its pawn
        assertFalse(Attacks.isSquareAttacked(board, Square.E8, Color.WHITE));  // no white piece reaches e8
        assertFalse(Attacks.isSquareAttacked(board, Square.B6, Color.WHITE));  // a5 rook and d1 queen both miss it
    }
}
