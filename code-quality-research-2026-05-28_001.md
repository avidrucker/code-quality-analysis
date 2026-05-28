# Code Quality Research — code-quality-analysis — 2026-05-28 (001)

Self-assessment takeaways surfaced by a 2nd run of this tool against the
`avatar-maker` ClojureScript project (`examples/avatar-maker.edn`).

- **Command:** `./assess.bb examples/avatar-maker.edn`
- **Result:** exit 0, 15 deterministic checks, ~9.9s. Byte-identical to the 1st run (only the report timestamp changed). Reproducibility is a quiet point in the tool's favor.
- **Target findings** (improvements for avatar-maker itself) live in that repo: `~/Documents/Study/ClojureScript/avatar-maker/code-quality-research-2026-05-28_001.md`.

## Gaps this run exposed in the tool

### 1. The deep-nesting check can't tell data from control flow
Indentation-based detection (`grep '^( {16,})'`) flags deeply-nested Hiccup/SVG/map literals identically to genuine `let`/`if` pyramids. For a Reagent project that's mostly false positives — the avatar-maker `no-deep-nesting-in-src` FAIL was almost entirely SVG attribute maps.

**Options:**
- Exclude lines whose first non-space char opens a literal (`[`, `{`, `:`).
- Count paren/bracket *nesting depth of forms* rather than raw indentation.

As-is, the check cries wolf on exactly the projects it's run against.

### 2. The avatar-maker config never exercises the AI-assisted runner
All 15 checks are `:deterministic`. The lccjs config had an `:ai-assisted` hot-path probe — the tool's most differentiated feature — but avatar-maker's config has zero. The avatar-maker run is effectively `grep` + `find` in an EDN wrapper.

**Add:** an AI readability/cohesion review of `render.cljs` or `ui.cljs` — precisely where the deterministic checks are weakest (see #1, #3).

### 3. `tests-not-empty` is binary and easily gamed
A project with one trivial `deftest` passes "testability" outright. avatar-maker proved it: PASS on testability with a ~2% test-to-source ratio.

**Consider:** a test-to-source LOC ratio, a `deftest`/`is` assertion count, or a real coverage probe — something that distinguishes "has tests" from "is tested."

### 4. FAIL evidence doesn't name the offender
`max-src-file-loc-bound.log` records `exit: 1` with **empty stdout** — it says a file exceeded 800 lines but not *which* file or by how much. Had to `wc -l` manually to learn it was `ui.cljs:1699` and `render.cljs:1496`. The `awk`/`find` command discards that.

**Fix:** findings should carry the offending path + measured value. Right now the report's advisory item just re-prints the check description — the opposite of evidence.

### 5. (nice-to-have) No run-over-run delta
Runs are deterministic and reports aren't version-controlled, so a repeat run is identical and uninformative. A trend/diff view ("ui.cljs grew 40 lines since last run") would make repeat runs worthwhile. The reproducibility needed for this already exists.

## Priority
Fix #1 (false positives) and #4 (evidence naming) first — they directly undermine trust in the output. #2 and #3 expand what the tool can actually claim.
