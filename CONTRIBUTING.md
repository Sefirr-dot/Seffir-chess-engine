# Contributing

## Branching model

- `main` is always green: it builds, all tests pass, and perft is correct.
- All work happens on short-lived branches and lands in `main` through a pull request with passing CI.

| Prefix      | Use                                     | Example                      |
|-------------|-----------------------------------------|------------------------------|
| `feat/`     | New functionality                       | `feat/bitboard-movegen`      |
| `fix/`      | Bug fixes                               | `fix/en-passant-pin`         |
| `perf/`     | Speed improvements (engine or movegen)  | `perf/magic-bitboards`       |
| `search/`   | Search changes that must be Elo-tested  | `search/null-move-pruning`   |
| `refactor/` | No behaviour change                     | `refactor/move-encoding`     |
| `docs/`     | Documentation only                      | `docs/uci-options`           |

PRs are merged with **squash merge**, so the PR title becomes the commit on `main`.

## Commit messages

[Conventional Commits](https://www.conventionalcommits.org/): `type(scope): summary`

```
feat(core): generate pseudo-legal pawn moves
fix(uci): handle "position startpos" without moves
perf(movegen): replace ray loops with magic bitboards
```

Scopes: `core`, `movegen`, `search`, `eval`, `uci`, `gui`, `build`, `ci`.

## Engine-strength changes

Any change to search or evaluation must include, in the PR description, the result of a match against the current
`main`. A change that does not gain Elo is not merged, however elegant it is.

How to run it with the built-in arena:

1. Build the reference from `main` and keep its jar outside `target/`:
   ```bash
   git switch main && ./mvnw -q package -DskipTests
   mkdir -p engines && cp engine-uci/target/seffir.jar engines/seffir-main.jar
   ```
2. Switch to your branch and start the app (`scripts/seffir-gui.bat`).
3. **Tools → Match arena…**: engine A = built-in (your branch), engine B = *Load engine…* → `engines/seffir-main.jar`.
4. Clock 10s + 0.1s, SPRT `[0, 5]` (gainers) or `[-5, 0]` (non-regression for simplifications), 4+ parallel games.
5. Let the SPRT finish, save the PGN, and paste the final W/D/L, Elo and verdict into the PR.

`engines/` is git-ignored.

## Correctness changes

Any change touching move generation or make/unmake must keep the perft suite green.
