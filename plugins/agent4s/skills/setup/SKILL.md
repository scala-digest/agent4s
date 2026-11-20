---
name: setup
description: "Save the project's build tool, Scala source layout and agent4s query instructions in a marked section of CLAUDE.md. Run again to refresh that section."
disable-model-invocation: true
---

# Save project notes for Claude

Work at the root of the user's Scala checkout. This optional setup records what you observe so future sessions can use agent4s.

1. Identify the project's build from its build files or documented compile command. Look for sbt (`build.sbt`), Mill (`build.mill`, `build.sc`), Scala CLI (`//> using` directives) or Gradle (`build.gradle`, `build.gradle.kts`). Include the Scala version if the project declares one.
2. Run the overview below from the project root. Record its file and symbol counts and name a package or type the user can look up. If the index is empty, check `git ls-files -- '*.scala' '*.java' | head -5` before writing the notes.
3. Look for compiled `.semanticdb` files under `META-INF/semanticdb`. Record whether you found any. A project can have compiler settings for SemanticDB before it has produced these files.

```bash
bash "${CLAUDE_PLUGIN_ROOT}/skills/agent4s/scripts/scalex-cli" overview --concise
```

Read `CLAUDE.md` and write a short block in the following form. Replace the bracketed text with observations.

```markdown
<!-- agent4s:start -->
## Scala navigation with agent4s

[Build tool and Scala version, if known.] [File and symbol counts, plus a package or type to explore.]

Use `/agent4s:agent4s` to find definitions, implementations and usages in tracked Scala and Java files. Start with [a query using a name from this project]. Use text search for untracked files and local variables.

[Describe whether you found SemanticDB files.] `rename --semantic` and outgoing `call-graph --semantic` queries can use compiler output. `refs` searches for name matches.
<!-- agent4s:end -->
```

Create the file if it is missing. Append the block if the file has no markers. On refresh, replace the existing block from its start marker through its end marker and preserve the surrounding text. If the markers are incomplete or duplicated, report their locations so the user can identify which section to refresh.

Report the build tool, where to start exploring, and whether you created or refreshed the notes.
