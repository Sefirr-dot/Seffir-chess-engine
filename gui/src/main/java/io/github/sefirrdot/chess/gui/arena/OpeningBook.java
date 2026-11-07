package io.github.sefirrdot.chess.gui.arena;

import io.github.sefirrdot.chess.core.board.Fen;
import io.github.sefirrdot.chess.core.game.Game;
import io.github.sefirrdot.chess.core.notation.San;
import java.util.ArrayList;
import java.util.List;

/**
 * Short, balanced opening lines for engine matches. Without them two deterministic engines would replay the same game
 * over and over. Each opening is played twice, once with each engine as White ("colour-reversed pairs"), so neither
 * side benefits from a lopsided line.
 */
public final class OpeningBook {

    /** Name and moves in SAN, six plies each: the main line of each opening's most common variation. */
    private static final String[][] LINES = {
        {"Ruy Lopez", "e4 e5 Nf3 Nc6 Bb5 a6"},
        {"Italian Game", "e4 e5 Nf3 Nc6 Bc4 Bc5"},
        {"Scotch Game", "e4 e5 Nf3 Nc6 d4 exd4"},
        {"Petrov Defence", "e4 e5 Nf3 Nf6 Nxe5 d6"},
        {"Sicilian, Open", "e4 c5 Nf3 d6 d4 cxd4"},
        {"Sicilian, Classical", "e4 c5 Nf3 Nc6 d4 cxd4"},
        {"Sicilian, Taimanov", "e4 c5 Nf3 e6 d4 cxd4"},
        {"Sicilian, Alapin", "e4 c5 c3 Nf6 e5 Nd5"},
        {"French Defence", "e4 e6 d4 d5 Nc3 Nf6"},
        {"Caro-Kann Defence", "e4 c6 d4 d5 Nc3 dxe4"},
        {"Scandinavian Defence", "e4 d5 exd5 Qxd5 Nc3 Qa5"},
        {"Alekhine Defence", "e4 Nf6 e5 Nd5 d4 d6"},
        {"Pirc Defence", "e4 d6 d4 Nf6 Nc3 g6"},
        {"Queen's Gambit Declined", "d4 d5 c4 e6 Nc3 Nf6"},
        {"Slav Defence", "d4 d5 c4 c6 Nf3 Nf6"},
        {"Queen's Gambit Accepted", "d4 d5 c4 dxc4 Nf3 Nf6"},
        {"King's Indian Defence", "d4 Nf6 c4 g6 Nc3 Bg7"},
        {"Nimzo-Indian Defence", "d4 Nf6 c4 e6 Nc3 Bb4"},
        {"Queen's Indian Defence", "d4 Nf6 c4 e6 Nf3 b6"},
        {"Modern Benoni", "d4 Nf6 c4 c5 d5 e6"},
        {"Dutch Defence", "d4 f5 g3 Nf6 Bg2 g6"},
        {"English, Four Knights", "c4 e5 Nc3 Nf6 Nf3 Nc6"},
        {"English, Symmetrical", "c4 c5 Nc3 Nc6 g3 g6"},
        {"Réti Opening", "Nf3 d5 g3 Nf6 Bg2 c6"},
    };

    public record Opening(String name, List<Integer> moves) {
    }

    private final List<Opening> openings;

    private OpeningBook(List<Opening> openings) {
        this.openings = List.copyOf(openings);
    }

    /** The built-in book; every line is validated by playing it. */
    public static OpeningBook builtIn() {
        List<Opening> openings = new ArrayList<>();
        for (String[] line : LINES) {
            var game = new Game();
            List<Integer> moves = new ArrayList<>();
            for (String san : line[1].split(" ")) {
                int move = San.parse(game.position(), san);
                game.play(move);
                moves.add(move);
            }
            openings.add(new Opening(line[0], moves));
        }
        return new OpeningBook(openings);
    }

    public int size() {
        return openings.size();
    }

    /** Opening for the {@code index}-th pair of games, cycling through the book. */
    public Opening forPair(int pairIndex) {
        return openings.get(pairIndex % openings.size());
    }

    /** A new game from the standard start with the opening already played. */
    public static Game start(Opening opening) {
        var game = new Game(Fen.STARTING_POSITION);
        opening.moves().forEach(game::play);
        return game;
    }
}
