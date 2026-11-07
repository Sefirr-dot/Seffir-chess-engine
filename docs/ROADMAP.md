# Roadmap

Each milestone is one or more branches/PRs. A milestone is done when it is merged with green CI.

## M0 — Project foundation ✅
- [x] Maven multi-module build (core / uci / gui), wrapper, CI
- [x] UCI handshake (`uci`, `isready`, `quit`)

## M1 — Board and move generation ✅
- [x] Bitboard board representation, piece/square/move encoding
- [x] FEN parsing and serialization
- [x] Pseudo-legal move generation (leapers via lookup tables, sliders via rays)
- [x] Legality: checks, pins, castling rights, en passant
- [x] make/unmake with an undo stack
- [x] Incremental Zobrist hashing, repetition and fifty-move detection
- [x] Perft test suite (standard positions) in CI — ~27 M nodes/s with hashing, perft(6) from start in 4.4 s

## M2 — First playable engine ✅
- [x] UCI `position` / `go` / `stop` with time management (search on its own thread)
- [x] Negamax + alpha-beta, iterative deepening, check extension
- [x] Evaluation: material + piece-square tables, tapered king
- [x] Quiescence search
- [x] MVV-LVA capture ordering, draws by repetition / fifty-move rule
- [x] Self-contained `seffir.jar` + launcher scripts for GUIs — depth 7 in ~2 s from the start position

## M3 — Desktop GUI v1
- [x] Board rendering with piece set, drag & drop, legal move highlighting, promotion chooser, themes, flip
- [x] Game model in core: result detection (mate, stalemate, threefold, fifty moves, insufficient material)
- [x] Play vs engine (engine as UCI subprocess, built-in Seffir or any executable), clocks, new game dialog
- [ ] Move list, PGN import/export, FEN setup
- [ ] Engine analysis panel (depth, eval, PV)
- [ ] **Match arena**: engine vs engine with a live grid of boards (several concurrent games), opening book with
      colour-reversed pairs, live W/D/L, Elo ± error and SPRT log-likelihood chart that stops the match on its own
- [ ] Click a live game to follow it full size (both engines' eval and PV); exhibition games at slow time controls
- [ ] PGN replayer to step through finished games (also for headless `fastchess` runs)

## M4 — Stronger search
- [ ] Transposition table
- [ ] Move ordering: TT move, killers, history
- [ ] Null-move pruning, late move reductions, PVS
- [ ] Magic bitboards
- [ ] Every strength change validated by SPRT in the match arena (headless `fastchess` for long runs)

## M5 — Public
- [ ] Lichess bot account
- [ ] Packaged desktop installer (jpackage)

## M6 — Neural evaluation
- [ ] NNUE-style evaluation trained on self-play data
