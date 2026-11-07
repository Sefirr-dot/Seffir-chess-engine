package io.github.sefirrdot.chess.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sefirrdot.chess.core.board.Fen;
import io.github.sefirrdot.chess.core.game.GameResult.Outcome;
import io.github.sefirrdot.chess.core.game.GameResult.Reason;
import io.github.sefirrdot.chess.core.move.Move;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GameTest {

    private static Game playing(String fen, String... uciMoves) {
        var game = new Game(fen);
        for (String move : uciMoves) {
            game.play(move);
        }
        return game;
    }

    @Test
    void newGameIsOngoingWithTwentyMoves() {
        var game = new Game();
        assertEquals(GameResult.ONGOING, game.result());
        assertEquals(20, game.legalMoves().size());
        assertEquals(Move.NONE, game.lastMove());
    }

    @Test
    void foolsMateIsCheckmateForBlack() {
        Game game = playing(Fen.STARTING_POSITION, "f2f3", "e7e5", "g2g4", "d8h4");
        assertEquals(new GameResult(Outcome.BLACK_WINS, Reason.CHECKMATE), game.result());
        assertEquals("0-1", game.result().outcome().pgn());
        assertTrue(game.legalMoves().isEmpty());
    }

    @Test
    void stalemate() {
        assertEquals(GameResult.draw(Reason.STALEMATE), new Game("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1").result());
    }

    @Test
    void threefoldRepetitionNeedsThreeOccurrences() {
        Game game = playing(Fen.STARTING_POSITION, "g1f3", "g8f6", "f3g1", "f6g8");
        assertEquals(2, game.repetitionCount());
        assertFalse(game.result().isOver());
        game.play("g1f3");
        game.play("g8f6");
        game.play("f3g1");
        game.play("f6g8");
        assertEquals(3, game.repetitionCount());
        assertEquals(GameResult.draw(Reason.THREEFOLD_REPETITION), game.result());
    }

    @Test
    void fiftyMoveRule() {
        Game game = new Game("4k3/8/8/8/8/8/8/R3K3 w - - 99 80");
        assertFalse(game.result().isOver());
        game.play("a1a2");
        assertEquals(GameResult.draw(Reason.FIFTY_MOVE_RULE), game.result());
    }

    @Test
    void checkmateOnTheHundredthPlyBeatsTheFiftyMoveRule() {
        // Ra8# is played with the clock reaching 100: mate takes precedence.
        Game game = new Game("6k1/5ppp/8/8/8/8/8/R5K1 w - - 99 80");
        game.play("a1a8");
        assertEquals(Reason.CHECKMATE, game.result().reason());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "4k3/8/8/8/8/8/8/4K3 w - - 0 1",        // K v K
        "4k3/8/8/8/8/8/8/4KB2 w - - 0 1",       // K+B v K
        "4k3/8/8/8/8/8/8/4KN2 w - - 0 1",       // K+N v K
        "4kb2/8/8/8/8/8/8/2B1K3 w - - 0 1",     // bishops on same-coloured squares (c1 and f8 are both dark)
    })
    void insufficientMaterial(String fen) {
        assertEquals(GameResult.draw(Reason.INSUFFICIENT_MATERIAL), new Game(fen).result());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1",      // a pawn can promote
        "4k3/8/8/8/8/8/8/3NKN2 w - - 0 1",      // K+N+N v K: mate possible
        "4kb2/8/8/8/8/8/8/3BK3 w - - 0 1",      // bishops on opposite colours (d1 light, f8 dark)
        "4k3/8/8/8/8/8/8/3NKB2 w - - 0 1",      // K+B+N v K
    })
    void sufficientMaterial(String fen) {
        assertFalse(new Game(fen).result().isOver());
    }

    @Test
    void rejectsIllegalMovesAndMovesAfterTheEnd() {
        var game = new Game();
        assertThrows(IllegalArgumentException.class, () -> game.play("e2e5"));
        Game mated = playing(Fen.STARTING_POSITION, "f2f3", "e7e5", "g2g4", "d8h4");
        assertThrows(IllegalArgumentException.class, () -> mated.play("a2a3"));
    }

    @Test
    void undoRestoresPositionAndClearsDeclaredResult() {
        Game game = playing(Fen.STARTING_POSITION, "e2e4", "e7e5");
        game.declareResult(GameResult.win(true, Reason.TIMEOUT));
        assertEquals(Reason.TIMEOUT, game.result().reason());
        game.undo();
        assertEquals(GameResult.ONGOING, game.result());
        assertEquals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1", game.fen());
        game.undo();
        assertEquals(Fen.STARTING_POSITION, game.fen());
        assertThrows(IllegalStateException.class, game::undo);
    }

    @Test
    void positionIsADefensiveCopy() {
        var game = new Game();
        game.position().makeMove(game.legalMoves().get(0));
        assertEquals(Fen.STARTING_POSITION, game.fen());
    }
}
