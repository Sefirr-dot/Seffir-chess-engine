package io.github.sefirrdot.chess.core.game;

import io.github.sefirrdot.chess.core.board.Bitboard;
import io.github.sefirrdot.chess.core.board.Board;
import io.github.sefirrdot.chess.core.board.Color;
import io.github.sefirrdot.chess.core.board.Fen;
import io.github.sefirrdot.chess.core.board.PieceType;
import io.github.sefirrdot.chess.core.game.GameResult.Reason;
import io.github.sefirrdot.chess.core.move.Move;
import io.github.sefirrdot.chess.core.move.MoveList;
import io.github.sefirrdot.chess.core.movegen.MoveGenerator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A game from a starting position: the moves played and how it stands under the rules of chess.
 *
 * <p>Only legal moves are accepted. The result is derived from the position (checkmate, stalemate, threefold
 * repetition, fifty-move rule, insufficient material) unless one was declared from outside, such as a loss on time or
 * a resignation. Used by the GUI for human games and by the match arena to referee engine games.
 */
public final class Game {

    private final String startFen;
    private final Board board;
    private final List<Integer> moves = new ArrayList<>();
    /** Hash of every position reached, starting position included, for threefold repetition. */
    private final List<Long> positionHashes = new ArrayList<>();
    private final MoveList legalMoves = new MoveList();
    private GameResult declaredResult;

    public Game() {
        this(Fen.STARTING_POSITION);
    }

    public Game(String fen) {
        this.board = Fen.parse(fen);
        this.startFen = Fen.format(board);
        positionHashes.add(board.hash());
        refreshLegalMoves();
    }

    public String startFen() {
        return startFen;
    }

    /** A copy of the current position: changing it does not affect the game. */
    public Board position() {
        return board.copy();
    }

    public String fen() {
        return Fen.format(board);
    }

    public int sideToMove() {
        return board.sideToMove();
    }

    /** Pieces as {@link io.github.sefirrdot.chess.core.board.Piece} codes, without copying the board. */
    public int pieceAt(int square) {
        return board.pieceAt(square);
    }

    public boolean isInCheck() {
        return MoveGenerator.isInCheck(board);
    }

    public List<Integer> moves() {
        return Collections.unmodifiableList(moves);
    }

    public int lastMove() {
        return moves.isEmpty() ? Move.NONE : moves.get(moves.size() - 1);
    }

    /** Legal moves in the current position; empty once the game is over. */
    public List<Integer> legalMoves() {
        if (result().isOver()) {
            return List.of();
        }
        List<Integer> list = new ArrayList<>(legalMoves.size());
        for (int i = 0; i < legalMoves.size(); i++) {
            list.add(legalMoves.get(i));
        }
        return list;
    }

    public boolean isLegal(int move) {
        return !result().isOver() && legalMoves.contains(move);
    }

    /** @throws IllegalArgumentException if the move is not legal or the game is over */
    public void play(int move) {
        if (!isLegal(move)) {
            throw new IllegalArgumentException("Illegal move " + Move.toUci(move) + " in " + fen());
        }
        board.makeMove(move);
        moves.add(move);
        positionHashes.add(board.hash());
        refreshLegalMoves();
    }

    /** Plays a move given in UCI notation ({@code e2e4}, {@code e7e8q}). */
    public void play(String uci) {
        int move = MoveGenerator.parseUciMove(board, uci);
        if (move == Move.NONE) {
            throw new IllegalArgumentException("Illegal move " + uci + " in " + fen());
        }
        play(move);
    }

    /** Takes back the last move (and any declared result). */
    public void undo() {
        if (moves.isEmpty()) {
            throw new IllegalStateException("No move to undo");
        }
        board.unmakeMove();
        moves.remove(moves.size() - 1);
        positionHashes.remove(positionHashes.size() - 1);
        declaredResult = null;
        refreshLegalMoves();
    }

    /**
     * {@code color} ran out of time. The opponent wins, unless it has nothing left to mate with (a bare king, or a king
     * and a single minor piece), in which case the game is drawn.
     */
    public void flag(int color) {
        int opponent = Color.opposite(color);
        long others = board.occupancy(opponent) & ~board.pieces(opponent, PieceType.KING);
        boolean opponentCanMate = Bitboard.count(others) > 1
                || (others & (board.pieces(opponent, PieceType.PAWN) | board.pieces(opponent, PieceType.ROOK)
                        | board.pieces(opponent, PieceType.QUEEN))) != 0;
        declareResult(opponentCanMate
                ? GameResult.win(opponent == Color.WHITE, Reason.TIMEOUT)
                : GameResult.draw(Reason.TIMEOUT));
    }

    /** Ends the game for a reason the position cannot show: time forfeit, resignation, adjudication. */
    public void declareResult(GameResult result) {
        if (!result.isOver()) {
            throw new IllegalArgumentException("Declared result must end the game");
        }
        this.declaredResult = result;
    }

    public GameResult result() {
        if (declaredResult != null) {
            return declaredResult;
        }
        if (legalMoves.size() == 0) {
            return isInCheck()
                    ? GameResult.win(board.sideToMove() == Color.BLACK, Reason.CHECKMATE)
                    : GameResult.draw(Reason.STALEMATE);
        }
        if (repetitionCount() >= 3) {
            return GameResult.draw(Reason.THREEFOLD_REPETITION);
        }
        if (board.isFiftyMoveDraw()) {
            return GameResult.draw(Reason.FIFTY_MOVE_RULE);
        }
        if (board.hasInsufficientMaterial()) {
            return GameResult.draw(Reason.INSUFFICIENT_MATERIAL);
        }
        return GameResult.ONGOING;
    }

    /** How many times the current position has occurred in this game, the current occurrence included. */
    public int repetitionCount() {
        long current = board.hash();
        int count = 0;
        for (long hash : positionHashes) {
            if (hash == current) {
                count++;
            }
        }
        return count;
    }

    private void refreshLegalMoves() {
        MoveGenerator.generateLegal(board, legalMoves);
    }
}
