package io.github.sefirrdot.chess.core.notation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.sefirrdot.chess.core.board.Board;
import io.github.sefirrdot.chess.core.board.Fen;
import io.github.sefirrdot.chess.core.move.Move;
import io.github.sefirrdot.chess.core.move.MoveList;
import io.github.sefirrdot.chess.core.movegen.MoveGenerator;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SanTest {

    private static String san(String fen, String uci) {
        Board board = Fen.parse(fen);
        return San.format(board, MoveGenerator.parseUciMove(board, uci));
    }

    @ParameterizedTest(name = "{2}")
    @CsvSource(delimiter = '|', value = {
        // pawns
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1                | e2e4 | e4",
        "rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2            | e4d5 | exd5",
        "rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3            | e5d6 | exd6",
        "4k3/1P6/8/8/8/8/8/4K3 w - - 0 1                                         | b7b8q | b8=Q+",
        "r3k3/1P6/8/8/8/8/8/4K3 w q - 0 1                                        | b7a8n | bxa8=N",
        // pieces
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1                | g1f3 | Nf3",
        "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3        | f3e5 | Nxe5",
        // disambiguation: file, rank, both
        "4k3/8/8/8/8/8/8/1N2KN2 w - - 0 1                                        | b1d2 | Nbd2",
        "4k3/8/8/8/8/8/8/R4RK1 w - - 0 1                                         | a1d1 | Rad1",
        "4k3/8/8/8/8/8/8/R3K2R w - - 0 1                                         | a1d1 | Rd1",
        "4k3/R7/8/8/8/8/8/R3K3 w - - 0 1                                         | a1a4 | R1a4",
        "7k/8/8/8/2Q1Q3/8/2Q5/4K3 w - - 0 1                                      | c4d3 | Qc4d3",
        // a pinned piece does not create ambiguity
        "4k3/8/8/8/8/2r5/8/1N2K1N1 w - - 0 1                                    | g1e2 | Ne2",
        // castling, check, mate
        "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1                                    | e1g1 | O-O",
        "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1                                    | e1c1 | O-O-O",
        "4k3/8/8/8/8/8/8/R3K3 w - - 0 1                                          | a1a8 | Ra8+",
        "6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1                                       | a1a8 | Ra8#",
        "rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq - 0 2           | d8h4 | Qh4#",
    })
    void formats(String fen, String uci, String expected) {
        assertEquals(expected.trim(), san(fen.trim(), uci.trim()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Nbd2", "Nbd2+", "Nbd2!?", "Nb1d2"})
    void parsesLenientForms(String text) {
        Board board = Fen.parse("4k3/8/8/8/8/8/8/1N2KN2 w - - 0 1");
        assertEquals("b1d2", Move.toUci(San.parse(board, text)));
    }

    @Test
    void parsesCastlingWithZerosAndPromotionWithoutEquals() {
        assertEquals("e1g1", Move.toUci(San.parse(Fen.parse("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"), "0-0")));
        assertEquals("b7b8r", Move.toUci(San.parse(Fen.parse("4k3/1P6/8/8/8/8/8/4K3 w - - 0 1"), "b8R")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Nd2", "N1d2", "e5", "Ke3x", "O-O", "Qd2", "hello"})
    void rejectsAmbiguousIllegalOrMalformed(String text) {
        Board board = Fen.parse("4k3/8/8/8/8/8/8/1N2KN2 w - - 0 1");
        assertThrows(IllegalArgumentException.class, () -> San.parse(board, text));
    }

    @Test
    void formatAndParseRoundTripForEveryMoveInRandomGames() {
        var random = new SplittableRandom(7);
        var moves = new MoveList();
        for (String fen : new String[] {
            Fen.STARTING_POSITION,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
        }) {
            for (int game = 0; game < 30; game++) {
                Board board = Fen.parse(fen);
                for (int ply = 0; ply < 120; ply++) {
                    MoveGenerator.generateLegal(board, moves);
                    if (moves.size() == 0) {
                        break;
                    }
                    String before = Fen.format(board);
                    for (int i = 0; i < moves.size(); i++) {
                        int move = moves.get(i);
                        String text = San.format(board, move);
                        assertEquals(move, San.parse(board, text), text + " in " + before);
                    }
                    assertEquals(before, Fen.format(board), "format/parse must not change the board");
                    board.makeMove(moves.get(random.nextInt(moves.size())));
                }
            }
        }
    }
}
