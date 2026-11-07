package io.github.sefirrdot.chess.core.notation;

import io.github.sefirrdot.chess.core.board.Board;
import io.github.sefirrdot.chess.core.board.Piece;
import io.github.sefirrdot.chess.core.board.PieceType;
import io.github.sefirrdot.chess.core.board.Square;
import io.github.sefirrdot.chess.core.move.Move;
import io.github.sefirrdot.chess.core.move.MoveList;
import io.github.sefirrdot.chess.core.movegen.MoveGenerator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Standard Algebraic Notation, as used in PGN and on every chess site: {@code e4}, {@code Nf3}, {@code exd5},
 * {@code Nbd7}, {@code R1e2}, {@code O-O}, {@code e8=Q+}, {@code Qh4#}.
 *
 * <p>Formatting adds only the disambiguation that is needed (file first, then rank, then both). Parsing is lenient the
 * way real PGN files require: check/mate marks and annotations ({@code !?}) are ignored, {@code 0-0} is accepted, the
 * {@code =} of a promotion is optional and over-specified moves such as {@code Ngf3} are understood.
 */
public final class San {

    private static final String PIECE_LETTERS = "PNBRQK";
    private static final Pattern MOVE = Pattern.compile("^([NBRQK])?([a-h])?([1-8])?x?([a-h][1-8])(?:=?([NBRQ]))?$");

    private San() {
    }

    /**
     * SAN of a legal {@code move} in {@code board}'s position. The board is used for move generation (make/unmake) and
     * is left unchanged.
     */
    public static String format(Board board, int move) {
        var sb = new StringBuilder(8);
        int from = Move.from(move);
        int to = Move.to(move);
        int flag = Move.flag(move);

        if (flag == Move.KING_CASTLE) {
            sb.append("O-O");
        } else if (flag == Move.QUEEN_CASTLE) {
            sb.append("O-O-O");
        } else {
            int type = Piece.type(board.pieceAt(from));
            if (type == PieceType.PAWN) {
                if (Move.isCapture(move)) {
                    sb.append((char) ('a' + Square.file(from))).append('x');
                }
                sb.append(Square.name(to));
                if (Move.isPromotion(move)) {
                    sb.append('=').append(PIECE_LETTERS.charAt(Move.promotionType(move)));
                }
            } else {
                sb.append(PIECE_LETTERS.charAt(type));
                sb.append(disambiguation(board, move, type));
                if (Move.isCapture(move)) {
                    sb.append('x');
                }
                sb.append(Square.name(to));
            }
        }

        board.makeMove(move);
        if (MoveGenerator.isInCheck(board)) {
            var replies = new MoveList();
            MoveGenerator.generateLegal(board, replies);
            sb.append(replies.size() == 0 ? '#' : '+');
        }
        board.unmakeMove();
        return sb.toString();
    }

    /**
     * The legal move written as {@code san} in this position.
     *
     * @throws IllegalArgumentException if it is malformed, illegal or ambiguous
     */
    public static int parse(Board board, String san) {
        String text = san.strip().replaceAll("[+#!?]+$", "").replace('0', 'O');
        var legal = new MoveList();
        MoveGenerator.generateLegal(board, legal);

        if (text.equals("O-O") || text.equals("O-O-O")) {
            int flag = text.equals("O-O") ? Move.KING_CASTLE : Move.QUEEN_CASTLE;
            for (int i = 0; i < legal.size(); i++) {
                if (Move.flag(legal.get(i)) == flag) {
                    return legal.get(i);
                }
            }
            throw new IllegalArgumentException("Illegal castling " + san);
        }

        Matcher m = MOVE.matcher(text);
        if (!m.matches()) {
            throw new IllegalArgumentException("Not a SAN move: " + san);
        }
        int type = m.group(1) == null ? PieceType.PAWN : PIECE_LETTERS.indexOf(m.group(1).charAt(0));
        int fromFile = m.group(2) == null ? -1 : m.group(2).charAt(0) - 'a';
        int fromRank = m.group(3) == null ? -1 : m.group(3).charAt(0) - '1';
        int to = Square.parse(m.group(4));
        int promotion = m.group(5) == null ? -1 : PIECE_LETTERS.indexOf(m.group(5).charAt(0));

        int found = Move.NONE;
        for (int i = 0; i < legal.size(); i++) {
            int move = legal.get(i);
            int from = Move.from(move);
            if (Move.to(move) != to
                    || Piece.type(board.pieceAt(from)) != type
                    || Move.isCastle(move)
                    || (fromFile >= 0 && Square.file(from) != fromFile)
                    || (fromRank >= 0 && Square.rank(from) != fromRank)
                    || (Move.isPromotion(move) ? Move.promotionType(move) != promotion : promotion >= 0)) {
                continue;
            }
            if (found != Move.NONE) {
                throw new IllegalArgumentException("Ambiguous move " + san);
            }
            found = move;
        }
        if (found == Move.NONE) {
            throw new IllegalArgumentException("Illegal move " + san);
        }
        return found;
    }

    /** {@code ""}, a file, a rank, or both, to tell this move apart from same-type pieces reaching the same square. */
    private static String disambiguation(Board board, int move, int type) {
        int from = Move.from(move);
        var legal = new MoveList();
        MoveGenerator.generateLegal(board, legal);
        boolean ambiguous = false;
        boolean sameFile = false;
        boolean sameRank = false;
        for (int i = 0; i < legal.size(); i++) {
            int other = legal.get(i);
            int otherFrom = Move.from(other);
            if (otherFrom == from || Move.to(other) != Move.to(move)
                    || Piece.type(board.pieceAt(otherFrom)) != type) {
                continue;
            }
            ambiguous = true;
            sameFile |= Square.file(otherFrom) == Square.file(from);
            sameRank |= Square.rank(otherFrom) == Square.rank(from);
        }
        if (!ambiguous) {
            return "";
        }
        String file = String.valueOf((char) ('a' + Square.file(from)));
        String rank = String.valueOf(Square.rank(from) + 1);
        if (!sameFile) {
            return file;
        }
        return sameRank ? file + rank : rank;
    }
}
