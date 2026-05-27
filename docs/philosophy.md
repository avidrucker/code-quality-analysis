# Philosophy

The framework's stance. Read this before the [concern catalog](./concern-catalog.md) or the [config schema](../schema/SCHEMA.edn) — it explains why the catalog is shaped the way it is.

## The reframing

Most "code quality" frameworks ask:

> What are the qualities of good code?

That question produces lists of virtues (clean, elegant, simple, readable, maintainable, idiomatic) which feel meaningful but resist measurement. Teams end up performing rituals — adopting SOLID, "clean code," Liskov substitution — without ever knowing whether those rituals lower a real cost.

This framework asks a different question:

> What claims do we want to make about this project, and what evidence would prove or disprove them?

Every concern in the catalog is expressible as one or more **checks**. A check may be automated, AI-assisted, or human-rated, but it must define:

- What is being measured
- How it's measured
- What threshold counts as passing
- Whether the check is required for this project
- What evidence supports the result

If you can't answer those five, the concern isn't ready to ship in a config yet — it's still a feeling.

## First-order, second-order, cross-cutting

The first big split: not all concerns are peers.

**First-order concerns** are externally meaningful outcomes. A system can fail catastrophically on any of them even when the code looks clean.

- Correctness, Performance, Reliability, Security, Data integrity, Scalability, Usability, Accessibility, Compatibility, Operability, Recoverability, Cost efficiency, Delivery safety

**Second-order code qualities** are internal qualities that help achieve first-order outcomes. They're valuable because they reduce the cost of correctness, change, debugging, testing, and operation — but they have no value by themselves.

- Readability, Maintainability, Testability, Debuggability, Simplicity, Composability, Modularity, Idiomaticity, Conciseness, Documentation, Naming, Explicitness, Dependency hygiene

**Cross-cutting mechanisms** affect many concerns at once and don't fit cleanly on either side.

- Logging, Types/schemas, Error handling, Configuration, Dependencies, CI/CD, Monitoring, Documentation, Tests, Code review, Feature flags

The practical consequence: a project can be in good shape on the first-order concerns while looking ugly internally, or it can have beautiful internals while failing on what users actually see. Conflating the two — as `WARMED` and `SOLID` both do — produces frameworks that score "passing" projects which are actually broken, or "failing" projects which are actually fine.

## Objective vs operationalized

Some things are directly objective:

- Time to load, memory usage, test pass/fail, vulnerability count, bundle size, dependency count, files-touched-per-change, cyclomatic complexity, accessibility scan violations.

Some things are not directly objective but can be **operationalized** — measured through a defined procedure that may include human or AI judgment:

- Readability, simplicity, maintainability, idiomaticity, architecture quality, UX clarity.

"Operationalized" means: we define a procedure for measuring it, even when the thing itself is partly subjective.

"Readable" is vague. But this is measurable:

> Give 5 developers a function and 5 minutes. Ask them to answer 3 questions about its behavior. At least 4 out of 5 must answer all 3 correctly.

That is not perfectly objective, but it's checkable, repeatable, and evidence-based. The framework treats objective and operationalized checks as peers — but tags their confidence differently (see below).

## Status model

Every check produces a status in `#{:pass :fail :unknown :n/a}`:

- `:pass` — the threshold was met
- `:fail` — the threshold was not met
- `:unknown` — there's no signal, and we refuse to fake one
- `:n/a` — the check doesn't apply to this project's profile

Plus a confidence in `#{:high :medium :low}`:

- `:high` — the check ran cleanly against canonical inputs
- `:medium` — the check ran, but with reduced precision (sample-based, AI-rated)
- `:low` — degraded path (timeout, parse failure, missing inputs)

**`:unknown` is first-class.** This is the most important rule in the framework. When a check can't be measured (no profiling data, no human reviewer available, the AI runner couldn't reach its model), the result is `:unknown` — never silently coerced to `:pass` or `:fail`. The point: a framework that fabricates numbers to "look complete" trains the same dishonesty Casey Muratori identifies in `SOLID` and "clean code" — performance of measurement without measurement.

## Severity policy

Every check declares a severity in `#{:required :recommended :advisory}`:

- `:required` — failure exits the runner non-zero. Use for things that must hold for this project (e.g., unit tests pass, no critical CVEs).
- `:recommended` — failure produces a warning in the report. Use for strong defaults you'd want to fix soon (e.g., bundle-size bound, no `.only` in tests).
- `:advisory` — failure is logged only, never gates anything. Use for aspirational or context-dependent checks.

Severity is **policy, not a numeric weight.** The framework deliberately avoids a single composite score like "code quality: 82%." Such scores hide which dimension is failing and reward gaming the weighting. Per-concern verdicts and explicit severity tiers are the alternative.

## Project profile

A project profile is just a curated **selection** of checks plus their severities. The same concern catalog supports radically different projects by toggling what's enabled.

A teaching demo should not be judged like a banking system. A safety-critical system shouldn't ship without `:required` checks on data integrity and recovery. The profile is the lever — not the catalog.

In v1, profile is informational (`:project/profile :cli-tool`). Future versions can add presets like `:safety-critical`, `:teaching-demo`, `:web-app` that ship with default check selections.

## Casey's WARMED bias, preserved as tiebreaker

Casey Muratori's WARMED framework (Writing, Agreeing, Reading, Modifying, Executing, Debugging) is too narrow to use directly — it conflates first-order outcomes (E: executing on hardware) with second-order code qualities (R: reading; M: modifying; A: agreeing). The full first-order/second-order split above supersedes it.

But the underlying instinct survives as a **tiebreaker on check selection**:

> When two candidate checks measure roughly the same thing, prefer the one that maps to what the machine actually does.

A check that measures milliseconds, bytes, cycles, allocations, or query counts beats a check that measures abstract structural properties. The framework should bias toward checks that ground out in real machine cost, especially within the Performance, Data integrity, and Cost efficiency concerns. This is the WARMED talk's most durable point and survives the framework's broader scope.

## What this framework does not do

A few anti-goals worth naming, so future versions don't drift:

- **No single composite score.** Per-concern verdicts only. "Quality 82%" is the failure mode this framework was built to avoid.
- **No ritual checks.** Every check must name a measurable cost that would go down if the check passed. "Follows SOLID" is not a check. "No class has more than 7 public methods" might be — if the team can name what cost that lowers.
- **No fake numbers under `:unknown`.** If the AI runner can't reach a model, the human reviewer hasn't signed off, or the profiler isn't installed, the result is `:unknown`. The framework will not fill in a plausible-looking value.
- **No coverage targets in lieu of correctness.** Coverage is a second-order quality; correctness is first-order. A 90%-coverage suite that doesn't test the actual business invariants fails the framework's intent even if it scores well on a coverage check.

## Replacing vague vocabulary

When a stakeholder asks for "clean," "elegant," or "simple" code, translate to operationalized checks before agreeing:

| Vague term | Operationalized replacement |
|---|---|
| Clean | Formatter passes; complexity under threshold; no dead code; no duplication above threshold |
| Elegant | Lower complexity than alternative; fewer concepts; same behavior with less duplication |
| Simple | Fewer branches; fewer states; fewer dependencies; lower nesting; lower cognitive complexity |
| Readable | Cognitive complexity + naming checklist + reviewer comprehension test |
| Maintainable | Change-impact count + duplication + dependency-graph health + test coverage of changed files |
| Idiomatic | Formatter/linter passes; project convention checklist; experienced reviewer agreement |
| Good architecture | No dependency cycles; layer boundaries enforced; change-impact ≤ N files for representative feature |
| Robust | Reliability + error-handling + recovery + edge-case checks pass |
| Production-ready | Explicit checklist by project type |

The catalog is the menu of those operationalized replacements.

## Bottom line

The framework's job is not to enumerate qualities of good code. It's to make the **claims a team wants to make about a specific project** checkable, evidenced, and honestly scored — including when the honest answer is "we don't know yet."
