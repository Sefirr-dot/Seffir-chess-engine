package io.github.sefirrdot.chess.gui;

import io.github.sefirrdot.chess.core.EngineInfo;
import io.github.sefirrdot.chess.core.board.Color;
import io.github.sefirrdot.chess.core.game.Game;
import io.github.sefirrdot.chess.core.game.GameResult;
import io.github.sefirrdot.chess.core.move.Move;
import io.github.sefirrdot.chess.gui.board.BoardTheme;
import io.github.sefirrdot.chess.gui.board.BoardView;
import io.github.sefirrdot.chess.gui.board.PieceSet;
import io.github.sefirrdot.chess.gui.clock.ChessClock;
import io.github.sefirrdot.chess.gui.clock.TimeControl;
import io.github.sefirrdot.chess.gui.engine.EngineConfig;
import io.github.sefirrdot.chess.gui.engine.UciEngine;
import io.github.sefirrdot.chess.gui.engine.UciInfo;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
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
    private final ListView<String> moveList = new ListView<>();
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
    private String stylesheet;

    public MainWindow() {
        getStyleClass().add("main-window");

        var boardHolder = new StackPane(board);
        boardHolder.getStyleClass().add("board-holder");
        boardHolder.setPadding(new Insets(24));
        setCenter(boardHolder);
        setRight(sidePanel());

        board.setOnMove(this::humanMove);
        ticker.setCycleCount(Timeline.INDEFINITE);
        ticker.play();
        startGame(GameSetup.defaultSetup());
    }

    public void installShortcuts(Scene scene, String stylesheet) {
        this.stylesheet = stylesheet;
        var accelerators = scene.getAccelerators();
        accelerators.put(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN), this::showNewGameDialog);
        accelerators.put(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN), this::undo);
        accelerators.put(new KeyCodeCombination(KeyCode.F), this::flip);
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
        moveList.getStyleClass().add("move-list");
        moveList.setFocusTraversable(false);
        VBox.setVgrow(moveList, Priority.ALWAYS);

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

        var panel = new VBox(12, header, topCard, status, engineLine, movesHeader, moveList, bottomCard, buttons,
                themeHeader, themes);
        panel.getStyleClass().add("side-panel");
        panel.setPadding(new Insets(24, 24, 24, 8));
        panel.setPrefWidth(340);
        panel.setMinWidth(280);
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

    // --- Game flow ---

    private void showNewGameDialog() {
        new NewGameDialog(getScene().getWindow(), setup, stylesheet).showAndWait().ifPresent(this::startGame);
    }

    /** Starts a new game with the given players and time control. */
    public void startGame(GameSetup newSetup) {
        gameId++;
        setup = newSetup;
        game = new Game();
        clock = new ChessClock(setup.timeControl());
        engineLine.setText("");
        board.setFlipped(setup.perspective() == Color.BLACK);
        board.setMovableSides(setup.isHuman(Color.WHITE), setup.isHuman(Color.BLACK));
        updatePlayerNames();
        if (setup.timeControl().isTimed()) {
            clock.start(Color.WHITE);
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
        board.setPosition(game);
        status.setText(describe(game));
        status.pseudoClassStateChanged(OVER, game.result().isOver());
        updateClocks();

        List<Integer> moves = game.moves();
        var rows = moveList.getItems();
        rows.clear();
        String[] startFields = game.startFen().split(" ");
        int offset = startFields[1].equals("b") ? 1 : 0;
        int fullmove = Integer.parseInt(startFields[5]);
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

    private void closeEngine(int color) {
        UciEngine engine = engines[color];
        engines[color] = null;
        if (engine != null) {
            engine.close();
        }
    }
}
