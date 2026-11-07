package io.github.sefirrdot.chess.core.board;

/** Thrown when a FEN string is malformed or describes an impossible position. */
public final class InvalidFenException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public InvalidFenException(String fen, String reason) {
        super("Invalid FEN \"" + fen + "\": " + reason);
    }
}
