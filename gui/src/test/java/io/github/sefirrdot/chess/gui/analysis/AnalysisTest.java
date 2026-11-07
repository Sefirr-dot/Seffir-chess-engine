package io.github.sefirrdot.chess.gui.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sefirrdot.chess.core.board.Color;
import io.github.sefirrdot.chess.core.board.Fen;
import io.github.sefirrdot.chess.gui.engine.UciInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisTest {

    private static UciInfo info(String line) {
        return UciInfo.parse(line).orElseThrow();
    }

    @Test
    void evaluationIsTurnedToWhitesPointOfView() {
        assertEquals("+0.35", Evaluation.fromEngine(info("info depth 5 score cp 35 pv e2e4"), Color.WHITE).label());
        assertEquals("-0.35", Evaluation.fromEngine(info("info depth 5 score cp 35 pv e7e5"), Color.BLACK).label());
        assertEquals("#-2", Evaluation.fromEngine(info("info depth 5 score mate 2 pv d8h4"), Color.BLACK).label());
        assertEquals("#3", Evaluation.fromEngine(info("info depth 5 score mate -3 pv g8h8"), Color.BLACK).label());
    }

    @Test
    void barShareFollowsTheWinProbabilityCurve() {
        assertEquals(0.5, new Evaluation(0, null).whiteShare(), 1e-9);
        double pawnUp = new Evaluation(100, null).whiteShare();
        assertTrue(pawnUp > 0.55 && pawnUp < 0.65, "one pawn ≈ 59%: " + pawnUp);
        assertEquals(1 - pawnUp, new Evaluation(-100, null).whiteShare(), 1e-9, "symmetric");
        assertTrue(new Evaluation(2000, null).whiteShare() > 0.99);
        assertEquals(1.0, new Evaluation(null, 4).whiteShare());
        assertEquals(0.0, new Evaluation(null, -1).whiteShare());
    }

    @Test
    void pvIsRenderedInSanWithMoveNumbers() {
        assertEquals("1. e4 e5 2. Nf3 Nc6",
                PvLine.toSan(Fen.STARTING_POSITION, List.of("e2e4", "e7e5", "g1f3", "b8c6"), 10));
        assertEquals("1. e4 e5",
                PvLine.toSan(Fen.STARTING_POSITION, List.of("e2e4", "e7e5", "g1f3", "b8c6"), 2));
        assertEquals("12... Kh8 13. Ra8#",
                PvLine.toSan("6k1/6pp/8/8/8/8/8/R5K1 b - - 0 12", List.of("g8h8", "a1a8"), 10));
    }

    @Test
    void pvStopsAtTheFirstIllegalMove() {
        assertEquals("1. e4", PvLine.toSan(Fen.STARTING_POSITION, List.of("e2e4", "e2e4", "g1f3"), 10));
        assertEquals("", PvLine.toSan(Fen.STARTING_POSITION, List.of("e7e5"), 10));
    }
}
