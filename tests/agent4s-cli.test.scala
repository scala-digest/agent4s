package scalex

import scalex.commands.*
import scalex.extraction.*
import scalex.index.*

class Agent4sCliSuite extends Agent4sTestBase {
  test("scaffold impl: shows unimplemented abstract members") {
    val idx = WorkspaceIndex.load(workspace)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val result = cmdScaffold(List("impl", "RoleProcessor"), ctx)
    result match {
      case r: CmdResult.ScaffoldImpl =>
        assertEquals(r.targetName, "RoleProcessor")
        assert(r.unimplemented.nonEmpty, "RoleProcessor should have unimplemented members from Processor")
        val allMembers = r.unimplemented.flatMap(_.members)
        val names = allMembers.map(_.name)
        assert(names.contains("process"), s"Should include abstract 'process': $names")
        assert(!names.contains("validate"), s"Should not include concrete 'validate': $names")
      case other => fail(s"Expected ScaffoldImpl but got: $other")
    }
  }

  test("scaffold impl: fully implemented class shows empty") {
    val idx = WorkspaceIndex.load(workspace)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val result = cmdScaffold(List("impl", "UserServiceLive"), ctx)
    result match {
      case r: CmdResult.ScaffoldImpl =>
        assertEquals(r.targetName, "UserServiceLive")
        assert(r.unimplemented.isEmpty, s"UserServiceLive implements everything: ${r.unimplemented}")
      case other => fail(s"Expected ScaffoldImpl but got: $other")
    }
  }

  test("scaffold impl: text output includes override stubs") {
    val idx = WorkspaceIndex.load(workspace)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val output = captureOut {
      renderWithBudget(cmdScaffold(List("impl", "RoleProcessor"), ctx), ctx)
    }
    assert(output.contains("override def process"), s"Should contain override stub:\n$output")
    assert(output.contains("= ???"), s"Should contain ??? placeholder:\n$output")
    assert(output.contains("Unimplemented members from Processor"), s"Should show parent name:\n$output")
    assert(output.contains("Insert into RoleProcessor"), s"Should show target class:\n$output")
  }

  test("scaffold impl: all-implemented text output says so") {
    val idx = WorkspaceIndex.load(workspace)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val output = captureOut {
      renderWithBudget(cmdScaffold(List("impl", "UserServiceLive"), ctx), ctx)
    }
    assert(output.contains("all abstract members are implemented"), s"Should say all implemented:\n$output")
  }

  test("scaffold impl: JSON output has stubs array and code array") {
    val idx = WorkspaceIndex.load(workspace)

    val ctx = CommandContext(idx = idx, workspace = workspace, output = OutputOptions(jsonOutput = true))
    val output = captureOut {
      renderWithBudget(cmdScaffold(List("impl", "RoleProcessor"), ctx), ctx)
    }
    assert(output.contains(""""target":"RoleProcessor""""), s"Should have target:\n$output")
    assert(output.contains(""""stubs":["""), s"Should have stubs array:\n$output")
    assert(output.contains(""""code":["""), s"Should have code array:\n$output")
    assert(output.contains(""""from":"Processor""""), s"Should reference parent:\n$output")
  }

  test("scaffold impl: not found shows suggestions") {
    val idx = WorkspaceIndex.load(workspace)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val result = cmdScaffold(List("impl", "NonExistent"), ctx)
    result match {
      case r: CmdResult.NotFound =>
        assert(r.message.contains("NonExistent"))
      case other => fail(s"Expected NotFound but got: $other")
    }
  }

  test("scaffold impl: walks transitive parent chain") {
    // PaymentServiceLive extends PaymentService (which has abstract members)
    // PaymentServiceLive implements everything so should be empty
    val idx = WorkspaceIndex.load(workspace)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val result = cmdScaffold(List("impl", "PaymentServiceLive"), ctx)
    result match {
      case r: CmdResult.ScaffoldImpl =>
        assert(r.unimplemented.isEmpty, s"PaymentServiceLive implements all: ${r.unimplemented}")
      case other => fail(s"Expected ScaffoldImpl but got: $other")
    }
  }

  test("scaffold impl: GenericProcessor has unimplemented process") {
    val idx = WorkspaceIndex.load(workspace)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val result = cmdScaffold(List("impl", "GenericProcessor"), ctx)
    result match {
      case r: CmdResult.ScaffoldImpl =>
        val names = r.unimplemented.flatMap(_.members).map(_.name)
        assert(names.contains("process"), s"GenericProcessor should need process: $names")
      case other => fail(s"Expected ScaffoldImpl but got: $other")
    }
  }

  test("scaffold impl: usage error without class name") {
    val idx = WorkspaceIndex.load(workspace)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val result = cmdScaffold(List("impl"), ctx)
    result match {
      case r: CmdResult.UsageError => assert(r.message.contains("Usage"))
      case other                   => fail(s"Expected UsageError but got: $other")
    }
  }

  // ── scaffold test ──────────────────────────────────────────────────────

  test("scaffold test: generates munit test skeleton") {
    val idx = WorkspaceIndex.load(workspace)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val output = captureOut {
      renderWithBudget(cmdScaffold(List("test", "UserService"), ctx), ctx)
    }
    assert(output.contains("class UserServiceSpec extends munit.FunSuite:"), s"Should have munit class:\n$output")
    assert(output.contains("""test("findUser should ..."):"""), s"Should have findUser test:\n$output")
    assert(output.contains("""test("createUser should ..."):"""), s"Should have createUser test:\n$output")
  }

  test("scaffold test: scalatest framework") {
    val idx = WorkspaceIndex.load(workspace)

    val ctx =
      CommandContext(idx = idx, workspace = workspace, refactoring = RefactoringOptions(framework = "scalatest"))
    val output = captureOut {
      renderWithBudget(cmdScaffold(List("test", "UserService"), ctx), ctx)
    }
    assert(output.contains("extends AnyFlatSpec with Matchers"), s"Should use scalatest:\n$output")
    assert(output.contains(""""UserService" should "findUser""""), s"Should have scalatest format:\n$output")
  }

  test("scaffold test: zio-test framework") {
    val idx = WorkspaceIndex.load(workspace)

    val ctx = CommandContext(idx = idx, workspace = workspace, refactoring = RefactoringOptions(framework = "zio-test"))
    val output = captureOut {
      renderWithBudget(cmdScaffold(List("test", "UserService"), ctx), ctx)
    }
    assert(output.contains("extends ZIOSpecDefault:"), s"Should use zio-test:\n$output")
    assert(output.contains("""suite("UserService")"""), s"Should have suite:\n$output")
    assert(output.contains("""test("findUser should ...")"""), s"Should have test:\n$output")
  }

  test("scaffold test: JSON output") {
    val idx = WorkspaceIndex.load(workspace)

    val ctx = CommandContext(idx = idx, workspace = workspace, output = OutputOptions(jsonOutput = true))
    val output = captureOut {
      renderWithBudget(cmdScaffold(List("test", "UserService"), ctx), ctx)
    }
    assert(output.contains(""""target":"UserService""""), s"Should have target:\n$output")
    assert(output.contains(""""framework":"munit""""), s"Should have framework:\n$output")
    assert(output.contains(""""members":["""), s"Should have members array:\n$output")
  }

  test("scaffold test: returns only def members") {
    val idx = WorkspaceIndex.load(workspace)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val result = cmdScaffold(List("test", "PaymentServiceLive"), ctx)
    result match {
      case r: CmdResult.ScaffoldTest =>
        val kinds = r.publicMembers.map(_.kind).toSet
        assertEquals(kinds, Set(SymbolKind.Def), s"Should only have Def members, got: ${r.publicMembers}")
      case other => fail(s"Expected ScaffoldTest but got: $other")
    }
  }

  test("scaffold test: not found shows suggestions") {
    val idx = WorkspaceIndex.load(workspace)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val result = cmdScaffold(List("test", "DoesNotExist"), ctx)
    result match {
      case r: CmdResult.NotFound => assert(r.message.contains("DoesNotExist"))
      case other                 => fail(s"Expected NotFound but got: $other")
    }
  }

  test("scaffold: usage error without subcommand") {
    val idx = WorkspaceIndex.load(workspace)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val result = cmdScaffold(Nil, ctx)
    result match {
      case r: CmdResult.UsageError => assert(r.message.contains("Usage"))
      case other                   => fail(s"Expected UsageError but got: $other")
    }
  }

  test("scaffold: usage error with unknown subcommand") {
    val idx = WorkspaceIndex.load(workspace)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val result = cmdScaffold(List("unknown", "Foo"), ctx)
    result match {
      case r: CmdResult.UsageError => assert(r.message.contains("Usage"))
      case other                   => fail(s"Expected UsageError but got: $other")
    }
  }

  test("scaffold impl: type parameter substitution — RoleProcessor") {
    val idx = WorkspaceIndex.load(workspace)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val result = cmdScaffold(List("impl", "RoleProcessor"), ctx)
    result match {
      case r: CmdResult.ScaffoldImpl =>
        val processMember = r.unimplemented.flatMap(_.members).find(_.name == "process")
        assert(processMember.isDefined, "Should find process member")
        val sig = processMember.get.signature
        assert(sig.contains("Role"), s"Should substitute T → Role: $sig")
        assert(!sig.matches(".*\\bT\\b.*"), s"Should not contain bare T: $sig")
      case other => fail(s"Expected ScaffoldImpl but got: $other")
    }
  }

  test("scaffold impl: type parameter substitution — GenericProcessor keeps type param") {
    val idx = WorkspaceIndex.load(workspace)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val result = cmdScaffold(List("impl", "GenericProcessor"), ctx)
    result match {
      case r: CmdResult.ScaffoldImpl =>
        val processMember = r.unimplemented.flatMap(_.members).find(_.name == "process")
        assert(processMember.isDefined, "Should find process member")
        val sig = processMember.get.signature
        // GenericProcessor[A] extends Processor[A], so T → A
        assert(sig.contains("A"), s"Should substitute T → A: $sig")
      case other => fail(s"Expected ScaffoldImpl but got: $other")
    }
  }

  test("scaffold impl: type substitution in rendered output") {
    val idx = WorkspaceIndex.load(workspace)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val output = captureOut {
      renderWithBudget(cmdScaffold(List("impl", "RoleProcessor"), ctx), ctx)
    }
    assert(output.contains("Role"), s"Output should contain substituted type Role:\n$output")
    assert(!output.matches("(?s).*\\bitem: T\\b.*"), s"Output should not contain 'item: T':\n$output")
  }

  test("scaffold impl: NestedTypeArgProcessor substitutes Map[String, User]") {
    val idx = WorkspaceIndex.load(workspace)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val result = cmdScaffold(List("impl", "NestedTypeArgProcessor"), ctx)
    result match {
      case r: CmdResult.ScaffoldImpl =>
        val processMember = r.unimplemented.flatMap(_.members).find(_.name == "process")
        assert(processMember.isDefined, "Should find process member")
        val sig = processMember.get.signature
        assert(sig.contains("Map[String, User]"), s"Should substitute T → Map[String, User]: $sig")
      case other => fail(s"Expected ScaffoldImpl but got: $other")
    }
  }

  test("extractParentTypeArgs: simple type arg") {
    val file = workspace.resolve("src/main/scala/com/example/Mixins.scala")
    val args = extractParentTypeArgs(file, "RoleProcessor", "Processor")
    assertEquals(args, List("Role"))
  }

  test("extractParentTypeArgs: generic forwarding") {
    val file = workspace.resolve("src/main/scala/com/example/Mixins.scala")
    val args = extractParentTypeArgs(file, "GenericProcessor", "Processor")
    assertEquals(args, List("A"))
  }

  test("extractParentTypeArgs: nested type arg") {
    val file = workspace.resolve("src/main/scala/com/example/Mixins.scala")
    val args = extractParentTypeArgs(file, "NestedTypeArgProcessor", "Processor")
    assertEquals(args, List("Map[String, User]"))
  }

  test("extractParentTypeArgs: no type args") {
    val file = workspace.resolve("src/main/scala/com/example/UserService.scala")
    val args = extractParentTypeArgs(file, "UserServiceLive", "UserService")
    assertEquals(args, Nil)
  }

  test("extractTypeParamNames: trait with type param") {
    val file = workspace.resolve("src/main/scala/com/example/Mixins.scala")
    val params = extractTypeParamNames(file, "Processor")
    assertEquals(params, List("T"))
  }

  test("extractTypeParamNames: class with type param") {
    val file = workspace.resolve("src/main/scala/com/example/Mixins.scala")
    val params = extractTypeParamNames(file, "GenericProcessor")
    assertEquals(params, List("A"))
  }

  test("extractTypeParamNames: no type params") {
    val file = workspace.resolve("src/main/scala/com/example/UserService.scala")
    val params = extractTypeParamNames(file, "UserService")
    assertEquals(params, Nil)
  }

  test("parseFlags: --framework flag") {
    val f1 = parseFlags(List("--framework", "scalatest"))
    assertEquals(flagsToContext(f1, WorkspaceIndex.empty(workspace), workspace).refactoring.framework, "scalatest")
    val f2 = parseFlags(List("--framework", "zio-test"))
    assertEquals(flagsToContext(f2, WorkspaceIndex.empty(workspace), workspace).refactoring.framework, "zio-test")
    val f3 = parseFlags(Nil)
    assertEquals(flagsToContext(f3, WorkspaceIndex.empty(workspace), workspace).refactoring.framework, "munit")
  }

  // ── rename ────────────────────────────────────────────────────────────

  test("rename: finds edits across files") {
    val idx = WorkspaceIndex.load(workspace, needBlooms = true)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val result = cmdRename(List("UserService", "AccountService"), ctx)
    result match {
      case r: CmdResult.RenameResult =>
        assertEquals(r.oldName, "UserService")
        assertEquals(r.newName, "AccountService")
        assert(r.edits.nonEmpty, "Should find edits for UserService")
        // Should include definition and import categories
        val categories = r.edits.map(_.category).toSet
        assert(categories.contains("definition"), s"Should include definition edits: $categories")
        // At least some edits should contain the new name
        assert(
          r.edits.exists(_.newLine.contains("AccountService")),
          s"At least some edits should contain AccountService"
        )
      case other => fail(s"Expected RenameResult but got: $other")
    }
  }

  test("rename: does not rename substrings") {
    val idx = WorkspaceIndex.load(workspace, needBlooms = true)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val result = cmdRename(List("UserService", "XService"), ctx)
    result match {
      case r: CmdResult.RenameResult =>
        // "UserServiceLive" should NOT be affected — it's a different symbol
        r.edits.foreach { e =>
          assert(!e.newLine.contains("XServiceLive"), s"Should not rename substring in UserServiceLive: ${e.newLine}")
        }
      case other => fail(s"Expected RenameResult but got: $other")
    }
  }

  test("rename: JSON output") {
    val idx = WorkspaceIndex.load(workspace, needBlooms = true)

    val ctx = CommandContext(idx = idx, workspace = workspace, output = OutputOptions(jsonOutput = true))
    val output = captureOut {
      renderWithBudget(cmdRename(List("Database", "DataStore"), ctx), ctx)
    }
    assert(output.contains(""""from":"Database""""), s"Should have from:\n$output")
    assert(output.contains(""""to":"DataStore""""), s"Should have to:\n$output")
    assert(output.contains(""""edits":["""), s"Should have edits:\n$output")
  }

  test("rename: text output shows diff") {
    val idx = WorkspaceIndex.load(workspace, needBlooms = true)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val output = captureOut {
      renderWithBudget(cmdRename(List("Database", "DataStore"), ctx), ctx)
    }
    assert(output.contains("→"), s"Should show diff arrow:\n$output")
    assert(output.contains("DataStore"), s"Should show new name:\n$output")
  }

  test("rename: not found") {
    val idx = WorkspaceIndex.load(workspace, needBlooms = true)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val result = cmdRename(List("NonExistent", "NewName"), ctx)
    result match {
      case _: CmdResult.NotFound => () // expected
      case other                 => fail(s"Expected NotFound but got: $other")
    }
  }

  test("rename: usage error without args") {
    val idx = WorkspaceIndex.load(workspace, needBlooms = true)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val result = cmdRename(Nil, ctx)
    result match {
      case _: CmdResult.UsageError => () // expected
      case other                   => fail(s"Expected UsageError but got: $other")
    }
  }

  // ── unused ────────────────────────────────────────────────────────────

  test("unused: finds symbols with no external refs") {
    val idx = WorkspaceIndex.load(workspace, needBlooms = true)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val result = cmdUnused(Nil, ctx)
    result match {
      case r: CmdResult.SymbolList =>
        // Some symbols should be unused (e.g., types only used in their own file)
        // At minimum, result should be a valid SymbolList
        assert(r.header.contains("unused"), s"Header should mention unused: ${r.header}")
      case other => fail(s"Expected SymbolList but got: $other")
    }
  }

  test("unused: scopes to package") {
    val idx = WorkspaceIndex.load(workspace, needBlooms = true)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val result = cmdUnused(List("com.other"), ctx)
    result match {
      case r: CmdResult.SymbolList =>
        r.symbols.foreach { s =>
          assert(s.packageName.startsWith("com.other"), s"Should be in com.other: ${s.packageName}")
        }
      case _: CmdResult.NotFound => () // acceptable if no unused
      case other                 => fail(s"Expected SymbolList or NotFound but got: $other")
    }
  }

  test("unused: well-used symbols not reported") {
    val idx = WorkspaceIndex.load(workspace, needBlooms = true)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val result = cmdUnused(Nil, ctx)
    result match {
      case r: CmdResult.SymbolList =>
        val names = r.symbols.map(_.name)
        // UserService is heavily used across files — should NOT be in unused list
        assert(!names.contains("UserService"), s"UserService should not be unused: $names")
      case other => fail(s"Expected SymbolList but got: $other")
    }
  }

  // ── call-graph ────────────────────────────────────────────────────────

  test("call-graph: returns valid structure") {
    val idx = WorkspaceIndex.load(workspace, needBlooms = true)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val result = cmdCallGraph(List("findUser"), ctx)
    result match {
      case r: CmdResult.CallGraph =>
        assertEquals(r.method, "findUser")
        assert(r.file != null, "Should have file")
        assert(r.line > 0, "Should have line")
      case other => fail(s"Expected CallGraph but got: $other")
    }
  }

  test("call-graph: includes callers") {
    val idx = WorkspaceIndex.load(workspace, needBlooms = true)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val result = cmdCallGraph(List("findUser"), ctx)
    result match {
      case r: CmdResult.CallGraph =>
        // findUser is called in UserServiceSpec
        assert(r.callers.nonEmpty, s"findUser should have callers")
      case other => fail(s"Expected CallGraph but got: $other")
    }
  }

  test("call-graph: JSON output") {
    val idx = WorkspaceIndex.load(workspace, needBlooms = true)

    val ctx = CommandContext(idx = idx, workspace = workspace, output = OutputOptions(jsonOutput = true))
    val output = captureOut {
      renderWithBudget(cmdCallGraph(List("findUser"), ctx), ctx)
    }
    assert(output.contains(""""method":"findUser""""), s"Should have method:\n$output")
    assert(output.contains(""""callees":["""), s"Should have callees:\n$output")
    assert(output.contains(""""callers":["""), s"Should have callers:\n$output")
  }

  test("call-graph: text output") {
    val idx = WorkspaceIndex.load(workspace, needBlooms = true)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val output = captureOut {
      renderWithBudget(cmdCallGraph(List("findUser"), ctx), ctx)
    }
    assert(output.contains("Call graph for"), s"Should have header:\n$output")
    assert(output.contains("Calls"), s"Should have calls section:\n$output")
  }

  test("call-graph: not found") {
    val idx = WorkspaceIndex.load(workspace, needBlooms = true)

    val ctx = CommandContext(idx = idx, workspace = workspace)
    val result = cmdCallGraph(List("nonExistentMethod"), ctx)
    result match {
      case _: CmdResult.NotFound => () // expected
      case other                 => fail(s"Expected NotFound but got: $other")
    }
  }

  test("MCP sees newly staged declarations between calls and reports an invalid Git ref") {
    def query(tool: String, q: String): ujson.Value = {
      val request = ujson.Obj(
        "jsonrpc" -> "2.0",
        "id" -> 1,
        "method" -> "tools/call",
        "params" -> ujson.Obj(
          "name" -> s"scalex_$tool",
          "arguments" -> ujson.Obj(
            "workspace" -> workspace.toString,
            "query" -> q,
            "args" -> ujson.Arr("--json")
          )
        )
      )
      ujson.read(McpServer.testHandleMessage(McpJson.parse(ujson.write(request))).get)("result")
    }
    val file = "McpRefresh.scala"
    writeFile(file, "class FirstMcpDeclaration")
    run("git", "add", file)
    assert(query("def", "FirstMcpDeclaration")("content")(0)("text").str.contains("FirstMcpDeclaration"))
    writeFile(file, "class SecondMcpDeclaration")
    run("git", "add", file)
    assertEquals(query("def", "FirstMcpDeclaration")("isError").bool, true)
    val fresh = query("def", "SecondMcpDeclaration")
    assert(!fresh.obj.contains("isError"))
    assert(fresh("content")(0)("text").str.contains("SecondMcpDeclaration"))
    val failed = query("diff", "missing-agent4s-test-ref")
    assertEquals(failed("isError").bool, true)
    assert(ujson.read(failed("content")(0)("text").str)("error").str.nonEmpty)
  }

  test("rename --apply --json reports the edits and preserves source line endings") {
    val file = "RenameFormatting.scala"
    val before = "class RenameSource\r\nclass RenameUser extends RenameSource"
    writeFile(file, before)
    run("git", "add", file)
    val out = captureOutErr {
      assertEquals(
        runCli(List("rename", "RenameSource", "RenamedTarget", "-w", workspace.toString, "--apply", "--json")),
        0
      )
    }
    val applied = ujson.read(out.stdout)
    assertEquals(applied("applied").bool, true)
    assertEquals(applied("appliedEdits").num, 2.0)
    assertEquals(
      java.nio.file.Files.readString(workspace.resolve(file)),
      before.replace("RenameSource", "RenamedTarget")
    )
  }

  test("SemanticDB member rename only edits compiler-resolved occurrences") {
    import scala.meta.internal.{semanticdb => sdb}
    val file = "SemanticMember.scala"
    val lines = List(
      "object SemanticMember {",
      "  def compute(): Int = 1",
      """  def next(): String = compute().toString + "compute" """.stripTrailing(),
      "}"
    )
    writeFile(file, lines.mkString("\n"))
    run("git", "add", file)
    val symbol = "SemanticMember.compute()."
    assertEquals(SemanticIndex.extractSimpleName(symbol), "compute")
    assertEquals(SemanticIndex.extractSimpleName("example/Owner#compute(+1)."), "compute")
    val uses = List((1, sdb.SymbolOccurrence.Role.DEFINITION), (2, sdb.SymbolOccurrence.Role.REFERENCE)).map {
      (row, role) =>
        val column = lines(row).indexOf("compute")
        sdb.SymbolOccurrence(Some(sdb.Range(row, column, row, column + "compute".length)), symbol, role)
    }
    val doc = sdb.TextDocument(
      uri = file,
      symbols =
        Seq(sdb.SymbolInformation(symbol = symbol, kind = sdb.SymbolInformation.Kind.METHOD, displayName = "compute")),
      occurrences = uses
    )
    val meta = workspace.resolve("target/classes/META-INF/semanticdb/SemanticMember.scala.semanticdb")
    java.nio.file.Files.createDirectories(meta.getParent)
    java.nio.file.Files.write(meta, sdb.TextDocuments(Seq(doc)).toByteArray)
    val ctx =
      CommandContext(WorkspaceIndex.load(workspace), workspace, refactoring = RefactoringOptions(semantic = true))
    cmdRename(List("compute", "calculate"), ctx) match {
      case r: CmdResult.RenameResult =>
        assertEquals(r.edits.size, 2)
        assertEquals(r.edits.last.newLine, """def next(): String = calculate().toString + "compute" """.stripTrailing())
      case other => fail(s"Expected semantic rename edits, got $other")
    }
  }
}
