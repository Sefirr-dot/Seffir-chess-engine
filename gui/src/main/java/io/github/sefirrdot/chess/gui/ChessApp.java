package io.github.sefirrdot.chess.gui;

import io.github.sefirrdot.chess.core.EngineInfo;
import java.util.Objects;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class ChessApp extends Application {

    static final String STYLESHEET = "/io/github/sefirrdot/chess/gui/seffir.css";

    @Override
    public void start(Stage stage) {
        var window = new MainWindow();
        var scene = new Scene(window, 1180, 780);
        scene.getStylesheets().add(Objects.requireNonNull(ChessApp.class.getResource(STYLESHEET)).toExternalForm());
        window.installShortcuts(scene);

        stage.setTitle(EngineInfo.NAME);
        stage.setMinWidth(760);
        stage.setMinHeight(520);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
