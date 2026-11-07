package io.github.sefirrdot.chess.uci;

import io.github.sefirrdot.chess.core.EngineInfo;
import io.github.sefirrdot.chess.core.board.Board;
import io.github.sefirrdot.chess.core.board.Fen;
import io.github.sefirrdot.chess.core.board.InvalidFenException;
import io.github.sefirrdot.chess.core.move.Move;
import io.github.sefirrdot.chess.core.movegen.MoveGenerator;
import io.github.sefirrdot.chess.core.search.Search;
import io.github.sefirrdot.chess.core.search.SearchInfo;
import io.github.sefirrdot.chess.core.search.SearchLimits;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

/**
 * UCI command loop.
 *
 * <p>Commands are read on the calling thread; {@code go} starts the search on a separate thread so that {@code stop},
 * {@code isready} and {@code quit} are handled while the engine thinks. Supported: {@code uci}, {@code isready},
 * {@code ucinewgame}, {@code position}, {@code go}, {@code stop}, {@code quit}, plus the non-standard {@code d}
 * (print the board) for debugging. Unknown commands are ignored, as the protocol requires.
 *
 * @see <a href="https://www.wbec-ridderkerk.nl/html/UCIProtocol.html">UCI protocol</a>
 */
public final class UciProtocol {

    private final BufferedReader in;
    private final PrintStream out;

    private Board board = Board.startingPosition();
    private Search search;
    private Thread searchThread;
    private SearchLimits searchLimits;
    /** Released by {@code stop}: an infinite search must not report its best move before being told to stop. */
    private CountDownLatch stopSignal = new CountDownLatch(0);

    public UciProtocol(BufferedReader in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    /**
     * Reads commands until {@code quit} or end of input. At end of input a running search is allowed to finish
     * (unless it is infinite), so that piping {@code "position ...\ngo depth 8"} into the engine prints the result.
     */
    public void run() throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            if (!handle(line.strip())) {
                return;
            }
        }
        if (searchLimits != null && searchLimits.infinite()) {
            stopSearch();
        }
        waitForSearch();
    }

    /** @return {@code false} when the loop must stop */
    boolean handle(String line) {
        String[] tokens = line.split("\\s+");
        switch (tokens[0]) {
            case "uci" -> {
                send("id name " + EngineInfo.fullName());
                send("id author " + EngineInfo.AUTHOR);
                send("uciok");
            }
            case "isready" -> send("readyok");
            case "ucinewgame" -> {
                stopSearch();
                board = Board.startingPosition();
            }
            case "position" -> {
                stopSearch();
                position(tokens);
            }
            case "go" -> go(tokens);
            case "stop" -> stopSearch();
            case "d" -> send(board.toString());
            case "quit" -> {
                stopSearch();
                return false;
            }
            default -> {
                // Unknown commands and empty lines are ignored.
            }
        }
        return true;
    }

    /** {@code position startpos [moves m1 m2 ...]} or {@code position fen <fen> [moves m1 m2 ...]}. */
    private void position(String[] tokens) {
        int movesIndex = Arrays.asList(tokens).indexOf("moves");
        int end = movesIndex >= 0 ? movesIndex : tokens.length;
        Board next;
        try {
            if (tokens.length > 1 && tokens[1].equals("startpos")) {
                next = Board.startingPosition();
            } else if (tokens.length > 2 && tokens[1].equals("fen")) {
                next = Fen.parse(String.join(" ", Arrays.copyOfRange(tokens, 2, end)));
            } else {
                send("info string invalid position command");
                return;
            }
        } catch (InvalidFenException e) {
            send("info string " + e.getMessage());
            return;
        }
        if (movesIndex >= 0) {
            for (int i = movesIndex + 1; i < tokens.length; i++) {
                int move = MoveGenerator.parseUciMove(next, tokens[i]);
                if (move == Move.NONE) {
                    send("info string illegal move " + tokens[i] + ", ignoring the rest of the move list");
                    break;
                }
                next.makeMove(move);
            }
        }
        board = next;
    }

    private void go(String[] tokens) {
        stopSearch();
        SearchLimits limits = parseGo(tokens);
        Search current = new Search(board, this::sendInfo);
        CountDownLatch latch = new CountDownLatch(limits.infinite() ? 1 : 0);
        search = current;
        searchLimits = limits;
        stopSignal = latch;
        searchThread = new Thread(() -> {
            SearchInfo result = current.search(limits);
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            send("bestmove " + Move.toUci(result.bestMove()));
        }, "search");
        searchThread.setDaemon(true);
        searchThread.start();
    }

    static SearchLimits parseGo(String[] tokens) {
        int depth = 0;
        int movesToGo = 0;
        long nodes = 0;
        long moveTime = 0;
        long wtime = 0;
        long btime = 0;
        long winc = 0;
        long binc = 0;
        boolean infinite = false;
        boolean clockGiven = false;
        for (int i = 1; i < tokens.length; i++) {
            String token = tokens[i];
            boolean hasValue = i + 1 < tokens.length;
            switch (token) {
                case "infinite" -> infinite = true;
                case "depth" -> depth = hasValue ? (int) parseLong(tokens[++i]) : 0;
                case "nodes" -> nodes = hasValue ? parseLong(tokens[++i]) : 0;
                case "movetime" -> moveTime = hasValue ? parseLong(tokens[++i]) : 0;
                case "wtime" -> {
                    wtime = hasValue ? parseLong(tokens[++i]) : 0;
                    clockGiven = true;
                }
                case "btime" -> {
                    btime = hasValue ? parseLong(tokens[++i]) : 0;
                    clockGiven = true;
                }
                case "winc" -> winc = hasValue ? parseLong(tokens[++i]) : 0;
                case "binc" -> binc = hasValue ? parseLong(tokens[++i]) : 0;
                case "movestogo" -> movesToGo = hasValue ? (int) parseLong(tokens[++i]) : 0;
                default -> {
                    // ponder, searchmoves, mate: not supported yet.
                }
            }
        }
        if (clockGiven) {
            // GUIs may send zero or negative times in severe time trouble. That must mean "almost no time",
            // not "no clock" (which would let the search run unbounded).
            wtime = Math.max(wtime, 1);
            btime = Math.max(btime, 1);
        }
        return new SearchLimits(depth, nodes, moveTime, wtime, btime, winc, binc, movesToGo, infinite);
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void sendInfo(SearchInfo info) {
        send("info depth " + info.depth() + " score " + info.uciScore() + " nodes " + info.nodes()
                + " nps " + info.nodesPerSecond() + " time " + info.millis() + " pv " + info.uciPv());
    }

    private void stopSearch() {
        if (search != null) {
            search.stop();
            stopSignal.countDown();
            waitForSearch();
        }
    }

    private void waitForSearch() {
        if (searchThread != null) {
            try {
                searchThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            searchThread = null;
            search = null;
            searchLimits = null;
        }
    }

    private void send(String message) {
        synchronized (out) {
            out.println(message);
            out.flush();
        }
    }
}
