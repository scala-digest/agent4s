package scalex

import scalex.commands.*

case class CommandSpec(
    name: String,
    arguments: String,
    description: String,
    handler: Option[(List[String], CommandContext) => CmdResult],
    needsBlooms: Boolean = false,
    workspaceOnly: Boolean = false,
    defaultNoTests: Boolean = false,
    needsIndex: Boolean = true,
    reuseNameIndex: Boolean = true,
    positionalWorkspace: Boolean = true
)

val commandSpecs: List[CommandSpec] = List(
  CommandSpec("mcp", "", "Start the MCP server over standard input and output", None, needsIndex = false),
  CommandSpec(
    "scaffold",
    "<impl|test> <type>",
    "Generate implementation stubs or a test skeleton",
    Some(cmdScaffold),
    positionalWorkspace = false
  ),
  CommandSpec(
    "rename",
    "<old> <new>",
    "Preview symbol edits; use --apply to write them",
    Some(cmdRename),
    needsBlooms = true,
    positionalWorkspace = false
  ),
  CommandSpec("call-graph", "<method>", "List callers and callees of a method", Some(cmdCallGraph), needsBlooms = true),
  CommandSpec("unused", "[package]", "List candidate unused symbols", Some(cmdUnused), needsBlooms = true),
  CommandSpec(
    "bug-hunt",
    "",
    "Review static findings and optional data-flow evidence",
    Some(cmdBugHunt),
    needsBlooms = true,
    workspaceOnly = true,
    defaultNoTests = true
  ),
  CommandSpec("memory", "<subcommand>", "Manage project suppression records", Some(cmdMemory), needsIndex = false),
  CommandSpec(
    "pattern",
    "validate <rule>",
    "Validate static rules against their examples",
    Some(cmdPattern),
    needsIndex = false
  ),
  CommandSpec("search", "<query>", "Search symbols by name", Some(cmdSearch)),
  CommandSpec("def", "<symbol>", "Where is this symbol defined?", Some(cmdDef), reuseNameIndex = false),
  CommandSpec("impl", "<trait>", "Who extends this trait/class?", Some(cmdImpl)),
  CommandSpec("refs", "<symbol>", "Who uses this symbol?", Some(cmdRefs), needsBlooms = true, reuseNameIndex = false),
  CommandSpec(
    "imports",
    "<symbol>",
    "Who imports this symbol?",
    Some(cmdImports),
    needsBlooms = true,
    reuseNameIndex = false
  ),
  CommandSpec("members", "<symbol>", "What's inside this class/trait?", Some(cmdMembers)),
  CommandSpec("doc", "<symbol>", "Show scaladoc for a symbol", Some(cmdDoc)),
  CommandSpec("overview", "", "Codebase summary", Some(cmdOverview), workspaceOnly = true, defaultNoTests = true),
  CommandSpec("symbols", "<file>", "What's defined in this file?", Some(cmdSymbols)),
  CommandSpec("file", "<query>", "Search files by name", Some(cmdFile)),
  CommandSpec("annotated", "<annotation>", "Find symbols with annotation", Some(cmdAnnotated)),
  CommandSpec("grep", "<pattern>", "Regex search in file contents", Some(cmdGrep)),
  CommandSpec("packages", "", "What packages exist?", Some(cmdPackages), workspaceOnly = true),
  CommandSpec("package", "<pkg>", "Symbols in a package", Some(cmdPackage)),
  CommandSpec("index", "", "Load or refresh the index", Some(cmdIndex), workspaceOnly = true),
  CommandSpec("batch", "", "Run multiple queries at once", None, needsBlooms = true, workspaceOnly = true),
  CommandSpec("body", "<symbol>", "Extract method/val/class body", Some(cmdBody)),
  CommandSpec("hierarchy", "<symbol>", "Full inheritance tree (--depth N, default 5)", Some(cmdHierarchy)),
  CommandSpec("overrides", "<method>", "Find override implementations", Some(cmdOverrides)),
  CommandSpec("explain", "<symbol>", "Composite one-shot summary", Some(cmdExplain)),
  CommandSpec("deps", "<symbol>", "Show symbol dependencies", Some(cmdDeps)),
  CommandSpec("context", "<file:line>", "Show enclosing scopes at line", Some(cmdContext)),
  CommandSpec("diff", "<git-ref>", "Symbol-level diff vs git ref", Some(cmdDiff)),
  CommandSpec("ast-pattern", "", "Structural AST search", Some(cmdAstPattern), workspaceOnly = true),
  CommandSpec("tests", "[<pattern>]", "List test cases structurally", Some(cmdTests)),
  CommandSpec("coverage", "<symbol>", "Is this symbol tested?", Some(cmdCoverage), needsBlooms = true),
  CommandSpec("api", "<package>", "Public API surface of a package", Some(cmdApi)),
  CommandSpec("summary", "<package>", "Sub-packages with symbol counts", Some(cmdSummary)),
  CommandSpec(
    "entrypoints",
    "",
    "Find @main, def main, extends App, test suites",
    Some(cmdEntrypoints),
    workspaceOnly = true
  ),
  CommandSpec(
    "graph",
    "--render <edges> | --parse",
    "Render graphs or parse diagrams from stdin",
    Some(cmdGraph),
    needsIndex = false
  )
)

val commandsByName: Map[String, CommandSpec] = commandSpecs.map(spec => spec.name -> spec).toMap
val commandHandlers: Map[String, (List[String], CommandContext) => CmdResult] =
  commandSpecs.flatMap(spec => spec.handler.map(handler => spec.name -> handler)).toMap
