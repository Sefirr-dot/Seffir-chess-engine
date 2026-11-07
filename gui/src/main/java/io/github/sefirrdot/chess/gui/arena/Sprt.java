package io.github.sefirrdot.chess.gui.arena;

import java.util.Locale;

/**
 * Sequential Probability Ratio Test between two Elo hypotheses, the stopping rule of engine testing (fishtest,
 * OpenBench, fastchess).
 *
 * <p>H0: the change is worth {@code elo0}; H1: it is worth {@code elo1}. After every game the log-likelihood ratio
 * (LLR) of H1 against H0 is updated; the test stops as soon as it leaves {@code [lowerBound, upperBound]}, which
 * bounds the probability of accepting a bad change by {@code alpha} and of rejecting a good one by {@code beta}. This
 * usually needs far fewer games than a fixed-length match.
 *
 * <p>The LLR uses the normal approximation of the generalised SPRT (Van den Bergh) over per-game scores:
 * {@code LLR = n (s1 - s0)(2 mean - s0 - s1) / (2 variance)}, with {@code s0}, {@code s1} the scores the two Elo
 * values imply under the logistic model.
 */
public record Sprt(double elo0, double elo1, double alpha, double beta) {

    public enum Verdict { CONTINUE, ACCEPT_H1, ACCEPT_H0 }

    /** Typical gainer test: is the change worth +5 Elo rather than nothing? */
    public static final Sprt DEFAULT = new Sprt(0, 5, 0.05, 0.05);

    public Sprt {
        if (!(elo1 > elo0) || alpha <= 0 || alpha >= 0.5 || beta <= 0 || beta >= 0.5) {
            throw new IllegalArgumentException("Need elo0 < elo1 and 0 < alpha, beta < 0.5");
        }
    }

    /** Below it, H0 is accepted (the change is not good enough). */
    public double lowerBound() {
        return Math.log(beta / (1 - alpha));
    }

    /** Above it, H1 is accepted (the change gains). */
    public double upperBound() {
        return Math.log((1 - beta) / alpha);
    }

    public double llr(MatchStats stats) {
        double variance = stats.variance();
        if (stats.games() < 2 || variance <= 0) {
            return 0;
        }
        double s0 = MatchStats.scoreFromElo(elo0);
        double s1 = MatchStats.scoreFromElo(elo1);
        return stats.games() * (s1 - s0) * (2 * stats.score() - s0 - s1) / (2 * variance);
    }

    public Verdict verdict(MatchStats stats) {
        double llr = llr(stats);
        if (llr >= upperBound()) {
            return Verdict.ACCEPT_H1;
        }
        if (llr <= lowerBound()) {
            return Verdict.ACCEPT_H0;
        }
        return Verdict.CONTINUE;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "SPRT [%.0f, %.0f] α=%.2f β=%.2f", elo0, elo1, alpha, beta);
    }
}
