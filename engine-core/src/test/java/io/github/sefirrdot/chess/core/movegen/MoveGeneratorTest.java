package io.github.sefirrdot.chess.core.movegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sefirrdot.chess.core.board.Board;
import io.github.sefirrdot.chess.core.board.Fen;
import io.github.sefirrdot.chess.core.board.Square;
import io.github.sefirrdot.chess.core.move.Move;
import io.github.sefirrdot.chess.core.move.MoveList;
import org.junit.jupiter.api.Test;

class MoveGeneratorTest {

    private static MoveList legal(String fen) {
        var moves = new MoveList();
        MoveGenerator.generateLegal(Fen.parse(fen), moves);
        return moves;
    }

    private static boolean hasUci(MoveList moves, String uci) {
        for (int i = 0; i < moves.size(); i++) {
            if (Move.toUci(moves.get(i)).equals(uci)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void startingPositionHasTwentyMoves() {
        MoveList moves = legal(Fen.STARTING_POSITION);
        assertEquals(20, moves.size());
        assertTrue(moves.contains(Move.of(Square.E2, Square.E4, Move.DOUBLE_PAWN_PUSH)));
        assertTrue(moves.contains(Move.of(Square.G1, Square.F3, Move.QUIET)));
    }

    @Test
    void enPassantIsGeneratedOnlyWhenAvailable() {
        assertTrue(legal("rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3")
                .contains(Move.of(Square.E5, Square.D6, Move.EN_PASSANT)));
        assertFalse(hasUci(legal("rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq - 0 3"), "e5d6"));
    }

    @Test
    void enPassantThatExposesTheKingOnTheRankIsIllegal() {
        // b5xc6 e.p. would remove both pawns from the 5th rank and expose the a5 king to the h5 rook.
        assertFalse(hasUci(legal("8/8/8/KPp4r/8/8/8/4k3 w - c6 0 1"), "b5c6"));
    }

    @Test
    void promotionsGenerateAllFourPieces() {
        MoveList moves = legal("4k3/1P6/8/8/8/8/8/4K3 w - - 0 1");
        for (String promo : new String[] {"b7b8q", "b7b8r", "b7b8b", "b7b8n"}) {
            assertTrue(hasUci(moves, promo), promo);
        }
    }

    @Test
    void castlingBothSidesWhenPathIsClearAndSafe() {
        MoveList moves = legal("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        assertTrue(moves.contains(Move.of(Square.E1, Square.G1, Move.KING_CASTLE)));
        assertTrue(moves.contains(Move.of(Square.E1, Square.C1, Move.QUEEN_CASTLE)));
    }

    @Test
    void noCastlingOutOfThroughOrIntoCheck() {
        // Out of check: rook e8 gives check.
        assertFalse(hasUci(legal("4r1k1/8/8/8/8/8/8/R3K2R w KQ - 0 1"), "e1g1"));
        // Through check: f1 attacked by the f8 rook.
        assertFalse(hasUci(legal("5rk1/8/8/8/8/8/8/R3K2R w KQ - 0 1"), "e1g1"));
        // Into check: g1 attacked by the g8 rook.
        assertFalse(hasUci(legal("6rk/8/8/8/8/8/8/R3K2R w KQ - 0 1"), "e1g1"));
        // Queenside: b1 may be attacked (only the rook crosses it), so castling is still legal.
        assertTrue(hasUci(legal("1r4k1/8/8/8/8/8/8/R3K2R w KQ - 0 1"), "e1c1"));
    }

    @Test
    void noCastlingThroughPieces() {
        assertFalse(hasUci(legal("r3k2r/8/8/8/8/8/8/RN2K1NR w KQkq - 0 1"), "e1c1"));
        assertFalse(hasUci(legal("r3k2r/8/8/8/8/8/8/RN2K1NR w KQkq - 0 1"), "e1g1"));
    }

    @Test
    void pinnedPieceCannotLeaveThePin() {
        // The e2 knight is pinned by the e8 rook against the e1 king.
        MoveList moves = legal("4r1k1/8/8/8/8/8/4N3/4K3 w - - 0 1");
        for (int i = 0; i < moves.size(); i++) {
            assertTrue(Move.from(moves.get(i)) != Square.E2, "pinned knight moved: " + Move.toUci(moves.get(i)));
        }
    }

    @Test
    void checkmateAndStalemateHaveNoMoves() {
        Board mate = Fen.parse("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3");  // fool's mate
        assertTrue(MoveGenerator.isInCheck(mate));
        assertEquals(0, legal(Fen.format(mate)).size());

        Board stalemate = Fen.parse("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1");
        assertFalse(MoveGenerator.isInCheck(stalemate));
        assertEquals(0, legal(Fen.format(stalemate)).size());
    }
}
