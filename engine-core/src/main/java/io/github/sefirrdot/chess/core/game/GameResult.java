package io.github.sefirrdot.chess.core.game;

/** How a game stands or ended. */
public record GameResult(Outcome outcome, Reason reason) {

    public enum Outcome {
        ONGOING("*"), WHITE_WINS("1-0"), BLACK_WINS("0-1"), DRAW("1/2-1/2");

        private final String pgn;

        Outcome(String pgn) {
            this.pgn = pgn;
        }

        /** PGN result token. */
        public String pgn() {
            return pgn;
        }
    }

    public enum Reason {
        NONE, CHECKMATE, STALEMATE, THREEFOLD_REPETITION, FIFTY_MOVE_RULE, INSUFFICIENT_MATERIAL,
        TIMEOUT, RESIGNATION, ADJUDICATION
    }

    public static final GameResult ONGOING = new GameResult(Outcome.ONGOING, Reason.NONE);

    public boolean isOver() {
        return outcome != Outcome.ONGOING;
    }

    public static GameResult draw(Reason reason) {
        return new GameResult(Outcome.DRAW, reason);
    }

    public static GameResult win(boolean whiteWins, Reason reason) {
        return new GameResult(whiteWins ? Outcome.WHITE_WINS : Outcome.BLACK_WINS, reason);
    }
}
