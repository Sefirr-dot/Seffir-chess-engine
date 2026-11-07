package io.github.sefirrdot.chess.gui.arena;

import io.github.sefirrdot.chess.core.board.Color;
import io.github.sefirrdot.chess.core.board.Fen;
import io.github.sefirrdot.chess.core.game.Game;
import io.github.sefirrdot.chess.core.game.GameResult;
import io.github.sefirrdot.chess.core.game.GameResult.Reason;
import io.github.sefirrdot.chess.core.move.Move;
import io.github.sefirrdot.chess.core.movegen.MoveGenerator;
import io.github.sefirrdot.chess.core.notation.Pgn;
import io.github.sefirrdot.chess.gui.arena.Sprt.Verdict;
import io.github.sefirrdot.chess.gui.clock.TimeControl;
import io.github.sefirrdot.chess.gui.engine.EngineConfig;
import io.github.sefirrdot.chess.gui.engine.UciEngine;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * An engine-vs-engine match: games played concurrently, each between its own pair of engine processes, refereed with
 * chess clocks and the rules of {@link Game}.
 *
 * <p>Game {@code n} uses opening {@code n / 2} of the book; even games have engine A as White, odd games engine B, so
 * every opening is played from both sides. The match ends after {@code maxGames}, when the SPRT reaches a verdict, or
 * when stopped. All callbacks run on the slot threads.
 */
public final class Match {

    /**
     * @param engineA          engine under test
     * @param engineB          reference engine
     * @param timeControl      clock per game; when untimed, each move gets {@code moveTimeMillis}
     * @param moveTimeMillis   thinking time per move in untimed matches
     * @param concurrency      games played at the same time
     * @param maxGames         upper bound on games
     * @param sprt             stopping rule, or {@code null} for a fixed number of games
     * @param maxPlies         games longer than this are adjudicated drawn
     */
    public record Config(EngineConfig engineA, EngineConfig engineB, TimeControl timeControl, long moveTimeMillis,
                         int concurrency, int maxGames, Sprt sprt, int maxPlies) {
    }

    /** A game in progress or finished, as shown to listeners. Snapshots are independent copies. */
    public record GameView(int number, String white, String black, String opening, Game game,
                           long whiteMillis, long blackMillis) {
    }

    public interface Listener {
        void gameStarted(int slot, GameView view);

        void movePlayed(int slot, GameView view);

        void gameFinished(int slot, GameView view, MatchStats stats, double llr);

        void matchFinished(MatchStats stats, Verdict verdict, String reason);
    }

    /** Grace beyond a side's remaining time before a silent engine is declared lost on time. */
    private static final long TIMEOUT_GRACE_MILLIS = 1_000;

    private final Config config;
    private final Listener listener;
    private final OpeningBook book = OpeningBook.builtIn();
    private final AtomicInteger nextGame = new AtomicInteger();
    private final AtomicInteger runningSlots = new AtomicInteger();
    private final List<GameView> finished = new ArrayList<>();
    private final List<Thread> slots = new ArrayList<>();
    private volatile boolean stopped;
    private MatchStats stats = MatchStats.EMPTY;
    private Verdict verdict = Verdict.CONTINUE;
    private boolean reported;

    public Match(Config config, Listener listener) {
        this.config = config;
        this.listener = listener;
    }

    public void start() {
        runningSlots.set(config.concurrency());
        for (int slot = 0; slot < config.concurrency(); slot++) {
            int s = slot;
            var thread = new Thread(() -> runSlot(s), "arena-slot-" + slot);
            thread.setDaemon(true);
            slots.add(thread);
            thread.start();
        }
    }

    /** Ends the match: running games are abandoned (not counted) and engines are closed. */
    public void stop() {
        stopped = true;
    }

    /** Blocks until every slot has finished. */
    public void await() throws InterruptedException {
        for (Thread thread : slots) {
            thread.join();
        }
    }

    public synchronized MatchStats stats() {
        return stats;
    }

    public synchronized List<GameView> finishedGames() {
        return List.copyOf(finished);
    }

    /** All finished games as one PGN file, in the order they ended. */
    public synchronized String pgn() {
        var sb = new StringBuilder();
        for (GameView view : finished) {
            Map<String, String> tags = new LinkedHashMap<>();
            tags.put("Event", "Seffir arena");
            tags.put("Site", "Seffir desktop app");
            tags.put("Round", String.valueOf(view.number() + 1));
            tags.put("White", view.white());
            tags.put("Black", view.black());
            tags.put("Opening", view.opening());
            TimeControl tc = config.timeControl();
            tags.put("TimeControl", tc.isTimed() ? tc.baseMillis() / 1000.0 + "+" + tc.incrementMillis() / 1000.0 : "-");
            if (view.game().result().isOver()) {
                tags.put("Termination", view.game().result().reason().name().toLowerCase().replace('_', ' '));
            }
            sb.append(Pgn.write(view.game(), tags)).append('\n');
        }
        return sb.toString();
    }

    public static String nameA(Config config) {
        return config.engineA().name() + " (A)";
    }

    public static String nameB(Config config) {
        return config.engineB().name() + " (B)";
    }

    // --- Slot ---

    private void runSlot(int slot) {
        UciEngine a = null;
        UciEngine b = null;
        try {
            a = UciEngine.start(config.engineA());
            b = UciEngine.start(config.engineB());
            while (!stopped) {
                int number = nextGame.getAndIncrement();
                if (number >= config.maxGames()) {
                    break;
                }
                boolean aIsWhite = number % 2 == 0;
                GameView result = playGame(slot, number, aIsWhite ? a : b, aIsWhite ? b : a,
                        aIsWhite ? nameA(config) : nameB(config), aIsWhite ? nameB(config) : nameA(config));
                if (result != null) {
                    recordResult(slot, result, aIsWhite);
                }
                if (!a.isAlive()) {
                    a.close();
                    a = UciEngine.start(config.engineA());
                }
                if (!b.isAlive()) {
                    b.close();
                    b = UciEngine.start(config.engineB());
                }
            }
        } catch (IOException e) {
            finish("engine failed to start: " + e.getMessage());
            stopped = true;
        } finally {
            if (a != null) {
                a.close();
            }
            if (b != null) {
                b.close();
            }
            if (runningSlots.decrementAndGet() == 0) {
                finish(stopped && verdict == Verdict.CONTINUE ? "stopped" : "all games played");
            }
        }
    }

    /** @return the finished game, or null if the match was stopped meanwhile */
    private GameView playGame(int slot, int number, UciEngine white, UciEngine black, String whiteName,
                              String blackName) {
        OpeningBook.Opening opening = book.forPair(number / 2);
        Game game = OpeningBook.start(opening);
        TimeControl tc = config.timeControl();
        long[] remaining = {tc.baseMillis(), tc.baseMillis()};
        white.newGame();
        black.newGame();
        listener.gameStarted(slot, view(number, whiteName, blackName, opening, game, remaining));

        while (!game.result().isOver()) {
            if (stopped) {
                return null;
            }
            if (game.moves().size() >= config.maxPlies()) {
                game.declareResult(GameResult.draw(Reason.ADJUDICATION));
                break;
            }
            int side = game.sideToMove();
            UciEngine engine = side == Color.WHITE ? white : black;
            String go = tc.isTimed()
                    ? "wtime " + remaining[Color.WHITE] + " btime " + remaining[Color.BLACK]
                            + " winc " + tc.incrementMillis() + " binc " + tc.incrementMillis()
                    : "movetime " + config.moveTimeMillis();
            long budget = tc.isTimed() ? remaining[side] : config.moveTimeMillis();
            long start = System.nanoTime();
            String uci;
            try {
                uci = engine.go(positionCommand(game), go, info -> { })
                        .get(budget + TIMEOUT_GRACE_MILLIS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                engine.stop();
                game.flag(side);
                break;
            } catch (Exception e) {
                // Crashed or closed engine: it loses the game.
                game.declareResult(GameResult.win(side == Color.BLACK, Reason.ADJUDICATION));
                break;
            }
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            if (tc.isTimed()) {
                remaining[side] -= elapsed;
                if (remaining[side] < 0) {
                    remaining[side] = 0;
                    game.flag(side);
                    break;
                }
                remaining[side] += tc.incrementMillis();
            }
            int move = MoveGenerator.parseUciMove(game.position(), uci);
            if (move == Move.NONE || !game.isLegal(move)) {
                game.declareResult(GameResult.win(side == Color.BLACK, Reason.ADJUDICATION));
                break;
            }
            game.play(move);
            listener.movePlayed(slot, view(number, whiteName, blackName, opening, game, remaining));
        }
        return view(number, whiteName, blackName, opening, game, remaining);
    }

    private void recordResult(int slot, GameView view, boolean aIsWhite) {
        GameResult result = view.game().result();
        double whiteScore = switch (result.outcome()) {
            case WHITE_WINS -> 1;
            case BLACK_WINS -> 0;
            default -> 0.5;
        };
        double scoreForA = aIsWhite ? whiteScore : 1 - whiteScore;
        MatchStats now;
        double llr;
        boolean decided = false;
        synchronized (this) {
            if (stopped && verdict != Verdict.CONTINUE) {
                return;  // the SPRT already ended the match; late games do not count
            }
            stats = stats.plus(scoreForA);
            finished.add(view);
            now = stats;
            llr = config.sprt() == null ? 0 : config.sprt().llr(stats);
            if (config.sprt() != null && config.sprt().verdict(stats) != Verdict.CONTINUE) {
                verdict = config.sprt().verdict(stats);
                stopped = true;
                decided = true;
            }
        }
        listener.gameFinished(slot, view, now, llr);
        if (decided) {
            finish(verdict == Verdict.ACCEPT_H1 ? "SPRT: H1 accepted" : "SPRT: H0 accepted");
        }
    }

    private void finish(String reason) {
        MatchStats finalStats;
        Verdict finalVerdict;
        synchronized (this) {
            if (reported) {
                return;
            }
            reported = true;
            finalStats = stats;
            finalVerdict = verdict;
        }
        listener.matchFinished(finalStats, finalVerdict, reason);
    }

    private static GameView view(int number, String white, String black, OpeningBook.Opening opening, Game game,
                                 long[] remaining) {
        return new GameView(number, white, black, opening.name(), copy(game), remaining[Color.WHITE],
                remaining[Color.BLACK]);
    }

    /** Independent copy of a game, safe to hand to another thread. */
    static Game copy(Game game) {
        var copy = new Game(game.startFen());
        game.moves().forEach(copy::play);
        if (game.result().isOver() && !copy.result().isOver()) {
            copy.declareResult(game.result());
        }
        return copy;
    }

    private static String positionCommand(Game game) {
        String moves = game.moves().stream().map(Move::toUci).collect(Collectors.joining(" "));
        String start = game.startFen().equals(Fen.STARTING_POSITION) ? "startpos" : "fen " + game.startFen();
        return moves.isEmpty() ? start : start + " moves " + moves;
    }
}
