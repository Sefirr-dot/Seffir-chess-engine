package io.github.sefirrdot.chess.gui;

import io.github.sefirrdot.chess.core.EngineInfo;
import java.util.Objects;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class ChessApp extends Application {

    static final String STYLESHEET = "/io/github/sefirrdot/chess/gui/seffir.css";

    private MainWindow window;

    @Override
    public void start(Stage stage) {
        String css = Objects.requireNonNull(ChessApp.class.getResource(STYLESHEET)).toExternalForm();
        window = new MainWindow();
        var scene = new Scene(window, 1200, 800);
        scene.getStylesheets().add(css);
        window.installShortcuts(scene, css);

        stage.setTitle(EngineInfo.NAME);
        stage.setMinWidth(780);
        stage.setMinHeight(540);
        stage.setScene(scene);
        stage.show();
    }

    /** Quits the engine processes so none is left running after the window closes. */
    @Override
    public void stop() {
        if (window != null) {
            window.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
