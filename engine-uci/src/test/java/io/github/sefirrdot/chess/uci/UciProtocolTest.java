package io.github.sefirrdot.chess.uci;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sefirrdot.chess.core.EngineInfo;
import io.github.sefirrdot.chess.core.board.Board;
import io.github.sefirrdot.chess.core.board.Fen;
import io.github.sefirrdot.chess.core.move.Move;
import io.github.sefirrdot.chess.core.movegen.MoveGenerator;
import io.github.sefirrdot.chess.core.search.SearchLimits;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class UciProtocolTest {

    private static List<String> session(String input) throws IOException {
        var buffer = new ByteArrayOutputStream();
        var out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        new UciProtocol(new BufferedReader(new StringReader(input)), out).run();
        return buffer.toString(StandardCharsets.UTF_8).lines().toList();
    }

    @Test
    void uciHandshakeIdentifiesEngine() throws IOException {
        assertEquals(
                List.of("id name " + EngineInfo.fullName(), "id author " + EngineInfo.AUTHOR,
                        "option name Hash type spin default 64 min 1 max 1024", "uciok"),
                session("uci\n"));
    }

    @Test
    void isReadyAnswersReadyOk() throws IOException {
        assertEquals(List.of("readyok"), session("isready\n"));
    }

    @Test
    void quitStopsProcessingFurtherCommands() throws IOException {
        assertTrue(session("quit\nisready\n").isEmpty());
    }

    @Test
    void unknownCommandsAreIgnored() throws IOException {
        assertEquals(List.of("readyok"), session("foo bar\nisready\n"));
    }

    private static String bestMove(List<String> output) {
        List<String> bestMoves = output.stream().filter(l -> l.startsWith("bestmove ")).toList();
        assertEquals(1, bestMoves.size(), "exactly one bestmove expected: " + output);
        return bestMoves.get(0).substring("bestmove ".length());
    }

    @Test
    void goReportsInfoLinesAndALegalBestMove() throws IOException {
        List<String> output = session("position startpos moves e2e4 e7e5\ngo depth 3\n");
        assertTrue(output.stream().anyMatch(l -> l.startsWith("info depth 3 score cp ")), output.toString());
        String move = bestMove(output);
        var board = Board.startingPosition();
        board.makeMove(MoveGenerator.parseUciMove(board, "e2e4"));
        board.makeMove(MoveGenerator.parseUciMove(board, "e7e5"));
        assertNotEquals(Move.NONE, MoveGenerator.parseUciMove(board, move), "illegal bestmove " + move);
    }

    @Test
    void findsMateFromAFenPosition() throws IOException {
        List<String> output = session("position fen 6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1\ngo depth 3\n");
        assertEquals("a1a8", bestMove(output));
        assertTrue(output.stream().anyMatch(l -> l.contains("score mate 1")), output.toString());
    }

    @Test
    void positionAppliesMovesFromAFen() throws IOException {
        List<String> output = session("position fen 4k3/8/8/8/8/8/8/4K2R w K - 0 1 moves e1g1\nd\n");
        assertTrue(output.stream().anyMatch(l -> l.equals("4k3/8/8/8/8/8/8/5RK1 b - - 1 1")), output.toString());
    }

    @Test
    void illegalMoveInPositionIsReportedAndSkipped() throws IOException {
        List<String> output = session("position startpos moves e2e5\nd\n");
        assertTrue(output.get(0).startsWith("info string illegal move e2e5"), output.toString());
        assertTrue(output.contains(Fen.STARTING_POSITION));
    }

    @Test
    void invalidFenIsReported() throws IOException {
        List<String> output = session("position fen not a fen at all x\n");
        assertTrue(output.get(0).startsWith("info string Invalid FEN"), output.toString());
    }

    @Test
    @Timeout(5)
    void infiniteSearchWaitsForStop() throws Exception {
        var toEngine = new PipedWriter();
        var buffer = new ByteArrayOutputStream();
        var out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        var protocol = new UciProtocol(new BufferedReader(new PipedReader(toEngine)), out);
        Thread engine = new Thread(() -> {
            try {
                protocol.run();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        engine.start();

        toEngine.write("go infinite\n");
        toEngine.flush();
        Thread.sleep(300);
        assertFalse(buffer.toString(StandardCharsets.UTF_8).contains("bestmove"), "bestmove before stop");

        toEngine.write("stop\nquit\n");
        toEngine.flush();
        engine.join();
        bestMove(buffer.toString(StandardCharsets.UTF_8).lines().toList());
    }

    @Test
    void parsesGoParameters() {
        SearchLimits limits = UciProtocol.parseGo(
                "go wtime 60000 btime 55000 winc 1000 binc 900 movestogo 20 depth 12 nodes 5000".split(" "));
        assertEquals(60_000, limits.whiteTime());
        assertEquals(55_000, limits.blackTime());
        assertEquals(1_000, limits.whiteIncrement());
        assertEquals(900, limits.blackIncrement());
        assertEquals(20, limits.movesToGo());
        assertEquals(12, limits.depth());
        assertEquals(5_000, limits.nodes());
        assertFalse(limits.infinite());
        assertTrue(UciProtocol.parseGo("go infinite".split(" ")).infinite());
        assertEquals(1_500, UciProtocol.parseGo("go movetime 1500".split(" ")).moveTime());
    }

    @Test
    void hashOptionAndNewGameAreAcceptedAndSearchStillWorks() throws IOException {
        List<String> output = session("""
                setoption name Hash value 16
                setoption name Hash value lots
                ucinewgame
                position fen 6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1
                go depth 3
                """);
        assertTrue(output.contains("info string invalid Hash value lots"), output.toString());
        assertEquals("a1a8", bestMove(output));
    }

    @Test
    void nonPositiveClockStillCountsAsAClock() {
        SearchLimits limits = UciProtocol.parseGo("go wtime -200 btime 0".split(" "));
        assertTrue(limits.hasClock(), "a flagged clock must not turn into unlimited time");
        assertEquals(1, limits.whiteTime());
    }
}
