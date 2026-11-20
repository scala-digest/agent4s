# agent4s command reference

Run `./agent4s` in the agent4s checkout, or use the installed skill's `scripts/scalex-cli`. Both launch the same engine. The tables show arguments to pass to the launcher. Substitute names, packages and paths from the repository you are querying. Use `-w /path/to/project` to select its root.

## Explore a checkout

| Arguments | Result |
| --- | --- |
| `overview --concise` | File and symbol counts, top packages and frequently extended types. Use `--include-tests` to include test files. |
| `overview --architecture` | Package dependencies and hub types. Narrow the view with `--focus-package com.example` or `--path module/src/`. |
| `entrypoints --no-tests` | Application entry points such as `@main`, `def main` and `extends App`. Drop the filter to include test suites. |
| `packages` | Package names in the index. |
| `summary com.example` | Subpackages and their symbol counts. |
| `package com.example --definitions-only` | Types in the package. Use `--explain` for a short description of each type. |
| `api com.example --used-by com.web` | Symbols imported by the consumer package. Omit `--used-by` to see other importers. |
| `file UserService` | Tracked source paths matching a fuzzy filename. |
| `symbols src/UserService.scala` | Declarations in a file. Use `--summary` for counts by kind. |
| `context src/UserService.scala:42` | Enclosing scopes at the specified line. |
| `index --json` | Index statistics, including parsed and reused files. The engine caches the index in `.scalex/index.bin` using Git index object IDs. |

## Find a declaration or read its source

| Arguments | Result |
| --- | --- |
| `search Service --kind trait` | Fuzzy name matches. Use `--exact` or `--prefix` to narrow, or `--takes Type` and `--returns Type` to filter the written signature. |
| `def com.example.UserService --verbose` | Definition and declared signature. A package name disambiguates types. `def UserService.findUser` looks inside an owner. |
| `explain UserService --related` | Definition, Scaladoc, members, companion, implementations and related project types from member signatures. |
| `members UserServiceLive --inherited` | Member signatures, including parent members. Use `--brief` for names or `--offset 20 --limit 20` for another page. |
| `doc UserService` | The Scaladoc attached to the declaration. |
| `body findUser --in UserServiceLive --imports` | The method's source and the file's imports. `body TypeName` returns the type's body. A quoted test name can identify a test body. |
| `annotated deprecated` | Declarations carrying an annotation. |
| `ast-pattern --extends UserService --has-method findUser` | Types that meet all supplied conditions. Add `--body-contains TEXT` to filter by body text. |
| `grep 'findUser' --in UserServiceLive --each-method` | Regex matches scoped to a type and grouped by method. Repeat `-e PATTERN` for additional patterns. |

For a quick description, `explain --brief` includes the declaration and up to three members. `--shallow` skips the implementation and import searches. Tune the full view with `--members-limit N`, `--impl-limit N`, `--expand N`, `--inherited` and `--no-doc`. For `explain`, `members` and `overrides`, add `--body --max-lines 20` to read short bodies inline.

## Follow relationships

| Arguments | Result |
| --- | --- |
| `impl UserService` | Implementations found in parsed parent declarations. |
| `hierarchy UserService --depth 2` | Parent and child types. Use `--up` for parents or `--down` for children. |
| `overrides findUser --of UserService --body` | Matching methods in the trait's implementations, with their bodies. |
| `refs UserService` | Name matches grouped as definitions, extensions, imports, type uses, other usages and comments. |
| `imports UserService` | Matching imports, including wildcard imports that cover the symbol. |
| `deps UserService --depth 2` | Dependencies found through imports and body references. |
| `call-graph findUser --in UserServiceLive` | Candidate callees and callers. `--brief` shows only callees. |

Use `refs Name --count` for the number of matching occurrences by category. Use `refs Name --top 10` to rank files. `--flat` lists matches without grouping, `--category ImportedBy` selects a group, and `-C 2` shows surrounding lines. With `refs` and `imports`, `--strict` treats underscores and dollar signs as part of identifiers.

Text matches can include unrelated declarations with the same name. With current SemanticDB output, `call-graph --semantic` can resolve outgoing calls. Check stderr for a text fallback. Incoming calls and `refs` use name matching.

## Review and change code

| Arguments | Result |
| --- | --- |
| `diff HEAD~1` | Declarations added, removed or changed relative to the Git revision. |
| `tests` | Recognized test cases and their locations. Supply part of a test name to display matching bodies, or `--count` for totals. |
| `coverage UserService` | Test files and lines that mention the name. Use the project's coverage runner for execution coverage. |
| `unused com.example --kind class` | Classes with no detected reference outside their definition files. Check external consumers and runtime entry points before removing code. |
| `rename OldName NewName` | Proposed line edits. Names in comments or strings and unrelated declarations can match. |
| `scaffold impl UserServiceLive` | Override stubs for inherited abstract members, printed to stdout. |
| `scaffold test UserService` | A test skeleton. Select `--framework munit`, `scalatest` or `zio-test`. |
| `bug-hunt` | Static check findings with source locations. Review each match in context. |
| `memory list` | Saved suppressions for static checks. The CLI also supports `add`, `remove`, `export`, `import` and `clear`. |
| `pattern validate spec.json` | Validation results for a scanner pattern specification. |

For a rename, run the preview with the intended filters. `--semantic` uses compiler metadata when the engine resolves the symbol and reports a fallback otherwise. Confirm the target and inspect the lines. For a requested edit, run the reviewed command with `--apply` or apply individual edits where matches need disambiguation.

## Run several queries or use a client

- `batch` reads one query per line from stdin and shares the index across those queries. For example, `printf '%s\n' 'def WorkspaceIndex' 'members WorkspaceIndex --brief' | ./agent4s batch` works in the agent4s checkout.
- `graph --render 'A->B'` draws a directed graph. `graph --parse` reads a diagram from stdin. Use these when a diagram is requested. [Graph examples](graph-examples.md)
- `mcp` starts a local stdio server for MCP clients. The client lists available tools through `tools/list`. Tool names start with `scalex_`, including `scalex_def` and `scalex_overview`. Pass an absolute `workspace`, a `query` when the tool requests one, and CLI options in the `args` array.

## Common options

| Option | Use |
| --- | --- |
| `-w PATH`, `--workspace PATH` | Select the project. Use an absolute path from another working directory. |
| `--path PREFIX`, `--exclude-path PREFIX` | Limit supported queries to a project-relative path or exclude that path. |
| `--in-package PKG`, `--kind KIND`, `--no-tests` | Narrow symbol results by package, declaration kind or source set. |
| `--limit N` | Limit result lists. Use `0` for all results on commands that support it. |
| `--json` | Request structured output. |
| `--max-output N` | Cap displayed output in characters. |
| `--timings` | Print timing information to stderr. |
| `help`, `--version` | Show the CLI's syntax or the engine's reported version. |
