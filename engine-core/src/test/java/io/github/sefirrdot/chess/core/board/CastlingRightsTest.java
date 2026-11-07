package io.github.sefirrdot.chess.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CastlingRightsTest {

    @ParameterizedTest
    @ValueSource(strings = {"-", "K", "Q", "k", "q", "KQ", "Kq", "Qk", "KQkq", "kq", "KQk"})
    void fenRoundTrip(String field) {
        assertEquals(field, CastlingRights.toFen(CastlingRights.fromFen(field)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "KK", "QK", "qk", "KQkqK", "x", "K-"})
    void rejectsMalformedFields(String field) {
        assertThrows(IllegalArgumentException.class, () -> CastlingRights.fromFen(field));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 4, 8, 15})
    void bitsMatchFenLetters(int rights) {
        assertEquals(rights, CastlingRights.fromFen(CastlingRights.toFen(rights)));
    }
}
