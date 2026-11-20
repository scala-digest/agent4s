---
name: semanticdb
description: "Configure compiler-generated SemanticDB data for symbol renames and outgoing call-graph queries in a Scala project."
disable-model-invocation: true
---

# Enable SemanticDB

The Scala compiler can record which symbol an occurrence refers to. Agent4s can use that data for `rename --semantic` and for outgoing calls in `call-graph --semantic`. Ordinary `refs` queries match names in source text.

## Check the project

From the project root, look for existing compiler output.

```sh
find . -type f -path '*/META-INF/semanticdb/*.semanticdb' -print
```

If output exists for the module the user is working on, try the verification below before editing its build. If the output is stale, recompile that module. Otherwise identify the project's Scala version and its build tool from the build files. Cross-built projects may need different settings for each Scala version.

## Configure the compiler

Show the user the proposed build-file edit and get their confirmation before applying it. Preserve existing compiler flags and use the project's own build command or wrapper.

| Build | Setting to use |
| --- | --- |
| sbt, Scala 3 | Add `scalacOptions += "-Xsemanticdb"` in the target project's settings. Use `ThisBuild` if all affected projects use Scala 3. |
| sbt, Scala 2 | Enable `semanticdbEnabled := true` in an sbt build that supports the setting. |
| Scala CLI, Scala 3 | Add `//> using options -Xsemanticdb` alongside the project's existing directives. |
| Mill or Gradle, Scala 3 | Append `-Xsemanticdb` to the target module's Scala compiler options. Follow the syntax of the existing build. |
| Other Scala 2 builds | Use a SemanticDB compiler plugin compatible with the project's exact Scala compiler version. [Scala 2 SemanticDB guide](https://scalameta.org/docs/semanticdb/guide.html) |

Compile the affected module. Typical commands include `sbt compile`, `./mill <module>.compile`, `scala-cli compile .` and `./gradlew compileScala`. Inspect the compile result and the generated files.

## Verify a real lookup

Pick a declared class or trait in the compiled module. Substitute its simple name for `UserService` in this preview and read the engine's diagnostic on stderr.

```bash
bash "${CLAUDE_PLUGIN_ROOT}/skills/agent4s/scripts/scalex-cli" rename UserService ProbeName --semantic
```

A semantic rename message confirms the engine resolved this symbol. If it falls back to text, report the fallback and check the workspace root, the compiled module and the output directory. The engine searches `target`, `out`, `.bloop` and Scala CLI's `.scala-build` directories. A build can emit metadata elsewhere, including Gradle's `build` directory.

Report the build file, compile command, and result of the preview. If `CLAUDE.md` contains the `agent4s:start` and `agent4s:end` markers, refresh only the compiler metadata sentence within that block.
