package io.github.sefirrdot.chess.core.board;

import java.util.Arrays;

/**
 * Mutable chess position.
 *
 * <p>The piece placement is stored twice and kept in sync by {@link #putPiece} / {@link #removePiece}:
 *
 * <ul>
 *   <li>bitboards per piece and per color, for set-wise questions ("which squares do the white pawns attack?");
 *   <li>a 64-entry mailbox, for "what is on this square?" in constant time.
 * </ul>
 *
 * <p>Create positions with {@link Fen#parse(String)} or {@link #startingPosition()}.
 */
public final class Board {

    private final long[] pieceBitboards = new long[Piece.COUNT];
    private final long[] colorBitboards = new long[Color.COUNT];
    private long occupied;
    private final int[] mailbox = new int[Square.COUNT];

    private int sideToMove = Color.WHITE;
    private int castlingRights = CastlingRights.NONE;
    private int enPassantSquare = Square.NONE;
    private int halfmoveClock;
    private int fullmoveNumber = 1;

    /** An empty board, white to move. */
    public Board() {
        Arrays.fill(mailbox, Piece.NONE);
    }

    public static Board startingPosition() {
        return Fen.parse(Fen.STARTING_POSITION);
    }

    public int pieceAt(int square) {
        return mailbox[square];
    }

    public long pieces(int piece) {
        return pieceBitboards[piece];
    }

    public long pieces(int color, int type) {
        return pieceBitboards[Piece.of(color, type)];
    }

    public long occupancy(int color) {
        return colorBitboards[color];
    }

    public long occupied() {
        return occupied;
    }

    public int sideToMove() {
        return sideToMove;
    }

    public int castlingRights() {
        return castlingRights;
    }

    /** Square a pawn may capture onto en passant, or {@link Square#NONE}. */
    public int enPassantSquare() {
        return enPassantSquare;
    }

    /** Plies since the last capture or pawn move, for the fifty-move rule. */
    public int halfmoveClock() {
        return halfmoveClock;
    }

    public int fullmoveNumber() {
        return fullmoveNumber;
    }

    public int kingSquare(int color) {
        return Bitboard.lsb(pieces(color, PieceType.KING));
    }

    // --- Mutation. Package-private: only Fen and (later) make/unmake may change a position. ---

    void putPiece(int piece, int square) {
        assert mailbox[square] == Piece.NONE : "square " + Square.name(square) + " is occupied";
        long bit = Bitboard.of(square);
        pieceBitboards[piece] |= bit;
        colorBitboards[Piece.color(piece)] |= bit;
        occupied |= bit;
        mailbox[square] = piece;
    }

    void removePiece(int square) {
        int piece = mailbox[square];
        assert piece != Piece.NONE : "square " + Square.name(square) + " is empty";
        long bit = ~Bitboard.of(square);
        pieceBitboards[piece] &= bit;
        colorBitboards[Piece.color(piece)] &= bit;
        occupied &= bit;
        mailbox[square] = Piece.NONE;
    }

    void setSideToMove(int color) {
        this.sideToMove = color;
    }

    void setCastlingRights(int rights) {
        this.castlingRights = rights;
    }

    void setEnPassantSquare(int square) {
        this.enPassantSquare = square;
    }

    void setHalfmoveClock(int plies) {
        this.halfmoveClock = plies;
    }

    void setFullmoveNumber(int number) {
        this.fullmoveNumber = number;
    }

    /** Board diagram (rank 8 on top) followed by the FEN. Debugging aid. */
    @Override
    public String toString() {
        var sb = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            sb.append(rank + 1).append("  ");
            for (int file = 0; file < 8; file++) {
                int piece = mailbox[Square.of(file, rank)];
                sb.append(piece == Piece.NONE ? '.' : Piece.toFenChar(piece));
                sb.append(file < 7 ? ' ' : '\n');
            }
        }
        sb.append("   a b c d e f g h\n\n").append(Fen.format(this));
        return sb.toString();
    }
}
