package io.github.sefirrdot.chess.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SquareTest {

    @Test
    void cornersFollowLittleEndianRankFileMapping() {
        assertEquals(0, Square.A1);
        assertEquals(7, Square.H1);
        assertEquals(56, Square.A8);
        assertEquals(63, Square.H8);
        assertEquals(Square.E4, Square.of(4, 3));
    }

    @Test
    void fileAndRankAreInverseOfOf() {
        for (int sq = 0; sq < Square.COUNT; sq++) {
            assertEquals(sq, Square.of(Square.file(sq), Square.rank(sq)));
        }
    }

    @Test
    void nameAndParseRoundTripForEverySquare() {
        for (int sq = 0; sq < Square.COUNT; sq++) {
            assertEquals(sq, Square.parse(Square.name(sq)));
        }
        assertEquals("e4", Square.name(Square.E4));
        assertEquals(Square.H8, Square.parse("h8"));
    }

    @Test
    void validity() {
        assertTrue(Square.isValid(0));
        assertTrue(Square.isValid(63));
        assertFalse(Square.isValid(64));
        assertFalse(Square.isValid(Square.NONE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "e", "e44", "i1", "a0", "a9", "E4"})
    void parseRejectsInvalidNames(String name) {
        assertThrows(IllegalArgumentException.class, () -> Square.parse(name));
    }

    @Test
    void nameRejectsInvalidIndex() {
        assertThrows(IllegalArgumentException.class, () -> Square.name(64));
    }
}
