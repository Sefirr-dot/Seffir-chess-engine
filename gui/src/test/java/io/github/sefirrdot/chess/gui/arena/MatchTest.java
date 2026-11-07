package io.github.sefirrdot.chess.gui.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sefirrdot.chess.core.notation.Pgn;
import io.github.sefirrdot.chess.gui.arena.Match.GameView;
import io.github.sefirrdot.chess.gui.arena.Sprt.Verdict;
import io.github.sefirrdot.chess.gui.clock.TimeControl;
import io.github.sefirrdot.chess.gui.engine.EngineConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Real matches between two built-in engine processes, kept short with tiny clocks and a ply cap. */
@Timeout(120)
class MatchTest {

    private static final class Recorder implements Match.Listener {
        final AtomicInteger started = new AtomicInteger();
        final AtomicInteger moves = new AtomicInteger();
        final List<GameView> finished = new CopyOnWriteArrayList<>();
        final Map<Integer, String> openings = new ConcurrentHashMap<>();
        final AtomicReference<String> reason = new AtomicReference<>();
        final CountDownLatch done = new CountDownLatch(1);

        @Override
        public void gameStarted(int slot, GameView view) {
            started.incrementAndGet();
            openings.put(view.number(), view.opening());
        }

        @Override
        public void movePlayed(int slot, GameView view) {
            moves.incrementAndGet();
        }

        @Override
        public void gameFinished(int slot, GameView view, MatchStats stats, double llr) {
            finished.add(view);
        }

        @Override
        public void matchFinished(MatchStats stats, Verdict verdict, String why) {
            reason.set(why);
            done.countDown();
        }
    }

    private static Match.Config config(int games, int concurrency) {
        return new Match.Config(EngineConfig.builtIn(), EngineConfig.builtIn(), new TimeControl(2_000, 20), 0,
                concurrency, games, null, 24);
    }

    @Test
    void playsEveryGameWithColourReversedPairs() throws Exception {
        var recorder = new Recorder();
        var match = new Match(config(4, 2), recorder);
        match.start();
        assertTrue(recorder.done.await(90, TimeUnit.SECONDS));
        match.await();

        assertEquals("all games played", recorder.reason.get());
        assertEquals(4, match.stats().games());
        assertEquals(4, recorder.started.get());
        assertTrue(recorder.moves.get() >= 4 * (24 - 6), "engine moves after the 6-ply openings");

        // Games 0/1 share an opening with colours swapped; so do 2/3.
        assertEquals(recorder.openings.get(0), recorder.openings.get(1));
        assertEquals(recorder.openings.get(2), recorder.openings.get(3));
        assertNotEquals(recorder.openings.get(0), recorder.openings.get(2));
        for (GameView view : recorder.finished) {
            assertTrue(view.game().result().isOver());
            boolean aWhite = view.number() % 2 == 0;
            assertEquals(aWhite, view.white().endsWith("(A)"), "game " + view.number());
        }

        // The PGN of the whole match reads back.
        var games = Pgn.read(match.pgn());
        assertEquals(4, games.size());
        assertTrue(games.stream().allMatch(g -> g.tags().containsKey("Opening")));
    }

    @Test
    void stopEndsTheMatchEarly() throws Exception {
        var recorder = new Recorder();
        var match = new Match(config(200, 2), recorder);
        match.start();
        while (recorder.finished.isEmpty()) {
            Thread.sleep(50);
        }
        match.stop();
        assertTrue(recorder.done.await(30, TimeUnit.SECONDS));
        match.await();
        assertEquals("stopped", recorder.reason.get());
        assertTrue(match.stats().games() < 200);
    }

    @Test
    void jarEnginesRunWithJavaDashJar() {
        EngineConfig config = EngineConfig.executable(Path.of("engines", "seffir-main.jar"));
        assertEquals("seffir-main", config.name());
        assertEquals("-jar", config.command().get(1));
        assertFalse(EngineConfig.executable(Path.of("stockfish.exe")).command().contains("-jar"));
    }
}
