package io.github.sefirrdot.chess.core.movegen;

import io.github.sefirrdot.chess.core.board.Bitboard;
import io.github.sefirrdot.chess.core.board.Board;
import io.github.sefirrdot.chess.core.board.CastlingRights;
import io.github.sefirrdot.chess.core.board.Color;
import io.github.sefirrdot.chess.core.board.Piece;
import io.github.sefirrdot.chess.core.board.PieceType;
import io.github.sefirrdot.chess.core.board.Square;
import io.github.sefirrdot.chess.core.move.Move;
import io.github.sefirrdot.chess.core.move.MoveList;

/**
 * Generates moves for the side to move.
 *
 * <p>{@link #generatePseudoLegal} follows piece movement rules but may leave the own king in check;
 * {@link #generateLegal} filters those out by playing each move and testing the king. Castling is fully validated
 * during generation (the king may not castle out of, through or into check).
 */
public final class MoveGenerator {

    private MoveGenerator() {
    }

    public static void generateLegal(Board board, MoveList moves) {
        var pseudoLegal = new MoveList();
        generatePseudoLegal(board, pseudoLegal);
        moves.clear();
        int us = board.sideToMove();
        for (int i = 0; i < pseudoLegal.size(); i++) {
            int move = pseudoLegal.get(i);
            board.makeMove(move);
            if (!Attacks.isSquareAttacked(board, board.kingSquare(us), Color.opposite(us))) {
                moves.add(move);
            }
            board.unmakeMove();
        }
    }

    /** The legal move matching a UCI string such as {@code e2e4} or {@code e7e8q}, or {@link Move#NONE}. */
    public static int parseUciMove(Board board, String uci) {
        var moves = new MoveList();
        generateLegal(board, moves);
        for (int i = 0; i < moves.size(); i++) {
            if (Move.toUci(moves.get(i)).equals(uci)) {
                return moves.get(i);
            }
        }
        return Move.NONE;
    }

    /** After a pseudo-legal move has been made: whether it left the mover's own king attacked, i.e. was illegal. */
    public static boolean leftKingInCheck(Board board) {
        int mover = Color.opposite(board.sideToMove());
        return Attacks.isSquareAttacked(board, board.kingSquare(mover), board.sideToMove());
    }

    public static boolean isInCheck(Board board) {
        int us = board.sideToMove();
        return Attacks.isSquareAttacked(board, board.kingSquare(us), Color.opposite(us));
    }

    public static void generatePseudoLegal(Board board, MoveList moves) {
        moves.clear();
        int us = board.sideToMove();
        long own = board.occupancy(us);
        long enemy = board.occupancy(Color.opposite(us));
        long occupied = board.occupied();

        generatePawnMoves(board, moves, us, enemy, occupied);
        for (long knights = board.pieces(us, PieceType.KNIGHT); knights != 0; knights = Bitboard.clearLsb(knights)) {
            int from = Bitboard.lsb(knights);
            addTargets(moves, from, Attacks.knight(from) & ~own, enemy);
        }
        for (long bishops = board.pieces(us, PieceType.BISHOP); bishops != 0; bishops = Bitboard.clearLsb(bishops)) {
            int from = Bitboard.lsb(bishops);
            addTargets(moves, from, Attacks.bishop(from, occupied) & ~own, enemy);
        }
        for (long rooks = board.pieces(us, PieceType.ROOK); rooks != 0; rooks = Bitboard.clearLsb(rooks)) {
            int from = Bitboard.lsb(rooks);
            addTargets(moves, from, Attacks.rook(from, occupied) & ~own, enemy);
        }
        for (long queens = board.pieces(us, PieceType.QUEEN); queens != 0; queens = Bitboard.clearLsb(queens)) {
            int from = Bitboard.lsb(queens);
            addTargets(moves, from, Attacks.queen(from, occupied) & ~own, enemy);
        }
        int king = board.kingSquare(us);
        addTargets(moves, king, Attacks.king(king) & ~own, enemy);
        generateCastling(board, moves, us, occupied);
    }

    private static void addTargets(MoveList moves, int from, long targets, long enemy) {
        for (; targets != 0; targets = Bitboard.clearLsb(targets)) {
            int to = Bitboard.lsb(targets);
            moves.add(Move.of(from, to, Bitboard.contains(enemy, to) ? Move.CAPTURE : Move.QUIET));
        }
    }

    private static void generatePawnMoves(Board board, MoveList moves, int us, long enemy, long occupied) {
        long pawns = board.pieces(us, PieceType.PAWN);
        long empty = ~occupied;
        boolean white = us == Color.WHITE;
        int forward = white ? 8 : -8;
        long promotionRank = white ? Bitboard.RANK_8 : Bitboard.RANK_1;

        // Pushes, set-wise: shift all pawns one rank forward at once.
        long singlePushes = (white ? pawns << 8 : pawns >>> 8) & empty;
        long doubleStartRank = white ? Bitboard.RANK_3 : Bitboard.RANK_6;
        long doublePushes = (white ? (singlePushes & doubleStartRank) << 8 : (singlePushes & doubleStartRank) >>> 8)
                & empty;

        for (long bb = singlePushes; bb != 0; bb = Bitboard.clearLsb(bb)) {
            int to = Bitboard.lsb(bb);
            int from = to - forward;
            if (Bitboard.contains(promotionRank, to)) {
                addPromotions(moves, from, to, false);
            } else {
                moves.add(Move.of(from, to, Move.QUIET));
            }
        }
        for (long bb = doublePushes; bb != 0; bb = Bitboard.clearLsb(bb)) {
            int to = Bitboard.lsb(bb);
            moves.add(Move.of(to - 2 * forward, to, Move.DOUBLE_PAWN_PUSH));
        }

        // Captures, per pawn.
        for (long bb = pawns; bb != 0; bb = Bitboard.clearLsb(bb)) {
            int from = Bitboard.lsb(bb);
            for (long targets = Attacks.pawn(us, from) & enemy; targets != 0; targets = Bitboard.clearLsb(targets)) {
                int to = Bitboard.lsb(targets);
                if (Bitboard.contains(promotionRank, to)) {
                    addPromotions(moves, from, to, true);
                } else {
                    moves.add(Move.of(from, to, Move.CAPTURE));
                }
            }
        }

        // En passant: our pawns that attack the target square are exactly where an enemy pawn on it would attack.
        int ep = board.enPassantSquare();
        if (ep != Square.NONE) {
            for (long attackers = Attacks.pawn(Color.opposite(us), ep) & pawns; attackers != 0;
                    attackers = Bitboard.clearLsb(attackers)) {
                moves.add(Move.of(Bitboard.lsb(attackers), ep, Move.EN_PASSANT));
            }
        }
    }

    private static void addPromotions(MoveList moves, int from, int to, boolean capture) {
        int base = capture ? Move.PROMO_CAPTURE_KNIGHT : Move.PROMO_KNIGHT;
        // Queen first: better move ordering later, and the most common choice.
        for (int i = 3; i >= 0; i--) {
            moves.add(Move.of(from, to, base + i));
        }
    }

    private static void generateCastling(Board board, MoveList moves, int us, long occupied) {
        int rights = board.castlingRights();
        int them = Color.opposite(us);
        boolean white = us == Color.WHITE;
        int kingFrom = white ? Square.E1 : Square.E8;
        int kingside = white ? CastlingRights.WHITE_KINGSIDE : CastlingRights.BLACK_KINGSIDE;
        int queenside = white ? CastlingRights.WHITE_QUEENSIDE : CastlingRights.BLACK_QUEENSIDE;
        int rook = Piece.of(us, PieceType.ROOK);

        if ((rights & (kingside | queenside)) == 0 || board.pieceAt(kingFrom) != Piece.of(us, PieceType.KING)
                || Attacks.isSquareAttacked(board, kingFrom, them)) {
            return;
        }
        if (CastlingRights.has(rights, kingside)
                && board.pieceAt(kingFrom + 3) == rook
                && (occupied & (Bitboard.of(kingFrom + 1) | Bitboard.of(kingFrom + 2))) == 0
                && !Attacks.isSquareAttacked(board, kingFrom + 1, them)
                && !Attacks.isSquareAttacked(board, kingFrom + 2, them)) {
            moves.add(Move.of(kingFrom, kingFrom + 2, Move.KING_CASTLE));
        }
        if (CastlingRights.has(rights, queenside)
                && board.pieceAt(kingFrom - 4) == rook
                && (occupied & (Bitboard.of(kingFrom - 1) | Bitboard.of(kingFrom - 2) | Bitboard.of(kingFrom - 3))) == 0
                && !Attacks.isSquareAttacked(board, kingFrom - 1, them)
                && !Attacks.isSquareAttacked(board, kingFrom - 2, them)) {
            moves.add(Move.of(kingFrom, kingFrom - 2, Move.QUEEN_CASTLE));
        }
    }
}
