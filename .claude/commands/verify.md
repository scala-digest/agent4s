# /project:verify

Run all quality checks. Use before committing or when unsure about code state.

## Steps

1. Compile with zero warnings:
   ```bash
   ./mill compile 2>&1 | tee /tmp/compile.log
   ```
   If warnings found — list them and fix.

2. Compile with deprecation check:
   ```bash
   ./mill compile 2>&1 | tee /tmp/deprec.log
   ```
   If deprecation warnings — find replacement API and fix.

3. Run all tests:
   ```bash
   ./mill test
   ```
   If failures — diagnose root cause, fix, re-run.

4. Check for anti-patterns in changed files:
   - Unnamed tuples: `(A, B)` without names
   - `return` statements
   - `pkg.children` instead of `pkg.stats`
   - `.collect` on Scalameta Tree
   - `.par` usage
   - `var` or mutable collections in model types
   - Functions >50 lines
   - Files >400 lines

5. Report: PASS (all green) or FAIL (list issues with file:line)
