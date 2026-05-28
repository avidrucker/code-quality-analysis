# Research findings

An accumulating log of design-level insights that emerge while extending the framework. Distinct from [`v1_learnings.md`](../v1_learnings.md) — that doc captures contemporaneous fixes/bugs during a specific work pass. This one captures **policy** and **principles** that should outlive any single pass.

New findings are appended at the bottom; oldest at the top.

---

## Finding A — Target repos should be treated as read-only by default

**Date:** 2026-05-27
**Context:** Picking the second example config (after lccjs), looking at avatar-maker. The user asked: "how do we avoid leaving noisy code-quality artifacts in the target repo that would confuse other contributors?"

### The two kinds of artifacts the framework produces

| Kind | Where it lives | Touched on every run? | Affects target's contributors? |
|---|---|---|---|
| Config + prompt + reports | Inside the framework (`examples/<project>.edn`, `prompts/*.md`, `reports/<project>/`) | yes | no |
| Modifications to the target itself | Inside the target repo | only if the framework or user makes them | yes |

The first kind is fine — they're scoped to the framework and visible only to people working on the framework. The second kind is the noise the user is rightly concerned about.

### Precedent: lccjs as the *exception*

For lccjs we did modify the target — adding `.nvmrc` and `engines.node`. That was justified independently: the Node version pinning was a real improvement that lccjs's project owner (the same user) wanted regardless. But it was a *separate decision* layered on top of running the assessment, not something the framework drove.

If we generalize the lccjs pattern carelessly, every assessment would be tempted to "fix" the target to make the scorecard greener. That defeats the framework's reason for existing — honest signal, no fake numbers. The same logic applies to "automatically pinning Node": pretending the target is healthier than it is.

### The principle

> The framework treats target repos as read-only by default. Failed checks are honest signals in our reports — they are not auto-fixed in the target. Modifying a target is a separate, deliberate, off-framework action that the user evaluates on its own merits.

Mechanically this means:

- `:project/path` always points outside the framework.
- Reports write to `reports/<project>/` inside the framework, never inside the target.
- Most checks are pure-read (grep, file existence, line counts, `npm test`-as-subprocess).
- The exception is `:human-rated` checks that expect a sign-off file at the target — but those are opt-in per check, and they don't *modify* sign-off content, just read it.

### Why this matters more as the framework gets used on more repos

Without this principle, every assessment run might add a `.nvmrc`, an `engines.node`, a `.github/workflows/lint.yml`, a `CHANGELOG.md` stub, etc. Each "fix" looks small in isolation; collectively they make the framework adversarial to projects it's nominally helping. People stop running the assessment because every run produces a PR diff.

With this principle, the assessment is a passive observer. The user (and only the user) decides what to do about findings. The assessment can be run as often as desired without changing the project's git history.

### How to apply this when a check's *natural fix* would mean modifying the target

You don't change the check. The check still fires honestly (e.g. "no `.nvmrc` or `engines.node`"). The report shows the finding. The user decides:

1. **Accept the finding** — known gap, will fix when ready. Leave the check firing as a reminder. (Default.)
2. **Suppress the finding** — change the check's `:check/severity` to `:advisory` in the EDN config so it logs without warning, OR remove the check from the config if it's permanently inapplicable.
3. **Fix the underlying issue in the target** — separate, deliberate action. Don't bundle with the assessment run. Commit and explain the change as itself, not as "the framework told me to."

### Per-stack adaptation is a real lever

The first thing that becomes obvious when porting a config from one stack to another is that some checks don't translate directly:

- `npm test` → `npm test` for both lccjs (Jest) and avatar-maker (shadow-cljs + node), but the underlying mechanics differ.
- Deep-nesting heuristic (`grep -E '^( {N,})[^ ]'`) — N depends on the language's idiomatic indent. JS commonly uses 4-space (so N=20 ≈ 5 levels). Clojure uses 2-space (so N=14 ≈ 7 levels is comparable, though Clojure's argument-alignment indenting changes the meaning).
- Banned-vague-name regex — JS `function (data|info|...)` doesn't translate to Clojure's `(defn name ...)` shape.
- Dependency count — JS reads `package.json`'s `dependencies`; Clojure reads `shadow-cljs.edn` or `deps.edn`'s `:dependencies`.

These are **adaptation points**, not framework bugs. They become a knowledge artifact in their own right: when we have 3–4 example configs, a `docs/adapting-checks.md` will be worth writing — explaining which checks port directly and which need stack-specific shaping.

### Avatar-maker exercise (in-progress)

This finding is being written *before* the avatar-maker config is run. The exercise will either confirm the principle (zero modifications needed; honest scorecard tells a real story) or surface a counter-example (the framework structurally needs to touch the target for some check class). Post-run observations will be appended below as a sub-section.

#### Post-run observations

**Result: 10 PASS / 5 FAIL / 0 UNKNOWN, exit 1.** Working tree on avatar-maker stayed clean throughout — read-only principle confirmed on a real second example.

The five failures break into three distinct categories, which is itself a useful finding:

##### (a) Honest project signals — exactly what the framework is for

Four of the five failures are real, valid signals about avatar-maker's state:

- `node-version-pinned` (advisory) — no `.nvmrc` or `engines.node`. Same finding as lccjs initially.
- `max-src-file-loc-bound` (advisory) — `ui.cljs` is 1699 lines and `render.cljs` is 1496 lines, both well over the 800-line threshold. Real god-files.
- `readme-substantive` (advisory) — README is 166 bytes (below the 200-byte threshold). A real onboarding gap.
- `no-deep-nesting-in-src` (advisory) — many matches in `config.cljs` and `render.cljs`. See category (c) for why this is *partly* a signal but also partly a framework limitation.

These are the framework working as intended. The user can choose to act on them or document why each one is acceptable for this project.

##### (b) A real signal that surfaces a framework-vs-subprocess concern

The `unit-tests-pass` failure (required, exit 1) was driven by `shadow-cljs: not found` — the subprocess running `npm test` couldn't find `shadow-cljs` on PATH, because the babashka subprocess inherits the parent process's PATH, which (for a Claude-Code-spawned shell) doesn't include the nvm-managed npm globals where `shadow-cljs` lives.

This is a real "works in my interactive shell but not in CI" signal — and a useful one. It applies symmetrically:

- If avatar-maker's own GitHub Actions CI tried to run the tests (it doesn't currently — its CI only deploys to Pages), it would hit the same problem unless the workflow explicitly installs shadow-cljs or runs `npx shadow-cljs`.
- A new contributor cloning the repo on a fresh machine would also have to know to set up shadow-cljs themselves.

The framework surfaces this honestly. The fix is in the target project (use `npx`, add shadow-cljs to devDependencies, document the global install requirement), not in the framework. We do not need to make the runner source `.bashrc` or use `bash -ic` — that would silently mask exactly the class of "PATH not portable" problems we want to surface. Read-only principle holds.

If a user *wants* the framework's subprocess to use a richer env for some specific check, that's what `:check/env` would be (deferred v2 feature) — opt-in per check, not a runner-wide setting.

##### (c) A real framework limitation surfaced: deep-nesting heuristic vs Clojure data literals

The `no-deep-nesting-in-src` check flags 16+ leading spaces. In Clojure, that fires for two structurally different things:

- **Control-flow nesting** — `when` inside `if` inside `let` inside `defn`. This is the bad kind, and the check correctly flags it.
- **Data-literal nesting** — `{:a {:b {:c {:d :e}}}}`-style nested maps used for configuration or SVG generation. The Clojure community considers this readable; "depth" of a data literal is not a complexity smell the way control-flow depth is.

avatar-maker's `config.cljs` and `render.cljs` have lots of category (2). The check fires honestly on the spaces, but the implied "this code is too complex" verdict is wrong for ~half the matches.

This is a real framework limitation, not a config bug. Fixing it requires either:

1. **A smarter Clojure-aware analyzer** like `clj-kondo` integrated as a runner — it can distinguish data nesting from control nesting. Real work; v2.
2. **Document the limitation per-check** and downgrade `no-deep-nesting-in-src` to advisory severity for Clojure projects (already done in this config). Communicates the imprecision honestly without pretending to be smarter.

We've taken option 2. Option 1 is on the deferred backlog.

##### Per-stack adaptations that ported, adapted, or limited

A practical table of what happened to each check class as it crossed from JS (lccjs) to CLJS (avatar-maker):

| Check class | Lccjs version | Avatar-maker version | Adaptation needed? |
|---|---|---|---|
| Tests pass | `npm test` | `npm test` | None — same surface, different mechanics underneath |
| Lockfile | `package-lock.json` exists | Same | None |
| No TODO / FIXME | grep `src/core/` | grep `src/avatar/` | Minor (path) |
| Node version pinned | `.nvmrc` or engines.node | Same | None |
| No uncommitted changes | `git diff --quiet HEAD` | Same | None |
| Source file size | `awk` on `assembler.js` | `find -exec awk` on every cljs file | Moderate (shape: one file vs many) |
| Deep nesting | grep 20+ spaces (4-space indent) | grep 16+ spaces (2-space indent) | Threshold + see category (c) limitation |
| Banned vague fn names | `function (data|info|...)` regex | `(defn (data|info|...)` regex | Syntactic — different language shape |
| Dependency count | parse `package.json` deps with `node -e` | parse `shadow-cljs.edn` `:dependencies` with `bb -e` | Significant — different file, different parser |
| Has test suite | `find tests/new -name '*.test.js'` | `find test -name '*_test.cljs'` | Minor (path + naming convention) |

About **half the checks ported with minor or no changes**. The rest needed syntactic or toolchain adaptation. None required new framework features — the EDN config DSL was expressive enough.

##### Confirmation: read-only principle held

`cd ~/Documents/Study/ClojureScript/avatar-maker && git status --short` after the run: empty output. The framework touched zero files in the target. The principle stated at the top of this finding is upheld in practice for this exercise.

##### What we'd commit to the framework after this exercise

Nothing yet. The findings above are all observations about the target or about per-stack adaptation — nothing surfaced a clear framework bug. The deep-nesting limitation is real but addressing it well requires clj-kondo integration, which is a v2 feature. For now, document the limitation honestly via `:check/rationale` and let the per-stack adaptation table in this finding serve as the knowledge artifact.
