package io.github.sefirrdot.chess.core.board;

import io.github.sefirrdot.chess.core.move.Move;
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
    private long hash;

    // Undo stack: everything makeMove overwrites that cannot be recomputed from the move itself.
    // historyHash also serves repetition detection: historyHash[i] is the hash of the position at ply i.
    private int ply;
    private long[] historyHash = new long[INITIAL_HISTORY];
    private int[] historyMove = new int[INITIAL_HISTORY];
    private int[] historyCaptured = new int[INITIAL_HISTORY];
    private int[] historyCastling = new int[INITIAL_HISTORY];
    private int[] historyEnPassant = new int[INITIAL_HISTORY];
    private int[] historyHalfmove = new int[INITIAL_HISTORY];

    private static final int INITIAL_HISTORY = 256;

    /**
     * Castling rights surviving a move that touches a square: moving the king or a rook from its original square, or
     * capturing a rook there, clears the matching rights. {@code rights &= MASK[from] & MASK[to]} covers every case.
     */
    private static final int[] CASTLING_MASK = new int[Square.COUNT];

    static {
        Arrays.fill(CASTLING_MASK, CastlingRights.ALL);
        CASTLING_MASK[Square.A1] &= ~CastlingRights.WHITE_QUEENSIDE;
        CASTLING_MASK[Square.H1] &= ~CastlingRights.WHITE_KINGSIDE;
        CASTLING_MASK[Square.E1] &= ~(CastlingRights.WHITE_KINGSIDE | CastlingRights.WHITE_QUEENSIDE);
        CASTLING_MASK[Square.A8] &= ~CastlingRights.BLACK_QUEENSIDE;
        CASTLING_MASK[Square.H8] &= ~CastlingRights.BLACK_KINGSIDE;
        CASTLING_MASK[Square.E8] &= ~(CastlingRights.BLACK_KINGSIDE | CastlingRights.BLACK_QUEENSIDE);
    }

    /** An empty board, white to move. */
    public Board() {
        Arrays.fill(mailbox, Piece.NONE);
    }

    public static Board startingPosition() {
        return Fen.parse(Fen.STARTING_POSITION);
    }

    /** Deep copy, including the move history (so repetitions and unmake keep working on the copy). */
    public Board copy() {
        var copy = new Board();
        System.arraycopy(pieceBitboards, 0, copy.pieceBitboards, 0, pieceBitboards.length);
        System.arraycopy(colorBitboards, 0, copy.colorBitboards, 0, colorBitboards.length);
        System.arraycopy(mailbox, 0, copy.mailbox, 0, mailbox.length);
        copy.occupied = occupied;
        copy.sideToMove = sideToMove;
        copy.castlingRights = castlingRights;
        copy.enPassantSquare = enPassantSquare;
        copy.halfmoveClock = halfmoveClock;
        copy.fullmoveNumber = fullmoveNumber;
        copy.hash = hash;
        copy.ply = ply;
        copy.historyHash = historyHash.clone();
        copy.historyMove = historyMove.clone();
        copy.historyCaptured = historyCaptured.clone();
        copy.historyCastling = historyCastling.clone();
        copy.historyEnPassant = historyEnPassant.clone();
        copy.historyHalfmove = historyHalfmove.clone();
        return copy;
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

    /** Number of moves made on this board that can still be unmade. */
    public int ply() {
        return ply;
    }

    /** Zobrist hash, maintained incrementally. Equal positions (for repetition purposes) have equal hashes. */
    public long hash() {
        return hash;
    }

    /**
     * Whether the current position already occurred earlier in this board's move history.
     *
     * <p>Only positions since the last capture or pawn move can repeat, and only every other ply (same side to move).
     * Positions given through FEN have no history, so repetitions before them are not seen.
     */
    public boolean isRepetition() {
        int oldest = Math.max(0, ply - halfmoveClock);
        for (int i = ply - 2; i >= oldest; i -= 2) {
            if (historyHash[i] == hash) {
                return true;
            }
        }
        return false;
    }

    /**
     * Neither side can possibly checkmate: K vs K, K + one minor piece vs K, or only bishops all standing on squares of
     * the same colour. (K+N+N vs K is not included: mate is possible, if not forceable.)
     */
    public boolean hasInsufficientMaterial() {
        long heavyOrPawns = 0;
        for (int color = 0; color < Color.COUNT; color++) {
            heavyOrPawns |= pieces(color, PieceType.PAWN) | pieces(color, PieceType.ROOK) | pieces(color, PieceType.QUEEN);
        }
        if (heavyOrPawns != 0) {
            return false;
        }
        long knights = pieces(Color.WHITE, PieceType.KNIGHT) | pieces(Color.BLACK, PieceType.KNIGHT);
        long bishops = pieces(Color.WHITE, PieceType.BISHOP) | pieces(Color.BLACK, PieceType.BISHOP);
        if (Bitboard.count(knights | bishops) <= 1) {
            return true;
        }
        return knights == 0
                && ((bishops & Bitboard.DARK_SQUARES) == 0 || (bishops & Bitboard.LIGHT_SQUARES) == 0);
    }

    /** Fifty-move rule: 100 plies without a capture or pawn move. */
    public boolean isFiftyMoveDraw() {
        return halfmoveClock >= 100;
    }

    /** Hash recomputed from scratch. Used to initialise positions and to verify the incremental updates. */
    public long computeHash() {
        long h = 0;
        for (int sq = 0; sq < Square.COUNT; sq++) {
            if (mailbox[sq] != Piece.NONE) {
                h ^= Zobrist.PIECE_SQUARE[mailbox[sq]][sq];
            }
        }
        if (sideToMove == Color.BLACK) {
            h ^= Zobrist.BLACK_TO_MOVE;
        }
        return h ^ Zobrist.CASTLING[castlingRights] ^ enPassantKey();
    }

    /**
     * The en passant file is hashed only when the side to move has a pawn next to the pawn that just double-pushed.
     * Otherwise the capture is impossible and the position is the same as without an en passant square; hashing it
     * anyway would make identical positions look different and miss repetitions.
     */
    private long enPassantKey() {
        if (enPassantSquare == Square.NONE) {
            return 0;
        }
        long pushedPawn = Bitboard.of(enPassantSquare ^ 8);
        long neighbours = ((pushedPawn << 1) & ~Bitboard.FILE_A) | ((pushedPawn >>> 1) & ~Bitboard.FILE_H);
        return (neighbours & pieces(sideToMove, PieceType.PAWN)) != 0
                ? Zobrist.EN_PASSANT_FILE[Square.file(enPassantSquare)]
                : 0;
    }

    /**
     * Plays a move. The move must be pseudo-legal in this position (as produced by the move generator); no validation is
     * done here. It may leave the mover's king in check, which the caller is expected to test.
     */
    public void makeMove(int move) {
        int from = Move.from(move);
        int to = Move.to(move);
        int flag = Move.flag(move);
        int piece = mailbox[from];
        int us = sideToMove;

        pushHistory(move);
        // Take out the state keys while the board still looks as it did when they were added.
        hash ^= Zobrist.CASTLING[castlingRights] ^ enPassantKey();

        int captured = Piece.NONE;
        if (flag == Move.EN_PASSANT) {
            // The captured pawn is beside us, on the moving pawn's rank: one rank behind the target square.
            captured = Piece.of(Color.opposite(us), PieceType.PAWN);
            removePiece(to ^ 8);
        } else if (Move.isCapture(move)) {
            captured = mailbox[to];
            removePiece(to);
        }
        historyCaptured[ply - 1] = captured;

        removePiece(from);
        putPiece(Move.isPromotion(move) ? Piece.of(us, Move.promotionType(move)) : piece, to);

        if (flag == Move.KING_CASTLE) {
            movePiece(from + 3, from + 1);
        } else if (flag == Move.QUEEN_CASTLE) {
            movePiece(from - 4, from - 1);
        }

        castlingRights &= CASTLING_MASK[from] & CASTLING_MASK[to];
        enPassantSquare = flag == Move.DOUBLE_PAWN_PUSH ? (from + to) >>> 1 : Square.NONE;
        halfmoveClock = Piece.type(piece) == PieceType.PAWN || captured != Piece.NONE ? 0 : halfmoveClock + 1;
        if (us == Color.BLACK) {
            fullmoveNumber++;
        }
        sideToMove = Color.opposite(us);
        // Piece keys were updated by putPiece/removePiece; add the new state keys.
        hash ^= Zobrist.BLACK_TO_MOVE ^ Zobrist.CASTLING[castlingRights] ^ enPassantKey();
    }

    /** Takes back the last move played with {@link #makeMove}. */
    public void unmakeMove() {
        if (ply == 0) {
            throw new IllegalStateException("No move to unmake");
        }
        ply--;
        int move = historyMove[ply];
        int from = Move.from(move);
        int to = Move.to(move);
        int flag = Move.flag(move);
        int us = Color.opposite(sideToMove);

        sideToMove = us;
        if (us == Color.BLACK) {
            fullmoveNumber--;
        }

        int piece = Move.isPromotion(move) ? Piece.of(us, PieceType.PAWN) : mailbox[to];
        removePiece(to);
        putPiece(piece, from);

        if (flag == Move.KING_CASTLE) {
            movePiece(from + 1, from + 3);
        } else if (flag == Move.QUEEN_CASTLE) {
            movePiece(from - 1, from - 4);
        }

        int captured = historyCaptured[ply];
        if (captured != Piece.NONE) {
            putPiece(captured, flag == Move.EN_PASSANT ? to ^ 8 : to);
        }

        castlingRights = historyCastling[ply];
        enPassantSquare = historyEnPassant[ply];
        halfmoveClock = historyHalfmove[ply];
        hash = historyHash[ply];
    }

    private void movePiece(int from, int to) {
        int piece = mailbox[from];
        removePiece(from);
        putPiece(piece, to);
    }

    private void pushHistory(int move) {
        if (ply == historyMove.length) {
            int capacity = ply * 2;
            historyHash = Arrays.copyOf(historyHash, capacity);
            historyMove = Arrays.copyOf(historyMove, capacity);
            historyCaptured = Arrays.copyOf(historyCaptured, capacity);
            historyCastling = Arrays.copyOf(historyCastling, capacity);
            historyEnPassant = Arrays.copyOf(historyEnPassant, capacity);
            historyHalfmove = Arrays.copyOf(historyHalfmove, capacity);
        }
        historyHash[ply] = hash;
        historyMove[ply] = move;
        historyCastling[ply] = castlingRights;
        historyEnPassant[ply] = enPassantSquare;
        historyHalfmove[ply] = halfmoveClock;
        ply++;
    }

    // --- Mutation. Package-private: only Fen and (later) make/unmake may change a position. ---

    void putPiece(int piece, int square) {
        assert mailbox[square] == Piece.NONE : "square " + Square.name(square) + " is occupied";
        long bit = Bitboard.of(square);
        pieceBitboards[piece] |= bit;
        colorBitboards[Piece.color(piece)] |= bit;
        occupied |= bit;
        mailbox[square] = piece;
        hash ^= Zobrist.PIECE_SQUARE[piece][square];
    }

    void removePiece(int square) {
        int piece = mailbox[square];
        assert piece != Piece.NONE : "square " + Square.name(square) + " is empty";
        long bit = ~Bitboard.of(square);
        pieceBitboards[piece] &= bit;
        colorBitboards[Piece.color(piece)] &= bit;
        occupied &= bit;
        mailbox[square] = Piece.NONE;
        hash ^= Zobrist.PIECE_SQUARE[piece][square];
    }

    /** Recomputes the hash after the side/castling/en passant setters, which do not maintain it. */
    void refreshHash() {
        hash = computeHash();
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
