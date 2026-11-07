package io.github.sefirrdot.chess.core.board;

/**
 * Forsyth-Edwards Notation parsing and formatting.
 *
 * <p>Six fields: placement, side to move, castling, en passant, halfmove clock, fullmove number. The two clocks may be
 * omitted (as in EPD test suites) and default to {@code 0 1}.
 */
public final class Fen {

    public static final String STARTING_POSITION = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private Fen() {
    }

    public static Board parse(String fen) {
        if (fen == null) {
            throw new InvalidFenException("null", "FEN is null");
        }
        String[] fields = fen.strip().split("\\s+");
        if (fields.length != 4 && fields.length != 6) {
            throw new InvalidFenException(fen, "expected 6 fields (or 4 without clocks), got " + fields.length);
        }

        var board = new Board();
        parsePlacement(fen, fields[0], board);
        board.setSideToMove(parseSideToMove(fen, fields[1]));
        board.setCastlingRights(parseCastling(fen, fields[2]));
        board.setEnPassantSquare(parseEnPassant(fen, fields[3], board.sideToMove()));
        if (fields.length == 6) {
            board.setHalfmoveClock(parseNumber(fen, fields[4], 0, "halfmove clock"));
            board.setFullmoveNumber(parseNumber(fen, fields[5], 1, "fullmove number"));
        }
        return board;
    }

    public static String format(Board board) {
        var sb = new StringBuilder(90);
        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                int piece = board.pieceAt(Square.of(file, rank));
                if (piece == Piece.NONE) {
                    empty++;
                    continue;
                }
                if (empty > 0) {
                    sb.append(empty);
                    empty = 0;
                }
                sb.append(Piece.toFenChar(piece));
            }
            if (empty > 0) {
                sb.append(empty);
            }
            if (rank > 0) {
                sb.append('/');
            }
        }
        int ep = board.enPassantSquare();
        return sb.append(' ').append(board.sideToMove() == Color.WHITE ? 'w' : 'b')
                .append(' ').append(CastlingRights.toFen(board.castlingRights()))
                .append(' ').append(ep == Square.NONE ? "-" : Square.name(ep))
                .append(' ').append(board.halfmoveClock())
                .append(' ').append(board.fullmoveNumber())
                .toString();
    }

    private static void parsePlacement(String fen, String placement, Board board) {
        String[] ranks = placement.split("/", -1);
        if (ranks.length != 8) {
            throw new InvalidFenException(fen, "piece placement must have 8 ranks, got " + ranks.length);
        }
        for (int i = 0; i < 8; i++) {
            int rank = 7 - i;
            int file = 0;
            for (char c : ranks[i].toCharArray()) {
                if (c >= '1' && c <= '8') {
                    file += c - '0';
                } else {
                    int piece = Piece.fromFenChar(c);
                    if (piece == Piece.NONE) {
                        throw new InvalidFenException(fen, "unexpected character '" + c + "' in piece placement");
                    }
                    if (file > 7) {
                        throw new InvalidFenException(fen, "rank " + (rank + 1) + " has more than 8 squares");
                    }
                    if (Piece.type(piece) == PieceType.PAWN && (rank == 0 || rank == 7)) {
                        throw new InvalidFenException(fen, "pawn on rank " + (rank + 1));
                    }
                    board.putPiece(piece, Square.of(file, rank));
                    file++;
                }
            }
            if (file != 8) {
                throw new InvalidFenException(fen, "rank " + (rank + 1) + " has " + file + " squares instead of 8");
            }
        }
        for (int color = 0; color < Color.COUNT; color++) {
            int kings = Bitboard.count(board.pieces(color, PieceType.KING));
            if (kings != 1) {
                throw new InvalidFenException(fen, Color.name(color) + " has " + kings + " kings");
            }
        }
    }

    private static int parseSideToMove(String fen, String field) {
        return switch (field) {
            case "w" -> Color.WHITE;
            case "b" -> Color.BLACK;
            default -> throw new InvalidFenException(fen, "side to move must be 'w' or 'b', got '" + field + "'");
        };
    }

    private static int parseCastling(String fen, String field) {
        try {
            return CastlingRights.fromFen(field);
        } catch (IllegalArgumentException e) {
            throw new InvalidFenException(fen, "invalid castling field '" + field + "'");
        }
    }

    private static int parseEnPassant(String fen, String field, int sideToMove) {
        if ("-".equals(field)) {
            return Square.NONE;
        }
        int square;
        try {
            square = Square.parse(field);
        } catch (IllegalArgumentException e) {
            throw new InvalidFenException(fen, "invalid en passant square '" + field + "'");
        }
        // The en passant target is behind the pawn that just moved: rank 6 if white is to move, rank 3 otherwise.
        int expectedRank = sideToMove == Color.WHITE ? 5 : 2;
        if (Square.rank(square) != expectedRank) {
            throw new InvalidFenException(fen, "en passant square " + field + " is impossible with "
                    + Color.name(sideToMove) + " to move");
        }
        return square;
    }

    private static int parseNumber(String fen, String field, int min, String what) {
        int value;
        try {
            value = Integer.parseInt(field);
        } catch (NumberFormatException e) {
            throw new InvalidFenException(fen, what + " is not a number: '" + field + "'");
        }
        if (value < min) {
            throw new InvalidFenException(fen, what + " must be >= " + min + ", got " + value);
        }
        return value;
    }
}
