package io.github.sefirrdot.chess.gui;

import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/** A player's name and clock. Highlighted while it is that player's turn; the clock turns red when time runs low. */
final class PlayerCard extends HBox {

    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
    private static final PseudoClass LOW_TIME = PseudoClass.getPseudoClass("low-time");
    private static final long LOW_TIME_MILLIS = 20_000;

    private final Label colorDot = new Label();
    private final Label name = new Label();
    private final Label clock = new Label();

    PlayerCard() {
        getStyleClass().add("player-card");
        colorDot.getStyleClass().add("color-dot");
        name.getStyleClass().add("player-name");
        clock.getStyleClass().add("clock");
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        getChildren().addAll(colorDot, name, spacer, clock);
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(10);
    }

    void setPlayer(String playerName, boolean white) {
        name.setText(playerName);
        colorDot.getStyleClass().removeAll("white", "black");
        colorDot.getStyleClass().add(white ? "white" : "black");
    }

    void update(String clockText, long remainingMillis, boolean timed, boolean active) {
        clock.setText(clockText);
        pseudoClassStateChanged(ACTIVE, active);
        clock.pseudoClassStateChanged(LOW_TIME, timed && remainingMillis < LOW_TIME_MILLIS);
    }
}
