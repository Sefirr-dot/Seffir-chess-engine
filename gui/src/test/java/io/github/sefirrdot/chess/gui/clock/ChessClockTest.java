package io.github.sefirrdot.chess.gui.clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sefirrdot.chess.core.board.Color;
import org.junit.jupiter.api.Test;

class ChessClockTest {

    private long now;

    private void advance(long millis) {
        now += millis * 1_000_000;
    }

    private ChessClock clock(TimeControl control) {
        return new ChessClock(control, () -> now);
    }

    @Test
    void onlyTheRunningSideLosesTime() {
        ChessClock clock = clock(TimeControl.minutes(5, 0));
        clock.start(Color.WHITE);
        advance(10_000);
        assertEquals(290_000, clock.remainingMillis(Color.WHITE));
        assertEquals(300_000, clock.remainingMillis(Color.BLACK));
    }

    @Test
    void movingAddsIncrementAndSwitchesSides() {
        ChessClock clock = clock(TimeControl.minutes(3, 2));
        clock.start(Color.WHITE);
        advance(5_000);
        clock.moveMade(Color.WHITE);
        assertEquals(180_000 - 5_000 + 2_000, clock.remainingMillis(Color.WHITE));
        assertEquals(Color.BLACK, clock.running());
        advance(1_000);
        assertEquals(179_000, clock.remainingMillis(Color.BLACK));
        assertEquals(177_000, clock.remainingMillis(Color.WHITE), "white's clock is paused");
    }

    @Test
    void pauseFreezesBothClocks() {
        ChessClock clock = clock(TimeControl.minutes(1, 0));
        clock.start(Color.BLACK);
        advance(2_000);
        clock.pause();
        advance(30_000);
        assertEquals(58_000, clock.remainingMillis(Color.BLACK));
        assertEquals(-1, clock.running());
    }

    @Test
    void flagFallsAtZeroAndNeverGoesNegative() {
        ChessClock clock = clock(TimeControl.minutes(1, 0));
        clock.start(Color.WHITE);
        advance(59_999);
        assertFalse(clock.hasFlagged(Color.WHITE));
        advance(5_000);
        assertTrue(clock.hasFlagged(Color.WHITE));
        assertEquals(0, clock.remainingMillis(Color.WHITE));
    }

    @Test
    void untimedGamesNeverFlag() {
        ChessClock clock = clock(TimeControl.UNTIMED);
        clock.start(Color.WHITE);
        advance(10_000_000);
        assertFalse(clock.hasFlagged(Color.WHITE));
        assertEquals("∞", clock.format(Color.WHITE));
    }

    @Test
    void formatting() {
        ChessClock clock = clock(TimeControl.minutes(90, 30));
        assertEquals("1:30:00", clock.format(Color.WHITE));
        clock = clock(TimeControl.minutes(5, 0));
        assertEquals("5:00", clock.format(Color.WHITE));
        clock.start(Color.WHITE);
        advance(300_000 - 9_450);
        assertEquals("0:09.4", clock.format(Color.WHITE));
    }

    @Test
    void timeControlNames() {
        assertEquals("3 + 2", TimeControl.minutes(3, 2).toString());
        assertEquals("Unlimited", TimeControl.UNTIMED.toString());
    }
}
