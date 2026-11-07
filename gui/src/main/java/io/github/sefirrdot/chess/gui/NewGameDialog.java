package io.github.sefirrdot.chess.gui;

import io.github.sefirrdot.chess.core.board.Color;
import io.github.sefirrdot.chess.gui.clock.TimeControl;
import io.github.sefirrdot.chess.gui.engine.EngineConfig;
import java.io.File;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/** Choose your colour, your opponent (human, Seffir or any UCI engine executable) and the time control. */
final class NewGameDialog extends Dialog<GameSetup> {

    private static final String HUMAN = "Human";
    private static final String LOAD_ENGINE = "Load UCI engine…";

    private final ComboBox<Object> opponent = new ComboBox<>();

    NewGameDialog(Window owner, GameSetup previous, String stylesheet) {
        initOwner(owner);
        setTitle("New game");
        setHeaderText(null);
        getDialogPane().getStylesheets().add(stylesheet);
        getDialogPane().getStyleClass().add("new-game-dialog");

        var colors = new ToggleGroup();
        var white = toggle("White", colors);
        var random = toggle("Random", colors);
        var black = toggle("Black", colors);
        (previous.perspective() == Color.BLACK ? black : white).setSelected(true);
        var colorRow = new HBox(6, white, random, black);
        colorRow.getStyleClass().add("segmented");

        EngineConfig previousEngine = previous.whiteEngine() != null ? previous.whiteEngine() : previous.blackEngine();
        EngineConfig builtIn = EngineConfig.builtIn();
        opponent.getItems().addAll(builtIn, HUMAN);
        if (previousEngine != null && !previousEngine.command().equals(builtIn.command())) {
            opponent.getItems().add(previousEngine);
        }
        opponent.getItems().add(LOAD_ENGINE);
        opponent.setValue(previousEngine == null ? HUMAN
                : opponent.getItems().stream().filter(previousEngine::equals).findFirst().orElse(builtIn));
        opponent.setMaxWidth(Double.MAX_VALUE);
        opponent.setOnAction(e -> {
            if (LOAD_ENGINE.equals(opponent.getValue())) {
                loadEngine().ifPresentOrElse(config -> {
                    opponent.getItems().add(opponent.getItems().size() - 1, config);
                    opponent.setValue(config);
                }, () -> opponent.setValue(builtIn));
            }
        });

        var time = new ComboBox<TimeControl>();
        time.getItems().addAll(TimeControl.PRESETS);
        time.setValue(previous.timeControl());
        time.setMaxWidth(Double.MAX_VALUE);

        var grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        grid.setPadding(new Insets(20, 24, 8, 24));
        grid.addRow(0, label("You play"), colorRow);
        grid.addRow(1, label("Opponent"), opponent);
        grid.addRow(2, label("Time control"), time);
        getDialogPane().setContent(grid);

        var start = new ButtonType("Start game", ButtonType.OK.getButtonData());
        getDialogPane().getButtonTypes().addAll(start, ButtonType.CANCEL);

        setResultConverter(button -> {
            if (button != start) {
                return null;
            }
            EngineConfig engine = opponent.getValue() instanceof EngineConfig config ? config : null;
            boolean humanIsWhite = random.isSelected() ? ThreadLocalRandom.current().nextBoolean() : white.isSelected();
            return humanIsWhite
                    ? new GameSetup(null, engine, time.getValue(), GameSetup.DEFAULT_ENGINE_MOVE_MILLIS)
                    : new GameSetup(engine, null, time.getValue(), GameSetup.DEFAULT_ENGINE_MOVE_MILLIS);
        });
    }

    private Optional<EngineConfig> loadEngine() {
        var chooser = new FileChooser();
        chooser.setTitle("Choose a UCI engine executable");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Engines", "*.exe", "*.bat", "*.cmd", "*.sh", "*"),
                new FileChooser.ExtensionFilter("All files", "*"));
        File file = chooser.showOpenDialog(getDialogPane().getScene().getWindow());
        return Optional.ofNullable(file).map(f -> EngineConfig.executable(f.toPath()));
    }

    private static ToggleButton toggle(String text, ToggleGroup group) {
        var button = new ToggleButton(text);
        button.setToggleGroup(group);
        // Keep exactly one selected: clicking the selected button must not unselect it.
        button.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (button.isSelected()) {
                e.consume();
            }
        });
        return button;
    }

    private static Label label(String text) {
        var label = new Label(text);
        label.getStyleClass().add("form-label");
        return label;
    }
}
