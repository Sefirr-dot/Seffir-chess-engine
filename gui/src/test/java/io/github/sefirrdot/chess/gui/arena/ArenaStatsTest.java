package io.github.sefirrdot.chess.gui.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sefirrdot.chess.gui.arena.Sprt.Verdict;
import org.junit.jupiter.api.Test;

class ArenaStatsTest {

    @Test
    void eloFromScoreFollowsTheLogisticModel() {
        assertEquals(0, MatchStats.eloFromScore(0.5), 1e-9);
        assertEquals(190.85, MatchStats.eloFromScore(0.75), 0.01);
        assertEquals(-190.85, MatchStats.eloFromScore(0.25), 0.01);
        assertEquals(0.75, MatchStats.scoreFromElo(MatchStats.eloFromScore(0.75)), 1e-12);
    }

    @Test
    void statsFromResults() {
        var stats = new MatchStats(60, 30, 10);
        assertEquals(100, stats.games());
        assertEquals(0.75, stats.score(), 1e-12);
        assertEquals(0.3, stats.drawRatio(), 1e-12);
        assertEquals(190.85, stats.elo(), 0.01);
        // Error bar shrinks with more games of the same proportions.
        double err100 = stats.eloError95();
        double err400 = new MatchStats(240, 120, 40).eloError95();
        assertTrue(err100 > 0 && err100 < 100, "err " + err100);
        assertTrue(err400 < err100 * 0.6, "4x the games ≈ half the error");
    }

    @Test
    void plusCountsTheRightBucket() {
        MatchStats s = MatchStats.EMPTY.plus(1).plus(0.5).plus(0).plus(1);
        assertEquals(new MatchStats(2, 1, 1), s);
    }

    @Test
    void likelihoodOfSuperiority() {
        assertEquals(0.5, new MatchStats(10, 50, 10).los(), 1e-9);
        assertTrue(new MatchStats(30, 40, 10).los() > 0.99);
        assertTrue(new MatchStats(10, 40, 30).los() < 0.01);
        assertEquals(0.5, MatchStats.EMPTY.los());
    }

    @Test
    void sprtBoundsForFivePercentErrors() {
        Sprt sprt = new Sprt(0, 5, 0.05, 0.05);
        assertEquals(-2.944, sprt.lowerBound(), 0.001);
        assertEquals(2.944, sprt.upperBound(), 0.001);
    }

    @Test
    void sprtAcceptsAClearImprovementAndRejectsARegression() {
        Sprt sprt = new Sprt(0, 10, 0.05, 0.05);
        assertEquals(Verdict.CONTINUE, sprt.verdict(new MatchStats(5, 5, 4)));
        assertEquals(Verdict.ACCEPT_H1, sprt.verdict(new MatchStats(700, 900, 500)));
        assertEquals(Verdict.ACCEPT_H0, sprt.verdict(new MatchStats(500, 900, 700)));
        // An even match drifts towards H0 (no gain), never towards H1.
        assertTrue(sprt.llr(new MatchStats(600, 800, 600)) < 0);
    }

    @Test
    void sprtRejectsNonsenseParameters() {
        assertThrows(IllegalArgumentException.class, () -> new Sprt(5, 0, 0.05, 0.05));
        assertThrows(IllegalArgumentException.class, () -> new Sprt(0, 5, 0, 0.05));
    }

    @Test
    void openingBookLinesAreLegalAndDistinct() {
        OpeningBook book = OpeningBook.builtIn();
        assertTrue(book.size() >= 20);
        for (int i = 0; i < book.size(); i++) {
            var opening = book.forPair(i);
            assertEquals(6, opening.moves().size(), opening.name());
            assertFalse(OpeningBook.start(opening).result().isOver());
        }
        assertEquals(book.forPair(0), book.forPair(book.size()), "cycles");
    }
}
