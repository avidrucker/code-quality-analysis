# Concern Catalog

The menu of checks the framework draws from. Read [philosophy.md](./philosophy.md) first — it explains why the catalog is split into first-order, second-order, and cross-cutting sections.

This is a **catalog, not a spec.** A real project config picks a small subset (v1: ~15 checks across 6 concerns) and adds severities. Don't try to enable everything at once.

Per concern, each entry follows the same shape:

- **Core question** — what the concern is actually asking
- **Measurable checks** — concrete things you can test, mechanically or with operationalized review
- **Example binary claim** — a yes/no proposition the project can stand behind
- **Example quantified claim** — a numerical threshold the project can stand behind

## Taxonomy

| Level | Concerns |
|---|---|
| First-order | [Correctness](#correctness), [Performance](#performance), [Reliability](#reliability), [Security](#security), [Data integrity](#data-integrity), [Scalability](#scalability), [Usability](#usability), [Accessibility](#accessibility), [Compatibility](#compatibility), [Operability](#operability), [Recoverability](#recoverability), [Cost efficiency](#cost-efficiency), [Delivery safety](#delivery-safety) |
| Second-order | [Readability](#readability), [Maintainability](#maintainability), [Testability](#testability), [Debuggability](#debuggability), [Simplicity](#simplicity), [Composability](#composability), [Idiomaticity](#idiomaticity), [Conciseness](#conciseness) |
| Cross-cutting | [Logging](#logging), [Configuration](#configuration), [Dependencies](#dependencies), [Documentation](#documentation), [Error handling](#error-handling), [Time](#time) |

---

# First-order concerns

These are externally meaningful outcomes. A system can fail catastrophically on any of them even when the code looks clean.

## Correctness

**Core question:** Does the system produce the right results?

**Measurable checks:**
- All unit tests pass
- All integration tests pass
- All property-based tests pass for N generated cases
- For known fixtures, output exactly matches expected output
- Every public function: invalid inputs return a documented error or throw a documented exception
- Every critical user flow has an end-to-end test
- Every fixed bug has a regression test
- Mutation score is at least X%
- Requirements coverage is at least X%
- No `TODO`/`FIXME` appears in production-critical files
- No skipped tests in critical modules
- No `only`/`skip`/`xit`/`pending` in CI

**Binary:** Every critical domain invariant has at least one automated test that fails if the invariant is violated.

**Quantified:** Mutation testing kills at least 85% of mutants in critical business logic.

## Performance

**Core question:** Does the system respond fast enough under expected conditions?

**Measurable checks:**
- App starts in under X seconds on target hardware
- First meaningful paint occurs under X ms
- API p95 / p99 latency stays under X ms
- CLI command completes under X seconds on a representative input
- Bundle size under X KB
- Memory usage remains under X MB
- No function exceeds an agreed complexity threshold for target inputs
- No benchmark regresses by more than X% from baseline
- Database query count for flow X is ≤ N
- No N+1 query detected in flow X

**Binary:** The app loads in under 1 second on a 2021 Moto G phone over wifi.

**Quantified:** API p95 latency stays under 300 ms at 100 requests/sec sustained.

## Reliability

**Core question:** Does the system keep working under normal failure conditions?

**Measurable checks:**
- Uptime over last N days ≥ X%
- Error rate below X%
- Failed jobs are retried with bounded retry policy
- All external calls have timeouts
- All external calls have documented failure behavior
- All scheduled jobs are idempotent
- No unhandled promise rejections in test suite
- No uncaught exceptions during E2E flows
- System survives dependency outage simulation
- System recovers after restart without data loss

**Binary:** Every network request has a timeout.

**Quantified:** During a simulated 5-minute database outage, the app returns controlled errors for 100% of affected requests and recovers without manual intervention.

## Security

**Core question:** Does the system prevent unauthorized access, data exposure, and common attacks?

**Measurable checks:**
- Dependency vulnerability scan has zero critical issues
- Secrets scan finds zero committed secrets
- Static analysis finds zero high-severity security issues
- Every privileged route has an authorization check
- Every mutation/write endpoint validates authorization
- User input rendered as HTML is sanitized or avoided
- Passwords/tokens are never logged
- Authenticated endpoints reject unauthenticated requests
- User A cannot access User B's private data in tests
- CSRF protection exists where needed
- Rate limiting exists for abuse-prone endpoints

**Binary:** Every server-side mutation has an authorization test.

**Quantified:** OWASP ZAP scan reports zero high-risk findings.

## Data integrity

**Core question:** Does the system preserve valid, consistent, durable data?

**Measurable checks:**
- All required fields have schema validation
- All uniqueness rules are enforced at the database level
- All critical writes occur in transactions
- Migrations are reversible or have a documented rollback plan
- Migration test passes against production-like data
- Foreign key / reference constraints exist where appropriate
- No orphaned records exist after delete operations
- Cascade behavior is tested
- Concurrent write tests preserve invariants
- Backfill scripts are idempotent

**Binary:** Every domain uniqueness rule is enforced by the database, not only by application code.

**Quantified:** Running the integrity audit over production data returns zero orphaned records.

## Scalability

**Core question:** Does the system continue to work as data, users, or traffic grow?

**Measurable checks:**
- Critical list views are paginated
- No endpoint returns unbounded collections
- Query count does not grow linearly with result size unless intended
- Load test supports N concurrent users
- API p95 latency stays under X ms at Y requests/sec
- Database indexes exist for all production query patterns
- Background jobs process N items/minute
- Memory usage grows no faster than O(1)/O(log n)/O(n) per expectation
- Batch process handles N records within X minutes
- Cache hit rate exceeds X% for hot paths

**Binary:** No production API endpoint returns an unbounded list.

**Quantified:** Search endpoint handles 100 concurrent users with p95 latency under 300 ms.

## Usability

**Core question:** Can intended users complete intended tasks successfully and comfortably?

**Measurable checks:**
- X out of Y users complete the task without help
- Median task completion time under X seconds
- User error rate below X%
- Form validation messages identify the field and the fix
- Every async action has loading, success, and failure states
- Every destructive action has confirmation or undo
- Empty states exist for all major list views
- Navigation depth to common action ≤ N clicks
- Layout shift below threshold
- Search returns useful results for known queries

**Binary:** Every form field with validation has a visible error message near the field.

**Quantified:** At least 80% of target users can complete onboarding without assistance.

## Accessibility

**Core question:** Can people with different abilities use the system?

**Measurable checks:**
- Automated axe scan has zero serious/critical violations
- All interactive controls are keyboard reachable
- Focus order matches visual order
- Visible focus indicator exists
- Images have appropriate alt text or are marked decorative
- Form fields have labels
- Color contrast meets WCAG AA
- Modals trap and restore focus
- Screen reader smoke test passes for critical flows
- Page can be zoomed to 200% without loss of function

**Binary:** Every button has an accessible name.

**Quantified:** Automated accessibility scan reports zero critical violations across all critical pages.

## Compatibility

**Core question:** Does the system work in the environments it claims to support?

**Measurable checks:**
- Test suite passes on all supported OSes
- Browser test suite passes on supported browsers
- App works at supported screen sizes
- Code avoids unsupported APIs for target browsers
- CLI works on supported shells
- Timezone tests pass for supported regions
- Locale tests pass for supported languages
- Database version compatibility tests pass
- Docker image builds reproducibly
- Fresh install works from documented setup steps

**Binary:** CI passes on Linux, macOS, and Windows.

**Quantified:** E2E smoke tests pass on Chrome, Firefox, and Safari.

## Operability

**Core question:** Can the team understand and run the system in production?

**Measurable checks:**
- Every service has a health-check endpoint
- Every request has a correlation ID
- Errors are logged with context
- Logs do not contain secrets
- Metrics exist for request rate, error rate, and latency
- Critical background jobs emit success/failure metrics
- Alerts exist for critical failure modes
- Dashboard exists for production health
- Runbook exists for common incidents
- Deployments are traceable to commit SHA

**Binary:** Every production error log includes request ID, user/session ID where appropriate, operation name, and exception cause.

**Quantified:** 95% of production incidents in the last quarter had enough logs/metrics to identify root cause without redeploying debug code.

## Recoverability

**Core question:** Can the team recover from failure, bad deploys, or data loss?

**Measurable checks:**
- Backups run successfully
- Backup restore test passes
- Rollback procedure exists
- Rollback has been tested in staging
- Database migration rollback is documented
- Disaster recovery time is under X
- Data recovery point objective is under X
- Failed jobs can be replayed safely
- Manual repair scripts are tested

**Binary:** A fresh environment can be restored from backup successfully.

**Quantified:** Recovery from a failed deployment takes less than 10 minutes.

## Cost efficiency

**Core question:** Does the system achieve its goals without unreasonable resource or money cost?

**Measurable checks:**
- Monthly infrastructure cost under X
- Cost per active user under X
- Cost per request under X
- Build minutes under X/month
- Storage growth under X GB/month
- Logging volume under X GB/day
- Cloud resources tagged by owner/project
- Idle resources detected and removed

**Binary:** Every cloud resource has an owner and project tag.

**Quantified:** Cost per 1,000 requests remains under $X.

## Delivery safety

**Core question:** Can changes ship without breaking important things?

**Measurable checks:**
- CI must pass before merge
- Code review required before merge
- Critical tests run on every PR
- Deployment is automated
- Deployment can be rolled back
- Feature flags protect risky changes
- Staging smoke test passes before production
- Regression escape rate below X%
- Mean time to detect regression below X
- Mean time to rollback below X
- No direct commits to main
- Changelog generated for releases

**Binary:** No direct commits to `main` — every change ships via PR with CI.

**Quantified:** Regression tests catch known seeded breaking changes 99% of the time before they ship to prod.

### Node version pinning (delivery-safety sub-topic)

For Node/JS projects, pinning the Node version is a delivery-safety check worth calling out explicitly. The cost it lowers is "works on my machine" — silent breakage from version skew between dev laptop, CI runner, and production.

Two mechanisms are commonly used together:

- **`.nvmrc`** (one-line text file at repo root, e.g. `24.16.0`) — read by `nvm` when a developer runs `nvm use`. Switches the local shell to the named version. Audience: developers in their terminal. It's a *hint*, not enforcement.

- **`engines.node` in `package.json`** (e.g. `"engines": {"node": ">=18.0.0"}`) — read by npm/yarn at install time and by deployment platforms (Vercel, Heroku, Render, Cloud Run) when choosing a runtime. Produces a warning by default; fails install when `engine-strict=true` is set. Audience: npm tooling, CI, PaaS.

**When does it matter?**
- Multiple developers, or any CI runner — version skew breaks builds intermittently.
- Code that uses features which landed in a specific Node major (`fetch`, `Array.findLast`, top-level await, etc.).
- Native-addon dependencies (`better-sqlite3`, `bcrypt`, `node-canvas`) — version-specific prebuilt binaries that fail to load or fall back to slow source builds on mismatch.

**When is it overkill?**
- Solo learning projects with no CI and no deploy target — the empirical "whatever Node version I have works" is fine until that's no longer true.

**Recommended values:** put the exact known-working version in `.nvmrc`; put a permissive floor (latest active LTS major or older) in `engines.node`. Different values for the two are not a contradiction — they serve different audiences.

---

# Second-order code qualities

These are valuable because they reduce the cost of correctness, change, debugging, testing, and operation — not because they're valuable in themselves. Harder to measure directly, but operationalizable.

## Readability

**Core question:** Can a reader understand this code quickly enough to change it correctly?

**Measurable checks:**
- Function length under N lines
- File length under N lines
- Cyclomatic complexity under N
- Cognitive complexity under N
- Nesting depth under N
- Public functions have docstrings/comments where required
- Names avoid banned vague terms: `data`, `info`, `thing`, `stuff`, `handle`, `process`
- No unexplained abbreviations
- No boolean parameters in public APIs unless wrapped in named options

**Binary:** No function in `src/core/` exceeds cognitive complexity 10.

**Quantified:** Given 5 engineers unfamiliar with the code, at least 4 can correctly answer 3 comprehension questions after 5 minutes.

## Maintainability

**Core question:** Will future changes stay cheap and safe as the code ages?

**Measurable checks:**
- Change impact count: a representative change touches ≤ N files
- Duplicate code below X%
- No circular dependencies
- Public API surface under N exported symbols
- Module dependency graph has no forbidden edges
- Code ownership is clear
- Top-churn files have test coverage above Y%
- No file is in both top-10 churn AND top-10 complexity
- Adding a new variant requires changing ≤ N places
- Configuration values are defined in one place

**Binary:** Adding a new payment provider requires implementing one interface and registering it in one place.

**Quantified:** Top 10 most-changed files all have cognitive complexity below 10 and test coverage above 80%.

## Testability

**Core question:** Can we exercise the code under test conditions cheaply enough to do it often?

**Measurable checks:**
- Pure/domain logic is testable without database/network/browser
- Unit tests run under X seconds
- Integration tests run under X minutes
- Tests are deterministic across N repeated runs
- No test depends on execution order
- Time/randomness/network are injectable or controlled
- Critical side effects are behind interfaces/adapters
- Test flake rate below X%
- Test setup requires ≤ N commands
- Coverage for critical modules above X%

**Binary:** Business logic tests can run without starting the app server.

**Quantified:** Test suite passes 100 consecutive times with zero flakes.

## Debuggability

**Core question:** When something goes wrong, how fast can someone find the cause?

**Measurable checks:**
- Errors include the operation name
- Errors include relevant entity IDs
- Errors preserve original cause
- Logs include request/correlation ID
- Stack traces are not swallowed
- No empty catch blocks
- No leftover debug logging in production code
- Failed validation returns field-level details
- Debug mode can be enabled safely

**Binary:** No catch block silently swallows errors.

**Quantified:** 4 out of 5 engineers can locate the source of a seeded failure within 10 minutes using logs/errors only.

## Simplicity

**Core question:** Does the code do only what it needs to, with the fewest moving parts?

**Measurable checks:**
- Cyclomatic complexity under N
- Cognitive complexity under N
- State count under N
- Number of feature flags affecting one path under N
- Number of configuration options under N
- Number of layers crossed in a common flow under N
- No unnecessary abstraction with one implementation
- No inheritance depth above N
- No function has more than N parameters
- No public API requires caller to know internal ordering constraints

**Binary:** A common flow can be explained as a sequence of fewer than 7 conceptual steps.

**Quantified:** Cognitive complexity of every function in critical modules is ≤ 10.

## Composability

**Core question:** Can pieces of this code be recombined to do new work without modification?

**Measurable checks:**
- Functions accept explicit inputs and return explicit outputs
- Pure functions are separated from side effects
- Public functions avoid hidden global state
- Components can be rendered in isolation
- Modules expose stable interfaces
- Adapters isolate external systems
- Function output type matches the next pipeline step's input type
- No module imports from forbidden internal paths

**Binary:** Domain functions do not directly call network, filesystem, database, or DOM APIs.

**Quantified:** 80% of domain functions are pure by static or review-based classification.

## Idiomaticity

**Core question:** Does the code match what an experienced practitioner in this stack would expect to read?

**Measurable checks:**
- Formatter passes
- Linter passes
- Static analyzer passes
- Project conventions checklist passes
- Naming conventions pass
- No banned patterns appear
- Language-specific idiom checks pass
- 4 of 5 experienced reviewers agree the code follows project conventions

**Binary:** Code passes formatter, linter, and project convention checks.

**Quantified:** 4 of 5 project maintainers rate the code as convention-following.

## Conciseness

**Core question:** Is the code as small as it can be without being cryptic?

Conciseness is dangerous unreframed — it can mean "terse to the point of obscurity." The useful operational frame is **surface area**: less code, fewer concepts, less to maintain — but never at the cost of comprehension.

**Measurable checks:**
- Lines of code below baseline for equivalent behavior
- Public API surface under N
- Number of concepts introduced under N
- Boilerplate ratio under X%
- Duplicate code below X%
- No dead code
- No unused exports
- No unused dependencies
- No unreachable branches

**Binary:** No unused exported functions exist.

**Quantified:** New implementation reduces code size by 30% without reducing test coverage, correctness, or readability score.

---

# Cross-cutting mechanisms

These mechanisms affect many concerns at once. They're worth tracking separately because a single change to (say) logging can simultaneously raise debuggability, observability, and security audit — or hurt performance and privacy.

## Logging

**Supports:** Debuggability, Operability, Security auditing, Reliability.
**Can hurt:** Performance, Privacy, Cost, Readability.

**Measurable checks:**
- Logs include correlation IDs
- Logs do not include secrets or PII
- Error logs include the cause chain
- Log volume below X GB/day
- Log levels used correctly (no DEBUG in prod hot paths)

## Configuration

**Supports:** Portability, Deployability, Security, Reliability.
**Can hurt:** Simplicity, Debuggability, Correctness.

**Measurable checks:**
- All required config validated at startup
- Missing config produces a clear error (not a runtime NPE)
- Secrets are not stored in repo
- Config schema exists
- Config defaults are documented

## Dependencies

**Supports:** Delivery speed, Conciseness, Compatibility.
**Can hurt:** Security, Build stability, Bundle size, Maintainability, Portability.

**Measurable checks:**
- No critical CVEs
- No abandoned packages in critical path
- Bundle impact under X KB
- License approved
- Dependency count under threshold
- Lockfile present and current

## Documentation

**Supports:** Maintainability, Operability, Onboarding, Correctness, Delivery safety.
**Can hurt:** Almost nothing — but stale docs are worse than missing docs.

**Measurable checks:**
- README setup instructions work from a clean machine
- Public APIs have examples
- Runbook exists for critical alerts
- Architecture decision records exist for major tradeoffs
- Docs pass link checker
- 4 of 5 new contributors can complete setup using docs only

## Error handling

**Supports:** Reliability, Debuggability, UX, Security, Correctness.

**Measurable checks:**
- No empty catch blocks
- All async calls handle failure
- User-facing errors are actionable
- Internal errors preserve cause
- Sensitive errors are not exposed to users

## Time

Time is a sneaky cross-cutting concern that touches Correctness, Testability, Reliability, Data integrity, and UX.

**Measurable checks:**
- Timezone tests pass
- Date parsing is explicit (no locale-dependent ambiguity)
- System clock access is injectable in tests
- Expiration behavior is tested
- Daylight saving transition is tested where relevant

---

# Using the catalog

A project config selects checks from this menu and assigns severities. Start small. The framework's v1 lccjs config selects 6 checks across 6 concerns; that's enough to validate the wiring and stay honest. Add more only when each new check is wired to a measurable cost that would go down.

See [`schema/SCHEMA.edn`](../schema/SCHEMA.edn) for the config shape and [`examples/lccjs.edn`](../examples/lccjs.edn) for a real example.
