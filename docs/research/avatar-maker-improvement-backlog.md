# avatar-maker — improvement backlog

Action items derived from [`avatar-maker-scorecard-2026-05-27.md`](./avatar-maker-scorecard-2026-05-27.md). Each entry is a failing check from that scorecard, with concrete next steps if you choose to act on it.

This list lives in the code-quality-analysis repo rather than inside avatar-maker (per [Finding A](./findings.md) — targets stay read-only). When/if you act on an item, the work happens in `~/Documents/Study/ClojureScript/avatar-maker/`, not here.

Ordered by recommended priority: highest-value, smallest-effort first.

---

## 1. `unit-tests-pass` (required, FAILED) — ✅ DONE 2026-05-27

**Status note (2026-05-27).** Resolved. shadow-cljs is already in `devDependencies` (`^3.4.11`) and `node_modules/.bin/shadow-cljs` resolves correctly. The previous "shadow-cljs: not found" was because `node_modules` hadn't been installed in the assessment's subprocess at the time. After `npm install` ran, the check flipped to PASS. No further action needed unless the dev-dep gets removed.



**What it measures.** `npm test` exits zero. The script in avatar-maker is `shadow-cljs compile test && node target/test.cjs`.

**Why it matters.** It's the only `:required` correctness check that failed. Required means "this must hold for the project to be considered shippable" — and right now `npm test` errors with `sh: shadow-cljs: not found`.

**Root cause.** `shadow-cljs` isn't in `node_modules/.bin/` (not listed in `package.json`'s `devDependencies`), and the framework's subprocess doesn't inherit the user's interactive-shell PATH where the nvm-managed global `shadow-cljs` lives. So when the npm script tries to run `shadow-cljs compile test`, it fails. Same thing would happen on any fresh-machine clone, or in any CI runner that doesn't explicitly install shadow-cljs.

**Concrete fix.** Add `shadow-cljs` to `devDependencies` in avatar-maker's `package.json`. Then `npm install` puts it in `node_modules/.bin/`, which npm scripts find automatically:

- [ ] In `~/Documents/Study/ClojureScript/avatar-maker/`, run `npm install --save-dev shadow-cljs`.
- [ ] Commit the change (one-line addition to `devDependencies`, plus `package-lock.json` update).
- [ ] Re-run `./assess.bb examples/avatar-maker.edn` from the framework and confirm `unit-tests-pass` flips to PASS.

**Alternative fix (if you prefer the global install).** Change the script to `npx shadow-cljs compile test && node target/test.cjs`. `npx` uses local install if present, else downloads. Same outcome, slightly slower on cold-cache machines.

**Effort.** ~5 min.

**Recommendation.** **Apply.** This is the single check that flipped exit code to 1. Real value, near-zero risk.

---

## 2. `readme-substantive` (advisory, FAILED) — ✅ DONE 2026-05-27

**Status note (2026-05-27).** Resolved. README rewritten to ~4 KB with stack, requirements, setup, common tasks (with a real command table), project layout, and deployment sections. Well over the 200-byte threshold. (Still uncommitted as of writing — the rewrite is in your working tree.)



**What it measures.** README.md exists and is ≥ 200 bytes.

**Why it matters.** avatar-maker's README is currently 166 bytes. A new contributor (or future-you in 6 months) lands on the GitHub repo and gets almost nothing — no description of what the project does, no run instructions, no link to the deployed page.

**Concrete fix.** Write a real README. Suggested skeleton:

- [ ] One-sentence elevator pitch (what + who for).
- [ ] One paragraph on what it does (avatar maker, what styling options, the use case).
- [ ] Install + dev section (`npm install`, `npm run dev`).
- [ ] Build + deploy section (`npm run build`, GH Pages link).
- [ ] License line.

**Effort.** ~15 min.

**Recommendation.** **Apply.** Trivial cost, useful payoff every time anyone (incl. you) visits the repo.

---

## 3. `node-version-pinned` (advisory, FAILED) — ✅ DONE 2026-05-27

**Status note (2026-05-27).** Resolved in avatar-maker commit `59b0ea3`. Added `.nvmrc` with `24.16.0` and `engines.node` `>=20.0.0` in package.json (matches the floor documented in your new README). Local commit only, not pushed yet — pending your push decision.



**What it measures.** `.nvmrc` OR `engines.node` in `package.json` exists.

**Why it matters.** avatar-maker has a real CI workflow (`.github/workflows/pages.yml`) that runs `npm install` and shadow-cljs builds. Without a Node pin, CI uses whatever default Node version GitHub Actions ships — which changes over time and can silently break builds. Same applies to other dev machines: clones from anywhere may use Node N+1 that lccjs hasn't been tested on.

**Concrete fix.** Same shape as the lccjs fix (commit `9422e9b` on lccjs is a precedent worth mirroring):

- [ ] `node --version` to confirm the known-working version.
- [ ] Write that bare version (no `v` prefix) to `.nvmrc` in avatar-maker root.
- [ ] Add `"engines": { "node": ">=18.0.0" }` to `package.json` (or whichever floor matches the project's audit signals).
- [ ] Commit.

**Effort.** ~5 min.

**Recommendation.** **Apply.** Real CI runs already; the pin is genuinely overdue here, not just a procedural advisory.

---

## 4. `max-src-file-loc-bound` (advisory, FAILED)

**What it measures.** No file in `src/avatar/` exceeds 800 lines.

**Failure detail.** Two files blow the bound:
- `src/avatar/ui.cljs` — **1699 lines**
- `src/avatar/render.cljs` — **1496 lines**

Together that's 3195 lines in two files, vs ~952 lines across the other 7 source files combined. Heavy concentration.

**Why it matters.** A 1600-line single-namespace file is harder to navigate, harder to reason about coherently, and concentrates change-impact — most edits to "the UI" probably touch one of two files, every time. Code review, debugging, and onboarding all pay this cost.

**Concrete fix (sketch — needs your judgment on the right splits).** Skim each file and identify natural cleavage seams:

- [ ] Read `ui.cljs` and `render.cljs` end-to-end (or have an agent skim them) and propose natural splits.
- [ ] Likely candidates for `ui.cljs`: separate components for sliders/toggles vs preview vs save/load controls. Each could become its own ns.
- [ ] Likely candidates for `render.cljs`: SVG primitives (paths, gradients) vs body-part renderers (head, eyes, hair) vs composition logic. Three nses minimum.
- [ ] One PR per logical split, with tests passing after each step. Don't bundle.

**Effort.** ~2–4 hours of careful refactor, easy to do iteratively.

**Recommendation.** **Apply when you have a longer block.** This is the most valuable structural improvement on the list but also the most expensive. Not blocking anything; defer until you next touch this code substantively.

---

## 5. `no-deep-nesting-in-src` (advisory, FAILED)

**What it measures.** No lines in `src/avatar/` indented 16+ spaces (≈8+ levels at 2-space indent).

**Failure detail.** Matches concentrated in `config.cljs` (nested data literals for default avatar configuration — maps inside maps inside maps describing eye/hair/body part defaults) and `render.cljs` (mixed: some real control-flow nesting, plus deep data-literal nesting for SVG-element generation).

**Why this finding is partly invalid.** Per [Finding A, category (c)](./findings.md) in the research log: the grep heuristic can't distinguish Clojure data-literal nesting (which the community considers readable) from control-flow nesting (which actually does indicate complexity). Maybe half the matches in `config.cljs` are data literals, not real complexity.

**Concrete fix.**

- [ ] Inspect `reports/avatar-maker/no-deep-nesting-in-src.log` (regenerated on each run) and visually classify matches: data-literal vs control-flow.
- [ ] For real control-flow nesting matches: refactor where they fall inside `render.cljs` — extract helper fns, replace deep `let` chains with threading macros, etc.
- [ ] For data-literal matches: accept. They're not a complexity smell.
- [ ] Optionally, defer entirely until [v2's clj-kondo integration](../v2_backlog.md) ships — clj-kondo's `:max-nesting` linter understands the distinction.

**Effort.** ~30 min to triage; variable for the actual refactors.

**Recommendation.** **Defer to v2.** The check is partially noisy on Clojure today. Wait for clj-kondo dispatch, then re-run; only act on what a smarter analyzer surfaces.

---

## Summary of recommended applies (low effort, high value)

### Status as of 2026-05-27

| # | Action | Status |
|---|---|---|
| 1 | shadow-cljs in devDependencies | ✅ Already done (in committed state pre-assessment) |
| 2 | Real README (≥ 200 bytes) | ✅ Done in working tree, awaiting your commit |
| 3 | `.nvmrc` + `engines.node` | ✅ Done in commit `59b0ea3`, awaiting your push |
| 4 | God-files refactor (`ui.cljs`, `render.cljs`) | ⬜ Deferred — needs a dedicated refactor session |
| 5 | Deep-nesting cleanup | ⬜ Blocked on v2's clj-kondo dispatch (framework side) |

**Latest scorecard:** 12 PASS / 3 FAIL / 0 UNKNOWN, **exit 0** (was 10/5/0, exit 1 before this round). The three remaining failures are all advisory — `no-uncommitted-changes` (flips when you commit your WIP), plus #4 and #5 above.

### What's left

After the cleanup above lands fully (your commits push), the avatar-maker scorecard's only unresolved items are:

- **#4** — a real structural improvement to avatar-maker itself; do when you next touch the UI substantively.
- **#5** — won't be cleanly actionable until the framework gains language-aware analyzer dispatch (T1-1 in [v2_backlog.md](../v2_backlog.md)).

So: avatar-maker is essentially "done from the framework's perspective" until either (a) you decide to refactor the god-files or (b) the framework gains clj-kondo dispatch.
