package io.github.sefirrdot.chess.gui.analysis;

import io.github.sefirrdot.chess.core.board.Board;
import io.github.sefirrdot.chess.core.board.Color;
import io.github.sefirrdot.chess.core.board.Fen;
import io.github.sefirrdot.chess.core.move.Move;
import io.github.sefirrdot.chess.core.movegen.MoveGenerator;
import io.github.sefirrdot.chess.core.notation.San;
import java.util.List;

/** Turns an engine's principal variation (UCI moves) into readable SAN with move numbers. */
public final class PvLine {

    private PvLine() {
    }

    /**
     * {@code 12. Nf3 Nc6 13. Bb5} from the position {@code fen}; {@code 12... Nc6 13. Bb5} when Black is to move.
     * Stops at {@code maxMoves} plies, or silently at the first move that is not legal (a stale or buggy PV).
     */
    public static String toSan(String fen, List<String> uciMoves, int maxMoves) {
        Board board = Fen.parse(fen);
        var sb = new StringBuilder();
        int count = 0;
        for (String uci : uciMoves) {
            if (count == maxMoves) {
                break;
            }
            int move = MoveGenerator.parseUciMove(board, uci);
            if (move == Move.NONE) {
                break;
            }
            boolean white = board.sideToMove() == Color.WHITE;
            if (count > 0) {
                sb.append(' ');
            }
            if (white) {
                sb.append(board.fullmoveNumber()).append(". ");
            } else if (count == 0) {
                sb.append(board.fullmoveNumber()).append("... ");
            }
            sb.append(San.format(board, move));
            board.makeMove(move);
            count++;
        }
        return sb.toString();
    }
}
