package io.github.sefirrdot.chess.core.notation;

import io.github.sefirrdot.chess.core.board.Board;
import io.github.sefirrdot.chess.core.board.Color;
import io.github.sefirrdot.chess.core.board.Fen;
import io.github.sefirrdot.chess.core.game.Game;
import io.github.sefirrdot.chess.core.game.GameResult;
import io.github.sefirrdot.chess.core.game.GameResult.Outcome;
import io.github.sefirrdot.chess.core.game.GameResult.Reason;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Portable Game Notation.
 *
 * <p>Writing produces the seven-tag roster (plus {@code SetUp}/{@code FEN} for games from a custom position), SAN
 * movetext wrapped at 80 columns and the result.
 *
 * <p>Reading accepts files with several games, comments ({@code {...}} and {@code ;...}), variations
 * ({@code (...)}, nested, skipped), numeric annotation glyphs ({@code $1}) and move-number styles {@code 12.} and
 * {@code 12...}. Only the main line is kept. A decisive or drawn {@code Result} that the final position does not
 * explain (a resignation, a time forfeit) is declared on the game with {@link Reason#NONE}.
 */
public final class Pgn {

    /** A game read from PGN, with its tag pairs in file order. */
    public record PgnGame(Map<String, String> tags, Game game) {
    }

    private static final int LINE_WIDTH = 80;

    private Pgn() {
    }

    // --- Writing ---

    /**
     * @param tags extra or overriding tag pairs ({@code White}, {@code Black}, {@code Event}...). The seven-tag roster
     *     is always present, with {@code ?} for unknown values; {@code Result} always reflects the game.
     */
    public static String write(Game game, Map<String, String> tags) {
        Map<String, String> all = new LinkedHashMap<>();
        all.put("Event", "?");
        all.put("Site", "?");
        all.put("Date", "????.??.??");
        all.put("Round", "-");
        all.put("White", "?");
        all.put("Black", "?");
        all.put("Result", "*");
        all.putAll(tags);
        String result = game.result().outcome().pgn();
        all.put("Result", result);
        if (!game.startFen().equals(Fen.STARTING_POSITION)) {
            all.put("SetUp", "1");
            all.put("FEN", game.startFen());
        }

        var sb = new StringBuilder();
        all.forEach((key, value) -> sb.append('[').append(key).append(" \"")
                .append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"]\n"));
        sb.append('\n');

        List<String> tokens = new ArrayList<>();
        Board board = Fen.parse(game.startFen());
        int fullmove = board.fullmoveNumber();
        boolean first = true;
        for (int move : game.moves()) {
            boolean white = board.sideToMove() == Color.WHITE;
            if (white) {
                tokens.add(fullmove + ".");
            } else if (first) {
                tokens.add(fullmove + "...");
            }
            tokens.add(San.format(board, move));
            board.makeMove(move);
            if (!white) {
                fullmove++;
            }
            first = false;
        }
        tokens.add(result);

        int column = 0;
        for (String token : tokens) {
            if (column > 0 && column + 1 + token.length() > LINE_WIDTH) {
                sb.append('\n');
                column = 0;
            } else if (column > 0) {
                sb.append(' ');
                column++;
            }
            sb.append(token);
            column += token.length();
        }
        return sb.append('\n').toString();
    }

    // --- Reading ---

    /** Every game in {@code text}. */
    public static List<PgnGame> read(String text) {
        List<PgnGame> games = new ArrayList<>();
        var reader = new Reader(text);
        while (reader.skipWhitespaceAndComments()) {
            games.add(reader.readGame());
        }
        return games;
    }

    /** The first game in {@code text}. */
    public static PgnGame readFirst(String text) {
        List<PgnGame> games = read(text);
        if (games.isEmpty()) {
            throw new IllegalArgumentException("No game found in PGN");
        }
        return games.get(0);
    }

    private static final class Reader {
        private final String s;
        private int pos;

        Reader(String s) {
            this.s = s;
        }

        /** @return whether anything is left */
        boolean skipWhitespaceAndComments() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (Character.isWhitespace(c)) {
                    pos++;
                } else if (c == '%' && (pos == 0 || s.charAt(pos - 1) == '\n')) {
                    skipLine();  // escape mechanism
                } else {
                    return true;
                }
            }
            return false;
        }

        PgnGame readGame() {
            Map<String, String> tags = new LinkedHashMap<>();
            while (skipWhitespaceAndComments() && s.charAt(pos) == '[') {
                readTag(tags);
            }
            String fen = tags.getOrDefault("FEN", Fen.STARTING_POSITION);
            Game game = new Game(fen);
            String resultToken = null;

            while (skipWhitespaceAndComments()) {
                char c = s.charAt(pos);
                if (c == '[') {
                    break;  // next game's tags without a result token
                } else if (c == '{') {
                    skipUntil('}');
                } else if (c == ';') {
                    skipLine();
                } else if (c == '(') {
                    skipVariation();
                } else if (c == ')') {
                    pos++;  // stray closing parenthesis: tolerate rather than loop on an empty token
                } else if (c == '$') {
                    pos++;
                    readToken();
                } else {
                    String token = readToken();
                    if (isResult(token)) {
                        resultToken = token;
                        break;
                    }
                    String move = token.replaceFirst("^\\d+\\.+", "");  // "12.e4" and "12...e5" styles
                    if (!move.isEmpty() && !move.matches("\\d+\\.*")) {
                        game.play(San.parse(game.position(), move));
                    }
                }
            }

            String result = resultToken != null ? resultToken : tags.getOrDefault("Result", "*");
            applyResult(game, result);
            return new PgnGame(tags, game);
        }

        private void readTag(Map<String, String> tags) {
            int end = s.indexOf(']', pos);
            if (end < 0) {
                throw new IllegalArgumentException("Unterminated tag at offset " + pos);
            }
            String body = s.substring(pos + 1, end).strip();
            pos = end + 1;
            int space = body.indexOf(' ');
            if (space < 0) {
                return;
            }
            String key = body.substring(0, space);
            String value = body.substring(space + 1).strip();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
            }
            tags.put(key, value);
        }

        private String readToken() {
            int start = pos;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (Character.isWhitespace(c) || c == '{' || c == '(' || c == ')' || c == ';' || c == '[') {
                    break;
                }
                pos++;
            }
            return s.substring(start, pos);
        }

        private void skipUntil(char close) {
            int end = s.indexOf(close, pos);
            pos = end < 0 ? s.length() : end + 1;
        }

        private void skipLine() {
            int end = s.indexOf('\n', pos);
            pos = end < 0 ? s.length() : end + 1;
        }

        private void skipVariation() {
            int depth = 0;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == '{') {
                    skipUntil('}');
                    continue;
                }
                pos++;
                if (c == '(') {
                    depth++;
                } else if (c == ')' && --depth == 0) {
                    return;
                }
            }
        }
    }

    private static boolean isResult(String token) {
        return token.equals("1-0") || token.equals("0-1") || token.equals("1/2-1/2") || token.equals("*");
    }

    private static void applyResult(Game game, String result) {
        if (game.result().isOver()) {
            return;  // checkmate, stalemate... the position speaks for itself
        }
        Outcome outcome = switch (result) {
            case "1-0" -> Outcome.WHITE_WINS;
            case "0-1" -> Outcome.BLACK_WINS;
            case "1/2-1/2" -> Outcome.DRAW;
            default -> Outcome.ONGOING;
        };
        if (outcome != Outcome.ONGOING) {
            game.declareResult(new GameResult(outcome, Reason.NONE));
        }
    }
}
