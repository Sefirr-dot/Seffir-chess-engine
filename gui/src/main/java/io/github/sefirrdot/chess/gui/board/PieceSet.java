package io.github.sefirrdot.chess.gui.board;

import io.github.sefirrdot.chess.core.board.Color;
import io.github.sefirrdot.chess.core.board.Piece;
import io.github.sefirrdot.chess.core.board.PieceType;
import io.github.sefirrdot.chess.gui.svg.SvgIcon;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import javafx.scene.Node;

/** The twelve piece icons of a set, loaded once from {@code pieces/<name>/{w,b}{K,Q,R,B,N,P}.svg}. */
public final class PieceSet {

    public static final String DEFAULT = "chessnut";

    private static final String TYPE_LETTERS = "PNBRQK";

    private final SvgIcon[] icons = new SvgIcon[Piece.COUNT];

    public PieceSet(String name) {
        for (int piece = 0; piece < Piece.COUNT; piece++) {
            char color = Piece.color(piece) == Color.WHITE ? 'w' : 'b';
            char type = TYPE_LETTERS.charAt(Piece.type(piece));
            String resource = "/io/github/sefirrdot/chess/gui/pieces/" + name + "/" + color + type + ".svg";
            try (InputStream in = PieceSet.class.getResourceAsStream(resource)) {
                if (in == null) {
                    throw new IllegalArgumentException("Missing piece resource " + resource);
                }
                icons[piece] = SvgIcon.parse(in);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot load " + resource, e);
            }
        }
    }

    public Node create(int piece, double size) {
        return icons[piece].createNode(size);
    }

    public Node create(int color, int type, double size) {
        return create(Piece.of(color, type), size);
    }

    /** Piece types offered when promoting, most popular first. */
    static final int[] PROMOTION_CHOICES = {PieceType.QUEEN, PieceType.KNIGHT, PieceType.ROOK, PieceType.BISHOP};
}
