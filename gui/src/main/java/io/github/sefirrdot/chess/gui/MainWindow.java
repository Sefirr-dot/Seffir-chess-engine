package io.github.sefirrdot.chess.gui;

import io.github.sefirrdot.chess.core.EngineInfo;
import io.github.sefirrdot.chess.core.board.Board;
import io.github.sefirrdot.chess.core.board.Color;
import io.github.sefirrdot.chess.core.board.Fen;
import io.github.sefirrdot.chess.core.game.Game;
import io.github.sefirrdot.chess.core.game.GameResult;
import io.github.sefirrdot.chess.core.move.Move;
import io.github.sefirrdot.chess.core.notation.Pgn;
import io.github.sefirrdot.chess.core.notation.San;
import io.github.sefirrdot.chess.gui.board.BoardTheme;
import io.github.sefirrdot.chess.gui.board.BoardView;
import io.github.sefirrdot.chess.gui.board.PieceSet;
import io.github.sefirrdot.chess.gui.clock.ChessClock;
import io.github.sefirrdot.chess.gui.clock.TimeControl;
import io.github.sefirrdot.chess.gui.engine.EngineConfig;
import io.github.sefirrdot.chess.gui.engine.UciEngine;
import io.github.sefirrdot.chess.gui.engine.UciInfo;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

/**
 * Main window: the board and a side panel with players and clocks, status, engine output, move list and controls.
 *
 * <p>Owns the game in progress. Human moves come from the board; engine moves are requested from {@link UciEngine}
 * processes and applied on the FX thread. Every request carries a token (the game's identity and ply), so a reply
 * that arrives after a new game or an undo is ignored.
 */
public final class MainWindow extends BorderPane {

    private static final PseudoClass OVER = PseudoClass.getPseudoClass("over");

    private final BoardView board = new BoardView(new PieceSet(PieceSet.DEFAULT));
    private final Label status = new Label();
    private final Label engineLine = new Label();
    private final MoveListView moveList = new MoveListView();
    private final PlayerCard topCard = new PlayerCard();
    private final PlayerCard bottomCard = new PlayerCard();
    private final Timeline ticker = new Timeline(new KeyFrame(Duration.millis(100), e -> tick()));

    private GameSetup setup;
    private Game game;
    private ChessClock clock;
    /** Running engine processes, reused across games while the configuration does not change. */
    private final UciEngine[] engines = new UciEngine[Color.COUNT];
    /** Starts and stops engine processes off the FX thread, one setup at a time, in order. */
    private final ExecutorService engineStarter = Executors.newSingleThreadExecutor(runnable -> {
        var thread = new Thread(runnable, "engine-starter");
        thread.setDaemon(true);
        return thread;
    });
    /** Identifies the current game; replies tagged with an older value are stale. */
    private long gameId;
    /** Ply shown on the board while browsing the move list, or -1 for the live position. */
    private int viewPly = -1;
    /** Names from a loaded PGN, overriding the setup's player names; null otherwise. */
    private String[] pgnNames;
    private String stylesheet;

    public MainWindow() {
        getStyleClass().add("main-window");

        var boardHolder = new StackPane(board);
        boardHolder.getStyleClass().add("board-holder");
        boardHolder.setPadding(new Insets(24));
        setTop(menuBar());
        setCenter(boardHolder);
        setRight(sidePanel());

        board.setOnMove(this::humanMove);
        moveList.setOnSelect(this::browseTo);
        ticker.setCycleCount(Timeline.INDEFINITE);
        ticker.play();
        startGame(GameSetup.defaultSetup());
    }

    public void installShortcuts(Scene scene, String stylesheet) {
        this.stylesheet = stylesheet;
        // Menu items carry their own accelerators; these are the move-list navigation keys.
        var accelerators = scene.getAccelerators();
        accelerators.put(new KeyCodeCombination(KeyCode.LEFT), () -> browseTo(shownPly() - 1));
        accelerators.put(new KeyCodeCombination(KeyCode.RIGHT), () -> browseTo(shownPly() + 1));
        accelerators.put(new KeyCodeCombination(KeyCode.HOME), () -> browseTo(0));
        accelerators.put(new KeyCodeCombination(KeyCode.END), () -> browseTo(game.moves().size()));
    }

    /** Stops the clock timer and quits all engine processes. */
    public void shutdown() {
        ticker.stop();
        gameId++;
        // Close on the starter thread so an engine being started right now is closed too.
        engineStarter.execute(() -> {
            for (int color = 0; color < Color.COUNT; color++) {
                closeEngine(color);
            }
        });
        engineStarter.shutdown();
        try {
            engineStarter.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // --- Layout ---

    private Region sidePanel() {
        var title = new Label(EngineInfo.NAME);
        title.getStyleClass().add("app-title");
        var subtitle = new Label("v" + EngineInfo.VERSION);
        subtitle.getStyleClass().add("app-subtitle");
        var header = new HBox(8, title, subtitle);
        header.setAlignment(Pos.BASELINE_LEFT);

        status.getStyleClass().add("status");
        status.setWrapText(true);
        status.setMaxWidth(Double.MAX_VALUE);
        status.setMinHeight(Region.USE_PREF_SIZE);
        engineLine.getStyleClass().add("engine-line");
        engineLine.setMaxWidth(Double.MAX_VALUE);
        engineLine.setWrapText(true);
        // Otherwise the VBox squeezes a wrapping label down to a single, ellipsised line.
        engineLine.setMinHeight(Region.USE_PREF_SIZE);
        // Takes no room at all while no engine is thinking.
        engineLine.visibleProperty().bind(engineLine.textProperty().isNotEmpty());
        engineLine.managedProperty().bind(engineLine.visibleProperty());

        var movesHeader = new Label("MOVES");
        movesHeader.getStyleClass().add("section-header");
        VBox.setVgrow(moveList, Priority.ALWAYS);
        var navigation = new HBox(6,
                navButton("⏮", "First move (Home)", () -> browseTo(0)),
                navButton("◀", "Previous move (←)", () -> browseTo(shownPly() - 1)),
                navButton("▶", "Next move (→)", () -> browseTo(shownPly() + 1)),
                navButton("⏭", "Last move (End)", () -> browseTo(game.moves().size())));
        navigation.getChildren().forEach(b -> HBox.setHgrow(b, Priority.ALWAYS));

        var newGame = button("New game", "Ctrl+N", this::showNewGameDialog);
        newGame.getStyleClass().add("primary");
        var undo = button("Undo", "Ctrl+Z", this::undo);
        var flip = button("Flip", "F", this::flip);
        var buttons = new HBox(8, newGame, undo, flip);
        buttons.getChildren().forEach(b -> HBox.setHgrow(b, Priority.ALWAYS));

        var themeHeader = new Label("BOARD");
        themeHeader.getStyleClass().add("section-header");
        var themes = new ComboBox<BoardTheme>();
        themes.getItems().addAll(BoardTheme.ALL);
        themes.setValue(board.theme());
        themes.setMaxWidth(Double.MAX_VALUE);
        themes.setOnAction(e -> board.setTheme(themes.getValue()));

        var panel = new VBox(12, header, topCard, status, engineLine, movesHeader, moveList, navigation, bottomCard,
                buttons,
                themeHeader, themes);
        panel.getStyleClass().add("side-panel");
        panel.setPadding(new Insets(24, 24, 24, 8));
        panel.setPrefWidth(340);
        panel.setMinWidth(280);
        return panel;
    }

    private static Button navButton(String symbol, String tooltip, Runnable action) {
        var button = new Button(symbol);
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setFocusTraversable(false);
        button.setOnAction(e -> action.run());
        button.setTooltip(new Tooltip(tooltip));
        return button;
    }

    private MenuBar menuBar() {
        var newGame = item("New game…", this::showNewGameDialog, shortcut(KeyCode.N));
        var takeBack = item("Take back", this::undo, shortcut(KeyCode.Z));
        var open = item("Open PGN…", this::openPgn, shortcut(KeyCode.O));
        var save = item("Save PGN…", this::savePgn, shortcut(KeyCode.S));
        var copyPgn = item("Copy PGN", this::copyPgn,
                new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
        var copyFen = item("Copy FEN", this::copyFen, null);
        var paste = item("Paste PGN or FEN", this::paste, shortcut(KeyCode.V));
        var exit = item("Exit", () -> getScene().getWindow().hide(), null);
        var gameMenu = new Menu("Game", null, newGame, takeBack, new SeparatorMenuItem(), open, save,
                new SeparatorMenuItem(), copyPgn, copyFen, paste, new SeparatorMenuItem(), exit);
        var viewMenu = new Menu("View", null, item("Flip board", this::flip, new KeyCodeCombination(KeyCode.F)));
        return new MenuBar(gameMenu, viewMenu);
    }

    private static KeyCombination shortcut(KeyCode key) {
        return new KeyCodeCombination(key, KeyCombination.SHORTCUT_DOWN);
    }

    private static MenuItem item(String text, Runnable action, KeyCombination accelerator) {
        var item = new MenuItem(text);
        item.setOnAction(e -> action.run());
        if (accelerator != null) {
            item.setAccelerator(accelerator);
        }
        return item;
    }

    private static Button button(String text, String shortcut, Runnable action) {
        var button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setFocusTraversable(false);
        button.setOnAction(e -> action.run());
        button.setTooltip(new Tooltip(text + " (" + shortcut + ")"));
        return button;
    }

    // --- Game flow ---

    private void showNewGameDialog() {
        new NewGameDialog(getScene().getWindow(), setup, stylesheet).showAndWait().ifPresent(this::startGame);
    }

    /** Starts a new game with the given players and time control. */
    public void startGame(GameSetup newSetup) {
        startGame(newSetup, new Game());
    }

    /** Starts from a prepared game: a custom position, or a game loaded from PGN that may be continued. */
    public void startGame(GameSetup newSetup, Game initial) {
        gameId++;
        setup = newSetup;
        game = initial;
        viewPly = -1;
        pgnNames = null;
        clock = new ChessClock(setup.timeControl());
        engineLine.setText("");
        board.setFlipped(setup.perspective() == Color.BLACK);
        board.setMovableSides(setup.isHuman(Color.WHITE), setup.isHuman(Color.BLACK));
        updatePlayerNames();
        if (setup.timeControl().isTimed() && !game.result().isOver()) {
            clock.start(game.sideToMove());
        }
        refresh();
        prepareEnginesThenPlay(gameId);
    }

    /** Starts (or reuses) the engines this setup needs off the FX thread, then lets the engine move if it is its turn. */
    private void prepareEnginesThenPlay(long id) {
        GameSetup wantedSetup = setup;
        CompletableFuture.runAsync(() -> {
            for (int color = 0; color < Color.COUNT; color++) {
                EngineConfig wanted = wantedSetup.engine(color);
                UciEngine running = engines[color];
                if (running != null && (wanted == null || !running.config().equals(wanted) || !running.isAlive())) {
                    closeEngine(color);
                    running = null;
                }
                if (wanted != null && running == null) {
                    try {
                        engines[color] = UciEngine.start(wanted);
                    } catch (IOException e) {
                        Platform.runLater(() -> engineLine.setText("Could not start " + wanted.name() + ": "
                                + e.getMessage()));
                        return;
                    }
                }
                if (engines[color] != null) {
                    engines[color].newGame();
                }
            }
            Platform.runLater(() -> {
                if (id == gameId) {
                    updatePlayerNames();
                    requestEngineMoveIfNeeded();
                }
            });
        }, engineStarter);
    }

    private void humanMove(int move) {
        play(move);
    }

    private void play(int move) {
        viewPly = -1;
        int mover = game.sideToMove();
        game.play(move);
        if (setup.timeControl().isTimed()) {
            clock.moveMade(mover);
        }
        if (game.result().isOver()) {
            clock.pause();
        }
        refresh();
        requestEngineMoveIfNeeded();
    }

    private void requestEngineMoveIfNeeded() {
        int side = game.sideToMove();
        UciEngine engine = engines[side];
        if (game.result().isOver() || setup.isHuman(side) || engine == null) {
            return;
        }
        long id = gameId;
        int ply = game.moves().size();
        engine.go(positionCommand(), goArguments(), info -> Platform.runLater(() -> {
            if (id == gameId && ply == game.moves().size()) {
                showThinking(engine, side, info);
            }
        })).thenAccept(uci -> Platform.runLater(() -> {
            if (id != gameId || ply != game.moves().size() || game.result().isOver()) {
                return;  // stale: new game, undo, or the game ended (e.g. on time) meanwhile
            }
            int move = game.legalMoves().stream().filter(m -> Move.toUci(m).equals(uci)).findFirst().orElse(Move.NONE);
            if (move == Move.NONE) {
                engineLine.setText(engine.name() + " sent an illegal move: " + uci);
            } else {
                play(move);
            }
        }));
    }

    private String positionCommand() {
        String moves = game.moves().stream().map(Move::toUci).collect(Collectors.joining(" "));
        String start = game.startFen().equals(new Game().startFen()) ? "startpos" : "fen " + game.startFen();
        return moves.isEmpty() ? start : start + " moves " + moves;
    }

    private String goArguments() {
        TimeControl tc = setup.timeControl();
        if (!tc.isTimed()) {
            return "movetime " + setup.engineMoveMillis();
        }
        return "wtime " + clock.remainingMillis(Color.WHITE) + " btime " + clock.remainingMillis(Color.BLACK)
                + " winc " + tc.incrementMillis() + " binc " + tc.incrementMillis();
    }

    private void showThinking(UciEngine engine, int side, UciInfo info) {
        // Engines report from their own side; show it from White's point of view, like every chess site.
        String score;
        if (info.mateIn() != null) {
            int mate = side == Color.WHITE ? info.mateIn() : -info.mateIn();
            score = "#" + mate;
        } else {
            int cp = side == Color.WHITE ? info.scoreCp() : -info.scoreCp();
            score = String.format(Locale.ROOT, "%+.2f", cp / 100.0);
        }
        String pv = info.pv().stream().limit(6).collect(Collectors.joining(" "));
        engineLine.setText(engine.name() + "  ·  depth " + info.depth() + "  ·  " + score + "  ·  " + pv);
    }

    private void undo() {
        if (game.moves().isEmpty()) {
            return;
        }
        gameId++;  // invalidates any pending engine reply
        viewPly = -1;
        for (UciEngine engine : engines) {
            if (engine != null) {
                engine.stop();
            }
        }
        // Take back until it is a human's turn again (one ply in human games, two against an engine).
        do {
            game.undo();
        } while (!game.moves().isEmpty() && !setup.isHuman(game.sideToMove()));
        if (setup.timeControl().isTimed() && !game.result().isOver()) {
            clock.start(game.sideToMove());
        }
        engineLine.setText("");
        refresh();
        requestEngineMoveIfNeeded();
    }

    private void flip() {
        board.setFlipped(!board.isFlipped());
        updatePlayerNames();
        refresh();
    }

    private void tick() {
        if (game == null) {
            return;
        }
        int side = game.sideToMove();
        if (!game.result().isOver() && clock.hasFlagged(side)) {
            game.flag(side);
            clock.pause();
            gameId++;
            if (engines[side] != null) {
                engines[side].stop();
            }
            refresh();
            return;
        }
        updateClocks();
    }

    // --- Display ---

    private int bottomColor() {
        return board.isFlipped() ? Color.BLACK : Color.WHITE;
    }

    private void updatePlayerNames() {
        int bottom = bottomColor();
        bottomCard.setPlayer(playerName(bottom), bottom == Color.WHITE);
        topCard.setPlayer(playerName(Color.opposite(bottom)), bottom != Color.WHITE);
    }

    private String playerName(int color) {
        if (pgnNames != null) {
            return pgnNames[color];
        }
        if (setup.isHuman(color)) {
            return setup.isHuman(Color.opposite(color)) ? (color == Color.WHITE ? "White" : "Black") : "You";
        }
        UciEngine engine = engines[color];
        return engine != null ? engine.name() : setup.engine(color).name();
    }

    private void updateClocks() {
        int bottom = bottomColor();
        boolean timed = setup.timeControl().isTimed();
        boolean live = !game.result().isOver();
        int toMove = game.sideToMove();
        bottomCard.update(clock.format(bottom), clock.remainingMillis(bottom), timed, live && toMove == bottom);
        int top = Color.opposite(bottom);
        topCard.update(clock.format(top), clock.remainingMillis(top), timed, live && toMove == top);
    }

    private void refresh() {
        boolean live = viewPly < 0;
        board.setPosition(live ? game : gameAt(viewPly));
        // Moves can only be made on the live position.
        board.setMovableSides(live && setup.isHuman(Color.WHITE), live && setup.isHuman(Color.BLACK));
        status.setText(live ? describe(game)
                : "Move " + viewPly + " of " + game.moves().size() + "  ·  End returns to the game");
        status.pseudoClassStateChanged(OVER, live && game.result().isOver());
        updateClocks();

        Board replay = Fen.parse(game.startFen());
        List<String> san = new ArrayList<>();
        for (int move : game.moves()) {
            san.add(San.format(replay, move));
            replay.makeMove(move);
        }
        String[] startFields = game.startFen().split(" ");
        moveList.show(san, startFields[1].equals("b"), Integer.parseInt(startFields[5]), shownPly());
    }

    /** Ply on the board: the browsed one, or the live position's. */
    private int shownPly() {
        return viewPly < 0 ? game.moves().size() : viewPly;
    }

    /** Shows the position after {@code ply} moves; the last ply returns to the live, playable position. */
    private void browseTo(int ply) {
        int last = game.moves().size();
        int target = Math.max(0, Math.min(last, ply));
        int next = target == last ? -1 : target;
        if (next != viewPly) {
            viewPly = next;
            refresh();
        }
    }

    private Game gameAt(int ply) {
        var view = new Game(game.startFen());
        for (int i = 0; i < ply; i++) {
            view.play(game.moves().get(i));
        }
        return view;
    }

    // --- PGN / FEN ---

    private Map<String, String> pgnTags() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("Event", "Seffir game");
        tags.put("Site", "Seffir desktop app");
        tags.put("Date", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")));
        tags.put("White", playerName(Color.WHITE));
        tags.put("Black", playerName(Color.BLACK));
        TimeControl tc = setup.timeControl();
        tags.put("TimeControl", tc.isTimed() ? tc.baseMillis() / 1000 + "+" + tc.incrementMillis() / 1000 : "-");
        return tags;
    }

    private void copyPgn() {
        putOnClipboard(Pgn.write(game, pgnTags()));
    }

    private void copyFen() {
        putOnClipboard(gameAt(shownPly()).fen());
    }

    private void savePgn() {
        var chooser = new FileChooser();
        chooser.setTitle("Save game");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PGN files", "*.pgn"));
        chooser.setInitialFileName("seffir-" + LocalDate.now() + ".pgn");
        File file = chooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            try {
                Files.writeString(file.toPath(), Pgn.write(game, pgnTags()), StandardCharsets.UTF_8);
            } catch (IOException e) {
                showError("Could not save " + file.getName(), e.getMessage());
            }
        }
    }

    private void openPgn() {
        var chooser = new FileChooser();
        chooser.setTitle("Open game");
        chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("PGN files", "*.pgn"),
                new FileChooser.ExtensionFilter("All files", "*"));
        File file = chooser.showOpenDialog(getScene().getWindow());
        if (file != null) {
            try {
                loadPgn(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            } catch (IOException | IllegalArgumentException e) {
                showError("Could not open " + file.getName(), e.getMessage());
            }
        }
    }

    /** Pasted text is tried as a FEN first, then as PGN. A FEN keeps the current opponents. */
    private void paste() {
        String text = Clipboard.getSystemClipboard().getString();
        if (text == null || text.isBlank()) {
            return;
        }
        Game fromFen;
        try {
            fromFen = new Game(text.strip());
        } catch (IllegalArgumentException notFen) {
            try {
                loadPgn(text);
            } catch (IllegalArgumentException e) {
                showError("The clipboard holds neither a FEN nor a PGN game", e.getMessage());
            }
            return;
        }
        startGame(setup, fromFen);
    }

    /** Loads a game for review between two humans, at its last move, from where it can be continued. */
    public void loadPgn(String text) {
        Pgn.PgnGame pgn = Pgn.readFirst(text);
        startGame(new GameSetup(null, null, TimeControl.UNTIMED, setup.engineMoveMillis()), pgn.game());
        pgnNames = new String[] {pgn.tags().getOrDefault("White", "White"), pgn.tags().getOrDefault("Black", "Black")};
        updatePlayerNames();
    }

    private static void putOnClipboard(String text) {
        var content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void showError(String header, String detail) {
        var alert = new Alert(Alert.AlertType.ERROR, detail);
        alert.initOwner(getScene().getWindow());
        alert.setHeaderText(header);
        if (stylesheet != null) {
            alert.getDialogPane().getStylesheets().add(stylesheet);
        }
        alert.showAndWait();
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
        String by = reason.isEmpty() ? "" : " by " + reason;
        return outcome + by + "  (" + result.outcome().pgn() + ")";
    }

    private void closeEngine(int color) {
        UciEngine engine = engines[color];
        engines[color] = null;
        if (engine != null) {
            engine.close();
        }
    }
}
