package io.github.sefirrdot.chess.core;

/**
 * Static identity of the engine, reported to GUIs through the UCI {@code id} command.
 */
public final class EngineInfo {

    public static final String NAME = "Seffir";
    public static final String VERSION = "0.1.0-dev";
    public static final String AUTHOR = "Miguel Leon";

    private EngineInfo() {
    }

    public static String fullName() {
        return NAME + " " + VERSION;
    }
}
