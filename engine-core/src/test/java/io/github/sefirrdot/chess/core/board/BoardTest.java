package io.github.sefirrdot.chess.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BoardTest {

    /** Every redundant representation must agree: mailbox, piece bitboards, color bitboards, occupancy. */
    static void assertConsistent(Board board) {
        long white = 0;
        long black = 0;
        for (int piece = 0; piece < Piece.COUNT; piece++) {
            long bb = board.pieces(piece);
            for (int other = piece + 1; other < Piece.COUNT; other++) {
                assertEquals(0, bb & board.pieces(other), "piece bitboards overlap");
            }
            if (Piece.color(piece) == Color.WHITE) {
                white |= bb;
            } else {
                black |= bb;
            }
            for (long it = bb; it != 0; it = Bitboard.clearLsb(it)) {
                assertEquals(piece, board.pieceAt(Bitboard.lsb(it)), "mailbox disagrees with bitboard");
            }
        }
        assertEquals(white, board.occupancy(Color.WHITE));
        assertEquals(black, board.occupancy(Color.BLACK));
        assertEquals(white | black, board.occupied());
        for (int sq = 0; sq < Square.COUNT; sq++) {
            boolean empty = board.pieceAt(sq) == Piece.NONE;
            assertEquals(empty, !Bitboard.contains(board.occupied(), sq), "mailbox disagrees with occupancy");
        }
        assertEquals(board.computeHash(), board.hash(), "incremental hash drifted from a full recomputation");
    }

    @Test
    void newBoardIsEmpty() {
        var board = new Board();
        assertEquals(0, board.occupied());
        for (int sq = 0; sq < Square.COUNT; sq++) {
            assertEquals(Piece.NONE, board.pieceAt(sq));
        }
        assertEquals(Color.WHITE, board.sideToMove());
        assertEquals(Square.NONE, board.enPassantSquare());
    }

    @Test
    void putAndRemoveKeepRepresentationsInSync() {
        var board = new Board();
        board.putPiece(Piece.WHITE_KNIGHT, Square.G1);
        board.putPiece(Piece.BLACK_QUEEN, Square.D8);
        assertConsistent(board);
        assertEquals(Piece.WHITE_KNIGHT, board.pieceAt(Square.G1));
        assertEquals(Bitboard.of(Square.D8), board.pieces(Color.BLACK, PieceType.QUEEN));

        board.removePiece(Square.G1);
        assertConsistent(board);
        assertEquals(Piece.NONE, board.pieceAt(Square.G1));
        assertEquals(Bitboard.of(Square.D8), board.occupied());
    }

    @Test
    void startingPositionMaterial() {
        var board = Board.startingPosition();
        assertConsistent(board);
        assertEquals(32, Bitboard.count(board.occupied()));
        assertEquals(Bitboard.RANK_2, board.pieces(Piece.WHITE_PAWN));
        assertEquals(Bitboard.RANK_7, board.pieces(Piece.BLACK_PAWN));
        assertEquals(Bitboard.RANK_1 | Bitboard.RANK_2, board.occupancy(Color.WHITE));
        assertEquals(Square.E1, board.kingSquare(Color.WHITE));
        assertEquals(Square.E8, board.kingSquare(Color.BLACK));
        assertEquals(CastlingRights.ALL, board.castlingRights());
    }

    @Test
    void copyIsIndependentAndKeepsHistory() {
        Board original = Board.startingPosition();
        original.putPiece(Piece.WHITE_QUEEN, Square.D4);
        original.refreshHash();
        Board copy = original.copy();
        assertConsistent(copy);
        assertEquals(Fen.format(original), Fen.format(copy));
        assertEquals(original.hash(), copy.hash());

        copy.removePiece(Square.D4);
        assertEquals(Piece.WHITE_QUEEN, original.pieceAt(Square.D4), "changing the copy must not touch the original");
    }

    @Test
    void puttingOnOccupiedSquareFailsFast() {
        var board = new Board();
        board.putPiece(Piece.WHITE_ROOK, Square.A1);
        assertThrows(AssertionError.class, () -> board.putPiece(Piece.BLACK_ROOK, Square.A1));
    }

    @Test
    void toStringShowsDiagramAndFen() {
        String text = Board.startingPosition().toString();
        assertEquals("8  r n b q k b n r", text.lines().findFirst().orElseThrow());
        assertEquals(Fen.STARTING_POSITION, text.lines().reduce((a, b) -> b).orElseThrow());
    }
}
