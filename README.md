# code-quality-analysis

> Programmatic code-quality assessment driven by an EDN config. Deterministic + AI-assisted + human-rated check runners. Inspired by but supersedes Casey Muratori's WARMED.

A small Babashka runner that reads a list of **checks** from a config file, dispatches each one by its runner type (shell command, Claude prompt, sign-off file), and emits a markdown scorecard + JSON results. Failures of `:required` checks exit non-zero; `:unknown` is a first-class status — no fake numbers.

## Why this exists

Most "code quality" frameworks measure rituals (SOLID, "clean code") instead of real costs. This framework asks a different question:

> What claims do we want to make about this project, and what evidence would prove or disprove them?

See [`docs/philosophy.md`](./docs/philosophy.md) for the framework's stance. See [`docs/concern-catalog.md`](./docs/concern-catalog.md) for the menu of checks across first-order concerns (correctness, performance, security, …), second-order qualities (readability, maintainability, …), and cross-cutting mechanisms (logging, configuration, …).

## Install

Requirements: [Babashka](https://babashka.org/) on `$PATH`. The AI runner additionally needs Claude Code's [`claude`](https://claude.com/claude-code) CLI.

```bash
git clone https://github.com/avidrucker/code-quality-analysis.git
cd code-quality-analysis
./assess.bb --help
```

## Run

```bash
./assess.bb examples/lccjs.edn
```

Outputs:

- `reports/<project>/report.md` — human-readable scorecard
- `reports/<project>/results.json` — machine-readable, full data
- `reports/<project>/<check-id>.log` (or `.md`) — per-check evidence

Exit codes:

| Code | Meaning |
|---|---|
| 0 | All `:required` checks passed (warnings may be present) |
| 1 | At least one `:required` check failed |
| 2 | Config missing or invalid |

## Check kinds

A check declares how it should be run via `:check/runner`:

- **`:deterministic`** — shell command + `:pass-when` predicate (`:exit-code`, `:stdout-matches`, etc.).
- **`:ai-assisted`** — prompt template + input files; invokes `claude -p` with a strict JSON schema, parses the structured reply (`{rating, reasoning, evidence, confidence}`) against a `:pass-when` predicate (e.g. `{:rating {:>= 4}}`). Returns `:unknown` if `claude` is unreachable.
- **`:human-rated`** — sign-off file existence + freshness window.

See [`schema/SCHEMA.edn`](./schema/SCHEMA.edn) for the full config shape with every key documented inline.

## Scoring model

Each check produces a result in `#{:pass :fail :unknown :n/a}` with confidence `#{:high :medium :low}`. Severity is policy, not a numeric weight:

- `:required` — failure exits non-zero
- `:recommended` — failure warns
- `:advisory` — failure is logged only

No single composite score. Per-concern verdicts only. `:unknown` is surfaced in its own section of the report.

## Status

**v1.** Dogfooded against [lccjs](https://github.com/avidrucker/lccjs) (5 deterministic checks + 1 AI-assisted probe over the interpreter hot path). The wiring works end-to-end including the personal-Claude profile (`:claude/config-dir`); the AI check needs a one-time `claude /login` before it can return real ratings.

Coming next: a test suite for the runner itself, expansion of the lccjs config from 6 to ~15 checks, and CI.

## License

MIT — see [LICENSE](./LICENSE).
