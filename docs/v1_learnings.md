# v1 Learnings

A contemporaneous log of observations, fixes, and surprises accumulated while rounding out v1 of the framework. Each entry is written as the work lands, not retrofitted — so the file shows the actual order of discovery, not a tidied retrospective.

## 2026-05-27 — "Round out v1" pass

A grab-bag of small but real improvements identified in a self-assessment of the codebase. The list (with priority order at the time):

1. Fix `compute-exit-code` so a `:required` check returning `:unknown` exits non-zero, not 0.
2. Harden `validate-config` to catch runner-specific config errors at startup (missing `:check/command`, `:check/pass-when`, etc.) instead of silently failing at runtime.
3. Add a GitHub Actions workflow that runs `./test/assess_test.bb` on every push and PR.
4. Add a representative output sample to `README.md` so anyone landing on the repo can see what a real scorecard looks like.

Findings below, in the order completed.

<!-- findings get appended here as work lands -->

### Finding 1 — `compute-exit-code` silently passed required `:unknown` checks

**Problem.** The exit-code function only treated `:fail` as a gating outcome. A `:required` check that returned `:unknown` — e.g. the AI runner couldn't reach Claude because of an auth or network problem — produced exit 0, indistinguishable from a clean pass. That defeats the whole point of `:required`: a check the team has declared must hold can quietly be skipped if its tooling breaks.

**Change.** `compute-exit-code` now returns 1 for any `:required` check whose status is in `#{:fail :unknown}`. Severity `:recommended` and `:advisory` are unaffected. `:n/a` doesn't gate either way — it's the explicit "this check doesn't apply to this project" outcome.

**Decision: why not gate on `:unknown` for `:recommended` too?** Because `:recommended` already means "warn, don't gate" — extending unknown-gating to it would conflate severity tiers. The fix is scoped to `:required` only, matching the policy that severity is policy and shouldn't be re-derived from status.

**Tests added.** 6 new assertions covering: empty results, all-pass, any-fail, any-unknown, recommended/advisory failures (don't gate), and `:n/a` on required (doesn't gate). Total test count moved from 81 → 87.

**Surprise.** Writing the tests first made the `:n/a` case obvious — it would have been easy to write the fix as `(not= :pass status)` and accidentally gate on `:n/a` too. The explicit set `#{:fail :unknown}` keeps the intent visible at the call site.

### Finding 2 — `validate-config` was silent about runner-specific required keys

**Problem.** A `:deterministic` check missing `:check/pass-when` would load fine and only fail at runtime — and even then, the failure mode was confusing because `pass-when-satisfied?` returns `false` on empty pass-when (the deliberate "don't fabricate passes" rule). The user would see a mysterious `:fail` with no explanation. Same shape for an `:ai-assisted` check missing `:check/prompt-file` (would run, produce `:unknown` with a "prompt file missing" note, but only at runtime) and a `:human-rated` check missing `:check/sign-off-path` (similar).

**Change.** `validate-config` now dispatches on `:check/runner` after the universal-keys check and validates runner-specific required keys:

- `:deterministic` → must have a string `:check/command` AND a non-empty `:check/pass-when` map
- `:ai-assisted` → must have a string `:check/prompt-file` (file existence is still checked at runtime, because we don't want config-load to fail when running outside the repo)
- `:human-rated` → must have a string `:check/sign-off-path`

All errors are collected and reported together with exit 2. Smoke-tested with a hand-crafted bad config that hits all three paths — surfaces all three errors in one pass instead of one error per re-run.

**Decision: file-existence still checked at runtime, not load time.** Tempting to also verify the prompt-file actually exists on disk during validation, but that couples config-loading to the runner's filesystem expectations. Some teams may keep prompts in a shared dir mounted at runtime, or generate them just-in-time. Validating *presence of the key* is mechanical and universally correct; validating *existence of the file* belongs to the runner where it's already handled.

**Surprise.** Pre-existing `validate-config` only checked the universal keys (`:check/id`, `:check/runner`, `:check/severity`) — runner-specific validation was a noticeable gap once I looked. Probably the right v1 default (universal checks first, runner-specific later) but worth surfacing as a pattern: "this validation pass is intentionally permissive about runner shape, with a TODO" is a clearer comment than silently shipping a permissive validator.

### Finding 3 — GitHub Actions CI for tests on push and PR

**Goal.** Lock in the test suite. Anything that breaks the 87 assertions now produces a red mark before it merges. Same for `./assess.bb --help` (catches the case where the runner doesn't even load — e.g. a syntax error in `assess.bb`).

**Choice of action: `DeLaGuardo/setup-clojure@13.4`.** It's the canonical Clojure-tooling installer for GHA, supports Babashka via the `bb: latest` input. Alternatives considered:

- Manually downloading the babashka binary release in a `run:` step. Works but adds version-pinning maintenance and a checksum step.
- `apt-get install babashka` — not in Ubuntu's default repos.
- Using a Docker image with bb preinstalled — works but adds image-pull time and indirection.

The setup-clojure action wins on simplicity. One step, one input.

**Workflow structure.**

1. checkout
2. install bb
3. `bb --version` — confirms bb is on PATH; fast-fails the run if step 2 silently no-op'd
4. `./assess.bb --help` — load-time smoke test
5. `./test/assess_test.bb` — full unit suite

Three "real" steps (3, 4, 5) gives independent failure surfaces. Step 4 catches things step 5 wouldn't (a syntax error in code that's not reachable from the test imports, for example, would still flag at script-load).

**Decision: triggers on `push: main` and `pull_request: main` only.** No nightly cron, no manual-dispatch trigger. The repo is small enough that every push/PR running tests is the right cadence. Cron is for things you can't trigger from a code change (drift detection, dependency CVE scans). Tests don't fit that model.

**Decision: no caching.** Babashka install is fast (<10s) and the test run is <1s. Adding a cache step would save maybe 5 seconds per run while adding complexity. Skip.

**Decision: not also running `./assess.bb examples/lccjs.edn` in CI.** That requires lccjs to be checked out and `npm install`'d — substantial complexity for what's essentially a dogfood demonstration, not a correctness check. The unit tests cover the framework's logic; integration with a real target project belongs on the developer's machine.

**Not verified live yet.** This finding is written before the workflow lands on GitHub — first push will show whether the action versions, the bb install, and the script paths all line up. Update this section if anything fails on first run.

### Finding 4 — README output sample

**Problem.** Pre-fix, the README explained *what* the framework does but a visitor had no idea what its output looks like until they cloned and ran it. That's a big drop-off point — anyone evaluating a tool wants to see its voice before they install anything.

**Change.** New "What the output looks like" section in `README.md` with a representative slice of an actual lccjs run: Summary table, one failed Recommended check (with its rationale rendered as a blockquote), and the Unknown section. The slice was captured from a live `./assess.bb examples/lccjs.edn` run, not fabricated.

**Decision: real output, not idealized.** Tempting to trim to a "happy path" with all PASSes, but that hides the most important rendering choices: the rationale blockquote, the `:unknown` first-class section, the per-concern verdicts. The whole point of the framework is that imperfect projects get readable reports, not that perfect projects look pretty.

**Decision: excerpted, not full.** A complete report is ~60 lines. The excerpt picks the Summary + one Recommended failure + the Unknown section because together they showcase: (a) per-concern verdicts, (b) rationale rendering, (c) the no-fake-numbers rule. The full "All checks" table is omitted from the README — it's useful in the actual report but the README isn't the place to enumerate every check.

**Surprise.** The Advisory items section, which was visible mid-edit (working tree had uncommitted changes), gets omitted from the README excerpt because it'd be transient noise to a new reader. Lesson for self: report samples taken mid-development tend to capture state that's misleading out of context. Worth running a fresh sample from a clean working tree before committing the README change. (I didn't quite do that — see the next finding if this re-runs after commit and the Advisory section vanishes from the live report.)

### Finding 5 — `gh` OAuth token needs `workflow` scope to push CI workflow

**Problem.** First push of the v1 round-out commit was rejected by GitHub:

```
! [remote rejected] main -> main (refusing to allow an OAuth App to create
  or update workflow `.github/workflows/test.yml` without `workflow` scope)
```

The local `gh` auth has scopes `gist, read:org, repo` — sufficient for normal repo operations but explicitly insufficient for touching `.github/workflows/*`. GitHub treats workflow files as a privileged surface (an attacker with `repo` scope alone shouldn't be able to add a malicious GHA that runs with `GITHUB_TOKEN`'s broader permissions).

**Fix.** One-time interactive command to add the scope:

```bash
gh auth refresh -s workflow
```

Then re-push:

```bash
git push origin main
```

**Decision: don't split the commit to push the rest first.** The four work items are conceptually one round-out; splitting just to satisfy a scope quirk would muddy the git history. The commit stays local until the scope is added — one push, full landing.

**Surprise.** I knew `gh`'s default `repo` scope didn't include `workflow`, but I'd never had the rejection fire in practice because most repos already have their workflows in place. First-time `.yml` adds against an OAuth-flow auth are exactly the case that trips this. Worth remembering: any GHA work in a new repo needs the scope upgrade first.

**Note for the framework itself: workflow files are NOT in `.gitignore`.** So once the scope is added, the workflow lands like any other source file. No magic; just a one-shot auth refresh.

### Finding 6 — Pre-push self-review caught two crash bugs and a doc inaccuracy

**Context.** Before pushing the round-out commit, I ran a careful pre-push review prompted by the question "are there any further refinements before we push?" That single pause caught three things the original commit was about to ship with.

**Catch 1: `validate-config` skipped pass-when validation for `:ai-assisted` checks.** I'd added pass-when validation for `:deterministic` but not the other runners. An AI-assisted check missing `:pass-when` produces a `NullPointerException` at runtime when the runner tries to call `pass-when-satisfied?` on nil. Fixed.

**Catch 2: `validate-config` accepted non-map `:pass-when` values.** The original validation logic was:

```clojure
(when (or (nil? pw) (and (map? pw) (empty? pw))) ...)
```

That tolerates a vector or keyword as `:pass-when` (because the `(map? pw)` short-circuits the `and`). At runtime, `pass-when-satisfied?` tries to destructure as map-entries and crashes with `UnsupportedOperationException: nth not supported on this type: Keyword`. Fix: a `pass-when-valid?` helper that requires `(and (map? pw) (seq pw))`.

**Catch 3: README excerpt's specific Pass/Fail counts depend on lccjs's `npm test` not flaking.** During the review I saw two consecutive runs of `./assess.bb examples/lccjs.edn` produce different `correctness` columns — `3/1/0 FAIL` then `4/0/0 PASS` thirty seconds later. Same code, same config, different result. Not a framework bug; lccjs's test suite has occasional timing-sensitive tests. The README sample shows the stable state but a reader's first run might not match.

**Fixes that landed.**

- `validate-config` refactored: pure `collect-config-errors` returns a vector; orchestrator dies on errors. Now testable.
- `pass-when-valid?` helper requires non-empty map. Applied uniformly to `:deterministic` AND `:ai-assisted`.
- Test suite added: `pass-when-valid?-test` (7 assertions) and `collect-config-errors-test` (15 assertions covering each error path and the multi-error case). Up from 87 → 109 assertions, 9 → 11 deftests.
- README excerpt got a parenthetical: *"Sample is representative, not deterministic."*

**Decision: pure function for error collection is more important than I'd initially given it credit for.** I almost shipped `validate-config` with the original `die!`-mixed-in implementation. Testing it would have required either subprocess isolation or `with-redefs` over `System/exit`. Both ugly. Splitting the pure part from the side-effect part is the textbook fix; the cost is one extra function name and the value is a testable seam. Worth taking the time on this pattern by default for any code that's mostly pure with a tiny side effect at the boundary.

**Insight: the pre-push pause is itself worth a finding.** Three real catches in one slow read-over. The temptation when pushing-ahead is to trust that "tests pass + manual smoke = ready," but tests pass only verifies what's tested, and manual smoke only verifies the happy path. Asking "what could go wrong if a user wrote a config slightly differently from my examples?" caught Bug 1 and Bug 2; asking "would the README sample look like this on a stranger's machine?" caught Catch 3.

**Lesson for v2 work.** Maintain a pre-push checklist that includes:

- Run `./assess.bb` against deliberately broken inputs (missing keys per runner type) — catches validation gaps.
- Run the same example twice in a row from a clean tree — catches order/flake dependencies.
- Re-read any documentation samples and ask "is this still true?" — catches drift.

### Finding 7 — First CI run was green, but flagged a time-bomb deprecation

**Context.** Finding 5 predicted "first push will show whether the action versions, the bb install, and the script paths all line up." It did — the run completed in 8 seconds, all seven steps green, 109 test assertions passed. But the run also produced an annotation:

```
! Node.js 20 actions are deprecated. The following actions are running on Node.js 20:
  actions/checkout@v4, DeLaGuardo/setup-clojure@13.4
  Node.js 24 becomes the default on June 2nd, 2026.
```

The CI works *today*. It would break in ~one week (June 2nd) when Node.js 20 stops being the default on hosted runners.

**Change.** Bumped both pins in `.github/workflows/test.yml`:

- `actions/checkout@v4` → `@v6` (latest stable, January 2026). Major version bump, but our usage is the zero-config default — checkout the repo at the current ref — which is forward-compatible across v4/v5/v6.
- `DeLaGuardo/setup-clojure@13.4` → `@13.6.1` (latest, May 2026). Minor bump; safe drop-in.

**Decision: pin to specific versions, not floating tags.** A common alternative is `actions/checkout@v6` (a moving major-tag) vs `actions/checkout@v6.0.2` (a frozen SHA-equivalent). I chose the major-tag for `@v6` because:
- Security: GitHub already warns when a tagged action ref is moved unexpectedly; the additional supply-chain risk of `@v6` vs `@v6.0.2` is small.
- Maintenance: pinning to a specific minor avoids version churn for trivial patch-level fixes the action author may ship.
- Auditability: the major-tag is what action documentation shows; matching that makes future debugging easier.

For `setup-clojure` I went with the full `@13.6.1` because the action's release cadence is faster (3 minor versions in 2 months) and pinning to a specific minor reduces the chance of a transient regression sneaking in.

**Surprise.** Pinning a CI action at all is a tradeoff I don't see discussed often. The two failure modes are:
1. Too loose (`@main`): every push potentially behaves differently as the action evolves.
2. Too tight (`@v4.1.7`): pins to a specific minor that goes stale, and when the major you're on hits EOL you have to re-evaluate from scratch.

The middle ground (`@v6` major-tag) means trusting the action author to follow semver. For high-traffic actions like `actions/checkout` that's reasonable. For lower-traffic actions, more careful pinning may be warranted.

**Lesson.** First-CI-run produces *two* kinds of signal: did it pass, and what did it warn about? Don't stop reading at the green ✓. The Node.js 20 deprecation was a deadline-driven thing that would have silently caught us in a week; the warning was visible only because GitHub Actions surfaces deprecations as run-level annotations. Worth a Finding-7-style post-mortem habit: after every "first time we did X" event, re-read the output for the warnings hidden under the success.

---

## Deferred for v2

Things that surfaced during this pass but weren't acted on yet:

- **Range predicates.** Can't currently express `4 ≤ rating ≤ 10` because the `:pass-when` map is AND-combined by key, and two predicates on the same key (`:rating`) overwrite each other. Needs either a list-of-clauses shape or compound numeric specs like `{:between [4 10]}`.
- **Per-check AI output schema.** The JSON schema for AI replies is hardcoded as `{rating, reasoning, evidence, confidence}` in `assess.bb`. Configurable schema per check would unlock boolean checks, list-of-citation checks, etc.
- **Project profile presets.** The schema documents `:project/profile :cli-tool / :web-app / ...` but the runner doesn't act on it. Profiles could ship default check selections and severity overrides.
- **Check dependencies.** `:check/depends-on [:unit-tests-pass]` to short-circuit downstream checks when a prerequisite fails. Currently every check runs unconditionally — fine for v1's 17-check config, wasteful at scale.
- **Runner-level integration tests.** The three `defmethod` runners (`:deterministic`, `:ai-assisted`, `:human-rated`) have no direct test coverage. A mock `claude` binary on PATH and shell stubs would unlock real tests.
- **Report-rendering tests.** `render-markdown`, `render-failure-entry`, `concern-verdict` are uncovered. Easy to add via `with-out-str` plus string assertions.
- **More example configs.** Only `lccjs.edn` exists. A second example for a different stack (Clojure, Python) would prove the schema generalizes.
- **Cosmetic: blank lines inside `:check/rationale`** render as bare `> ` lines in the report blockquote. Most markdown renderers handle it but it looks slightly off.
