package io.github.sefirrdot.chess.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.sefirrdot.chess.core.move.Move;
import io.github.sefirrdot.chess.core.move.MoveList;
import io.github.sefirrdot.chess.core.movegen.MoveGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MakeUnmakeTest {

    /** Plays one move and checks the resulting FEN, then unmakes it and checks the original FEN is restored. */
    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiter = '|', value = {
        // quiet rook move: halfmove clock ticks, fullmove does not (white moved), queenside right lost
        "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 3 10 | Rb1 | a1b1 0 | r3k2r/8/8/8/8/8/8/1R2K2R b Kkq - 4 10",
        // double pawn push sets en passant square and resets the clock
        Fen.STARTING_POSITION + " | e2e4 | e2e4 1 | rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
        // black move increments fullmove
        "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1 | c7c5 | c7c5 1 | rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2",
        // en passant removes the pawn beside, not on the target square
        "rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3 | ep | e5d6 5 | rnbqkbnr/ppp1pppp/3P4/8/8/8/PPPP1PPP/RNBQKBNR b KQkq - 0 3",
        "rnbqkbnr/pppp1ppp/8/8/3Pp3/8/PPP1PPPP/RNBQKBNR b KQkq d3 0 3 | ep black | e4d3 5 | rnbqkbnr/pppp1ppp/8/8/8/3p4/PPP1PPPP/RNBQKBNR w KQkq - 0 4",
        // castling moves the rook and clears both rights of that side
        "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1 | O-O | e1g1 2 | r3k2r/8/8/8/8/8/8/R4RK1 b kq - 1 1",
        "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1 | O-O-O | e1c1 3 | r3k2r/8/8/8/8/8/8/2KR3R b kq - 1 1",
        "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1 | black O-O | e8g8 2 | r4rk1/8/8/8/8/8/8/R3K2R w KQ - 1 2",
        "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1 | black O-O-O | e8c8 3 | 2kr3r/8/8/8/8/8/8/R3K2R w KQ - 1 2",
        // capturing a rook on its home square removes the opponent's right
        "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1 | Rxa8 | a1a8 4 | R3k2r/8/8/8/8/8/8/4K2R b Kk - 0 1",
        // promotions, quiet and capturing
        "4k3/1P6/8/8/8/8/8/4K3 w - - 0 1 | b8=Q | b7b8 11 | 1Q2k3/8/8/8/8/8/8/4K3 b - - 0 1",
        "r3k3/1P6/8/8/8/8/8/4K3 w q - 0 1 | bxa8=N | b7a8 12 | N3k3/8/8/8/8/8/8/4K3 b - - 0 1",
    })
    void makeThenUnmake(String fen, String description, String moveSpec, String expectedFen) {
        String[] parts = moveSpec.trim().split(" ");
        int move = Move.of(Square.parse(parts[0].substring(0, 2)), Square.parse(parts[0].substring(2, 4)),
                Integer.parseInt(parts[1]));
        Board board = Fen.parse(fen.trim());

        board.makeMove(move);
        BoardTest.assertConsistent(board);
        assertEquals(expectedFen.trim(), Fen.format(board));

        board.unmakeMove();
        BoardTest.assertConsistent(board);
        assertEquals(fen.trim(), Fen.format(board));
        assertEquals(0, board.ply());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        Fen.STARTING_POSITION,
        "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
        "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
        "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
    })
    void randomGamesUnwindToTheStartingFen(String fen) {
        var random = new SplittableRandom(fen.hashCode());
        var moves = new MoveList();
        for (int game = 0; game < 50; game++) {
            Board board = Fen.parse(fen);
            List<String> fens = new ArrayList<>();
            for (int ply = 0; ply < 300; ply++) {
                MoveGenerator.generateLegal(board, moves);
                if (moves.size() == 0) {
                    break;
                }
                fens.add(Fen.format(board));
                board.makeMove(moves.get(random.nextInt(moves.size())));
                BoardTest.assertConsistent(board);
            }
            for (int i = fens.size() - 1; i >= 0; i--) {
                board.unmakeMove();
                assertEquals(fens.get(i), Fen.format(board));
            }
            assertEquals(0, board.ply());
        }
    }

    @Test
    void unmakeWithoutHistoryFails() {
        assertThrows(IllegalStateException.class, () -> Board.startingPosition().unmakeMove());
    }

    @Test
    void historyGrowsBeyondInitialCapacity() {
        // Knights shuffling back and forth: 1000 plies without ever repeating a capture or pawn move.
        Board board = Board.startingPosition();
        int[] cycle = {
            Move.of(Square.G1, Square.F3, Move.QUIET), Move.of(Square.G8, Square.F6, Move.QUIET),
            Move.of(Square.F3, Square.G1, Move.QUIET), Move.of(Square.F6, Square.G8, Move.QUIET),
        };
        for (int i = 0; i < 1000; i++) {
            board.makeMove(cycle[i % 4]);
        }
        assertEquals(1000, board.ply());
        for (int i = 0; i < 1000; i++) {
            board.unmakeMove();
        }
        assertEquals(Fen.STARTING_POSITION, Fen.format(board));
    }
}
