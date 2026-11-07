package io.github.sefirrdot.chess.gui;

/**
 * Plain entry point. When the main class extends {@code Application}, the JavaFX launcher insists on JavaFX being on
 * the module path; starting through a class that does not lets the app also run from a classpath / fat jar.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        ChessApp.main(args);
    }
}
