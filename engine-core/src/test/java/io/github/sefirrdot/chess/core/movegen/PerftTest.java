package io.github.sefirrdot.chess.core.movegen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.sefirrdot.chess.core.board.Board;
import io.github.sefirrdot.chess.core.board.Fen;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Reference perft results from the Chess Programming Wiki (https://www.chessprogramming.org/Perft_Results).
 *
 * <p>Depths are chosen so the whole suite runs in seconds while still exercising castling, en passant, promotions,
 * pins and checks thoroughly. Kiwipete alone covers most of the tricky rules.
 */
class PerftTest {

    @ParameterizedTest(name = "{0} depth {2} = {3}")
    @CsvSource(delimiter = '|', value = {
        "start     | rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1         | 1 | 20",
        "start     | rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1         | 2 | 400",
        "start     | rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1         | 3 | 8902",
        "start     | rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1         | 4 | 197281",
        "start     | rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1         | 5 | 4865609",
        "kiwipete  | r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1 | 1 | 48",
        "kiwipete  | r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1 | 2 | 2039",
        "kiwipete  | r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1 | 3 | 97862",
        "kiwipete  | r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1 | 4 | 4085603",
        "position3 | 8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1                       | 5 | 674624",
        "position3 | 8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1                       | 6 | 11030083",
        "position4 | r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1 | 4 | 422333",
        "mirrored4 | r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1 | 4 | 422333",
        "position5 | rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8        | 4 | 2103487",
        "position6 | r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10 | 4 | 3894594",
    })
    void matchesReferenceCounts(String name, String fen, int depth, long expected) {
        Board board = Fen.parse(fen.trim());
        assertEquals(expected, Perft.perft(board, depth));
        assertEquals(fen.trim(), Fen.format(board), "board not restored after perft");
    }

    @Test
    void divideSumsToPerft() {
        Board board = Board.startingPosition();
        Map<String, Long> divide = Perft.divide(board, 3);
        assertEquals(20, divide.size());
        assertEquals(8902L, divide.values().stream().mapToLong(Long::longValue).sum());
        assertEquals(600L, divide.get("e2e4"));
    }
}
