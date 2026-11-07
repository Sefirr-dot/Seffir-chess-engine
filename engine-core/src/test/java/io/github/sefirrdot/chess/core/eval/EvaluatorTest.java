package io.github.sefirrdot.chess.core.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sefirrdot.chess.core.board.Board;
import io.github.sefirrdot.chess.core.board.Fen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EvaluatorTest {

    /** Same position with colors swapped and the board flipped vertically. */
    static String mirror(String fen) {
        String[] f = fen.split(" ");
        String[] ranks = f[0].split("/");
        var placement = new StringBuilder();
        for (int i = 7; i >= 0; i--) {
            placement.append(swapCase(ranks[i])).append(i > 0 ? "/" : "");
        }
        String side = f[1].equals("w") ? "b" : "w";
        String castling = f[2].equals("-") ? "-" : sortCastling(swapCase(f[2]));
        String ep = f[3].equals("-") ? "-" : f[3].charAt(0) + String.valueOf((char) ('1' + '8' - f[3].charAt(1)));
        return placement + " " + side + " " + castling + " " + ep + " " + f[4] + " " + f[5];
    }

    private static String swapCase(String s) {
        var sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            sb.append(Character.isUpperCase(c) ? Character.toLowerCase(c) : Character.toUpperCase(c));
        }
        return sb.toString();
    }

    private static String sortCastling(String rights) {
        var sb = new StringBuilder();
        for (char c : "KQkq".toCharArray()) {
            if (rights.indexOf(c) >= 0) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Test
    void startingPositionIsBalanced() {
        assertEquals(0, Evaluator.evaluate(Board.startingPosition()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
        "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
        "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
        "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
        "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
        "8/8/4k3/8/2Q5/8/3K4/8 w - - 0 1",
    })
    void evaluationIsColorSymmetric(String fen) {
        assertEquals(Evaluator.evaluate(Fen.parse(fen)), Evaluator.evaluate(Fen.parse(mirror(fen))));
    }

    @Test
    void scoreIsFromTheSideToMovesPointOfView() {
        String whiteUp = "4k3/8/8/8/8/8/8/3QK3 ";
        int whiteToMove = Evaluator.evaluate(Fen.parse(whiteUp + "w - - 0 1"));
        int blackToMove = Evaluator.evaluate(Fen.parse(whiteUp + "b - - 0 1"));
        assertTrue(whiteToMove > 800, "white is a queen up: " + whiteToMove);
        assertEquals(-whiteToMove, blackToMove);
    }

    @Test
    void centralKnightBeatsCornerKnight() {
        int center = Evaluator.evaluate(Fen.parse("4k3/8/8/8/3N4/8/8/4K3 w - - 0 1"));
        int corner = Evaluator.evaluate(Fen.parse("4k3/8/8/8/8/8/8/N3K3 w - - 0 1"));
        assertTrue(center > corner);
    }

    @Test
    void kingPrefersShelterInMiddlegameAndCenterInEndgame() {
        // Middlegame (same material in both): castled king on g1 beats a king walking out to e3.
        int castled = Evaluator.evaluate(Fen.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQ1RK1 w kq - 0 1"));
        int exposed = Evaluator.evaluate(Fen.parse("rnbqkbnr/pppppppp/8/8/8/4K3/PPPPPPPP/RNBQ1R2 w kq - 0 1"));
        assertTrue(castled > exposed);
        // Pawn endgame: the centralised king is better.
        int central = Evaluator.evaluate(Fen.parse("4k3/pppp4/8/8/3K4/8/PPPP4/8 w - - 0 1"));
        int cornered = Evaluator.evaluate(Fen.parse("4k3/pppp4/8/8/8/8/PPPP4/7K w - - 0 1"));
        assertTrue(central > cornered);
    }
}
