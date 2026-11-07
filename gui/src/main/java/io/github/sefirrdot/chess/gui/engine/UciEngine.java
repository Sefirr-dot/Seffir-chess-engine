package io.github.sefirrdot.chess.gui.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A UCI engine running as a child process.
 *
 * <p>A reader thread consumes the engine's output: {@code uciok}/{@code readyok} complete the handshake futures,
 * {@code info} lines go to the search's listener and {@code bestmove} completes the search's future. Callbacks run on
 * that reader thread; UI code must hop to the FX thread itself.
 *
 * <p>Only one search runs at a time. {@link #go} supersedes any search still running: it is stopped and its result
 * discarded.
 */
public final class UciEngine implements AutoCloseable {

    private final EngineConfig config;
    private final Process process;
    private final PrintWriter toEngine;
    private final Thread reader;

    private volatile String engineName;
    private final CompletableFuture<Void> uciOk = new CompletableFuture<>();
    private CompletableFuture<Void> readyOk = new CompletableFuture<>();

    private final Object searchLock = new Object();
    private CompletableFuture<String> bestMove;
    private Consumer<UciInfo> infoListener;
    /** bestmove lines still owed by searches we stopped and abandoned; they must not complete the current search. */
    private int abandonedSearches;

    private UciEngine(EngineConfig config, Process process) {
        this.config = config;
        this.process = process;
        this.engineName = config.name();
        this.toEngine = new PrintWriter(process.getOutputStream(), true, StandardCharsets.UTF_8);
        this.reader = new Thread(this::readLoop, "uci-" + config.name());
        reader.setDaemon(true);
        reader.start();
    }

    /** Starts the process and completes the {@code uci}/{@code isready} handshake. */
    public static UciEngine start(EngineConfig config) throws IOException {
        var builder = new ProcessBuilder(config.command());
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        var engine = new UciEngine(config, builder.start());
        try {
            engine.send("uci");
            engine.uciOk.get(10, TimeUnit.SECONDS);
            engine.isReady().get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            engine.close();
            throw new IOException("Engine " + config.name() + " did not complete the UCI handshake", e);
        }
        return engine;
    }

    /** Name reported by the engine ({@code id name}), or the configured one. */
    public String name() {
        return engineName;
    }

    public EngineConfig config() {
        return config;
    }

    public CompletableFuture<Void> isReady() {
        synchronized (this) {
            readyOk = new CompletableFuture<>();
        }
        send("isready");
        return readyOk;
    }

    public void newGame() {
        stop();
        send("ucinewgame");
    }

    /**
     * Starts a search.
     *
     * @param position   {@code startpos} or {@code fen <fen>}, optionally followed by {@code moves ...}
     * @param goArgs     arguments after {@code go}, e.g. {@code wtime 60000 btime 60000 winc 0 binc 0}
     * @param onInfo     receives every parsed info line of this search (reader thread)
     * @return completes with the best move in UCI notation ({@code 0000} if the engine has none)
     */
    public CompletableFuture<String> go(String position, String goArgs, Consumer<UciInfo> onInfo) {
        synchronized (searchLock) {
            if (bestMove != null && !bestMove.isDone()) {
                abandonedSearches++;
                send("stop");
                bestMove.cancel(false);
            }
            bestMove = new CompletableFuture<>();
            infoListener = onInfo;
            send("position " + position);
            send("go " + goArgs);
            return bestMove;
        }
    }

    /** Asks the running search, if any, to stop; its bestmove still completes the future returned by {@link #go}. */
    public void stop() {
        synchronized (searchLock) {
            if (bestMove != null && !bestMove.isDone()) {
                send("stop");
            }
        }
    }

    /** Sends {@code quit}, then kills the process if it does not exit promptly. */
    @Override
    public void close() {
        try {
            send("quit");
            if (!process.waitFor(1, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
        synchronized (searchLock) {
            if (bestMove != null) {
                bestMove.cancel(false);
            }
        }
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    private void send(String command) {
        toEngine.println(command);
    }

    private void readLoop() {
        try (var in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                handle(line.strip());
            }
        } catch (IOException e) {
            // Process gone; fall through.
        }
        uciOk.completeExceptionally(new IOException("Engine exited"));
        synchronized (this) {
            readyOk.completeExceptionally(new IOException("Engine exited"));
        }
        synchronized (searchLock) {
            if (bestMove != null) {
                bestMove.completeExceptionally(new IOException("Engine exited"));
            }
        }
    }

    private void handle(String line) {
        if (line.startsWith("id name ")) {
            engineName = line.substring("id name ".length());
        } else if (line.equals("uciok")) {
            uciOk.complete(null);
        } else if (line.equals("readyok")) {
            synchronized (this) {
                readyOk.complete(null);
            }
        } else if (line.startsWith("info ")) {
            Consumer<UciInfo> listener;
            synchronized (searchLock) {
                listener = abandonedSearches == 0 ? infoListener : null;
            }
            if (listener != null) {
                UciInfo.parse(line).ifPresent(listener);
            }
        } else if (line.startsWith("bestmove")) {
            List<String> tokens = List.of(line.split("\\s+"));
            String move = tokens.size() > 1 ? tokens.get(1) : "0000";
            synchronized (searchLock) {
                if (abandonedSearches > 0) {
                    abandonedSearches--;
                } else if (bestMove != null) {
                    bestMove.complete(move);
                }
            }
        }
    }
}
