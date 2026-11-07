package io.github.sefirrdot.chess.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sefirrdot.chess.core.move.Move;
import io.github.sefirrdot.chess.core.move.MoveList;
import io.github.sefirrdot.chess.core.movegen.MoveGenerator;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ZobristTest {

    /** Plays moves given in UCI notation, resolving them against the legal moves of each position. */
    private static Board play(String fen, String... uciMoves) {
        Board board = Fen.parse(fen);
        var moves = new MoveList();
        for (String uci : uciMoves) {
            MoveGenerator.generateLegal(board, moves);
            int found = Move.NONE;
            for (int i = 0; i < moves.size(); i++) {
                if (Move.toUci(moves.get(i)).equals(uci)) {
                    found = moves.get(i);
                }
            }
            assertNotEquals(Move.NONE, found, "illegal move " + uci + " in " + Fen.format(board));
            board.makeMove(found);
        }
        return board;
    }

    private static long hashOf(String fen) {
        return Fen.parse(fen).hash();
    }

    @Test
    void transpositionsReachTheSameHash() {
        Board a = play(Fen.STARTING_POSITION, "g1f3", "g8f6", "b1c3", "b8c6");
        Board b = play(Fen.STARTING_POSITION, "b1c3", "b8c6", "g1f3", "g8f6");
        assertEquals(Fen.format(a).split(" ")[0], Fen.format(b).split(" ")[0]);
        assertEquals(a.hash(), b.hash());
    }

    @Test
    void sideToMoveIsPartOfTheHash() {
        assertNotEquals(hashOf("4k3/8/8/8/8/8/8/4K3 w - - 0 1"), hashOf("4k3/8/8/8/8/8/8/4K3 b - - 0 1"));
    }

    @Test
    void castlingRightsArePartOfTheHash() {
        Set<Long> hashes = new HashSet<>();
        for (String rights : new String[] {"-", "K", "Q", "k", "q", "KQ", "kq", "KQkq"}) {
            hashes.add(hashOf("r3k2r/8/8/8/8/8/8/R3K2R w " + rights + " - 0 1"));
        }
        assertEquals(8, hashes.size());
    }

    @Test
    void clocksAreNotPartOfTheHash() {
        assertEquals(hashOf("4k3/8/8/8/8/8/8/4K3 w - - 0 1"), hashOf("4k3/8/8/8/8/8/8/4K3 w - - 37 80"));
    }

    @Test
    void enPassantSquareCountsOnlyWhenACaptureIsPossible() {
        // After 1.e4 no black pawn stands next to e4: the en passant square is irrelevant.
        Board afterE4 = play(Fen.STARTING_POSITION, "e2e4");
        assertEquals(Square.E3, afterE4.enPassantSquare());
        assertEquals(hashOf("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"), afterE4.hash());

        // With a black pawn on d4, ...dxe3 e.p. is possible: the hash must differ.
        String withCapture = "rnbqkbnr/ppp1pppp/8/8/3pP3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 3";
        String withoutCapture = "rnbqkbnr/ppp1pppp/8/8/3pP3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 3";
        assertNotEquals(hashOf(withCapture), hashOf(withoutCapture));
    }

    @Test
    void enPassantNeighbourCheckDoesNotWrapAroundTheBoard() {
        // Black just played ...a7a5. Shifting a5 one bit down lands on h4 (index 31), so without the file mask the
        // white pawn on h4 would wrongly count as a neighbour able to capture en passant.
        assertEquals(hashOf("4k3/8/8/p7/7P/8/8/4K3 w - - 0 2"), hashOf("4k3/8/8/p7/7P/8/8/4K3 w - a6 0 2"));
    }

    @Test
    void unmakeRestoresTheHash() {
        Board board = Board.startingPosition();
        long start = board.hash();
        Board played = play(Fen.STARTING_POSITION, "e2e4", "d7d5", "e4d5", "g8f6", "f1b5", "c7c6",
                "g1f3", "c6b5", "e1g1");
        assertNotEquals(start, played.hash());
        while (played.ply() > 0) {
            played.unmakeMove();
        }
        assertEquals(start, played.hash());
    }

    @Test
    void repetitionIsDetectedAfterShufflingBackAndForth() {
        Board board = play(Fen.STARTING_POSITION, "g1f3", "g8f6", "f3g1");
        assertFalse(board.isRepetition());
        board = play(Fen.STARTING_POSITION, "g1f3", "g8f6", "f3g1", "f6g8");
        assertTrue(board.isRepetition());
    }

    @Test
    void irreversibleMoveCutsRepetitionHistory() {
        // The pawn move resets the halfmove clock: positions before it can never come back.
        Board board = play(Fen.STARTING_POSITION, "g1f3", "g8f6", "f3g1", "f6g8", "e2e3");
        assertFalse(board.isRepetition());
    }

    @Test
    void fiftyMoveRule() {
        assertFalse(Fen.parse("4k3/8/8/8/8/8/8/4K2R w - - 99 80").isFiftyMoveDraw());
        assertTrue(Fen.parse("4k3/8/8/8/8/8/8/4K2R w - - 100 80").isFiftyMoveDraw());
    }
}
