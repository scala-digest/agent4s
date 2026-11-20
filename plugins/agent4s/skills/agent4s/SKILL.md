---
name: agent4s
description: "Look up definitions, references and implementations in Git-tracked Scala and Java source. Use for navigating a codebase, tracing a method, finding related tests, or previewing a symbol rename."
---

# Navigate Scala and Java source

Run the bundled launcher from the user's repository. Claude Code expands the skill directory in the command below. If the host supplies the skill as a file, resolve `scripts/scalex-cli` beside this file. Pass the project root with `-w` when the shell is in another directory.

```bash
bash "${CLAUDE_SKILL_DIR}/scripts/scalex-cli" overview --concise
```

The launcher downloads the pinned engine on first use. Commands index Git-tracked `.scala` and `.java` files, so you can answer source questions before building the project.

## Pick the query that answers the question

| You need | Pass these arguments to the launcher |
| --- | --- |
| An entry point or an unfamiliar type | `entrypoints --no-tests` or `explain TypeName --related` |
| A declaration or trait implementation | `def package.TypeName --verbose` or `impl TraitName` |
| A method body or its calls | `body method --in Owner --imports` or `call-graph method --in Owner` |
| Usages or mentions in tests | `refs Name` or `coverage Name` |
| A rename preview or implementation stubs | `rename OldName NewName` or `scaffold impl ClassName` |

For a direct lookup, report the declaration with its file and line. Read the body when explaining behavior. If several declarations share a name, narrow `def` by package or use `Owner.member`. If a lookup misses, check the tracked files and use text search for locals, untracked files or other languages. The cache uses staged Git object IDs, so check the working tree if the user just edited a declaration.

Treat `refs` and incoming calls as name matches. For compiled projects, `--semantic` can resolve a rename target or outgoing calls using SemanticDB. The engine reports a text fallback on stderr. Inspect the proposed edits before using `rename --apply` for a requested change. Scaffolding prints code to stdout.

Use `--path`, `--no-tests` or `--limit` to narrow a broad result. For independent lookups, pipe one query per line to `batch`.

```bash
printf '%s\n' 'def UserService' 'impl UserService' 'refs UserService --count' |
  bash "${CLAUDE_SKILL_DIR}/scripts/scalex-cli" batch
```

Look up syntax and filters for an individual command in the [command reference](references/commands.md). For a change that needs several queries, use the [workflow examples](references/workflows.md). When the user asks for a diagram, see the [graph examples](references/graph-examples.md).
