package io.github.sefirrdot.chess.gui.arena;

/**
 * Results of a match from engine A's point of view, and the Elo difference they imply.
 *
 * <p>The Elo model is the logistic one: a score fraction {@code s} corresponds to {@code -400 log10(1/s - 1)}. The
 * error bars come from the per-game score variance (wins, draws and losses as 1, ½ and 0), with a 95% normal
 * interval, as reported by cutechess-cli and fastchess.
 */
public record MatchStats(int wins, int draws, int losses) {

    public static final MatchStats EMPTY = new MatchStats(0, 0, 0);

    public int games() {
        return wins + draws + losses;
    }

    public MatchStats plus(double scoreForA) {
        if (scoreForA == 1) {
            return new MatchStats(wins + 1, draws, losses);
        }
        if (scoreForA == 0) {
            return new MatchStats(wins, draws, losses + 1);
        }
        return new MatchStats(wins, draws + 1, losses);
    }

    /** Points per game, 0..1. */
    public double score() {
        return games() == 0 ? 0.5 : (wins + 0.5 * draws) / games();
    }

    public double drawRatio() {
        return games() == 0 ? 0 : (double) draws / games();
    }

    /** Variance of a single game's score. */
    public double variance() {
        if (games() == 0) {
            return 0;
        }
        double s = score();
        double n = games();
        return (wins * (1 - s) * (1 - s) + draws * (0.5 - s) * (0.5 - s) + losses * s * s) / n;
    }

    public double elo() {
        return eloFromScore(score());
    }

    /** Half-width of the 95% confidence interval of {@link #elo()}, or infinity with too little data. */
    public double eloError95() {
        if (games() < 2 || variance() == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double stdError = Math.sqrt(variance() / games());
        double high = eloFromScore(score() + 1.959964 * stdError);
        double low = eloFromScore(score() - 1.959964 * stdError);
        return (high - low) / 2;
    }

    /** Likelihood of superiority: probability that A is really stronger, from wins and losses (draws carry no sign). */
    public double los() {
        if (wins + losses == 0) {
            return 0.5;
        }
        return 0.5 * (1 + erf((wins - losses) / Math.sqrt(2.0 * (wins + losses))));
    }

    static double eloFromScore(double score) {
        if (score <= 0) {
            return Double.NEGATIVE_INFINITY;
        }
        if (score >= 1) {
            return Double.POSITIVE_INFINITY;
        }
        return -400 * Math.log10(1 / score - 1);
    }

    static double scoreFromElo(double elo) {
        return 1 / (1 + Math.pow(10, -elo / 400));
    }

    /** Abramowitz-Stegun 7.1.26 approximation (error < 1.5e-7), plenty for a percentage. */
    static double erf(double x) {
        double sign = Math.signum(x);
        x = Math.abs(x);
        double t = 1 / (1 + 0.3275911 * x);
        double y = 1 - ((((1.061405429 * t - 1.453152027) * t + 1.421413741) * t - 0.284496736) * t + 0.254829592)
                * t * Math.exp(-x * x);
        return sign * y;
    }
}
