package io.github.sefirrdot.chess.core.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sefirrdot.chess.core.board.Board;
import io.github.sefirrdot.chess.core.board.Fen;
import io.github.sefirrdot.chess.core.move.Move;
import io.github.sefirrdot.chess.core.move.MoveList;
import io.github.sefirrdot.chess.core.movegen.MoveGenerator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SearchTest {

    private static SearchInfo search(String fen, int depth) {
        return new Search(Fen.parse(fen)).search(SearchLimits.ofDepth(depth));
    }

    @ParameterizedTest(name = "{0}: {2}")
    @CsvSource(delimiter = '|', value = {
        "back rank      | 6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1                                   | a1a8",
        "scholar's mate | r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4  | h5f7",
        "fool's mate    | rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq - 0 2      | d8h4",
    })
    void findsMateInOne(String name, String fen, String expected) {
        SearchInfo result = search(fen.trim(), 3);
        assertEquals(expected.trim(), Move.toUci(result.bestMove()));
        assertEquals("mate 1", result.uciScore());
    }

    @Test
    void findsMateInTwoWithTwoRooks() {
        // Rook ladder: 1.Ra7 (or Rb7) and 2.Rb8# / Ra8#.
        SearchInfo result = search("7k/8/8/8/8/8/R7/1R5K w - - 0 1", 4);
        assertEquals("mate 2", result.uciScore());
        assertEquals(3, result.pv().length);
        Board board = Fen.parse("7k/8/8/8/8/8/R7/1R5K w - - 0 1");
        for (int move : result.pv()) {
            board.makeMove(move);
        }
        assertTrue(MoveGenerator.isInCheck(board));
        var replies = new MoveList();
        MoveGenerator.generateLegal(board, replies);
        assertEquals(0, replies.size(), "PV should end in checkmate");
    }

    @Test
    void reportsBeingMated() {
        // Black's only move is ...Kb8, then Rh8# (the b6 king covers a7, b7 and c7).
        SearchInfo result = search("k7/8/1K6/8/8/8/8/7R b - - 0 1", 4);
        assertEquals("a8b8", Move.toUci(result.bestMove()));
        assertEquals("mate -1", result.uciScore());
    }

    @Test
    void capturesAHangingQueen() {
        assertEquals("d1d5", Move.toUci(search("4k3/8/8/3q4/8/8/8/3QK3 w - - 0 1", 3).bestMove()));
    }

    @Test
    void doesNotGrabAPoisonedPawn() {
        // Qxb7?? loses the queen to ...Rxb7. Quiescence must see the recapture.
        SearchInfo result = search("1r2k3/1p6/8/8/8/8/8/1Q2K3 w - - 0 1", 2);
        assertNotEquals("b1b7", Move.toUci(result.bestMove()));
    }

    @Test
    void stalemateAndCheckmateAtTheRoot() {
        SearchInfo stalemate = search("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1", 5);
        assertEquals(Move.NONE, stalemate.bestMove());
        assertEquals(0, stalemate.score());

        SearchInfo mated = search("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3", 5);
        assertEquals(Move.NONE, mated.bestMove());
        assertEquals(-Search.MATE, mated.score());
    }

    @Test
    void losingSideTakesTheDrawByRepetition() {
        // Black is a queen up. After Kg1 Qa6 Kh1 Qa5 we are back at the start; playing Kg1 again repeats the position
        // reached after White's first move, which the search scores as a draw: far better than any other move.
        Board board = Fen.parse("k7/8/8/q7/8/8/8/7K w - - 0 1");
        for (String uci : new String[] {"h1g1", "a5a6", "g1h1", "a6a5"}) {
            board.makeMove(MoveGenerator.parseUciMove(board, uci));
        }
        SearchInfo result = new Search(board).search(SearchLimits.ofDepth(4));
        assertEquals("h1g1", Move.toUci(result.bestMove()));
        assertEquals(0, result.score());
    }

    @Test
    void iterativeDeepeningReportsEveryDepth() {
        List<SearchInfo> infos = new ArrayList<>();
        new Search(Board.startingPosition(), infos::add).search(SearchLimits.ofDepth(4));
        assertEquals(4, infos.size());
        for (int i = 0; i < infos.size(); i++) {
            SearchInfo info = infos.get(i);
            assertEquals(i + 1, info.depth());
            assertTrue(info.pv().length >= 1);
            assertTrue(Math.abs(info.score()) < 100, "opening should look balanced: " + info);
        }
    }

    @Test
    void respectsNodeLimit() {
        SearchInfo result = new Search(Board.startingPosition()).search(SearchLimits.NONE.withNodes(5_000));
        assertTrue(result.nodes() <= 5_000);
        assertNotEquals(Move.NONE, result.bestMove());
    }

    @Test
    @Timeout(5)
    void respectsMoveTime() {
        long start = System.nanoTime();
        SearchInfo result = new Search(Board.startingPosition()).search(SearchLimits.ofMoveTime(200));
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertTrue(millis < 400, "took " + millis + " ms");
        assertNotEquals(Move.NONE, result.bestMove());
    }

    @Test
    @Timeout(5)
    void stopEndsAnInfiniteSearch() throws InterruptedException {
        var search = new Search(Board.startingPosition());
        var result = new SearchInfo[1];
        Thread thread = new Thread(() -> result[0] = search.search(SearchLimits.infiniteSearch()));
        thread.start();
        Thread.sleep(150);
        search.stop();
        thread.join();
        assertNotEquals(Move.NONE, result[0].bestMove());
    }

    @Test
    void searchDoesNotModifyTheCallersBoard() {
        Board board = Fen.parse("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");
        String before = Fen.format(board);
        new Search(board).search(SearchLimits.ofDepth(3));
        assertEquals(before, Fen.format(board));
    }
}
