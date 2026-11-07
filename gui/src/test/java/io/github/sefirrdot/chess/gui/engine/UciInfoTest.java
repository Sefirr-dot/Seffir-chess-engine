package io.github.sefirrdot.chess.gui.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class UciInfoTest {

    @Test
    void parsesSeffirInfoLine() {
        UciInfo info = UciInfo.parse(
                "info depth 7 score cp 35 nodes 11652309 nps 5706321 time 2042 pv e2e4 d7d5 e4d5").orElseThrow();
        assertEquals(7, info.depth());
        assertEquals(35, info.scoreCp());
        assertNull(info.mateIn());
        assertEquals(11_652_309, info.nodes());
        assertEquals(5_706_321, info.nps());
        assertEquals(2042, info.timeMillis());
        assertEquals(List.of("e2e4", "d7d5", "e4d5"), info.pv());
        assertEquals("+0.35", info.formatScore());
    }

    @Test
    void parsesStockfishStyleLineWithExtraFields() {
        UciInfo info = UciInfo.parse("info depth 22 seldepth 30 multipv 1 score mate -3 lowerbound nodes 100 "
                + "nps 50 hashfull 12 tbhits 0 time 2 pv h7h8 g1g2").orElseThrow();
        assertEquals(22, info.depth());
        assertEquals(-3, info.mateIn());
        assertEquals(List.of("h7h8", "g1g2"), info.pv());
        assertEquals("#-3", info.formatScore());
    }

    @Test
    void negativeCentipawns() {
        assertEquals("-1.20", UciInfo.parse("info depth 3 score cp -120 pv a2a3").orElseThrow().formatScore());
    }

    @Test
    void ignoresLinesWithoutDepthOrScore() {
        assertTrue(UciInfo.parse("info string NNUE evaluation enabled").isEmpty());
        assertTrue(UciInfo.parse("info currmove e2e4 currmovenumber 1").isEmpty());
        assertTrue(UciInfo.parse("bestmove e2e4").isEmpty());
        assertTrue(UciInfo.parse("info depth x score cp 1").isEmpty());
    }
}
