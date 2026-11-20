---
name: doctor
description: "Diagnose an agent4s installation or an empty Scala lookup. Check the native launcher, tracked sources, index statistics and optional project configuration."
disable-model-invocation: true
---

# Diagnose agent4s

Run these checks from the root of the project the user is querying. Keep stdout, stderr and the exit status so a failed command is visible.

```bash
bash "${CLAUDE_PLUGIN_ROOT}/skills/agent4s/scripts/scalex-cli" --version
git rev-parse --show-toplevel
bash "${CLAUDE_PLUGIN_ROOT}/skills/agent4s/scripts/scalex-cli" index --json
```

- **Launcher.** A successful exit and a version string show the engine can start. The published v0.5.0 binary prints `0.1.0`. Use the launcher's `EXPECTED_VERSION` for the release tag and `claude plugin list` for the installed plugin version. For download failures, report the failing URL or checksum error. For a platform error, use the listed release executable or run from source.
- **Source index.** Report `fileCount`, `symbolCount` and `parseFailures` from the JSON. If the user expected a missing file, check its path with `git ls-files -- path/to/File.scala`. If a cached declaration differs from the working tree, inspect the source and the Git diff. Cached symbols use staged Git object IDs.
- **Compiler metadata.** Check for `.semanticdb` files under `META-INF/semanticdb` if the user needs type-aware results. To test whether the engine can use them, choose a real type from the compiled module and preview `rename TypeName ProbeName --semantic` with the launcher. Read stderr for semantic resolution or a text fallback. Leave the preview unapplied.
- **Project notes.** Check for the paired `<!-- agent4s:start -->` and `<!-- agent4s:end -->` markers in `CLAUDE.md`. If present, compare the recorded build tool and metadata status with what you found.

Report the observed values and the specific next step for any failed lookup. Mark missing compiler output and project notes as optional for source navigation. Use `/agent4s:semanticdb` for compiler setup or `/agent4s:setup` to save project notes.
