package io.github.sefirrdot.chess.gui;

import io.github.sefirrdot.chess.core.board.Color;
import io.github.sefirrdot.chess.gui.clock.TimeControl;
import io.github.sefirrdot.chess.gui.engine.EngineConfig;

/**
 * Who plays a new game and at what time control.
 *
 * @param whiteEngine   engine playing White, or {@code null} for a human
 * @param blackEngine   engine playing Black, or {@code null} for a human
 * @param timeControl   clock settings; when untimed, engines think {@code engineMoveMillis} per move
 * @param engineMoveMillis thinking time per move for engines in untimed games
 */
public record GameSetup(EngineConfig whiteEngine, EngineConfig blackEngine, TimeControl timeControl,
                        long engineMoveMillis) {

    public static final long DEFAULT_ENGINE_MOVE_MILLIS = 2_000;

    /** Startup default: you play White against Seffir, no clock. */
    public static GameSetup defaultSetup() {
        return new GameSetup(null, EngineConfig.builtIn(), TimeControl.UNTIMED, DEFAULT_ENGINE_MOVE_MILLIS);
    }

    public EngineConfig engine(int color) {
        return color == Color.WHITE ? whiteEngine : blackEngine;
    }

    public boolean isHuman(int color) {
        return engine(color) == null;
    }

    /** The side shown at the bottom of the board: the human's, or White if both or neither are human. */
    public int perspective() {
        return isHuman(Color.BLACK) && !isHuman(Color.WHITE) ? Color.BLACK : Color.WHITE;
    }
}
