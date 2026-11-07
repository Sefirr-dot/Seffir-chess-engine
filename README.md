# Seffir

A chess engine written from scratch in Java 21, with a native JavaFX desktop GUI.

[![CI](https://github.com/Sefirr-dot/Seffir-chess-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/Sefirr-dot/Seffir-chess-engine/actions/workflows/ci.yml)

> **Status:** early development. The UCI handshake works; board representation and move generation are next. See the [roadmap](docs/ROADMAP.md).

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

Run the engine directly in UCI mode:

```bash
java -cp "engine-uci/target/engine-uci-0.1.0-SNAPSHOT.jar:engine-core/target/engine-core-0.1.0-SNAPSHOT.jar" \
     io.github.sefirrdot.chess.uci.UciMain
```

(On Windows use `;` instead of `:` as classpath separator.)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the branching model and commit conventions.

## License

[MIT](LICENSE)
