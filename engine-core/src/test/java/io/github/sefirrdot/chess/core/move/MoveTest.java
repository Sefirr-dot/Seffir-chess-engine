package io.github.sefirrdot.chess.core.move;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sefirrdot.chess.core.board.PieceType;
import io.github.sefirrdot.chess.core.board.Square;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoveTest {

    @Test
    void encodesAndDecodesEveryFromToFlagCombination() {
        for (int from = 0; from < 64; from++) {
            for (int to = 0; to < 64; to++) {
                for (int flag = 0; flag < 16; flag++) {
                    int move = Move.of(from, to, flag);
                    assertEquals(from, Move.from(move));
                    assertEquals(to, Move.to(move));
                    assertEquals(flag, Move.flag(move));
                    assertTrue(move >= 0 && move < (1 << 16), "fits in 16 bits");
                }
            }
        }
    }

    @ParameterizedTest(name = "flag {0}: capture={1} promotion={2} castle={3}")
    @CsvSource({
        "0,  false, false, false",
        "1,  false, false, false",
        "2,  false, false, true",
        "3,  false, false, true",
        "4,  true,  false, false",
        "5,  true,  false, false",
        "8,  false, true,  false",
        "11, false, true,  false",
        "12, true,  true,  false",
        "15, true,  true,  false",
    })
    void flagPredicates(int flag, boolean capture, boolean promotion, boolean castle) {
        int move = Move.of(Square.E2, Square.E4, flag);
        assertEquals(capture, Move.isCapture(move));
        assertEquals(promotion, Move.isPromotion(move));
        assertEquals(castle, Move.isCastle(move));
    }

    @Test
    void promotionTypeFollowsFlagLowBits() {
        assertEquals(PieceType.KNIGHT, Move.promotionType(Move.of(Square.A7, Square.A8, Move.PROMO_KNIGHT)));
        assertEquals(PieceType.BISHOP, Move.promotionType(Move.of(Square.A7, Square.A8, Move.PROMO_BISHOP)));
        assertEquals(PieceType.ROOK, Move.promotionType(Move.of(Square.A7, Square.B8, Move.PROMO_CAPTURE_ROOK)));
        assertEquals(PieceType.QUEEN, Move.promotionType(Move.of(Square.A7, Square.B8, Move.PROMO_CAPTURE_QUEEN)));
    }

    @Test
    void uciNotation() {
        assertEquals("e2e4", Move.toUci(Move.of(Square.E2, Square.E4, Move.DOUBLE_PAWN_PUSH)));
        assertEquals("e1g1", Move.toUci(Move.of(Square.E1, Square.G1, Move.KING_CASTLE)));
        assertEquals("e7e8q", Move.toUci(Move.of(Square.E7, Square.E8, Move.PROMO_QUEEN)));
        assertEquals("b2a1n", Move.toUci(Move.of(Square.B2, Square.A1, Move.PROMO_CAPTURE_KNIGHT)));
        assertEquals("0000", Move.toUci(Move.NONE));
    }

    @Test
    void noneIsNotARealMove() {
        assertEquals(Move.from(Move.NONE), Move.to(Move.NONE));
        assertFalse(Move.isCapture(Move.NONE));
    }
}
