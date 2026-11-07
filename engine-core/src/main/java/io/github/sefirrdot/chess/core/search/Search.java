package io.github.sefirrdot.chess.core.search;

import io.github.sefirrdot.chess.core.board.Board;
import io.github.sefirrdot.chess.core.board.Piece;
import io.github.sefirrdot.chess.core.board.PieceType;
import io.github.sefirrdot.chess.core.eval.Evaluator;
import io.github.sefirrdot.chess.core.move.Move;
import io.github.sefirrdot.chess.core.move.MoveList;
import io.github.sefirrdot.chess.core.movegen.MoveGenerator;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Alpha-beta search.
 *
 * <ul>
 *   <li><b>Negamax</b>: one function for both sides, since a position worth {@code +x} for one side is worth
 *       {@code -x} for the other.
 *   <li><b>Alpha-beta pruning</b>: once a move is shown to be worse than an alternative already found higher in the
 *       tree, its remaining replies are skipped. With good move ordering this roughly squares the reachable depth.
 *   <li><b>Iterative deepening</b>: search depth 1, 2, 3... until time runs out. Each iteration's best move is tried
 *       first in the next one, which makes the deeper search cheaper, and there is always a move ready to play.
 *   <li><b>Quiescence search</b>: at the horizon, keep searching captures until the position is quiet, so the engine
 *       does not stop counting in the middle of an exchange (the "horizon effect").
 *   <li><b>Check extension</b>: positions in check are searched one ply deeper.
 * </ul>
 *
 * <p>A {@code Search} instance runs one search on its own copy of the board; {@link #stop()} may be called from
 * another thread.
 */
public final class Search {

    public static final int MAX_PLY = 128;
    public static final int INFINITY = 32_000;
    /** Score of being mated right now; mate in {@code n} plies scores {@code MATE - n}. */
    public static final int MATE = 31_000;

    private static final int CHECK_EVERY_NODES = 1024;

    // Move ordering scores: previous best move, then captures by MVV-LVA, then promotions, then quiet moves.
    private static final int SCORE_BEST_MOVE = 1_000_000;
    private static final int SCORE_CAPTURE = 100_000;
    private static final int SCORE_PROMOTION = 90_000;

    private final Board board;
    private final TranspositionTable tt;
    private final Consumer<SearchInfo> listener;
    private volatile boolean stopRequested;

    private final MoveList[] moveLists = new MoveList[MAX_PLY + 1];
    private final int[][] moveScores = new int[MAX_PLY + 1][MoveList.CAPACITY];
    // Triangular principal variation table: pv[ply] holds the best line found from ply onwards.
    private final int[][] pv = new int[MAX_PLY + 1][MAX_PLY + 1];
    private final int[] pvLength = new int[MAX_PLY + 1];

    private long nodes;
    private long startNanos;
    private long hardDeadlineMillis;
    private long nodeLimit;
    private boolean aborted;
    private int rootBestMove = Move.NONE;

    /**
     * @param tt       shared transposition table; it outlives a single search so later moves of a game reuse it
     * @param listener receives a {@link SearchInfo} after every completed iteration (called on the search thread)
     */
    public Search(Board board, TranspositionTable tt, Consumer<SearchInfo> listener) {
        this.board = board.copy();
        this.tt = tt;
        this.listener = listener;
        for (int i = 0; i <= MAX_PLY; i++) {
            moveLists[i] = new MoveList();
        }
    }

    /** A search with its own small transposition table. */
    public Search(Board board, Consumer<SearchInfo> listener) {
        this(board, new TranspositionTable(8), listener);
    }

    public Search(Board board) {
        this(board, info -> { });
    }

    /** Asks a running search to return as soon as possible. Safe to call from any thread, before or during search. */
    public void stop() {
        stopRequested = true;
    }

    public static boolean isMateScore(int score) {
        return Math.abs(score) >= MATE - MAX_PLY;
    }

    /**
     * Runs iterative deepening within {@code limits}.
     *
     * @return the result of the deepest completed iteration; its best move is {@link Move#NONE} only when the position
     *     has no legal moves
     */
    public SearchInfo search(SearchLimits limits) {
        startNanos = System.nanoTime();
        TimeManager time = TimeManager.forLimits(limits, board.sideToMove());
        hardDeadlineMillis = time.hardMillis();
        nodeLimit = limits.nodes() > 0 ? limits.nodes() : Long.MAX_VALUE;
        int maxDepth = limits.depth() > 0 ? Math.min(limits.depth(), MAX_PLY) : MAX_PLY;

        var rootMoves = new MoveList();
        MoveGenerator.generateLegal(board, rootMoves);
        if (rootMoves.size() == 0) {
            int score = MoveGenerator.isInCheck(board) ? -MATE : 0;
            return new SearchInfo(0, score, 0, elapsedMillis(), new int[0]);
        }
        // Fallback in case even depth 1 is aborted.
        SearchInfo best = new SearchInfo(0, 0, 0, 0, new int[] {rootMoves.get(0)});

        for (int depth = 1; depth <= maxDepth; depth++) {
            int score = negamax(depth, -INFINITY, INFINITY, 0);
            if (aborted) {
                break;
            }
            int[] line = Arrays.copyOf(pv[0], pvLength[0]);
            rootBestMove = line[0];
            best = new SearchInfo(depth, score, nodes, elapsedMillis(), line);
            listener.accept(best);

            boolean mateFoundWithinDepth = isMateScore(score) && MATE - Math.abs(score) <= depth;
            if (mateFoundWithinDepth || elapsedMillis() >= time.softMillis()) {
                break;
            }
        }
        return best;
    }

    private int negamax(int depth, int alpha, int beta, int ply) {
        pvLength[ply] = ply;
        if (ply > 0 && (board.isRepetition() || board.isFiftyMoveDraw())) {
            return 0;
        }
        boolean inCheck = MoveGenerator.isInCheck(board);
        if (inCheck) {
            depth++;
        }
        if (depth <= 0) {
            return quiescence(alpha, beta, ply);
        }
        if (ply >= MAX_PLY) {
            return Evaluator.evaluate(board);
        }
        if (countNodeAndCheckLimits()) {
            return 0;
        }

        // Transposition table: return a stored result that is deep enough and conclusive for this window; otherwise
        // at least search its best move first. The root always searches, so it always produces a move and a PV.
        long key = board.hash();
        long entry = tt.probe(key);
        int ttMove = TranspositionTable.move(entry);
        if (entry != TranspositionTable.NO_ENTRY && ply > 0 && TranspositionTable.depth(entry) >= depth) {
            int ttScore = TranspositionTable.scoreFromTable(TranspositionTable.score(entry), ply);
            int bound = TranspositionTable.bound(entry);
            if (bound == TranspositionTable.EXACT
                    || (bound == TranspositionTable.LOWER_BOUND && ttScore >= beta)
                    || (bound == TranspositionTable.UPPER_BOUND && ttScore <= alpha)) {
                return ttScore;
            }
        }
        int alphaAtEntry = alpha;

        MoveList moves = moveLists[ply];
        MoveGenerator.generatePseudoLegal(board, moves);
        scoreMoves(moves, ply, ttMove != Move.NONE ? ttMove : (ply == 0 ? rootBestMove : Move.NONE));

        int legalMoves = 0;
        int bestScore = -INFINITY;
        int bestMove = Move.NONE;
        for (int i = 0; i < moves.size(); i++) {
            int move = pickNext(moves, ply, i);
            board.makeMove(move);
            if (MoveGenerator.leftKingInCheck(board)) {
                board.unmakeMove();
                continue;
            }
            legalMoves++;
            int score = -negamax(depth - 1, -beta, -alpha, ply + 1);
            board.unmakeMove();
            if (aborted) {
                return 0;
            }

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
                if (score > alpha) {
                    alpha = score;
                    updatePv(ply, move);
                    if (alpha >= beta) {
                        break;  // the opponent will avoid this position: no need to look at our other moves
                    }
                }
            }
        }

        if (legalMoves == 0) {
            // Checkmate (prefer the quickest mate / slowest defeat via ply) or stalemate.
            return inCheck ? -MATE + ply : 0;
        }
        int bound = bestScore >= beta ? TranspositionTable.LOWER_BOUND
                : bestScore > alphaAtEntry ? TranspositionTable.EXACT
                : TranspositionTable.UPPER_BOUND;
        // After a fail low no move proved better than the others: keep whatever move the entry already had.
        int moveToStore = bound == TranspositionTable.UPPER_BOUND ? Move.NONE : bestMove;
        tt.store(key, moveToStore, TranspositionTable.scoreToTable(bestScore, ply), depth, bound);
        return bestScore;
    }

    /** Searches only captures and promotions until the position is quiet. */
    private int quiescence(int alpha, int beta, int ply) {
        pvLength[ply] = ply;
        if (countNodeAndCheckLimits()) {
            return 0;
        }
        int standPat = Evaluator.evaluate(board);
        if (ply >= MAX_PLY) {
            return standPat;
        }
        // "Stand pat": the side to move is not forced to capture, so the static score is a lower bound.
        if (standPat >= beta) {
            return standPat;
        }
        if (standPat > alpha) {
            alpha = standPat;
        }

        MoveList moves = moveLists[ply];
        MoveGenerator.generatePseudoLegal(board, moves);
        scoreMoves(moves, ply, Move.NONE);

        int bestScore = standPat;
        for (int i = 0; i < moves.size(); i++) {
            int move = pickNext(moves, ply, i);
            if (!Move.isCapture(move) && !Move.isPromotion(move)) {
                break;  // ordering puts all captures and promotions first
            }
            board.makeMove(move);
            if (MoveGenerator.leftKingInCheck(board)) {
                board.unmakeMove();
                continue;
            }
            int score = -quiescence(-beta, -alpha, ply + 1);
            board.unmakeMove();
            if (aborted) {
                return 0;
            }
            if (score > bestScore) {
                bestScore = score;
                if (score > alpha) {
                    alpha = score;
                    if (alpha >= beta) {
                        break;
                    }
                }
            }
        }
        return bestScore;
    }

    /** @return true if the search must stop now */
    private boolean countNodeAndCheckLimits() {
        nodes++;
        if (nodes >= nodeLimit) {
            aborted = true;
        } else if ((nodes & (CHECK_EVERY_NODES - 1)) == 0) {
            aborted = stopRequested || elapsedMillis() >= hardDeadlineMillis;
        }
        return aborted;
    }

    /**
     * MVV-LVA ("most valuable victim, least valuable attacker"): capturing a queen with a pawn is tried before
     * capturing a pawn with a queen. Good captures first produce early cutoffs.
     */
    private void scoreMoves(MoveList moves, int ply, int bestMove) {
        int[] scores = moveScores[ply];
        for (int i = 0; i < moves.size(); i++) {
            int move = moves.get(i);
            int score = 0;
            if (move == bestMove) {
                score = SCORE_BEST_MOVE;
            } else if (Move.isCapture(move)) {
                int victim = Move.flag(move) == Move.EN_PASSANT ? PieceType.PAWN : Piece.type(board.pieceAt(Move.to(move)));
                int attacker = Piece.type(board.pieceAt(Move.from(move)));
                score = SCORE_CAPTURE + Evaluator.PIECE_VALUE[victim] * 10 - attacker;
                if (Move.isPromotion(move)) {
                    score += Evaluator.PIECE_VALUE[Move.promotionType(move)];
                }
            } else if (Move.isPromotion(move)) {
                score = SCORE_PROMOTION + Evaluator.PIECE_VALUE[Move.promotionType(move)];
            }
            scores[i] = score;
        }
    }

    /**
     * Selection sort, one step at a time: moves are only sorted as far as they are actually searched, which matters
     * because a cutoff often happens after the first one or two.
     */
    private int pickNext(MoveList moves, int ply, int index) {
        int[] scores = moveScores[ply];
        int bestIndex = index;
        for (int j = index + 1; j < moves.size(); j++) {
            if (scores[j] > scores[bestIndex]) {
                bestIndex = j;
            }
        }
        if (bestIndex != index) {
            moves.swap(index, bestIndex);
            int tmp = scores[index];
            scores[index] = scores[bestIndex];
            scores[bestIndex] = tmp;
        }
        return moves.get(index);
    }

    private void updatePv(int ply, int move) {
        pv[ply][ply] = move;
        int childLength = pvLength[ply + 1];
        System.arraycopy(pv[ply + 1], ply + 1, pv[ply], ply + 1, childLength - (ply + 1));
        pvLength[ply] = childLength;
    }

    private long elapsedMillis() {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
