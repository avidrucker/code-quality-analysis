# v2 backlog

Forward-looking work list for the framework itself. Each item is either something that surfaced as a real limitation during a real assessment, or a feature whose absence is acceptable today but blocks something specific later.

This doc supersedes the "Deferred for v2" section of [`v1_learnings.md`](./v1_learnings.md) as the canonical "what's next on the framework." The v1_learnings Deferred section is preserved there as the contemporaneous historical record of "what was noted at the time of the v1 round-out pass."

Items are grouped by priority tier. Within a tier, ordered by recommended sequencing.

---

## Tier 1 — concrete next-session work

### T1-1. Language-aware analyzer dispatch (clj-kondo for Clojure, eslint for JS)

**The cost it removes.** The `no-deep-nesting-in-src` grep heuristic is partially noisy on Clojure projects — it can't distinguish data-literal nesting (readable) from control-flow nesting (the actual smell). Surfaced concretely in [Finding A](./research/findings.md) on avatar-maker. The same imprecision shows up across other readability/structure checks: any heuristic that ports across stacks is necessarily crude.

**What v2 would add.** A new runner kind, `:linter-dispatch`, that knows how to invoke a real language-aware analyzer and parse its output. Initial implementations:

- `:linter clj-kondo` for `.clj`/`.cljs`/`.cljc` — handles nesting depth correctly, finds unused vars, real cyclomatic complexity.
- `:linter eslint` for `.js`/`.ts` — defers to project's eslint config if present.

Per-check usage example:

```clojure
{:check/runner :linter-dispatch
 :check/linter :clj-kondo
 :check/inputs ["src"]
 :check/pass-when {:max-warnings 0 :categories #{:complexity :unused}}}
```

**Pairs with T1-2 (`:project/language`)** — the language toggle drives default linter selection.

**Effort.** ~4–6 hours. Includes parsing clj-kondo's `--config '{:output {:format :edn}}'` output, mapping its findings into the framework's pass/fail predicate model, and writing 2–3 example checks.

### T1-2. `:project/language` optional EDN key + auto-detection

**The cost it removes.** Today the language a target is written in is implicit in the commands the EDN config picks. That's been sufficient for two examples (lccjs JS, avatar-maker Clojure), but breaks down once T1-1 lands — the framework needs to know which linter to dispatch.

**What v2 would add.**

- Optional `:project/language` key in the config. Values: `:javascript`, `:typescript`, `:clojure`, `:clojurescript`, `:python`, `:rust`, etc.
- Auto-detection when absent: walk `:project/path`, look at filesystem signals (`shadow-cljs.edn`/`deps.edn` → `:clojure[script]`, `package.json` + `.ts` files → `:typescript`, `Cargo.toml` → `:rust`, etc.). One language per project for v2 — multi-language projects pick the dominant one or set explicitly.
- Override always wins: if the user sets `:project/language` in the EDN, that beats auto-detection.

**Crucially:** stays inside the analyzer. The target is NOT touched. No `.code-quality-language` file landed in the target. Per [Finding A](./research/findings.md), targets stay read-only.

**Effort.** ~2 hours. Mostly the auto-detection signal logic + one round of testing.

### T1-3. Runner-level integration tests (mock `claude` binary)

**The cost it removes.** The three `defmethod` runners (`:deterministic`, `:ai-assisted`, `:human-rated`) have zero direct test coverage today. Only their helpers are tested. A change that subtly breaks the AI runner's envelope parsing or the human-rated freshness check wouldn't be caught by unit tests.

**What v2 would add.** A shell-stub `claude` binary placed on PATH for tests (via a `test/bin/` dir prepended to PATH). The stub returns canned JSON envelopes from fixture files. Then integration tests can:

- Run a real `./assess.bb` with a fixture config and confirm the produced report matches the expected shape.
- Test the `:ai-assisted` happy path, the `:is_error` envelope path, the timeout path, etc.

**Effort.** ~3 hours. Mostly fixture authoring.

### T1-4. Report-rendering tests

**The cost it removes.** `render-markdown`, `render-failure-entry`, `concern-verdict` have no unit tests. The new `:check/rationale` blockquote rendering shipped untested.

**What v2 would add.** Tests via `with-out-str` + string assertions. About 15 assertions covering each section of the report.

**Effort.** ~1 hour.

---

## Tier 2 — useful but lower-priority

### T2-1. Range predicates in `:pass-when`

**The cost it removes.** Can't express `4 ≤ rating ≤ 10` because the AND-combined map shape collides when two clauses share a key. Surfaced during v1 round-out.

**Two candidate shapes:**

- Compound numeric: `{:rating {:between [4 10]}}` — new operator.
- List-of-clauses: `:pass-when [[:rating {:>= 4}] [:rating {:<= 10}]]` — fundamentally different data shape.

Worth [prototyping both](./research/findings.md) before committing — they have different forward-compatibility implications.

**Effort.** ~1.5 hours including the prototype-and-decide.

### T2-2. `:check/env` per-check (subprocess env override)

**The cost it removes.** Surfaced as category (b) in [Finding A](./research/findings.md) on avatar-maker: the framework's subprocess can't find `shadow-cljs` because it's an nvm-managed global not on the slim default PATH. Today the only mitigations are (a) accept the failure as honest signal, (b) modify the target (add to devDependencies). A `:check/env` key would be a third option: opt-in per check.

```clojure
{:check/runner :deterministic
 :check/command "npm test"
 :check/env {"PATH" "/home/avi/.nvm/versions/node/v24.16.0/bin:$PATH"}
 :check/pass-when {:exit-code 0}}
```

**Design caveat.** If we add this, document carefully that **using it can mask real CI-portability problems** in the target. The default should remain "use the slim subprocess env" because that's what catches "works on my machine" patterns. `:check/env` is for the case where the user has decided "yes, I know my CI also sources this env, this check is for my machine."

**Effort.** ~1 hour.

### T2-3. Per-check AI output schema

**The cost it removes.** The JSON schema for AI replies is hardcoded as `{rating, reasoning, evidence, confidence}` in `assess.bb`. Limiting if a check wants a different shape (boolean + reasoning, list of citations, multi-dimensional rating).

**What v2 would add.** Optional `:check/output-schema` in the EDN that overrides the default. Threaded into `claude --json-schema`.

**Effort.** ~1.5 hours including a second AI prompt that uses a non-default schema.

### T2-4. Project profile presets

**The cost it removes.** `:project/profile :web-app` etc. is currently informational only. A second-time user setting up a new config has to compose all 15+ checks from scratch.

**What v2 would add.** A profile registry: `:web-app` ships with sensible defaults (build-time check, bundle size, lighthouse, accessibility); `:cli-tool` ships with different defaults; `:library` ships with API-surface checks. Users override per-check.

**Effort.** ~3 hours. Bulk of the work is designing the override-merging shape carefully.

---

## Tier 3 — speculative / nice-to-have

### T3-1. Check dependencies (`:check/depends-on`)

**The cost.** If `unit-tests-pass` fails, running `test-suite-time-bound` is wasted seconds. Today every check runs unconditionally. Fine for 15-check configs; matters at 50+ checks.

**Sketch.** `:check/depends-on [:unit-tests-pass]` — short-circuit downstream checks when prerequisites fail. Result is `:n/a` (not `:fail`) for the skipped check.

**Effort.** ~2 hours.

### T3-2. `docs/adapting-checks.md`

**The cost.** The per-stack adaptation table currently lives inside [Finding A](./research/findings.md). As we add more example configs (Clojure JVM, Python, Rust), that table grows. At some point it deserves to be a top-level reference: "which checks port directly across stacks, which need adaptation, what shape of adaptation."

**Trigger condition.** When we have 4+ example configs. Today at 2 (lccjs, avatar-maker), the in-finding table is enough.

**Effort.** ~2 hours once trigger conditions met.

### T3-3. Multi-language project support

Tier 2's `:project/language` assumes one language per project. A real polyglot (Clojure backend + ClojureScript frontend, or JS + Python) wants different linters per directory subtree.

**Design idea.** `:project/languages` (plural) mapping language → directory glob: `{:javascript "src/api/**" :clojurescript "src/ui/**"}`. Checks scoped to a directory inherit that language.

**Trigger condition.** When we attempt to assess a polyglot real-world project and run into the limit.

**Effort.** TBD; pure design exploration.

### T3-4. Cosmetic: blank lines in `:check/rationale` render as bare `> `

Multi-paragraph rationale produces empty blockquote lines (` > `) in reports. Most renderers handle it; a few don't. Fix would be skipping the blockquote prefix on empty lines.

**Effort.** ~10 min.

---

## What is NOT planned (explicit non-goals)

These came up in design discussions but the conclusion was: not building it.

- **No `:project/language` file in the target.** [Finding A](./research/findings.md) — targets stay read-only. The language toggle lives in the framework's EDN, not in a `.code-quality-language` or similar file inside the target repo. The target shouldn't know or care that we're assessing it.

- **No automatic "fix" actions.** The framework reports findings. It does not modify the target to flip checks from FAIL to PASS. That decision belongs to a human; the framework's job is honest signal.

- **No composite "quality score."** Per-concern verdicts only. "Quality 82%" is the failure mode the framework was built to avoid. Severity (`:required | :recommended | :advisory`) is the only policy lever; aggregating across concerns is a category error.

---

## How to use this list

Items in Tier 1 are the natural next session of framework work. Pick the one whose value matches the time available; T1-2 + T1-1 together are the highest-leverage move (unlocks the deep-nesting Clojure problem). T1-3 + T1-4 are pure test-coverage debt repayment, valuable but less visible.

When an item lands, mark it done by deleting it from this file and capturing the change in a commit message that references the item's ID (e.g. "T1-2: add :project/language with auto-detection"). If a substantive lesson came out of doing it, add a finding to [`research/findings.md`](./research/findings.md).
