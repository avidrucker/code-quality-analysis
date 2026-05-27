# Handoff — code-quality-analysis (end of v1 round-out session)

**Date:** 2026-05-27
**Project root:** `~/Documents/Study/AI/avi_drucker/code-quality-analysis/`
**Remote:** https://github.com/avidrucker/code-quality-analysis (public, MIT)
**Personal Claude profile:** `CLAUDE_CONFIG_DIR=~/.claude-personal` (via `personal-claude` alias)

This handoff supersedes `/tmp/handoff-code-quality-analysis-2026-05-26.md` if it's still around. Most of yesterday's content has moved into the repo's persistent docs; this one captures the v2-pointing state of play.

---

## Project in one paragraph

Avi has built a programmatic code-quality-assessment framework that supersedes Casey Muratori's WARMED conceptually (first-order vs second-order concerns, with `:unknown` as a first-class status — no fake numbers). v1 is shipped: 17 checks across 6 concerns dogfooded against lccjs, three runner kinds (`:deterministic` shell + `:ai-assisted` Claude + `:human-rated` sign-off file), per-machine config via gitignored `local.edn`, 109 unit-test assertions in CI, and a doc set covering philosophy, concern catalog, and contemporaneous learnings.

## Where to read instead of re-explaining

Persistent project memory (auto-loads in any session in this dir):
- `~/.claude-personal/projects/-home-avi-Documents-Study-AI-avi-drucker-code-quality-analysis/MEMORY.md` — index pointing to `project_overview.md`, `warmed_skills_sibling.md`, `user_stack_clojure.md`, `personal_claude_alias.md`.

Repo docs:
- `README.md` — entry point with install, run, scoring model, output sample.
- `docs/philosophy.md` — the framework's stance (first/second/cross-cutting, status model, severity policy, Casey's bias as tiebreaker).
- `docs/concern-catalog.md` — the menu of measurable checks per concern with binary/quantified claim examples.
- `docs/v1_learnings.md` — **7 contemporaneous findings + a Deferred-for-v2 backlog. Read this first if you're picking up where I left off.**
- `schema/SCHEMA.edn` — self-documenting config reference, every key inline.
- `examples/lccjs.edn` — the real v1 config (17 checks, with two `:check/rationale` examples).
- `local.example.edn` — committed template for per-machine config.
- `prompts/hotpath-review.md` — the one AI-assisted check's prompt.

Code:
- `assess.bb` — runner (~620 lines, one file).
- `test/assess_test.bb` — 109 assertions across 11 deftests.

## Status — what works, what's pending

**Working end-to-end as of commit `0aadc1c`:**
- All three runner kinds wired (deterministic / ai-assisted / human-rated).
- Local + project EDN merge with precedence resolution for `:claude/*` knobs (`:cmd`, `:config-dir`, `:max-budget-usd`, `:model`).
- `:check/rationale` rendered as blockquote in Required / Recommended / Advisory report sections AND embedded at the top of evidence files.
- `compute-exit-code` fails closed on `:required + :unknown` (not just `:fail`).
- `validate-config` catches runner-specific config errors at startup with exit 2 — including non-map `:pass-when`, missing `:check/command`, missing `:check/prompt-file`, missing `:check/sign-off-path`.
- GitHub Actions CI green on every push (5–8s) with no deprecation warnings after the `@v6` / `@13.6.1` bumps.

**Pending user action (interactive, agent can't do):**
- `personal-claude /login` once for the AI runner to return real ratings instead of "Not logged in" `:unknown`.

**Uncommitted elsewhere:**
- `~/dotfiles/install.sh` — `install_claude_personal_skills` section is locally committed (`d1b0531`) but unpushed. Push at user's discretion.

## Repo / on-disk state

- `code-quality-analysis`: clean tree, in sync with `origin/main`. 8 commits since initial.
- `lccjs`: clean tree, in sync. User added two unrelated feature commits (OB-035, OB-036) during this session, also pushed.
- `~/dotfiles`: clean tree, ahead 1 (mine) — push when ready.

## Recommended next steps (open-ended)

In rough priority order, drawn from `docs/v1_learnings.md`'s Deferred-for-v2 section:

1. **Runner-level integration tests using a mock `claude` binary on PATH.** The three `defmethod` runners have zero direct coverage; only their helpers are tested. A shell-stub `claude` (or PATH-prepended script) would unlock real `:ai-assisted` tests. ~1 hour. Closes the biggest test-coverage gap.

2. **Range predicates in `:pass-when` DSL.** Can't currently say `4 ≤ rating ≤ 10`. The map-keyed predicate shape collides when two clauses share a key. Either: (a) compound numeric specs like `{:between [4 10]}`, or (b) a list-of-clauses shape. Worth prototyping both before committing. ~1 hour.

3. **A second example config for a non-JS project** to validate schema generality. lccjs is the only example. A Clojure or Python target would shake out implicit JS assumptions. Needs a real target project to dogfood against.

4. **`personal-claude /login` then a real AI rating end-to-end.** User action gated. Once done, the interpreter hot-path AI check will produce a real rating instead of `:unknown`. May surface bugs in the JSON-schema-strict-output path that the stub can't hit.

5. **Cosmetic: blank lines in `:check/rationale` render as bare `> ` blockquote lines.** Minor polish. Fix probably in `render-failure-entry`.

6. **Report-rendering tests** (`render-markdown`, `render-failure-entry`, `concern-verdict`). Easy via `with-out-str` + string assertions. Closes another test-coverage seam.

Lower-leverage items in the Deferred section: project profile presets, per-check AI output schema, check dependencies.

## Design rules to preserve (these are LOAD-BEARING)

- **`:unknown` is first-class.** Surface it in its own report section. Never fake a number — Casey's bias and the framework's reason for existing.
- **Severity (`:required | :recommended | :advisory`) is policy, not numeric weight.** Required failures (or required `:unknown`s) gate exit code; others warn or log.
- **Per-concern verdicts only.** No single composite score. "Quality 82%" is the failure mode the framework was built to avoid.
- **`init_research.md` was deleted on purpose.** Don't restore it. `docs/concern-catalog.md` is the catalog; `docs/philosophy.md` is the stance.
- **`local.edn` is the canonical place for `:claude/config-dir`,** not the per-project EDN. Resolution precedence: project EDN > process env (CLAUDE_CONFIG_DIR) > local.edn > default.
- **WARMED bias survives as a tiebreaker.** When choosing between candidate checks, prefer ones that map to real machine cost (ms, bytes, cycles, queries) over abstract structural properties.

## Caveats / gotchas worth knowing

- **`npm test` in lccjs is occasionally flaky.** Consecutive runs of `./assess.bb examples/lccjs.edn` may show different correctness verdicts. Not a runner bug — captured in Finding 6. The README sample is "representative, not deterministic."
- **AI prompt piped via stdin, not argv.** Don't revert; argv-size limits and the "no stdin received in 3s" warning both bite if you do.
- **`--bare` strips CLAUDE.md auto-discovery, hooks, plugins, skills** in the AI subprocess. Necessary for predictable invocation. Don't loosen.
- **`gh` token needs `workflow` scope** for any edit to `.github/workflows/*`. Already granted on this machine (`gh auth status` shows `gist, read:org, repo, workflow`).
- **Pre-push checklist** lives in Finding 6 of `v1_learnings.md`. Use it before any push that touches `assess.bb`, the schema, or the lccjs example.

## Suggested skills for the next session

- **`clojure`** — mandatory if editing `assess.bb`. Babashka is Clojure-on-the-JVM.
- **`tdd`** — natural fit for the runner integration tests (item 1 above). Red-green-refactor against the mock-claude scaffold.
- **`prototype`** — good fit for the range-predicate DSL exploration (item 2). Throw away two or three shapes before committing.
- **`diagnose`** — if anything surfaces as a real bug (e.g. CI red on a fresh PR).
- **`consolidate-memory`** — periodic pass over the four memory files; they're current as of end of session 2026-05-27.
- **`find-skills`** — if discovering a skill for a subtask (dependency analysis, static analysis tooling, etc.).

Skip skills not applicable here: no Fulcro / Datomic / RAD code in this project; no PDF / docx / xlsx; no Trello board work; no React.

## Closing note

The repo is in genuinely good shape — small, tested, documented, dogfooded, in CI. v1 is the natural pause point. The Deferred-for-v2 list in `docs/v1_learnings.md` is the canonical backlog. Pick whichever item gives you the most value for the time you have; nothing in the list is hard-blocked.

---

*End of handoff.*
