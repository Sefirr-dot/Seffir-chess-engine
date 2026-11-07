package io.github.sefirrdot.chess.gui.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sefirrdot.chess.core.EngineInfo;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** End-to-end against the real built-in engine, running as a child process. */
@Timeout(30)
class UciEngineTest {

    private UciEngine engine;

    @BeforeEach
    void startEngine() throws IOException {
        engine = UciEngine.start(EngineConfig.builtIn());
    }

    @AfterEach
    void closeEngine() {
        engine.close();
    }

    @Test
    void handshakeReportsTheEngineName() {
        assertEquals(EngineInfo.fullName(), engine.name());
        assertTrue(engine.isAlive());
    }

    @Test
    void searchReturnsTheMatingMoveAndStreamsInfo() throws Exception {
        List<UciInfo> infos = new CopyOnWriteArrayList<>();
        String move = engine.go("fen 6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1", "depth 3", infos::add)
                .get(10, TimeUnit.SECONDS);
        assertEquals("a1a8", move);
        assertFalse(infos.isEmpty());
        assertEquals(1, infos.get(infos.size() - 1).mateIn());
    }

    @Test
    void stopEndsAnInfiniteSearchWithAMove() throws Exception {
        var future = engine.go("startpos moves e2e4", "infinite", info -> { });
        Thread.sleep(200);
        assertFalse(future.isDone(), "infinite search must wait for stop");
        engine.stop();
        String move = future.get(5, TimeUnit.SECONDS);
        assertEquals(4, move.length());
    }

    @Test
    void newSearchSupersedesTheRunningOne() throws Exception {
        var first = engine.go("startpos", "infinite", info -> { });
        var second = engine.go("fen 6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1", "depth 3", info -> { });
        assertThrows(CancellationException.class, first::join);
        assertEquals("a1a8", second.get(10, TimeUnit.SECONDS), "the abandoned search's bestmove must not leak");
    }

    @Test
    void closeTerminatesTheProcess() throws Exception {
        engine.close();
        assertFalse(engine.isAlive());
    }
}
