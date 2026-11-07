package io.github.sefirrdot.chess.uci;

import io.github.sefirrdot.chess.core.EngineInfo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;

/**
 * Line-based UCI command loop.
 *
 * <p>Only the handshake is implemented for now. {@code go} answers with the UCI null move
 * ({@code 0000}) until move generation and search exist.
 *
 * @see <a href="https://www.wbec-ridderkerk.nl/html/UCIProtocol.html">UCI protocol</a>
 */
public final class UciProtocol {

    private final BufferedReader in;
    private final PrintStream out;

    public UciProtocol(BufferedReader in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    /** Reads commands until {@code quit} or end of input. */
    public void run() throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            if (!handle(line.strip())) {
                return;
            }
        }
    }

    /** @return {@code false} when the loop must stop */
    boolean handle(String line) {
        String command = line.split("\\s+", 2)[0];
        switch (command) {
            case "uci" -> {
                send("id name " + EngineInfo.fullName());
                send("id author " + EngineInfo.AUTHOR);
                send("uciok");
            }
            case "isready" -> send("readyok");
            case "go" -> send("bestmove 0000");
            case "quit" -> {
                return false;
            }
            default -> {
                // ucinewgame, position, stop... are accepted silently; unknown commands are ignored per spec.
            }
        }
        return true;
    }

    private void send(String message) {
        out.println(message);
        out.flush();
    }
}
