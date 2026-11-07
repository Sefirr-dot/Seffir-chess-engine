package io.github.sefirrdot.chess.core.notation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sefirrdot.chess.core.game.Game;
import io.github.sefirrdot.chess.core.game.GameResult.Outcome;
import io.github.sefirrdot.chess.core.game.GameResult.Reason;
import io.github.sefirrdot.chess.core.notation.Pgn.PgnGame;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class PgnTest {

    /** The Opera Game, Morphy vs Duke Karl / Count Isouard, Paris 1858, with comments, NAGs and a variation. */
    private static final String OPERA = """
            [Event "Paris"]
            [Site "Paris FRA"]
            [Date "1858.??.??"]
            [Round "?"]
            [White "Paul Morphy"]
            [Black "Duke Karl / Count Isouard"]
            [Result "1-0"]

            1. e4 e5 2. Nf3 d6 3. d4 Bg4 $6 {This is a weak move already.} 4. dxe5 Bxf3
            5. Qxf3 dxe5 6. Bc4 Nf6 7. Qb3 Qe7 8. Nc3 c6 9. Bg5 b5 10. Nxb5 cxb5
            11. Bxb5+ Nbd7 12. O-O-O Rd8 13. Rxd7 Rxd7 (13... Nxd7 14. Qb3 {also wins}) 14. Rd1 Qe6
            15. Bxd7+ Nxd7 16. Qb8+ Nxb8 17. Rd8# 1-0
            """;

    @Test
    void readsTheOperaGame() {
        PgnGame pgn = Pgn.readFirst(OPERA);
        assertEquals("Paul Morphy", pgn.tags().get("White"));
        Game game = pgn.game();
        assertEquals(33, game.moves().size());
        assertEquals(Outcome.WHITE_WINS, game.result().outcome());
        assertEquals(Reason.CHECKMATE, game.result().reason(), "the final position is mate, not just a tag");
    }

    @Test
    void writeThenReadRoundTrips() {
        Game original = Pgn.readFirst(OPERA).game();
        String text = Pgn.write(original, Map.of("White", "Paul Morphy", "Black", "Allies", "Event", "Paris"));
        assertTrue(text.startsWith("[Event \"Paris\"]\n[Site \"?\"]"), text);
        assertTrue(text.contains("[Result \"1-0\"]"));
        assertTrue(text.contains("12. O-O-O Rd8"), text);
        assertTrue(text.strip().endsWith("17. Rd8# 1-0"), text);
        assertTrue(text.lines().allMatch(line -> line.length() <= 80), "movetext wrapped at 80 columns");

        PgnGame again = Pgn.readFirst(text);
        assertEquals(original.moves(), again.game().moves());
        assertEquals("Allies", again.tags().get("Black"));
    }

    @Test
    void customStartPositionWritesSetUpAndFenAndBlackMoveNumbers() {
        String fen = "4k3/8/8/8/8/8/4P3/4K3 b - - 0 40";
        Game game = new Game(fen);
        game.play("e8d7");
        game.play("e2e4");
        String text = Pgn.write(game, Map.of());
        assertTrue(text.contains("[SetUp \"1\"]"));
        assertTrue(text.contains("[FEN \"" + fen + "\"]"));
        assertTrue(text.contains("40... Kd7 41. e4 *"), text);
        assertEquals(game.fen(), Pgn.readFirst(text).game().fen());
    }

    @Test
    void readsSeveralGamesAndDeclaresUnexplainedResults() {
        String two = """
                [White "A"]
                [Result "0-1"]

                1. e4 e5 2. Qh5 Nc6 0-1

                [White "B"]
                [Result "1/2-1/2"]

                1.d4 d5 2.c4 1/2-1/2
                """;
        List<PgnGame> games = Pgn.read(two);
        assertEquals(2, games.size());
        assertEquals(Outcome.BLACK_WINS, games.get(0).game().result().outcome());
        assertEquals(Reason.NONE, games.get(0).game().result().reason(), "a resignation: nothing on the board");
        assertEquals("B", games.get(1).tags().get("White"));
        assertEquals(3, games.get(1).game().moves().size(), "compact '1.d4' move numbers");
        assertEquals(Outcome.DRAW, games.get(1).game().result().outcome());
    }

    @Test
    void movetextOnlyAndOngoingGames() {
        Game game = Pgn.readFirst("1. e4 c5 2. Nf3 *").game();
        assertEquals(3, game.moves().size());
        assertFalse(game.result().isOver());
    }

    @Test
    @Timeout(2)
    void toleratesNestedVariationsLineCommentsAndStrayParentheses() {
        Game game = Pgn.readFirst("""
                1. e4 ; a line comment
                1... e5 (1... c5 2. Nf3 (2. c3 {Alapin}) d6) 2. Nf3 ) 2... Nc6 *
                """).game();
        assertEquals(4, game.moves().size());
    }

    @Test
    void escapedQuotesInTags() {
        Game game = new Game();
        String text = Pgn.write(game, Map.of("Event", "The \"Big\" One"));
        assertTrue(text.contains("[Event \"The \\\"Big\\\" One\"]"), text);
        assertEquals("The \"Big\" One", Pgn.readFirst(text).tags().get("Event"));
    }

    @Test
    void illegalMoveIsReported() {
        assertThrows(IllegalArgumentException.class, () -> Pgn.readFirst("1. e4 e5 2. Ke3 *"));
        assertThrows(IllegalArgumentException.class, () -> Pgn.readFirst("   "));
    }
}
