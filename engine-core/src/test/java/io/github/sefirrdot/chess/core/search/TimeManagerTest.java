package io.github.sefirrdot.chess.core.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sefirrdot.chess.core.board.Color;
import org.junit.jupiter.api.Test;

class TimeManagerTest {

    @Test
    void noTimeLimitsMeansUnlimited() {
        var time = TimeManager.forLimits(SearchLimits.ofDepth(5), Color.WHITE);
        assertEquals(TimeManager.UNLIMITED, time.softMillis());
        assertEquals(TimeManager.UNLIMITED, time.hardMillis());
        assertEquals(TimeManager.UNLIMITED, TimeManager.forLimits(SearchLimits.infiniteSearch(), Color.WHITE).hardMillis());
    }

    @Test
    void moveTimeIsUsedExactlyMinusOverhead() {
        var time = TimeManager.forLimits(SearchLimits.ofMoveTime(1000), Color.BLACK);
        assertEquals(1000 - TimeManager.MOVE_OVERHEAD, time.softMillis());
        assertEquals(time.softMillis(), time.hardMillis());
    }

    @Test
    void clockUsesOwnSideTimeAndIncrement() {
        var limits = SearchLimits.NONE.withClock(60_000, 6_000, 1_000, 0, 0);
        var white = TimeManager.forLimits(limits, Color.WHITE);
        var black = TimeManager.forLimits(limits, Color.BLACK);
        assertTrue(white.softMillis() > black.softMillis());
        // Target: 60 s / 30 moves + 3/4 of the 1 s increment ≈ 2.7 s; stop deepening at half of it.
        assertTrue(white.softMillis() > 1_250 && white.softMillis() < 1_500, "soft " + white.softMillis());
        assertTrue(white.hardMillis() > 3_200 && white.hardMillis() < 3_500, "hard " + white.hardMillis());
    }

    @Test
    void neverPlansToUseMoreThanAQuarterOfTheClock() {
        var limits = SearchLimits.NONE.withClock(3_000, 3_000, 5_000, 5_000, 0);
        var time = TimeManager.forLimits(limits, Color.WHITE);
        assertTrue(time.hardMillis() <= (3_000 - TimeManager.MOVE_OVERHEAD) / 4);
        assertTrue(time.softMillis() <= time.hardMillis());
    }

    @Test
    void movesToGoSplitsTheRemainingTime() {
        var time = TimeManager.forLimits(SearchLimits.NONE.withClock(10_030, 10_030, 0, 0, 10), Color.WHITE);
        assertEquals(500, time.softMillis(), "10 s over 10 moves: 1 s target, deepen until 0.5 s");
    }

    @Test
    void almostNoTimeStillGivesAPositiveBudget() {
        var time = TimeManager.forLimits(SearchLimits.NONE.withClock(10, 10, 0, 0, 0), Color.WHITE);
        assertTrue(time.softMillis() >= 1 && time.hardMillis() >= 1);
    }
}
