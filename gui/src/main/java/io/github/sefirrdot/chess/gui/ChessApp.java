package io.github.sefirrdot.chess.gui;

import io.github.sefirrdot.chess.core.EngineInfo;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public final class ChessApp extends Application {

    @Override
    public void start(Stage stage) {
        var root = new StackPane(new Label(EngineInfo.fullName()));
        stage.setTitle(EngineInfo.NAME);
        stage.setScene(new Scene(root, 960, 720));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
