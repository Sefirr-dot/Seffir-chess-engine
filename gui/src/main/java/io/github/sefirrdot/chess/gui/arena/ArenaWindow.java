package io.github.sefirrdot.chess.gui.arena;

import io.github.sefirrdot.chess.core.board.Color;
import io.github.sefirrdot.chess.gui.arena.Match.GameView;
import io.github.sefirrdot.chess.gui.arena.Sprt.Verdict;
import io.github.sefirrdot.chess.gui.board.BoardView;
import io.github.sefirrdot.chess.gui.board.PieceSet;
import io.github.sefirrdot.chess.gui.clock.TimeControl;
import io.github.sefirrdot.chess.gui.engine.EngineConfig;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Spinner;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

/**
 * Match arena: engine-vs-engine games played live, with Elo and SPRT statistics updating after every game.
 *
 * <p>The featured game is shown full size; every running game appears as a thumbnail and can be featured with a click.
 */
public final class ArenaWindow {

    private static final PseudoClass FEATURED = PseudoClass.getPseudoClass("featured");
    private static final int MAX_CONCURRENCY = 8;

    /** Arena time controls: much faster than human ones, since SPRT needs many games. */
    private record ArenaClock(String label, TimeControl control) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static final List<ArenaClock> CLOCKS = List.of(
            new ArenaClock("5s + 0.05s  (bullet test)", new TimeControl(5_000, 50)),
            new ArenaClock("10s + 0.1s  (standard test)", new TimeControl(10_000, 100)),
            new ArenaClock("30s + 0.3s", new TimeControl(30_000, 300)),
            new ArenaClock("60s + 0.6s  (long test)", new TimeControl(60_000, 600)),
            new ArenaClock("3 min + 2s  (exhibition)", TimeControl.minutes(3, 2)));

    private final Stage stage = new Stage();

    // Setup
    private final EngineChooser engineA = new EngineChooser();
    private final EngineChooser engineB = new EngineChooser();
    private final ComboBox<ArenaClock> clock = new ComboBox<>();
    private final Spinner<Integer> concurrency = new Spinner<>(1, MAX_CONCURRENCY,
            Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 4)));
    private final Spinner<Integer> maxGames = new Spinner<>(2, 100_000, 1_000, 2);
    private final CheckBox useSprt = new CheckBox("SPRT");
    private final Spinner<Integer> elo0 = new Spinner<>(-50, 50, 0);
    private final Spinner<Integer> elo1 = new Spinner<>(-50, 50, 5);
    private final Button startStop = new Button("Start match");
    private final Button savePgn = new Button("Save PGN…");

    // Featured game
    private final BoardView featuredBoard = new BoardView(new PieceSet(PieceSet.DEFAULT));
    private final Label featuredTitle = new Label("No match running");
    private final Label topPlayer = new Label();
    private final Label bottomPlayer = new Label();

    // Thumbnails
    private final HBox thumbnails = new HBox(10);
    private final List<Thumbnail> slots = new ArrayList<>();
    private int featuredSlot;

    // Statistics
    private final Label elo = new Label("—");
    private final Label wdl = new Label();
    private final Label details = new Label();
    private final Label sprtText = new Label();
    private final ProgressBar sprtProgress = new ProgressBar(0.5);
    private final Label verdict = new Label();
    private final XYChart.Series<Number, Number> llrSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> upperSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> lowerSeries = new XYChart.Series<>();
    private final ListView<String> log = new ListView<>();

    /** The running match, null when idle. */
    private Match match;
    /** The most recent match, running or not, for saving its games. */
    private Match lastMatch;
    private Match.Config config;

    private final class Thumbnail extends VBox {
        final BoardView board = new BoardView(new PieceSet(PieceSet.DEFAULT));
        final Label caption = new Label("waiting…");
        GameView last;

        Thumbnail(int slot) {
            getStyleClass().add("arena-thumb");
            board.setMovableSides(false, false);
            board.setPrefSize(150, 150);
            board.setMinSize(150, 150);
            board.setMouseTransparent(true);
            caption.getStyleClass().add("arena-thumb-caption");
            getChildren().addAll(board, caption);
            setSpacing(4);
            setOnMouseClicked(e -> feature(slot));
        }
    }

    public ArenaWindow(Window owner, String stylesheet) {
        stage.initOwner(owner);
        stage.setTitle("Seffir · Match arena");

        var root = new BorderPane();
        root.getStyleClass().addAll("main-window", "arena");
        root.setTop(setupBar());
        root.setCenter(featuredPane());
        root.setRight(statsPane());
        root.setBottom(thumbnailStrip());

        var scene = new Scene(root, 1360, 900);
        scene.getStylesheets().add(stylesheet);
        stage.setScene(scene);
        stage.setOnHidden(e -> {
            if (match != null) {
                match.stop();
            }
        });
        featuredBoard.setMovableSides(false, false);
        refreshStats(MatchStats.EMPTY, 0);
    }

    public void show() {
        stage.show();
        stage.toFront();
    }

    // --- Layout ---

    private Region setupBar() {
        clock.getItems().setAll(CLOCKS);
        clock.setValue(CLOCKS.get(1));
        for (Spinner<Integer> s : List.of(concurrency, maxGames, elo0, elo1)) {
            s.setEditable(true);
            s.setPrefWidth(s == maxGames ? 100 : 76);
        }
        useSprt.setSelected(true);
        elo0.disableProperty().bind(useSprt.selectedProperty().not());
        elo1.disableProperty().bind(useSprt.selectedProperty().not());
        startStop.getStyleClass().add("primary");
        startStop.setOnAction(e -> {
            if (match == null) {
                startMatch();
            } else {
                match.stop();
                startStop.setDisable(true);
                startStop.setText("Stopping…");
            }
        });
        savePgn.setOnAction(e -> savePgn());
        savePgn.setDisable(true);

        var grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, label("Engine A (tested)"), engineA, label("Clock"), clock, label("Parallel games"), concurrency,
                useSprt, label("elo0"), elo0);
        grid.addRow(1, label("Engine B (reference)"), engineB, label(""), new Region(), label("Max games"), maxGames,
                new Region(), label("elo1"), elo1);
        engineA.setPrefWidth(240);
        engineB.setPrefWidth(240);
        clock.setPrefWidth(220);

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var buttons = new VBox(8, startStop, savePgn);
        buttons.getChildren().forEach(b -> ((Button) b).setMaxWidth(Double.MAX_VALUE));
        buttons.setPrefWidth(150);
        var bar = new HBox(16, grid, spacer, buttons);
        bar.getStyleClass().add("arena-setup");
        bar.setPadding(new Insets(14, 20, 14, 20));
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private Region featuredPane() {
        featuredTitle.getStyleClass().add("arena-featured-title");
        topPlayer.getStyleClass().add("arena-player");
        bottomPlayer.getStyleClass().add("arena-player");
        var boardHolder = new StackPane(featuredBoard);
        VBox.setVgrow(boardHolder, Priority.ALWAYS);
        var pane = new VBox(8, featuredTitle, topPlayer, boardHolder, bottomPlayer);
        pane.setPadding(new Insets(16, 12, 12, 20));
        return pane;
    }

    private Region statsPane() {
        elo.getStyleClass().add("arena-elo");
        wdl.getStyleClass().add("arena-wdl");
        details.getStyleClass().add("arena-details");
        details.setWrapText(true);
        sprtText.getStyleClass().add("arena-details");
        sprtProgress.setMaxWidth(Double.MAX_VALUE);
        sprtProgress.setPrefHeight(10);
        sprtProgress.setMinHeight(10);
        sprtProgress.getStyleClass().add("arena-sprt-bar");
        verdict.getStyleClass().add("arena-verdict");
        verdict.setWrapText(true);
        verdict.setMinHeight(Region.USE_PREF_SIZE);

        var xAxis = new NumberAxis();
        xAxis.setLabel("games");
        xAxis.setForceZeroInRange(true);
        var yAxis = new NumberAxis();
        yAxis.setLabel("LLR");
        // Default tick labels follow the OS locale ("2,5" in Spanish); chess tools use a decimal point.
        for (NumberAxis axis : List.of(xAxis, yAxis)) {
            axis.setTickLabelFormatter(new StringConverter<>() {
                @Override
                public String toString(Number n) {
                    double v = n.doubleValue();
                    return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
                }

                @Override
                public Number fromString(String text) {
                    return Double.parseDouble(text);
                }
            });
        }
        var chart = new LineChart<>(xAxis, yAxis);
        chart.getStyleClass().add("arena-chart");
        chart.setCreateSymbols(false);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setPrefHeight(230);
        llrSeries.setName("LLR");
        upperSeries.setName("accept H1");
        lowerSeries.setName("accept H0");
        chart.getData().addAll(upperSeries, lowerSeries, llrSeries);

        log.getStyleClass().add("arena-log");
        log.setFocusTraversable(false);
        VBox.setVgrow(log, Priority.ALWAYS);

        var eloHeader = label("ELO DIFFERENCE  (A − B)");
        eloHeader.getStyleClass().add("section-header");
        var sprtHeader = label("SPRT");
        sprtHeader.getStyleClass().add("section-header");
        var logHeader = label("RESULTS");
        logHeader.getStyleClass().add("section-header");
        var pane = new VBox(8, eloHeader, elo, wdl, details, sprtHeader, sprtText, sprtProgress, verdict, chart,
                logHeader, log);
        pane.getStyleClass().add("arena-stats");
        pane.setPadding(new Insets(16, 20, 12, 12));
        pane.setPrefWidth(420);
        return pane;
    }

    private Region thumbnailStrip() {
        thumbnails.setPadding(new Insets(10, 20, 16, 20));
        thumbnails.getStyleClass().add("arena-strip");
        return thumbnails;
    }

    private static Label label(String text) {
        var label = new Label(text);
        label.getStyleClass().add("form-label");
        return label;
    }

    // --- Match control ---

    private void startMatch() {
        EngineConfig a = engineA.selectedEngine();
        EngineConfig b = engineB.selectedEngine();
        Sprt sprt = null;
        if (useSprt.isSelected()) {
            try {
                sprt = new Sprt(elo0.getValue(), elo1.getValue(), 0.05, 0.05);
            } catch (IllegalArgumentException e) {
                verdict.setText("SPRT needs elo0 < elo1.");
                return;
            }
        }
        int parallel = concurrency.getValue();
        config = new Match.Config(a, b, clock.getValue().control(), 0, parallel, maxGames.getValue(), sprt, 400);

        slots.clear();
        thumbnails.getChildren().clear();
        for (int i = 0; i < parallel; i++) {
            var thumb = new Thumbnail(i);
            slots.add(thumb);
            thumbnails.getChildren().add(thumb);
        }
        featuredSlot = 0;
        markFeatured();
        llrSeries.getData().clear();
        log.getItems().clear();
        verdict.setText("");
        refreshStats(MatchStats.EMPTY, 0);
        setSetupDisabled(true);
        startStop.setText("Stop match");
        savePgn.setDisable(true);
        featuredTitle.setText("Starting " + parallel * 2 + " engine processes…");

        match = new Match(config, new Match.Listener() {
            @Override
            public void gameStarted(int slot, GameView view) {
                Platform.runLater(() -> showGame(slot, view));
            }

            @Override
            public void movePlayed(int slot, GameView view) {
                Platform.runLater(() -> showGame(slot, view));
            }

            @Override
            public void gameFinished(int slot, GameView view, MatchStats stats, double llr) {
                Platform.runLater(() -> {
                    showGame(slot, view);
                    log.getItems().add(0, describe(view));
                    llrSeries.getData().add(new XYChart.Data<>(stats.games(), llr));
                    refreshStats(stats, llr);
                });
            }

            @Override
            public void matchFinished(MatchStats stats, Verdict result, String reason) {
                Platform.runLater(() -> {
                    verdict.setText(verdictText(result, reason, stats));
                    verdict.pseudoClassStateChanged(PseudoClass.getPseudoClass("pass"), result == Verdict.ACCEPT_H1);
                    verdict.pseudoClassStateChanged(PseudoClass.getPseudoClass("fail"), result == Verdict.ACCEPT_H0);
                    match = null;
                    setSetupDisabled(false);
                    startStop.setDisable(false);
                    startStop.setText("Start match");
                    savePgn.setDisable(stats.games() == 0);
                });
            }
        });
        lastMatch = match;
        match.start();
    }

    private void setSetupDisabled(boolean disabled) {
        for (var control : List.of(engineA, engineB, clock, concurrency, maxGames, useSprt)) {
            control.setDisable(disabled);
        }
    }

    // --- Display ---

    private void showGame(int slot, GameView view) {
        Thumbnail thumb = slots.get(slot);
        thumb.last = view;
        thumb.board.setPosition(view.game());
        thumb.caption.setText("#" + (view.number() + 1) + "  " + shortResult(view));
        if (slot == featuredSlot) {
            showFeatured(view);
        }
    }

    private void feature(int slot) {
        featuredSlot = slot;
        markFeatured();
        GameView view = slots.get(slot).last;
        if (view != null) {
            showFeatured(view);
        }
    }

    private void markFeatured() {
        for (int i = 0; i < slots.size(); i++) {
            slots.get(i).pseudoClassStateChanged(FEATURED, i == featuredSlot);
        }
    }

    private void showFeatured(GameView view) {
        featuredBoard.setPosition(view.game());
        featuredTitle.setText("Game " + (view.number() + 1) + "  ·  " + view.opening() + "  ·  "
                + (view.game().result().isOver() ? view.game().result().outcome().pgn() : "move "
                + (view.game().moves().size() / 2 + 1)));
        topPlayer.setText("●  " + view.black() + "    " + formatClock(view.blackMillis()));
        bottomPlayer.setText("○  " + view.white() + "    " + formatClock(view.whiteMillis()));
    }

    private void refreshStats(MatchStats stats, double llr) {
        if (stats.games() == 0) {
            elo.setText("—");
        } else {
            double err = stats.eloError95();
            double value = stats.elo();
            // Round first: an even score must read "0.0", not "-0.0" or "+0.0".
            double rounded = Math.round(value * 10) / 10.0;
            String e = !Double.isFinite(value) ? "±∞"
                    : rounded == 0 ? "0.0" : String.format(Locale.ROOT, "%+.1f", rounded);
            elo.setText(Double.isFinite(err) ? e + "  ± " + String.format(Locale.ROOT, "%.1f", err) : e);
        }
        wdl.setText("W " + stats.wins() + "   D " + stats.draws() + "   L " + stats.losses());
        details.setText(String.format(Locale.ROOT, "%d games  ·  score %.1f%%  ·  draws %.0f%%  ·  LOS %.1f%%",
                stats.games(), 100 * stats.score(), 100 * stats.drawRatio(), 100 * stats.los()));

        Sprt sprt = config == null ? null : config.sprt();
        if (sprt == null) {
            sprtText.setText(config == null ? "Configure and start a match." : "Fixed-length match (SPRT off)");
            sprtProgress.setProgress(0);
            upperSeries.getData().clear();
            lowerSeries.getData().clear();
            return;
        }
        sprtText.setText(String.format(Locale.ROOT, "%s   LLR %.2f  in [%.2f, %.2f]", sprt, llr,
                sprt.lowerBound(), sprt.upperBound()));
        double span = sprt.upperBound() - sprt.lowerBound();
        sprtProgress.setProgress(Math.max(0, Math.min(1, (llr - sprt.lowerBound()) / span)));
        int x = Math.max(10, stats.games());
        upperSeries.getData().setAll(new XYChart.Data<>(0, sprt.upperBound()), new XYChart.Data<>(x, sprt.upperBound()));
        lowerSeries.getData().setAll(new XYChart.Data<>(0, sprt.lowerBound()), new XYChart.Data<>(x, sprt.lowerBound()));
    }

    /** One log line, compact enough for the panel: {@code #23  0-1  B-A  checkmate · Alekhine Defence}. */
    private static String describe(GameView view) {
        var result = view.game().result();
        String reason = result.reason().name().toLowerCase().replace('_', ' ');
        String pairing = view.white().endsWith("(A)") ? "A-B" : "B-A";
        return String.format("#%-4d %-7s %s  %s · %s", view.number() + 1, result.outcome().pgn(), pairing, reason,
                view.opening());
    }

    private static String shortResult(GameView view) {
        var result = view.game().result();
        return result.isOver() ? result.outcome().pgn() : (view.game().sideToMove() == Color.WHITE ? "○" : "●")
                + " move " + (view.game().moves().size() / 2 + 1);
    }

    private static String verdictText(Verdict result, String reason, MatchStats stats) {
        return switch (result) {
            case ACCEPT_H1 -> "✔ PASSED — engine A is stronger (SPRT accepted H1 after " + stats.games() + " games).";
            case ACCEPT_H0 -> "✘ FAILED — no proven gain (SPRT accepted H0 after " + stats.games() + " games).";
            case CONTINUE -> "Match ended: " + reason + " after " + stats.games() + " games.";
        };
    }

    private static String formatClock(long millis) {
        long seconds = millis / 1000;
        return millis < 60_000
                ? String.format(Locale.ROOT, "%d.%d s", seconds, (millis / 100) % 10)
                : String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private void savePgn() {
        Match finished = lastMatch;
        if (finished == null) {
            return;
        }
        var chooser = new FileChooser();
        chooser.setTitle("Save match games");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PGN files", "*.pgn"));
        chooser.setInitialFileName("arena-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
                + ".pgn");
        File file = chooser.showSaveDialog(stage);
        if (file != null) {
            try {
                Files.writeString(file.toPath(), finished.pgn(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                verdict.setText("Could not save: " + e.getMessage());
            }
        }
    }

    /** Engine picker: the built-in Seffir, or any UCI executable / jar loaded from disk. */
    private final class EngineChooser extends ComboBox<Object> {
        private static final String LOAD = "Load engine (exe or jar)…";

        EngineChooser() {
            EngineConfig builtIn = EngineConfig.builtIn();
            getItems().addAll(builtIn, LOAD);
            setValue(builtIn);
            setOnAction(e -> {
                if (LOAD.equals(getValue())) {
                    var chooser = new FileChooser();
                    chooser.setTitle("Choose a UCI engine");
                    chooser.getExtensionFilters().addAll(
                            new FileChooser.ExtensionFilter("Engines", "*.exe", "*.jar", "*.bat", "*.cmd", "*.sh", "*"));
                    File file = chooser.showOpenDialog(stage);
                    if (file == null) {
                        setValue(builtIn);
                    } else {
                        EngineConfig config = EngineConfig.executable(file.toPath());
                        getItems().add(getItems().size() - 1, config);
                        setValue(config);
                    }
                }
            });
        }

        EngineConfig selectedEngine() {
            return getValue() instanceof EngineConfig config ? config : EngineConfig.builtIn();
        }
    }
}
