# Seffir

A chess engine written from scratch in Java 21, with a native JavaFX desktop GUI.

[![CI](https://github.com/Sefirr-dot/Seffir-chess-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/Sefirr-dot/Seffir-chess-engine/actions/workflows/ci.yml)

> **Status:** playable UCI engine (alpha-beta search, perft-validated move generation). The desktop GUI is next.
> See the [roadmap](docs/ROADMAP.md).

## Features

- **Bitboard board** with mailbox, incremental Zobrist hashing, repetition and fifty-move detection.
- **Legal move generation** validated against the standard perft suite (~27M nodes/s).
- **Search:** negamax alpha-beta, iterative deepening, quiescence, check extension, MVV-LVA ordering.
- **Evaluation:** material + piece-square tables with a tapered king.
- **UCI:** `position`, `go` (depth / nodes / movetime / clock / infinite), `stop`, plus `d` to print the board.

## Architecture

```
Seffir-chess-engine/
├── engine-core/   Board (bitboards), move generation, search, evaluation. Zero dependencies.
├── engine-uci/    Standalone UCI executable on top of engine-core.
└── gui/           JavaFX desktop app. Runs engines as external UCI processes.
```

The GUI never calls the engine in-process: it launches it and talks [UCI](https://www.wbec-ridderkerk.nl/html/UCIProtocol.html),
the same way Arena, CuteChess or Lichess bots do. That means the GUI can load any UCI engine (e.g. Stockfish for comparison),
and the engine runs in any UCI-compatible GUI or tournament manager.

## Build

Requires JDK 21+. Maven is provided through the wrapper.

```bash
./mvnw verify                       # build + all tests
./mvnw -pl gui -am javafx:run       # launch the desktop app (after `./mvnw install` once)
```

## Play against it

`./mvnw package` produces a self-contained `engine-uci/target/seffir.jar`. In any UCI GUI
([Arena](http://www.playwitharena.de/), [CuteChess](https://cutechess.com/), [BanksiaGUI](https://banksiagui.com/)…)
add a new engine pointing at `scripts/seffir.bat` (Windows) or `scripts/seffir.sh` (Linux/macOS).

Or talk to it directly:

```text
$ java -jar engine-uci/target/seffir.jar
position startpos moves e2e4
go movetime 2000
info depth 1 score cp 0 nodes 21 ... pv e7e5
...
bestmove e7e5
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the branching model and commit conventions.

## License

[MIT](LICENSE)
