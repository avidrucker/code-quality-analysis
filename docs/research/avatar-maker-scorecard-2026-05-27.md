> **Frozen snapshot.** Copy of `reports/avatar-maker/report.md` as it existed
> on 2026-05-27, preserved here in git because `reports/` is gitignored and
> overwritten on each run. See [`avatar-maker-improvement-backlog.md`](./avatar-maker-improvement-backlog.md)
> for action items derived from this scorecard, and [`findings.md`](./findings.md)
> (Finding A) for the post-run analysis.

# Code Quality Assessment — avatar-maker

- **Started:** 2026-05-28T03:22:07.632120537Z
- **Duration:** 370 ms
- **Config:** `examples/avatar-maker.edn`
- **Checks:** 15

## Summary

| Concern | Level | Pass | Fail | Unknown | Verdict |
|---|---|---|---|---|---|
| correctness | first-order | 2 | 1 | 0 | FAIL |
| delivery-safety | first-order | 4 | 2 | 0 | WARN |
| maintainability | second-order | 1 | 1 | 0 | WARN |
| readability | second-order | 1 | 1 | 0 | WARN |
| testability | second-order | 2 | 0 | 0 | PASS |

## Required failures

- **[correctness] unit-tests-pass** — `npm test` exits zero (compiles + runs node-test build).
  - Evidence: `reports/avatar-maker/unit-tests-pass.log`

## Advisory items

- [delivery-safety] node-version-pinned — Node version is pinned via `.nvmrc` or `engines.node` in package.json.
  > Why: silent breakage from Node version skew between dev laptop, CI, and production is one of the most common 'works on my machine' failure modes. The cost a pin lowers is the hours spent diagnosing that skew after the fact.
  > 
  > When it matters: multiple developers; any CI runner; code that uses features tied to a Node major (fetch, Array.findLast, top-level await); native-addon deps that ship version-specific prebuilds.
  > 
  > When it's overkill: solo learning projects with no CI and no deploy target. avatar-maker has a `.github/workflows/pages.yml` CI workflow, so a pin would actually matter here — but adding one is a deliberate decision, not something the framework should automate. See docs/research/findings.md Finding A.
  - Evidence: `reports/avatar-maker/node-version-pinned.log`
- [readability] no-deep-nesting-in-src — No lines in `src/avatar/` indented 16+ spaces (≈8+ levels at 2-space indent).
  - Evidence: `reports/avatar-maker/no-deep-nesting-in-src.log`
- [maintainability] max-src-file-loc-bound — No file in `src/avatar/` exceeds 800 lines.
  - Evidence: `reports/avatar-maker/max-src-file-loc-bound.log`
- [delivery-safety] readme-substantive — `README.md` exists and is at least 200 bytes (i.e. more than a title line).
  > A README is the first thing anyone — a future contributor or a future-you — sees on arrival. A near-empty README isn't catastrophic, but the cost of writing 200+ bytes is trivial and the value scales with every onboarder. The 200-byte threshold is generous: it asks for a title, one sentence on what the project does, and one sentence on how to run it. Anything less is a red flag that orientation is missing.
  - Evidence: `reports/avatar-maker/readme-substantive.log`

## All checks

| ID | Concern | Severity | Runner | Status | Conf | Evidence |
|---|---|---|---|---|---|---|
| unit-tests-pass | correctness | required | deterministic | FAIL | high | `reports/avatar-maker/unit-tests-pass.log` |
| no-fixme-in-src | correctness | advisory | deterministic | PASS | high | `reports/avatar-maker/no-fixme-in-src.log` |
| no-todo-in-src | correctness | advisory | deterministic | PASS | high | `reports/avatar-maker/no-todo-in-src.log` |
| lockfile-present | delivery-safety | recommended | deterministic | PASS | high | `reports/avatar-maker/lockfile-present.log` |
| shadow-cljs-config-present | delivery-safety | recommended | deterministic | PASS | high | `reports/avatar-maker/shadow-cljs-config-present.log` |
| ci-workflow-present | delivery-safety | recommended | deterministic | PASS | high | `reports/avatar-maker/ci-workflow-present.log` |
| no-uncommitted-changes | delivery-safety | advisory | deterministic | PASS | high | `reports/avatar-maker/no-uncommitted-changes.log` |
| node-version-pinned | delivery-safety | advisory | deterministic | FAIL | high | `reports/avatar-maker/node-version-pinned.log` |
| no-deep-nesting-in-src | readability | advisory | deterministic | FAIL | high | `reports/avatar-maker/no-deep-nesting-in-src.log` |
| no-banned-vague-fn-names | readability | advisory | deterministic | PASS | high | `reports/avatar-maker/no-banned-vague-fn-names.log` |
| max-src-file-loc-bound | maintainability | advisory | deterministic | FAIL | high | `reports/avatar-maker/max-src-file-loc-bound.log` |
| few-runtime-dependencies | maintainability | advisory | deterministic | PASS | high | `reports/avatar-maker/few-runtime-dependencies.log` |
| tests-not-empty | testability | required | deterministic | PASS | high | `reports/avatar-maker/tests-not-empty.log` |
| no-commented-out-tests | testability | advisory | deterministic | PASS | high | `reports/avatar-maker/no-commented-out-tests.log` |
| readme-substantive | delivery-safety | advisory | deterministic | FAIL | high | `reports/avatar-maker/readme-substantive.log` |
