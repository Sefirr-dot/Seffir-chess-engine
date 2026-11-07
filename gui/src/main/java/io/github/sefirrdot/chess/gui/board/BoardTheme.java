package io.github.sefirrdot.chess.gui.board;

import javafx.scene.paint.Color;

/** Colours of the board and its highlights. */
public record BoardTheme(
        String name,
        Color lightSquare,
        Color darkSquare,
        Color lastMove,
        Color selected,
        Color moveHint,
        Color check) {

    /** Default: cool slate blue, easy on the eyes on a dark UI. */
    public static final BoardTheme SLATE = new BoardTheme("Slate",
            Color.web("#dfe5ea"), Color.web("#7f97ab"),
            Color.web("#c9d86a", 0.55), Color.web("#5fa8d3", 0.55),
            Color.web("#1e2a35", 0.32), Color.web("#ff3b30"));

    /** Classic tournament wood. */
    public static final BoardTheme WALNUT = new BoardTheme("Walnut",
            Color.web("#ecd8b4"), Color.web("#b0835a"),
            Color.web("#cdd26a", 0.6), Color.web("#5fa8d3", 0.5),
            Color.web("#3a2a1a", 0.3), Color.web("#ff3b30"));

    /** Tournament green, high contrast. */
    public static final BoardTheme GREEN = new BoardTheme("Green",
            Color.web("#eeeed2"), Color.web("#769656"),
            Color.web("#f6f669", 0.6), Color.web("#5fa8d3", 0.5),
            Color.web("#1f2a14", 0.28), Color.web("#ff3b30"));

    public static final BoardTheme[] ALL = {SLATE, WALNUT, GREEN};

    @Override
    public String toString() {
        return name;
    }
}
