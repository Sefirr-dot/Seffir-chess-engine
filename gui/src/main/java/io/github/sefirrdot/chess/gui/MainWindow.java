package io.github.sefirrdot.chess.gui;

import io.github.sefirrdot.chess.core.EngineInfo;
import io.github.sefirrdot.chess.core.board.Color;
import io.github.sefirrdot.chess.core.game.Game;
import io.github.sefirrdot.chess.core.game.GameResult;
import io.github.sefirrdot.chess.core.move.Move;
import io.github.sefirrdot.chess.gui.board.BoardTheme;
import io.github.sefirrdot.chess.gui.board.BoardView;
import io.github.sefirrdot.chess.gui.board.PieceSet;
import java.util.List;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Main window content: the board and a side panel with game status, move list and controls. */
public final class MainWindow extends BorderPane {

    private static final PseudoClass OVER = PseudoClass.getPseudoClass("over");

    private final BoardView board = new BoardView(new PieceSet(PieceSet.DEFAULT));
    private final Label status = new Label();
    private final ListView<String> moveList = new ListView<>();
    private Game game = new Game();

    public MainWindow() {
        getStyleClass().add("main-window");

        var boardHolder = new StackPane(board);
        boardHolder.getStyleClass().add("board-holder");
        boardHolder.setPadding(new Insets(24));
        setCenter(boardHolder);
        setRight(sidePanel());

        board.setOnMove(this::play);
        refresh();
    }

    public void installShortcuts(Scene scene) {
        var accelerators = scene.getAccelerators();
        accelerators.put(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN), this::newGame);
        accelerators.put(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN), this::undo);
        accelerators.put(new KeyCodeCombination(KeyCode.F), () -> board.setFlipped(!board.isFlipped()));
    }

    private Region sidePanel() {
        var title = new Label(EngineInfo.NAME);
        title.getStyleClass().add("app-title");
        var subtitle = new Label("v" + EngineInfo.VERSION);
        subtitle.getStyleClass().add("app-subtitle");
        var header = new HBox(8, title, subtitle);
        header.setAlignment(Pos.BASELINE_LEFT);

        status.getStyleClass().add("status");
        status.setWrapText(true);

        var movesHeader = new Label("MOVES");
        movesHeader.getStyleClass().add("section-header");
        moveList.getStyleClass().add("move-list");
        moveList.setFocusTraversable(false);
        VBox.setVgrow(moveList, Priority.ALWAYS);

        var newGame = button("New game", "Ctrl+N", this::newGame);
        var undo = button("Undo", "Ctrl+Z", this::undo);
        var flip = button("Flip board", "F", () -> board.setFlipped(!board.isFlipped()));
        var buttons = new HBox(8, newGame, undo, flip);
        buttons.getChildren().forEach(b -> HBox.setHgrow(b, Priority.ALWAYS));

        var themeHeader = new Label("BOARD");
        themeHeader.getStyleClass().add("section-header");
        var themes = new ComboBox<BoardTheme>();
        themes.getItems().addAll(BoardTheme.ALL);
        themes.setValue(board.theme());
        themes.setMaxWidth(Double.MAX_VALUE);
        themes.setOnAction(e -> board.setTheme(themes.getValue()));

        var panel = new VBox(14, header, status, movesHeader, moveList, buttons, themeHeader, themes);
        panel.getStyleClass().add("side-panel");
        panel.setPadding(new Insets(24, 24, 24, 8));
        panel.setPrefWidth(320);
        panel.setMinWidth(260);
        return panel;
    }

    private static Button button(String text, String shortcut, Runnable action) {
        var button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setFocusTraversable(false);
        button.setOnAction(e -> action.run());
        button.setTooltip(new Tooltip(text + " (" + shortcut + ")"));
        return button;
    }

    private void play(int move) {
        game.play(move);
        refresh();
    }

    private void newGame() {
        game = new Game();
        refresh();
    }

    private void undo() {
        if (!game.moves().isEmpty()) {
            game.undo();
            refresh();
        }
    }

    private void refresh() {
        board.setPosition(game);
        status.setText(describe(game));
        status.pseudoClassStateChanged(OVER, game.result().isOver());

        List<Integer> moves = game.moves();
        var rows = moveList.getItems();
        rows.clear();
        boolean blackStarts = game.startFen().split(" ")[1].equals("b");
        int offset = blackStarts ? 1 : 0;
        int fullmove = Integer.parseInt(game.startFen().split(" ")[5]);
        for (int i = -offset; i < moves.size(); i += 2) {
            String white = i >= 0 ? Move.toUci(moves.get(i)) : "…";
            String black = i + 1 < moves.size() ? Move.toUci(moves.get(i + 1)) : "";
            rows.add(String.format("%3d.  %-8s %s", fullmove++, white, black));
        }
        if (!rows.isEmpty()) {
            moveList.scrollTo(rows.size() - 1);
        }
    }

    private static String describe(Game game) {
        GameResult result = game.result();
        String side = game.sideToMove() == Color.WHITE ? "White" : "Black";
        if (!result.isOver()) {
            return side + " to move" + (game.isInCheck() ? " — check!" : "");
        }
        String reason = switch (result.reason()) {
            case CHECKMATE -> "checkmate";
            case STALEMATE -> "stalemate";
            case THREEFOLD_REPETITION -> "threefold repetition";
            case FIFTY_MOVE_RULE -> "fifty-move rule";
            case INSUFFICIENT_MATERIAL -> "insufficient material";
            case TIMEOUT -> "time forfeit";
            case RESIGNATION -> "resignation";
            case ADJUDICATION -> "adjudication";
            case NONE -> "";
        };
        String outcome = switch (result.outcome()) {
            case WHITE_WINS -> "White wins";
            case BLACK_WINS -> "Black wins";
            case DRAW -> "Draw";
            case ONGOING -> "";
        };
        return outcome + " by " + reason + "  (" + result.outcome().pgn() + ")";
    }
}
