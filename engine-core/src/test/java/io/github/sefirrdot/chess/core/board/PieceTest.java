package io.github.sefirrdot.chess.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PieceTest {

    @Test
    void colorAndTypeDecomposeEveryPiece() {
        for (int color = 0; color < Color.COUNT; color++) {
            for (int type = 0; type < PieceType.COUNT; type++) {
                int piece = Piece.of(color, type);
                assertEquals(color, Piece.color(piece));
                assertEquals(type, Piece.type(piece));
            }
        }
    }

    @Test
    void namedConstantsMatchComposition() {
        assertEquals(Piece.WHITE_PAWN, Piece.of(Color.WHITE, PieceType.PAWN));
        assertEquals(Piece.WHITE_KING, Piece.of(Color.WHITE, PieceType.KING));
        assertEquals(Piece.BLACK_PAWN, Piece.of(Color.BLACK, PieceType.PAWN));
        assertEquals(Piece.BLACK_KING, Piece.of(Color.BLACK, PieceType.KING));
    }

    @Test
    void fenCharsRoundTrip() {
        for (int piece = 0; piece < Piece.COUNT; piece++) {
            assertEquals(piece, Piece.fromFenChar(Piece.toFenChar(piece)));
        }
        assertEquals('N', Piece.toFenChar(Piece.WHITE_KNIGHT));
        assertEquals('q', Piece.toFenChar(Piece.BLACK_QUEEN));
    }

    @Test
    void nonPieceCharsMapToNone() {
        assertEquals(Piece.NONE, Piece.fromFenChar('x'));
        assertEquals(Piece.NONE, Piece.fromFenChar('/'));
        assertEquals(Piece.NONE, Piece.fromFenChar('3'));
    }

    @Test
    void oppositeColor() {
        assertEquals(Color.BLACK, Color.opposite(Color.WHITE));
        assertEquals(Color.WHITE, Color.opposite(Color.BLACK));
    }
}
