# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What is Scalex

Scalex is a Scala code intelligence CLI for coding agents. It provides fast symbol search, find definitions, and find references — without requiring an IDE, build server, or compilation. agent4s ships this engine for Claude Code, Codex and MCP clients.

## Read before any work

1. Read `docs/DEVELOPMENT.md` for the current architecture, builds and releases, then this file for fork-specific conventions.
2. `docs/ARCHITECTURE.md` — system design, query flow, component diagrams
3. `MEMORY.md` (auto-loaded) — current project state, recent decisions
4. Relevant ADRs in `docs/adr/` — reasoning behind architectural choices
5. Active spec in `docs/specs/` — if working on a planned task

## IMPORTANT: No company references

NEVER mention any company names, internal project names, proprietary codebases, or organization-specific details in any output — including commit messages, PR descriptions, changelogs, roadmaps, documentation, code comments, or conversations. Always use generic examples (e.g. `HttpMessageService`, `UserServiceLive`) instead.

## Search scope

Source code lives in `src/` (production) and `tests/` (test suite). When searching for Scala code, scope searches to these directories. Avoid searching repo-wide — `benchmark/` contains ~17.7k Scala files from the scala3 compiler clone that will pollute results.

## Workflow

- **Pipeline-first**: All non-trivial tasks go through the agentic pipeline: `/project:plan` → human approve → `/project:start-task` → implement → `/project:complete-task` → `/project:review`
- Before planning or implementing any feature, first add it to `docs/ROADMAP.md` under the appropriate section
- The roadmap is the source of truth for what's planned and what's done
- **Spec-driven development**: Write a BDD spec (`docs/specs/`) before code. Every feature needs SHALL/SHALL NOT scenarios. See `docs/specs/TEMPLATE.md`
- **Bug fix workflow**: When receiving a bug report, always write a failing test that reproduces the bug *before* writing the fix. This validates the bug is real and ensures the fix is verifiable. Only then apply the code fix and confirm the test passes.
- **Post-mortem on failure**: When a task fails or needs significant rework, create a post-mortem via `/project:postmortem`. See `docs/pipeline-log/POSTMORTEM-TEMPLATE.md`

## Architectural Invariants (NEVER violate)

| # | Invariant | Why |
|---|-----------|-----|
| 1 | Scalameta only — no presentation compiler, no build server dependency | PC requires `.class`/`.tasty` on classpath, reintroduces compilation. See ADR-001 |
| 2 | No new dependencies without explicit approval | AI adds packages without evaluating impact — 3 deps is the sweet spot |
| 3 | Named tuples only — never `(A, B)`, always `(name: A, value: B)` | Unnamed tuples are unreadable in Scala 3; named tuples are self-documenting |
| 4 | No `return` statements anywhere | Deprecated in Scala 3 lambdas, footgun everywhere. Use `boundary`/`break` |
| 5 | Zero warnings, zero deprecations in compiled code | CI blocks merge on any warning. Run `./mill compile` and verify clean |
| 6 | Every new feature benchmarked before/after on scala3 (17.7k files) | Performance budget: <5% regression on index times, 0% index size growth for non-index features |
| 7 | Must be better than grep or don't ship it | Agent can always fall back to grep — scalex must never be the worse option |
| 8 | No backwards compatibility preservation | Tool, not library. Fix inconsistencies, don't preserve them |
| 9 | Bug fix = failing test first, then code fix | Validates bug is real, ensures fix is verifiable |
| 10 | Immutable domain — no `var`, no mutable collections in model types | Purity enables safe concurrent access and predictable behavior |

## Code Generation Rules

### Size limits

| Metric | Recommended | Hard limit | Action if exceeded |
|--------|-------------|------------|-------------------|
| Lines per function | 30–40 | 50 | Extract helper methods |
| Lines per file | 200–300 | 400 | Extract to separate file |
| Function parameters | 3–4 | 5 | Use parameter object / named tuple |
| Nesting depth | 2–3 | 4 | Extract sub-functions |

### Tests (mandatory for every change)

For each public method / command:
- Happy path — основной сценарий работает
- Primary error — основная ошибка обрабатывается
- Edge case — граничное условие (empty input, not found, max size)

### After generating code (MANDATORY)

1. `./mill compile` — zero warnings
2. `./mill compile` — zero deprecations
3. `./mill test` — all tests pass
4. If fails → fix immediately, do not proceed

## Comment Styleguide

Write **WHY**, not **WHAT**. Comments are context for the next AI session that has no project history.

| Type | Write? | Contains | Example |
|------|--------|----------|---------|
| **WHY** | Yes | Reasoning, tradeoff, ADR reference | `// WHY: bloom filter false-positive rate ~1% — acceptable for candidate shortlisting` |
| **HACK** | Yes | Workaround + removal condition | `// HACK: Scalameta Pkg.children wraps in PkgBody — use pkg.stats instead. Track scalameta#NNNN` |
| **TODO** | Yes | Scope + issue link | `// TODO(perf): parallel file parsing — #42` |
| **WHAT** | No | Code paraphrase | `// Get the symbol by name` ← obvious from code, delete |

Magic numbers — always extract to named constants with WHY-comment:
```scala
// WHY: 20s timeout — refs does text search across all candidate files,
// worst case on scala3 (17.7k files) takes ~15s
val RefsTimeoutSeconds = 20
```

## Anti-Patterns (NEVER do)

| Wrong | Correct | Why |
|-------|---------|-----|
| `(List[Reference], Boolean)` | `(results: List[Reference], timedOut: Boolean)` | Named tuples are self-documenting |
| `return` inside lambda/foreach | `boundary` + `boundary.break` | `return` is deprecated in Scala 3 lambdas |
| `pkg.children` to get Pkg contents | `pkg.stats` | `.children` wraps stats in `PkgBody` wrapper |
| `tree.collect { case ... }` | Manual `traverse` + `visit` | `.collect` doesn't work on Scalameta Tree in Scala 3 |
| `list.par.map(...)` | `list.asJava.parallelStream()` | `.par` doesn't exist in Scala 3 |
| `import scalameta._` (wildcard) | `import scala.meta.{Defn, Term, ...}` | Explicit imports prevent namespace pollution |
| `com.google.common:guava` | `com.google.guava:guava` | Wrong Maven group ID — will not resolve |
| Add a new dependency | Ask first, justify need | 3 deps is the sweet spot, each new one adds maintenance burden |
| `throw new RuntimeException(...)` | Return typed error / `Option` / `boundary.break` | Exceptions bypass type system, unrecoverable in CLI |
| File >400 lines | Extract to separate file | AI loses context in long files |

## Build, release and plugin maintenance

Use the checked-in Mill launcher for source runs, tests and native builds. The shared guide describes the build commands, package structure, command registry and release steps. [Development guide](docs/DEVELOPMENT.md)

## Gotchas

- **Protected main branch**: Cannot push directly to main — all changes require a PR
- **Zero warnings policy**: Before creating a PR, run `./mill compile 2>&1 | grep -i warn` and verify no output. Do not ship compiler warnings.
- **Zero deprecations policy**: Also verify with `./mill compile 2>&1 | grep -i warn`. Do not use deprecated APIs — find and use the replacement immediately.

- **Guava group ID**: `com.google.guava:guava`, NOT `com.google.common:guava`
- **GraalVM native image**: Guava needs `--initialize-at-run-time=com.google.common.hash.Striped64,com.google.common.hash.LongAdder,com.google.common.hash.BloomFilter,com.google.common.hash.BloomFilterStrategies` (see `build.mill`)
- **No `.par` in Scala 3**: Use `list.asJava.parallelStream()` instead of `list.par`
- **No non-local `return` in Scala 3**: `return` inside lambdas/closures is deprecated. Use `scala.util.boundary` + `boundary.break` instead. This includes `return` inside `.foreach`, `.map`, `try`/`catch` blocks inside lambdas, etc.
- **Scalameta `Pkg.children` wraps stats in `PkgBody`**: Use `pkg.stats` to access direct children (Import, Defn.Class, etc.), not `pkg.children` which nests them inside a `PkgBody` wrapper node.
- **Scalameta Tree**: `.collect` doesn't work on Tree in Scala 3 — use manual `traverse` + `visit` pattern
- **Anonymous givens**: Only named givens are indexed; anonymous givens are skipped
- **`refs`/`imports` use text search**: They use bloom filters to shortlist candidate files, then do word-boundary text matching. They are NOT index-based and have a 20-second timeout.
- **Scala 3 indentation in `WorkspaceIndex`**: Deeply nested code can break method boundaries — use brace syntax for nested blocks
- **Test fixture file counts**: Tests hardcode file counts — adding/removing fixtures requires updating all count assertions
- **GitHub Actions SHA pinning**: All actions in `release.yml` are pinned to commit SHAs. To verify/update: `git ls-remote --refs https://github.com/<owner>/<repo>.git refs/tags/<tag>`. Never trust SHAs from memory or LLM output without verifying.

## Pipeline Protocol

Every task follows the agentic pipeline. Use slash commands for each phase.

### Phase 1: Plan (`/project:plan`)
- Read CLAUDE.md, ARCHITECTURE.md, relevant ADRs
- Understand WHAT and WHY before writing code
- Create BDD spec in `docs/specs/[task-name].md` with SHALL / SHALL NOT
- **Wait for human approval** — do NOT start implementation without it

### Phase 2: Implement (`/project:start-task [name]`)
- Work in isolated worktree (one module per agent, no conflicts)
- BDD flow: tests first (RED) → minimal code (GREEN) → mutation check → refactor
- WHY-comments on non-obvious decisions
- One task per worktree — don't mix unrelated changes
- SHALL NOT from spec = hard PROHIBITIONS, not suggestions

### Phase 3: Verify (`/project:verify`)
- Compile: `./mill compile` — zero warnings
- Deprecations: `./mill compile` — zero warnings
- Tests: `./mill test` — all green
- If fails → fix immediately (up to 3 iterations). If still fails → task is poorly scoped

### Phase 4: Complete (`/project:complete-task [name]`)
- Full verification pass
- Check all acceptance criteria from spec
- Feature checklist (if command/flag changed)
- Commit, push, create PR

### Phase 5: Review (`/project:review`)
- Architectural review against spec and invariants
- APPROVE / REQUEST_CHANGES / REJECT
- Every issue references a specific invariant or rule

### On Failure (`/project:postmortem [name]`)
- Create structured post-mortem in `docs/pipeline-log/`
- Classify failure taxonomy
- Document action items to prevent recurrence

### Parallelization Rules
- One module — one agent (no two agents editing the same module)
- Dependency order: if task B depends on task A, finish A first
- Max 3-5 parallel worktrees
- Each worktree is expendable: if stuck, delete and restart with better spec

### Agents (`.claude/agents/`)
- **spec-writer** — creates specs, does NOT write code
- **implementer** — implements from spec in isolated worktree, does NOT change scope
- **reviewer** — evaluates against invariants/ADRs, does NOT write code
- **test-writer** — writes MUnit tests from BDD scenarios, does NOT write production code

### Scoped Rules (`.claude/rules/`)
- `scala.md` — Scala-specific patterns, anti-patterns, size limits
- `tests.md` — MUnit conventions, BDD mapping, fixture rules
