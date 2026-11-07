package io.github.sefirrdot.chess.uci;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sefirrdot.chess.core.EngineInfo;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

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
                List.of("id name " + EngineInfo.fullName(), "id author " + EngineInfo.AUTHOR, "uciok"),
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
}
