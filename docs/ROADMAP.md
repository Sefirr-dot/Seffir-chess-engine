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

## M2 — First playable engine
- [ ] UCI `position` / `go` / `stop` with time management
- [ ] Negamax + alpha-beta, iterative deepening
- [ ] Evaluation: material + piece-square tables
- [ ] Quiescence search

## M3 — Desktop GUI v1
- [ ] Board rendering with piece set, drag & drop, legal move highlighting
- [ ] Play vs engine (engine as UCI subprocess), clocks
- [ ] Move list, PGN import/export, FEN setup
- [ ] Engine analysis panel (depth, eval, PV)

## M4 — Stronger search
- [ ] Transposition table
- [ ] Move ordering: TT move, MVV-LVA, killers, history
- [ ] Null-move pruning, late move reductions, PVS
- [ ] Magic bitboards
- [ ] Self-play testing pipeline (fastchess / cutechess-cli, SPRT)

## M5 — Public
- [ ] Lichess bot account
- [ ] Packaged desktop installer (jpackage)

## M6 — Neural evaluation
- [ ] NNUE-style evaluation trained on self-play data
