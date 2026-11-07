package io.github.sefirrdot.chess.gui.board;

import io.github.sefirrdot.chess.core.board.Color;
import io.github.sefirrdot.chess.core.board.Piece;
import io.github.sefirrdot.chess.core.board.PieceType;
import io.github.sefirrdot.chess.core.board.Square;
import io.github.sefirrdot.chess.core.game.Game;
import io.github.sefirrdot.chess.core.move.Move;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;
import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

/**
 * Interactive chess board.
 *
 * <p>The view knows nothing about the rules: it displays a {@link Game}'s position and its legal moves, lets the user
 * pick one (drag and drop or click-click, with a chooser for promotions) and reports it through {@link #setOnMove}. The
 * owner plays it on the game and calls {@link #setPosition} again. Moves that did not come from a drag are animated.
 *
 * <p>Layers, bottom to top: a canvas with squares, highlights, move hints and coordinates; the piece nodes; an overlay
 * for the promotion chooser.
 */
public final class BoardView extends Region {

    private static final Duration MOVE_ANIMATION = Duration.millis(160);

    private final Canvas canvas = new Canvas();
    private final Pane pieceLayer = new Pane();
    private final Pane overlay = new Pane();

    private final PieceSet pieceSet;
    private BoardTheme theme = BoardTheme.SLATE;
    private boolean flipped;
    private boolean whiteMovable = true;
    private boolean blackMovable = true;
    private IntConsumer onMove = move -> { };

    private final int[] pieces = new int[Square.COUNT];
    private final Node[] pieceNodes = new Node[Square.COUNT];
    private List<Integer> legalMoves = List.of();
    private int lastMove = Move.NONE;
    private int checkSquare = Square.NONE;

    private int selected = Square.NONE;
    private boolean dragging;
    private int hoverSquare = Square.NONE;
    /** A move the user just dropped: the piece is already on its target square, so don't animate it. */
    private int moveWithoutAnimation = Move.NONE;
    /** Identity of the position last shown (start FEN + number of moves), to recognise a one-move step. */
    private String shownStartFen;
    private int shownMoveCount = -1;

    private double squareSize;
    private double nodesBuiltForSize = -1;

    /** Optional node shown left of the board at the board's exact height (e.g. an evaluation bar). */
    private Region gutter;
    private static final double GUTTER_GAP = 10;

    public BoardView(PieceSet pieceSet) {
        this.pieceSet = pieceSet;
        Arrays.fill(pieces, Piece.NONE);
        pieceLayer.setMouseTransparent(true);
        overlay.setVisible(false);
        getChildren().addAll(canvas, pieceLayer, overlay);
        getStyleClass().add("board-view");
        setMinSize(160, 160);
        setPrefSize(640, 640);

        addEventHandler(MouseEvent.MOUSE_PRESSED, this::onPressed);
        addEventHandler(MouseEvent.MOUSE_DRAGGED, this::onDragged);
        addEventHandler(MouseEvent.MOUSE_RELEASED, this::onReleased);
    }

    // --- Public API ---

    /** Shows the game's current position. Call after every change to the game. */
    public void setPosition(Game game) {
        int previousLastMove = lastMove;
        for (int sq = 0; sq < Square.COUNT; sq++) {
            pieces[sq] = game.pieceAt(sq);
        }
        legalMoves = game.legalMoves();
        lastMove = game.lastMove();
        checkSquare = game.isInCheck() ? findKing(game.sideToMove()) : Square.NONE;
        selected = Square.NONE;
        dragging = false;
        hoverSquare = Square.NONE;
        hidePromotionChooser();

        rebuildPieceNodes();
        // Animate only a single step forward from what was shown (a move just played, or "next" while browsing);
        // loading a game, jumping around or taking back simply redraws.
        boolean oneMoveLater = game.startFen().equals(shownStartFen) && game.moves().size() == shownMoveCount + 1;
        if (oneMoveLater && lastMove != previousLastMove && lastMove != moveWithoutAnimation) {
            animate(lastMove);
        }
        shownStartFen = game.startFen();
        shownMoveCount = game.moves().size();
        moveWithoutAnimation = Move.NONE;
        draw();
    }

    /** Called with the move the user chose. */
    public void setOnMove(IntConsumer handler) {
        this.onMove = handler;
    }

    /** Which sides the user may move (e.g. only White when playing White against an engine). */
    public void setMovableSides(boolean white, boolean black) {
        this.whiteMovable = white;
        this.blackMovable = black;
        clearSelection();
    }

    public void setFlipped(boolean flipped) {
        this.flipped = flipped;
        clearSelection();
        nodesBuiltForSize = -1;
        requestLayout();
    }

    public boolean isFlipped() {
        return flipped;
    }

    public void setTheme(BoardTheme theme) {
        this.theme = theme;
        draw();
    }

    public BoardTheme theme() {
        return theme;
    }

    /** Places {@code node} left of the board, as tall as the board; it is laid out only while visible. */
    public void setGutter(Region node) {
        if (gutter != null) {
            getChildren().remove(gutter);
        }
        gutter = node;
        if (node != null) {
            getChildren().add(0, node);
            node.visibleProperty().addListener((obs, was, is) -> requestLayout());
        }
        requestLayout();
    }

    // --- Layout and drawing ---

    @Override
    protected void layoutChildren() {
        boolean showGutter = gutter != null && gutter.isVisible();
        double gutterWidth = showGutter ? gutter.prefWidth(-1) + GUTTER_GAP : 0;
        double size = Math.min(getWidth() - gutterWidth, getHeight());
        squareSize = Math.floor(size / 8);
        double boardSize = squareSize * 8;
        double x = Math.floor((getWidth() - boardSize + gutterWidth) / 2);
        double y = Math.floor((getHeight() - boardSize) / 2);
        if (showGutter) {
            gutter.resizeRelocate(x - gutterWidth, y, gutterWidth - GUTTER_GAP, boardSize);
        }
        for (Region layer : new Region[] {pieceLayer, overlay}) {
            layer.resizeRelocate(x, y, boardSize, boardSize);
        }
        canvas.setWidth(boardSize);
        canvas.setHeight(boardSize);
        canvas.relocate(x, y);
        pieceLayer.setClip(new Rectangle(boardSize, boardSize));
        if (squareSize != nodesBuiltForSize) {
            rebuildPieceNodes();
        }
        draw();
    }

    private void draw() {
        if (squareSize <= 0) {
            return;
        }
        GraphicsContext g = canvas.getGraphicsContext2D();
        double s = squareSize;
        for (int sq = 0; sq < Square.COUNT; sq++) {
            double x = squareX(sq);
            double y = squareY(sq);
            g.setFill(isLight(sq) ? theme.lightSquare() : theme.darkSquare());
            g.fillRect(x, y, s, s);
            if (lastMove != Move.NONE && (sq == Move.from(lastMove) || sq == Move.to(lastMove))) {
                g.setFill(theme.lastMove());
                g.fillRect(x, y, s, s);
            }
            if (sq == selected) {
                g.setFill(theme.selected());
                g.fillRect(x, y, s, s);
            }
            if (sq == checkSquare) {
                g.setFill(new RadialGradient(0, 0, x + s / 2, y + s / 2, s * 0.62, false, CycleMethod.NO_CYCLE,
                        new Stop(0, theme.check()), new Stop(0.35, theme.check().deriveColor(0, 1, 1, 0.85)),
                        new Stop(1, theme.check().deriveColor(0, 1, 1, 0))));
                g.fillRect(x, y, s, s);
            }
        }
        if (selected != Square.NONE) {
            drawMoveHints(g);
        }
        drawCoordinates(g);
    }

    private void drawMoveHints(GraphicsContext g) {
        double s = squareSize;
        for (int to : targetsOf(selected)) {
            double x = squareX(to);
            double y = squareY(to);
            if (to == hoverSquare && dragging) {
                g.setFill(theme.moveHint().deriveColor(0, 1, 1, 0.6));
                g.fillRect(x, y, s, s);
            } else if (pieces[to] != Piece.NONE) {
                // Capture: a ring hugging the square, leaving the target piece visible.
                g.setStroke(theme.moveHint());
                g.setLineWidth(s * 0.08);
                g.strokeOval(x + s * 0.06, y + s * 0.06, s * 0.88, s * 0.88);
            } else {
                double r = s * 0.16;
                g.setFill(theme.moveHint());
                g.fillOval(x + s / 2 - r, y + s / 2 - r, 2 * r, 2 * r);
            }
        }
    }

    /** File letters along the bottom edge and rank numbers along the left edge, inside the squares. */
    private void drawCoordinates(GraphicsContext g) {
        double s = squareSize;
        g.setFont(Font.font("System", FontWeight.BOLD, Math.max(9, s * 0.16)));
        double pad = s * 0.06;
        for (int i = 0; i < 8; i++) {
            int fileSquare = squareAtScreen(i, 7);
            g.setFill(isLight(fileSquare) ? theme.darkSquare() : theme.lightSquare());
            g.setTextAlign(TextAlignment.RIGHT);
            g.setTextBaseline(VPos.BOTTOM);
            g.fillText(String.valueOf((char) ('a' + Square.file(fileSquare))), i * s + s - pad, 8 * s - pad * 0.5);

            int rankSquare = squareAtScreen(0, i);
            g.setFill(isLight(rankSquare) ? theme.darkSquare() : theme.lightSquare());
            g.setTextAlign(TextAlignment.LEFT);
            g.setTextBaseline(VPos.TOP);
            g.fillText(String.valueOf(Square.rank(rankSquare) + 1), pad, i * s + pad * 0.5);
        }
    }

    private void rebuildPieceNodes() {
        pieceLayer.getChildren().clear();
        Arrays.fill(pieceNodes, null);
        if (squareSize <= 0) {
            return;
        }
        for (int sq = 0; sq < Square.COUNT; sq++) {
            if (pieces[sq] != Piece.NONE) {
                Node node = pieceSet.create(pieces[sq], squareSize);
                node.relocate(squareX(sq), squareY(sq));
                pieceNodes[sq] = node;
                pieceLayer.getChildren().add(node);
            }
        }
        nodesBuiltForSize = squareSize;
    }

    private void animate(int move) {
        slide(Move.from(move), Move.to(move));
        int from = Move.from(move);
        if (Move.flag(move) == Move.KING_CASTLE) {
            slide(from + 3, from + 1);
        } else if (Move.flag(move) == Move.QUEEN_CASTLE) {
            slide(from - 4, from - 1);
        }
    }

    private void slide(int from, int to) {
        Node node = pieceNodes[to];
        if (node == null) {
            return;
        }
        var transition = new TranslateTransition(MOVE_ANIMATION, node);
        transition.setFromX(squareX(from) - squareX(to));
        transition.setFromY(squareY(from) - squareY(to));
        transition.setToX(0);
        transition.setToY(0);
        transition.setInterpolator(Interpolator.EASE_OUT);
        node.toFront();
        transition.play();
    }

    // --- Interaction ---

    private void onPressed(MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY || overlay.isVisible()) {
            return;
        }
        int sq = squareAt(e.getX(), e.getY());
        if (sq == Square.NONE) {
            clearSelection();
            return;
        }
        if (selected != Square.NONE && targetsOf(selected).contains(sq)) {
            tryMove(selected, sq, false);
            return;
        }
        if (canPick(sq)) {
            selected = sq;
            dragging = true;
            Node node = pieceNodes[sq];
            node.toFront();
            centerOnPointer(node, e);
        } else {
            clearSelection();
        }
        draw();
    }

    private void onDragged(MouseEvent e) {
        if (!dragging || selected == Square.NONE) {
            return;
        }
        centerOnPointer(pieceNodes[selected], e);
        int hover = squareAt(e.getX(), e.getY());
        if (hover != hoverSquare) {
            hoverSquare = hover;
            draw();
        }
    }

    private void onReleased(MouseEvent e) {
        if (!dragging || selected == Square.NONE) {
            return;
        }
        dragging = false;
        hoverSquare = Square.NONE;
        int target = squareAt(e.getX(), e.getY());
        if (target != selected && targetsOf(selected).contains(target)) {
            tryMove(selected, target, true);
        } else {
            // Back to its square; a release on the same square keeps the selection for click-click moves.
            pieceNodes[selected].relocate(squareX(selected), squareY(selected));
            if (target != selected) {
                selected = Square.NONE;
            }
            draw();
        }
    }

    private void tryMove(int from, int to, boolean dropped) {
        List<Integer> candidates = new ArrayList<>();
        for (int move : legalMoves) {
            if (Move.from(move) == from && Move.to(move) == to) {
                candidates.add(move);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        if (candidates.size() == 1) {
            commit(candidates.get(0), dropped);
        } else {
            showPromotionChooser(to, candidates, dropped);
        }
    }

    private void commit(int move, boolean dropped) {
        selected = Square.NONE;
        if (dropped) {
            moveWithoutAnimation = move;
        }
        onMove.accept(move);
    }

    /**
     * Column of the four promotion pieces on the target file, starting on the promotion square and running towards the
     * centre of the board. Clicking anywhere else cancels.
     */
    private void showPromotionChooser(int to, List<Integer> candidates, boolean dropped) {
        overlay.getChildren().clear();
        var dim = new Rectangle(squareSize * 8, squareSize * 8, javafx.scene.paint.Color.rgb(0, 0, 0, 0.35));
        dim.setOnMousePressed(e -> {
            hidePromotionChooser();
            clearSelection();
            rebuildPieceNodes();
            draw();
            e.consume();
        });
        overlay.getChildren().add(dim);

        int color = Piece.color(pieces[Move.from(candidates.get(0))]);
        boolean downwards = squareY(to) == 0;
        for (int i = 0; i < PieceSet.PROMOTION_CHOICES.length; i++) {
            int type = PieceSet.PROMOTION_CHOICES[i];
            int move = candidates.stream().filter(m -> Move.promotionType(m) == type).findFirst().orElseThrow();
            double x = squareX(to);
            double y = squareY(to) + (downwards ? i : -i) * squareSize;

            var background = new Circle(squareSize / 2, squareSize / 2, squareSize * 0.47,
                    javafx.scene.paint.Color.web("#f4f6f8"));
            background.setEffect(new DropShadow(squareSize * 0.12, javafx.scene.paint.Color.rgb(0, 0, 0, 0.45)));
            Node icon = pieceSet.create(color, type, squareSize * 0.9);
            icon.relocate(squareSize * 0.05, squareSize * 0.05);
            var button = new Pane(background, icon);
            button.getStyleClass().add("promotion-choice");
            button.relocate(x, y);
            button.setOnMouseEntered(e -> background.setFill(javafx.scene.paint.Color.web("#ffd479")));
            button.setOnMouseExited(e -> background.setFill(javafx.scene.paint.Color.web("#f4f6f8")));
            button.setOnMousePressed(e -> {
                hidePromotionChooser();
                commit(move, dropped);
                e.consume();
            });
            overlay.getChildren().add(button);
        }
        overlay.setVisible(true);
    }

    private void hidePromotionChooser() {
        overlay.setVisible(false);
        overlay.getChildren().clear();
    }

    private void clearSelection() {
        if (selected != Square.NONE && dragging && pieceNodes[selected] != null) {
            pieceNodes[selected].relocate(squareX(selected), squareY(selected));
        }
        selected = Square.NONE;
        dragging = false;
        hoverSquare = Square.NONE;
        draw();
    }

    private boolean canPick(int sq) {
        int piece = pieces[sq];
        if (piece == Piece.NONE) {
            return false;
        }
        boolean movable = Piece.color(piece) == Color.WHITE ? whiteMovable : blackMovable;
        return movable && !targetsOf(sq).isEmpty();
    }

    private List<Integer> targetsOf(int from) {
        List<Integer> targets = new ArrayList<>();
        for (int move : legalMoves) {
            if (Move.from(move) == from && !targets.contains(Move.to(move))) {
                targets.add(Move.to(move));
            }
        }
        return targets;
    }

    private void centerOnPointer(Node node, MouseEvent e) {
        Region layer = pieceLayer;
        double x = e.getX() - layer.getLayoutX() - squareSize / 2;
        double y = e.getY() - layer.getLayoutY() - squareSize / 2;
        node.relocate(x, y);
    }

    // --- Geometry ---

    private int findKing(int color) {
        int king = Piece.of(color, PieceType.KING);
        for (int sq = 0; sq < Square.COUNT; sq++) {
            if (pieces[sq] == king) {
                return sq;
            }
        }
        return Square.NONE;
    }

    private static boolean isLight(int sq) {
        return (Square.file(sq) + Square.rank(sq)) % 2 == 1;
    }

    private double squareX(int sq) {
        int column = flipped ? 7 - Square.file(sq) : Square.file(sq);
        return column * squareSize;
    }

    private double squareY(int sq) {
        int row = flipped ? Square.rank(sq) : 7 - Square.rank(sq);
        return row * squareSize;
    }

    /** Square shown at screen column/row (0..7, row 0 at the top). */
    private int squareAtScreen(int column, int row) {
        int file = flipped ? 7 - column : column;
        int rank = flipped ? row : 7 - row;
        return Square.of(file, rank);
    }

    /** Square under a point in this region's coordinates, or {@link Square#NONE}. */
    private int squareAt(double x, double y) {
        double bx = x - canvas.getLayoutX();
        double by = y - canvas.getLayoutY();
        if (squareSize <= 0 || bx < 0 || by < 0 || bx >= squareSize * 8 || by >= squareSize * 8) {
            return Square.NONE;
        }
        return squareAtScreen((int) (bx / squareSize), (int) (by / squareSize));
    }
}
