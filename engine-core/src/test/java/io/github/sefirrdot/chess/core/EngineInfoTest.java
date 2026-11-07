package io.github.sefirrdot.chess.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EngineInfoTest {

    @Test
    void fullNameCombinesNameAndVersion() {
        assertEquals(EngineInfo.NAME + " " + EngineInfo.VERSION, EngineInfo.fullName());
    }
}
