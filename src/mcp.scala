package scalex

import clibase.Timings
import scalex.index.*
import scalex.output.*

import java.io.{BufferedReader, ByteArrayOutputStream, InputStreamReader, PrintStream}
import java.io.IOException

// ── Minimal JSON (just enough for MCP JSON-RPC) ────────────────────────────

enum McpJson {
  case Str(value: String)
  case Num(value: Double)
  case Bool(value: Boolean)
  case Null
  case Arr(values: List[McpJson])
  case Obj(fields: List[(key: String, value: McpJson)])

  def apply(k: String): McpJson = this match {
    case Obj(fields) => fields.find(_.key == k).map(_.value).getOrElse(McpJson.Null)
    case _           => McpJson.Null
  }

  def asStr: Option[String] = this match {
    case Str(v) => Some(v)
    case _      => None
  }

  def asNum: Option[Double] = this match {
    case Num(v) => Some(v)
    case _      => None
  }

  def asArr: Option[List[McpJson]] = this match {
    case Arr(vs) => Some(vs)
    case _       => None
  }

  def asBool: Option[Boolean] = this match {
    case Bool(v) => Some(v)
    case _       => None
  }
}

object McpJson {
  def parse(input: String): McpJson = {
    val r = Reader(input)
    parseValue(r)
  }

  private class Reader(val s: String, var pos: Int = 0) {
    def peek: Char = if pos < s.length then s.charAt(pos) else '\u0000'
    def next(): Char = { val c = peek; pos += 1; c }
    def skip(): Unit = while pos < s.length && s.charAt(pos).isWhitespace do pos += 1
  }

  private def parseValue(r: Reader): McpJson = {
    r.skip()
    r.peek match {
      case '"' => parseStr(r)
      case '{' => parseObj(r)
      case '[' => parseArr(r)
      case 't' => r.pos += 4; Bool(true)
      case 'f' => r.pos += 5; Bool(false)
      case 'n' => r.pos += 4; Null
      case _   => parseNum(r)
    }
  }

  private def parseStr(r: Reader): Str = {
    r.next() // skip opening "
    val sb = StringBuilder()
    while r.peek != '"' && r.peek != '\u0000' do
      if r.peek == '\\' then {
        r.next()
        r.next() match {
          case '"'  => sb.append('"')
          case '\\' => sb.append('\\')
          case '/'  => sb.append('/')
          case 'n'  => sb.append('\n')
          case 'r'  => sb.append('\r')
          case 't'  => sb.append('\t')
          case 'u'  =>
            val hex = new String(Array(r.next(), r.next(), r.next(), r.next()))
            sb.append(Integer.parseInt(hex, 16).toChar)
          case c => sb.append(c)
        }
      } else sb.append(r.next())
    r.next() // skip closing "
    Str(sb.toString)
  }

  private def parseNum(r: Reader): Num = {
    val start = r.pos
    if r.peek == '-' then r.next()
    while r.peek.isDigit do r.next()
    if r.peek == '.' then { r.next(); while r.peek.isDigit do r.next() }
    if r.peek == 'e' || r.peek == 'E' then {
      r.next()
      if r.peek == '+' || r.peek == '-' then r.next()
      while r.peek.isDigit do r.next()
    }
    Num(r.s.substring(start, r.pos).toDouble)
  }

  private def parseObj(r: Reader): Obj = {
    r.next() // skip {
    r.skip()
    if r.peek == '}' then {
      r.next()
      Obj(Nil)
    } else {
      val fields = scala.collection.mutable.ListBuffer[(key: String, value: McpJson)]()
      var more = true
      while more do {
        r.skip()
        val k = parseStr(r).value
        r.skip(); r.next() // skip :
        fields += ((key = k, value = parseValue(r)))
        r.skip()
        if r.peek == ',' then r.next() else more = false
      }
      r.skip(); r.next() // skip }
      Obj(fields.toList)
    }
  }

  private def parseArr(r: Reader): Arr = {
    r.next() // skip [
    r.skip()
    if r.peek == ']' then {
      r.next()
      Arr(Nil)
    } else {
      val values = scala.collection.mutable.ListBuffer[McpJson]()
      var more = true
      while more do {
        values += parseValue(r)
        r.skip()
        if r.peek == ',' then r.next() else more = false
      }
      r.skip(); r.next() // skip ]
      Arr(values.toList)
    }
  }
}

// ── MCP Server ────────────────────────────────────────────────────────────

object McpServer {
  private case class ToolDef(name: String, desc: String, queryDesc: Option[String])

  private val toolDefs: List[ToolDef] = List(
    ToolDef(
      "search",
      "Search symbols by name (fuzzy camelCase matching). Returns matching classes, traits, objects, methods, types.",
      Some("Symbol name to search for")
    ),
    ToolDef("def", "Find where a symbol is defined — returns file path and line number.", Some("Symbol name")),
    ToolDef("impl", "Find all implementations/extensions of a trait or class.", Some("Trait or class name")),
    ToolDef(
      "refs",
      "Find all references to a symbol, categorized by type (definition, extension, import, type usage, call site).",
      Some("Symbol name")
    ),
    ToolDef("imports", "Find all files that import a symbol.", Some("Symbol name")),
    ToolDef(
      "members",
      "List members of a class, trait, or object. Supports --inherited, --body, --brief.",
      Some("Class, trait, or object name")
    ),
    ToolDef("doc", "Show Scaladoc documentation for a symbol.", Some("Symbol name")),
    ToolDef("symbols", "List all symbols defined in a file.", Some("File path relative to workspace")),
    ToolDef("file", "Search files by name (fuzzy camelCase matching).", Some("File name or pattern")),
    ToolDef("annotated", "Find symbols with a specific annotation (e.g. main, deprecated).", Some("Annotation name")),
    ToolDef(
      "grep",
      "Regex search in file contents. Supports --in <Type>, --each-method, --count.",
      Some("Regex pattern")
    ),
    ToolDef(
      "package",
      "List symbols in a package. Use --explain for brief explanation of each type.",
      Some("Package name")
    ),
    ToolDef(
      "body",
      "Extract source code of a method, val, or class. Supports -C N context lines, --imports.",
      Some("Symbol name")
    ),
    ToolDef(
      "hierarchy",
      "Show full inheritance tree — parents and children. Use --up/--down/--depth N.",
      Some("Class or trait name")
    ),
    ToolDef("overrides", "Find override implementations of a method across the codebase.", Some("Method name")),
    ToolDef(
      "explain",
      "Composite one-shot summary: definition + scaladoc + members + implementations + imports.",
      Some("Symbol name")
    ),
    ToolDef("deps", "Show symbol dependencies — what this symbol depends on.", Some("Symbol name")),
    ToolDef(
      "context",
      "Show enclosing scopes at a specific file location.",
      Some("file:line (e.g. src/Main.scala:42)")
    ),
    ToolDef(
      "diff",
      "Symbol-level diff: what symbols were added/removed/changed vs a git ref.",
      Some("Git ref (branch, tag, or commit)")
    ),
    ToolDef("coverage", "Check if a symbol is tested — finds test files that reference it.", Some("Symbol name")),
    ToolDef("api", "Show public API surface of a package — symbols imported by other packages.", Some("Package name")),
    ToolDef("summary", "Show sub-packages with symbol counts.", Some("Package name")),
    ToolDef(
      "overview",
      "Codebase summary: packages, hub types, dependency graph. Use --architecture, --concise.",
      None
    ),
    ToolDef("packages", "List all packages in the workspace.", None),
    ToolDef("index", "Refresh the workspace index and report parsing statistics.", None),
    ToolDef("ast-pattern", "Structural AST search. Use --has-method NAME, --extends TRAIT, --body-contains PAT.", None),
    ToolDef("tests", "List test cases structurally (MUnit, ScalaTest, specs2). Use --count for summary.", None),
    ToolDef("entrypoints", "Find @main, def main, extends App, and test suites.", None),
    ToolDef(
      "scaffold",
      "Generate implementation stubs or a test skeleton. Pass the type name in args.",
      Some("impl or test")
    ),
    ToolDef(
      "rename",
      "Preview a symbol rename. Pass the new name in args and add --apply to write the edits.",
      Some("Current symbol name")
    ),
    ToolDef("call-graph", "List callers and callees of a method.", Some("Method name")),
    ToolDef("unused", "Find candidate unused symbols in the workspace or a package.", None),
    ToolDef("bug-hunt", "Review static findings in the workspace.", None),
    ToolDef(
      "memory",
      "Read or update project suppression records. Pass subcommand operands in args.",
      Some("Subcommand")
    ),
    ToolDef("pattern", "Validate a static rule against its examples. Pass the rule path in args.", Some("validate")),
    ToolDef("graph", "Render a directed graph as ASCII/Unicode art.", Some("Graph expression (e.g. 'A->B, B->C')"))
  )

  def run(): Unit = {
    val reader = BufferedReader(InputStreamReader(System.in))
    // Each tool response captures its own output. Reserve this stream for protocol frames.
    val jsonOut = System.out
    val nullStream = PrintStream(java.io.OutputStream.nullOutputStream())
    try
      Console.withOut(nullStream) {
        var running = true
        while running do {
          val line = reader.readLine()
          if line == null then running = false
          else if line.trim.nonEmpty then
            try {
              val msg = McpJson.parse(line)
              try
                handleMessage(msg).foreach { response =>
                  jsonOut.println(response)
                  jsonOut.flush()
                }
              catch {
                case e: Exception =>
                  System.err.println(s"scalex MCP: internal error: ${e.getMessage}")
                  val id = msg("id")
                  val errResp =
                    jsonRpcError(id, -32603, s"Internal error: ${Option(e.getMessage).getOrElse(e.getClass.getName)}")
                  jsonOut.println(errResp)
                  jsonOut.flush()
              }
            } catch {
              case e: Exception =>
                System.err.println(s"scalex MCP: parse error: ${e.getMessage}")
                val errResp =
                  jsonRpcError(McpJson.Null, -32700, s"Parse error: ${Option(e.getMessage).getOrElse("invalid JSON")}")
                jsonOut.println(errResp)
                jsonOut.flush()
            }
        }
      }
    finally nullStream.close()
  }

  private def handleMessage(msg: McpJson): Option[String] = {
    val id = msg("id")
    val method = msg("method").asStr.getOrElse("")
    method match {
      case "initialize"                => Some(respondInitialize(id))
      case "notifications/initialized" => None
      case "tools/list"                => Some(respondToolsList(id))
      case "tools/call"                => Some(respondToolsCall(id, msg("params")))
      case "ping"                      => Some(jsonRpcResult(id, "{}"))
      case _                           =>
        if id == McpJson.Null then None // unknown notification — ignore
        else Some(jsonRpcError(id, -32601, s"Method not found: $method"))
    }
  }

  // ── Protocol responses ──────────────────────────────────────────────────

  private def respondInitialize(id: McpJson): String =
    jsonRpcResult(
      id,
      s"""{"protocolVersion":"2024-11-05","capabilities":{"tools":{}},"serverInfo":{"name":"scalex","version":"${jsonEscape(
          ScalexVersion
        )}"}}"""
    )

  private def respondToolsList(id: McpJson): String = {
    val tools = toolDefs
      .map { td =>
        val props = StringBuilder()
        props.append(""""workspace":{"type":"string","description":"Absolute path to the project root"}""")
        td.queryDesc.foreach { qd =>
          props.append(s""","query":{"type":"string","description":"${jsonEscape(qd)}"}""")
        }
        props.append(
          ""","args":{"type":"array","items":{"type":"string"},"description":"Additional CLI flags (e.g. [\"--verbose\", \"--json\", \"--kind\", \"class\"])"}"""
        )
        val required = if td.queryDesc.isDefined then """"workspace","query"""" else """"workspace""""
        s"""{"name":"scalex_${td.name}","description":"${jsonEscape(
            td.desc
          )}","inputSchema":{"type":"object","properties":{${props.toString}},"required":[$required]}}"""
      }
      .mkString("[", ",", "]")
    jsonRpcResult(id, s"""{"tools":$tools}""")
  }

  private def respondToolsCall(id: McpJson, params: McpJson): String = {
    val toolName = params("name").asStr.getOrElse("")
    val arguments = params("arguments")
    val cmd = toolName.stripPrefix("scalex_")

    if !toolName.startsWith("scalex_") || !commandHandlers.contains(cmd) then
      toolResult(id, s"Unknown tool: $toolName", isError = true)
    else {
      val workspace = arguments("workspace").asStr.getOrElse(".")
      val query = arguments("query").asStr
      val extraArgs = arguments("args").asArr.getOrElse(Nil).flatMap(_.asStr)

      def failed(message: String, category: String): String = {
        val text = if (extraArgs.contains("--json")) { s"""{"error":${jStr(message)}}""" }
        else { s"Error: $message" }
        toolResult(id, text, isError = true, errorCategory = Some(category))
      }
      try {
        val wsPath = resolveWorkspace(workspace)
        val allArgs = query.toList ++ extraArgs
        val flags = parseFlags(allArgs)
        Timings.enabled = flags(TimingsFlag)
        Timings.reset()
        val spec = commandsByName(cmd)
        val idx = if (spec.needsIndex) { WorkspaceIndex.load(wsPath, spec.needsBlooms) }
        else { WorkspaceIndex.empty(wsPath) }
        val noTests = (spec.defaultNoTests && !flags(IncludeTestsFlag)) || flags(NoTestsFlag)
        val ctx = flagsToContext(flags, idx, wsPath, effectiveNoTests = Some(noTests)).copy(stdinAvailable = false)
        val args = cmd match {
          case "graph" =>
            query.toList.flatMap(q => List("--render", q)) ++ commandLocalArgs(extraArgs)
          case "memory" | "pattern" => commandLocalArgs(allArgs)
          case _                    => flags.positional
        }
        val result = commandHandlers(cmd)(args, ctx)
        val output = captureOutput(ctx.output.jsonOutput) { renderWithBudget(result, ctx) }
        val category = classifyResult(result)
        toolResult(id, output, isError = category.isDefined, errorCategory = category)
      } catch {
        case e: GitFailure  => failed(e.getMessage, "git_error")
        case e: IOException => failed(e.getMessage, "io_error")
        case e: Exception   =>
          val msg = Option(e.getMessage).getOrElse(e.getClass.getName)
          val category = if msg.toLowerCase.contains("parse") then "parse_error" else "internal_error"
          failed(msg, category)
      } finally { Timings.report(); Timings.enabled = false }
    }
  }

  // ── Helpers ─────────────────────────────────────────────────────────────

  private def classifyResult(result: CmdResult): Option[String] = result match {
    case _: CmdResult.Failure                                             => Some("operation_error")
    case r: CmdResult.RenameResult if r.application.exists(_.skipped > 0) => Some("operation_error")
    case CmdResult.WithDiagnostics(inner, _)                              => classifyResult(inner)
    case _: CmdResult.NotFound                                            => Some("not_found")
    case _: CmdResult.UsageError                                          => Some("usage_error")
    case r: CmdResult.BugHuntReport if r.timedOut                         => Some("timeout")
    case r: CmdResult.RefList if r.timedOut                               => Some("timeout")
    case r: CmdResult.CategorizedRefs if r.timedOut                       => Some("timeout")
    case r: CmdResult.FlatRefs if r.timedOut                              => Some("timeout")
    case r: CmdResult.GrepCount if r.timedOut                             => Some("timeout")
    case r: CmdResult.GrepByMethod if r.timedOut                          => Some("timeout")
    case _                                                                => None
  }

  private def captureOutput(json: Boolean)(block: => Unit): String = {
    val bytes = ByteArrayOutputStream()
    val stream = PrintStream(bytes, true, java.nio.charset.StandardCharsets.UTF_8)
    try {
      Console.withOut(stream) {
        if (json) { block }
        else { Console.withErr(stream) { block } }
      }
    } finally stream.close()
    bytes.toString(java.nio.charset.StandardCharsets.UTF_8).stripTrailing()
  }

  private def toolResult(
      id: McpJson,
      text: String,
      isError: Boolean = false,
      errorCategory: Option[String] = None
  ): String = {
    val errField = if isError then ""","isError":true""" else ""
    val metaField = errorCategory.map(cat => s""","_meta":{"errorCategory":"$cat"}""").getOrElse("")
    jsonRpcResult(id, s"""{"content":[{"type":"text","text":"${jsonEscape(text)}"}]$errField$metaField}""")
  }

  private def jsonRpcResult(id: McpJson, result: String): String =
    s"""{"jsonrpc":"2.0","id":${formatId(id)},"result":$result}"""

  private def jsonRpcError(id: McpJson, code: Int, message: String): String =
    s"""{"jsonrpc":"2.0","id":${formatId(id)},"error":{"code":$code,"message":"${jsonEscape(message)}"}}"""

  // Test-only entry points
  def testHandleMessage(msg: McpJson): Option[String] = handleMessage(msg)

  private def formatId(id: McpJson): String = id match {
    case McpJson.Num(n) => if n == n.toLong then n.toLong.toString else n.toString
    case McpJson.Str(s) => s""""${jsonEscape(s)}""""
    case _              => "null"
  }
}
