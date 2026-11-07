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

Any change to search or evaluation must include, in the PR description, the result of a match
against the current `main` (tool, time control, number of games, Elo difference with error bars).
A change that does not gain Elo is not merged, however elegant it is.

## Correctness changes

Any change touching move generation or make/unmake must keep the perft suite green.
