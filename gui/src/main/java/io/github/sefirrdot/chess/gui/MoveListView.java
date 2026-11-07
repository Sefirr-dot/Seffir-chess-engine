package io.github.sefirrdot.chess.gui;

import java.util.List;
import java.util.function.IntConsumer;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

/**
 * Moves in SAN, two per row like a score sheet. Each move is clickable; the highlighted one is the position on the board.
 *
 * <p>Plies are 1-based: ply {@code n} is the position after the {@code n}-th move, ply 0 the starting position.
 */
final class MoveListView extends ScrollPane {

    private final GridPane grid = new GridPane();
    private IntConsumer onSelect = ply -> { };

    MoveListView() {
        getStyleClass().add("move-list");
        grid.getStyleClass().add("move-grid");
        grid.setPadding(new Insets(4, 6, 4, 6));
        var number = new ColumnConstraints(44);
        var white = new ColumnConstraints();
        white.setPercentWidth(42);
        var black = new ColumnConstraints();
        black.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(number, white, black);
        setContent(grid);
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setFocusTraversable(false);
    }

    void setOnSelect(IntConsumer handler) {
        this.onSelect = handler;
    }

    /**
     * @param sanMoves     all moves of the game, in SAN
     * @param blackStarts  whether the first move is Black's
     * @param firstNumber  move number of the first move
     * @param currentPly   ply shown on the board (highlighted), 0 for the start
     */
    void show(List<String> sanMoves, boolean blackStarts, int firstNumber, int currentPly) {
        grid.getChildren().clear();
        int offset = blackStarts ? 1 : 0;
        int row = 0;
        for (int i = -offset; i < sanMoves.size(); i += 2, row++) {
            var number = new Label((firstNumber + row) + ".");
            number.getStyleClass().add("move-number");
            grid.add(number, 0, row);
            grid.add(i >= 0 ? moveLabel(sanMoves.get(i), i + 1, currentPly) : placeholder(), 1, row);
            if (i + 1 < sanMoves.size()) {
                grid.add(moveLabel(sanMoves.get(i + 1), i + 2, currentPly), 2, row);
            }
        }
        // Keep the highlighted move in view. Rows must be measured first: style and lay out now rather than waiting
        // for the next pulse.
        applyCss();
        layout();
        grid.getChildren().stream().filter(n -> n.getStyleClass().contains("current")).findFirst()
                .ifPresentOrElse(this::scrollTo, () -> setVvalue(currentPly == 0 ? 0 : 1));
    }

    private Label moveLabel(String san, int ply, int currentPly) {
        var label = new Label(san);
        label.getStyleClass().add("move");
        if (ply == currentPly) {
            label.getStyleClass().add("current");
        }
        label.setMaxWidth(Double.MAX_VALUE);
        label.setOnMouseClicked(e -> onSelect.accept(ply));
        return label;
    }

    private static Label placeholder() {
        var label = new Label("…");
        label.getStyleClass().add("move-number");
        return label;
    }

    private void scrollTo(Node node) {
        double contentHeight = grid.getHeight();
        double viewport = getViewportBounds().getHeight();
        if (contentHeight <= viewport) {
            setVvalue(0);
            return;
        }
        double y = node.getBoundsInParent().getMinY();
        double target = (y - viewport / 2) / (contentHeight - viewport);
        setVvalue(Math.max(0, Math.min(1, target)));
    }
}
