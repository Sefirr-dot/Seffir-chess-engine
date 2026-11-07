package io.github.sefirrdot.chess.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FenTest {

    /** Standard perft positions from the Chess Programming Wiki, plus a few edge cases. */
    @ParameterizedTest
    @ValueSource(strings = {
        Fen.STARTING_POSITION,
        "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
        "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
        "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
        "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
        "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
        "rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3",
        "rnbqkbnr/pppp1ppp/8/8/3Pp3/8/PPP1PPPP/RNBQKBNR b KQkq d3 0 3",
        "8/8/8/8/8/8/8/K6k b - - 99 150",
    })
    void formatIsInverseOfParse(String fen) {
        var board = Fen.parse(fen);
        BoardTest.assertConsistent(board);
        assertEquals(fen, Fen.format(board));
    }

    @Test
    void parsesEveryField() {
        var board = Fen.parse("rnbqkbnr/pppp1ppp/8/8/3Pp3/8/PPP1PPPP/RNBQKBNR b Kq d3 4 12");
        assertEquals(Color.BLACK, board.sideToMove());
        assertEquals(CastlingRights.WHITE_KINGSIDE | CastlingRights.BLACK_QUEENSIDE, board.castlingRights());
        assertEquals(Square.D3, board.enPassantSquare());
        assertEquals(4, board.halfmoveClock());
        assertEquals(12, board.fullmoveNumber());
        assertEquals(Piece.BLACK_PAWN, board.pieceAt(Square.E4));
        assertEquals(Piece.WHITE_PAWN, board.pieceAt(Square.D4));
    }

    @Test
    void clocksAreOptional() {
        var board = Fen.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -");
        assertEquals(0, board.halfmoveClock());
        assertEquals(1, board.fullmoveNumber());
        assertEquals(Fen.STARTING_POSITION, Fen.format(board));
    }

    @Test
    void toleratesSurroundingAndRepeatedWhitespace() {
        assertEquals(Fen.STARTING_POSITION,
                Fen.format(Fen.parse("  rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR   w KQkq -  0 1 ")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq",               // 3 fields
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0",            // 5 fields
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP w KQkq - 0 1",                   // 7 ranks
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR/8 w KQkq - 0 1",        // 9 ranks
        "rnbqkbnr/ppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",           // rank too short
        "rnbqkbnrr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",         // rank too long
        "rnbqkbnr/pppppppp/9/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",          // digit 9
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNX w KQkq - 0 1",          // bad piece letter
        "rnbqqbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",          // black has no king
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBKKBNR w KQkq - 0 1",          // white has two kings
        "rnbqkbnP/pppppppp/8/8/8/8/PPPPPPP1/RNBQKBNR w KQkq - 0 1",          // pawn on rank 8
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR x KQkq - 0 1",          // side to move
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkx - 0 1",          // castling letter
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq e4 0 1",         // ep square on rank 4
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq e3 0 1",         // ep rank 3 with white to move
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq z9 0 1",         // ep not a square
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - -1 1",         // negative halfmove
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 0",          // fullmove 0
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - a 1",          // halfmove not a number
    })
    void rejectsInvalidFen(String fen) {
        assertThrows(InvalidFenException.class, () -> Fen.parse(fen));
    }

    @Test
    void rejectsNull() {
        assertThrows(InvalidFenException.class, () -> Fen.parse(null));
    }
}
