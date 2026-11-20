package scalex.output

import scalex.*
import java.nio.file.Path

private[output] def renderRenameResult(r: CmdResult.RenameResult, ctx: CommandContext): Unit = {
  if ctx.output.jsonOutput then {
    val edits = r.edits
      .map { e =>
        val rel = jsonEscape(ctx.workspace.relativize(e.file).toString)
        s"""{"file":"$rel","line":${e.line},"category":"${e.category}","old":"${jsonEscape(
            e.oldLine
          )}","new":"${jsonEscape(e.newLine)}"}"""
      }
      .mkString("[", ",", "]")
    val appliedField = r.application
      .map { a =>
        val issues = a.issues.map(jStr).mkString("[", ",", "]")
        s""","applied":${a.skipped == 0},"appliedEdits":${a.applied},"changedFiles":${a.files},"skippedEdits":${a.skipped},"issues":$issues"""
      }
      .getOrElse("")
    println(s"""{"from":"${jsonEscape(r.oldName)}","to":"${jsonEscape(
        r.newName
      )}","editCount":${r.edits.size},"edits":$edits$appliedField}""")
  } else {
    if r.edits.isEmpty then println(s"""No occurrences of "${r.oldName}" found to rename""")
    else {
      val mode = if r.application.isDefined then "Applied" else "Preview"
      println(s"""$mode rename "${r.oldName}" → "${r.newName}" — ${r.edits.size} edits in ${r.edits
          .map(_.file)
          .distinct
          .size} files:""")
      println()
      var lastFile: Path = null
      r.edits.foreach { e =>
        val rel = ctx.workspace.relativize(e.file)
        if e.file != lastFile then {
          if lastFile != null then println()
          println(s"  $rel")
          lastFile = e.file
        }
        println(s"    L${e.line} [${e.category}]  ${e.oldLine}")
        println(s"    ${" " * (e.line.toString.length + 1)} →  ${e.newLine}")
      }
      println()
      r.application match {
        case Some(a) =>
          println(s"Applied ${a.applied} edits to ${a.files} files.")
          a.issues.foreach(Console.err.println)
        case None => println("(dry run — pass --apply to write changes to disk)")
      }
    }
  }
}

private[output] def renderCallGraph(r: CmdResult.CallGraph, ctx: CommandContext): Unit = {
  if ctx.output.jsonOutput then {
    val callees = r.callees
      .map { c =>
        val file = c.file.map(f => s""""${jsonEscape(ctx.workspace.relativize(f).toString)}"""").getOrElse("null")
        val line = c.line.map(_.toString).getOrElse("null")
        s"""{"name":"${jsonEscape(
            c.name
          )}","kind":"${c.kind.toString.toLowerCase}","file":$file,"line":$line,"package":"${jsonEscape(
            c.packageName
          )}","signature":"${jsonEscape(c.signature)}"}"""
      }
      .mkString("[", ",", "]")
    val callers = r.callers
      .map(ref => {
        val rel = jsonEscape(ctx.workspace.relativize(ref.file).toString)
        s"""{"file":"$rel","line":${ref.line},"context":"${jsonEscape(ref.contextLine)}"}"""
      })
      .mkString("[", ",", "]")
    val relFile = jsonEscape(ctx.workspace.relativize(r.file).toString)
    println(s"""{"method":"${jsonEscape(r.method)}","file":"$relFile","line":${r.line},"owner":"${jsonEscape(
        r.owner
      )}","callees":$callees,"callers":$callers}""")
  } else {
    val relFile = ctx.workspace.relativize(r.file)
    val ownerPrefix = if r.owner.nonEmpty then s"${r.owner}." else ""
    println(s"Call graph for $ownerPrefix${r.method} ($relFile:${r.line})")
    println()
    if r.callees.isEmpty then println("  Calls: (none detected)")
    else {
      println(s"  Calls (${r.callees.size}):")
      r.callees.foreach { c =>
        val loc = c.file.map(f => s" — ${ctx.workspace.relativize(f)}:${c.line.getOrElse("?")}").getOrElse("")
        val pkg = if c.packageName.nonEmpty then s" (${c.packageName})" else ""
        println(s"    ${c.kind.toString.toLowerCase.padTo(4, ' ')} ${c.name}$pkg$loc")
      }
    }
    if r.callers.nonEmpty then {
      println()
      println(s"  Called by (${r.callers.size}):")
      r.callers.foreach { ref =>
        val rel = ctx.workspace.relativize(ref.file)
        println(s"    $rel:${ref.line} — ${ref.contextLine}")
      }
    }
  }
}

private[output] def renderScaffoldImpl(r: CmdResult.ScaffoldImpl, ctx: CommandContext): Unit = {
  if ctx.output.jsonOutput then {
    val entries = r.unimplemented.flatMap { (parentName, parentFile, parentLine, members) =>
      members.map { m =>
        val rel = jsonEscape(ctx.workspace.relativize(parentFile).toString)
        s"""{"name":"${jsonEscape(m.name)}","kind":"${m.kind.toString.toLowerCase}","signature":"${jsonEscape(
            m.signature
          )}","from":"${jsonEscape(parentName)}","fromFile":"$rel","fromLine":$parentLine}"""
      }
    }
    val targetRel = jsonEscape(ctx.workspace.relativize(r.targetFile).toString)
    val code = r.unimplemented.flatMap(_.members).map { m =>
      val stub = m.kind match {
        case SymbolKind.Type => s"override ${m.signature} = Any // TODO: specify concrete type"
        case _               => s"override ${m.signature} = ???"
      }
      jsonEscape(stub)
    }
    val arr = entries.mkString("[", ",", "]")
    val codeArr = code.map(c => s""""$c"""").mkString("[", ",", "]")
    println(s"""{"target":"${jsonEscape(
        r.targetName
      )}","targetFile":"$targetRel","targetLine":${r.targetLine},"package":"${jsonEscape(
        r.targetPackage
      )}","stubs":$arr,"code":$codeArr}""")
  } else {
    if r.unimplemented.isEmpty then println(s"${r.targetName}: all abstract members are implemented")
    else {
      r.unimplemented.foreach { (parentName, parentFile, _, members) =>
        val rel = ctx.workspace.relativize(parentFile)
        println(s"// Unimplemented members from $parentName ($rel)")
      }
      val targetRel = ctx.workspace.relativize(r.targetFile)
      println(s"// Insert into ${r.targetName} at $targetRel:${r.targetLine}")
      println()
      r.unimplemented.foreach { (_, _, _, members) =>
        members.foreach { m =>
          m.kind match {
            case SymbolKind.Type => println(s"override ${m.signature} = Any // TODO: specify concrete type")
            case _               => println(s"override ${m.signature} = ???")
          }
        }
      }
    }
  }
}

private[output] def renderScaffoldTest(r: CmdResult.ScaffoldTest, ctx: CommandContext): Unit = {
  if ctx.output.jsonOutput then {
    val members = r.publicMembers
      .map { m =>
        s"""{"name":"${jsonEscape(m.name)}","signature":"${jsonEscape(m.signature)}"}"""
      }
      .mkString("[", ",", "]")
    val targetRel = jsonEscape(ctx.workspace.relativize(r.targetFile).toString)
    println(s"""{"target":"${jsonEscape(
        r.targetName
      )}","targetFile":"$targetRel","targetLine":${r.targetLine},"package":"${jsonEscape(
        r.targetPackage
      )}","framework":"${r.framework}","members":$members}""")
  } else {
    r.framework match {
      case "scalatest" =>
        println("import org.scalatest.flatspec.AnyFlatSpec")
        println("import org.scalatest.matchers.should.Matchers")
        println()
        println(s"class ${r.targetName}Spec extends AnyFlatSpec with Matchers:")
        if r.publicMembers.isEmpty then {
          println(s"""  it should "work" in:""")
          println("    ???")
        } else
          r.publicMembers.foreach { m =>
            println(s"""  "${r.targetName}" should "${m.name}" in:""")
            println("    ???")
          }
      case "zio-test" =>
        println("import zio.test.*")
        println()
        println(s"object ${r.targetName}Spec extends ZIOSpecDefault:")
        println(s"""  def spec = suite("${r.targetName}")(""")
        if r.publicMembers.isEmpty then println(s"""    test("should work")(???)""")
        else
          r.publicMembers.zipWithIndex.foreach { (m, i) =>
            val comma = if i < r.publicMembers.size - 1 then "," else ""
            println(s"""    test("${m.name} should ...")(???)$comma""")
          }
        println("  )")
      case _ => // munit (default)
        println(s"class ${r.targetName}Spec extends munit.FunSuite:")
        if r.publicMembers.isEmpty then {
          println("""  test("should work"):""")
          println("    ???")
        } else
          r.publicMembers.foreach { m =>
            println(s"""  test("${m.name} should ..."):""")
            println("    ???")
          }
    }
  }
}

private def confidenceLabel(score: Double): String =
  if score >= 5.0 then "HIGH"
  else if score >= 2.0 then "MEDIUM"
  else "LOW"

private[output] def renderBugHuntReport(r: CmdResult.BugHuntReport, ctx: CommandContext): Unit = {
  if ctx.output.jsonOutput then {
    val findingsJson = r.findings
      .map { f =>
        val rel = jsonEscape(ctx.workspace.relativize(f.file).toString)
        val taintJson = f.taintFlow match {
          case Some(flow) =>
            val stepsJson = flow.steps
              .map(s => s"""{"var":"${jsonEscape(s.varName)}","line":${s.line},"op":"${jsonEscape(s.operation)}"}""")
              .mkString("[", ",", "]")
            s""","taint":{"source":"${jsonEscape(flow.source.name)}","sourceType":"${jsonEscape(
                flow.source.description
              )}","sourceLine":${flow.source.line},"steps":$stepsJson,"confidence":${flow.confidence}}"""
          case None => ""
        }
        val reachJson = f.reachableFrom match {
          case Some(eps) if eps.nonEmpty =>
            val depthStr = f.callDepth.map(d => s""","callDepth":$d""").getOrElse("")
            s""","reachableFrom":[${eps.map(e => s"\"${jsonEscape(e)}\"").mkString(",")}]$depthStr"""
          case _ => ""
        }
        s"""{"file":"$rel","line":${f.line},"pattern":"${f.pattern.toString}","severity":"${f.pattern.severity.toString.toLowerCase}","category":"${f.pattern.category.toString.toLowerCase}","message":"${jsonEscape(
            f.message
          )}","contextLine":"${jsonEscape(f.contextLine)}","enclosingSymbol":"${jsonEscape(
            f.enclosingSymbol
          )}"$taintJson$reachJson}"""
      }
      .mkString("[", ",", "]")
    val hotspotsJson = r.hotspots
      .map { h =>
        s"""{"file":"${jsonEscape(
            h.file
          )}","findingCount":${h.findingCount},"churnScore":${h.churnScore},"complexityScore":${h.complexityScore},"riskScore":${h.riskScore}}"""
      }
      .mkString("[", ",", "]")
    println(
      s"""{"findings":$findingsJson,"hotspots":$hotspotsJson,"filesScanned":${r.filesScanned},"timedOut":${r.timedOut}}"""
    )
  } else {
    val fileCount = r.findings.map(_.file).distinct.size
    println(s"Bug hunt: ${r.findings.size} finding${if r.findings.size != 1 then "s" else ""} in $fileCount file${
        if fileCount != 1 then "s" else ""
      } (scanned ${r.filesScanned} files)")
    if r.timedOut then println("  (timed out — partial results)")
    if r.findings.isEmpty then println("  No issues found.")
    else {
      println()
      r.findings.foreach { f =>
        val rel = ctx.workspace.relativize(f.file)
        val sevTag = f.pattern.severity.toString.toUpperCase
        val taintTag = f.taintFlow.map(t => s" (taint: ${confidenceLabel(t.confidence)})").getOrElse("")
        println(s"  [$sevTag] $rel:${f.line} — ${f.pattern.category}$taintTag")
        println(s"    ${f.contextLine}")
        println(s"    ${f.message}")
        f.taintFlow.foreach { flow =>
          val chain = (flow.source.name :: flow.steps.map(s => s"${s.varName}")).mkString(" → ")
          println(s"    Flow: $chain")
        }
        f.reachableFrom.foreach { eps =>
          if eps.nonEmpty then {
            val depthStr = f.callDepth.map(d => s" (depth: $d)").getOrElse("")
            println(s"    Reachable from: ${eps.mkString(", ")}$depthStr")
          }
        }
        println()
      }
    }
    if r.hotspots.nonEmpty then {
      println("Hotspots (risk = findings x churn):")
      r.hotspots.take(10).foreach { h =>
        println(s"  ${h.file} — ${h.findingCount} findings, ${h.churnScore} commits (risk: ${f"${h.riskScore}%.0f"})")
      }
      println()
    }
  }
}

private[output] def renderMemoryResult(r: CmdResult.MemoryResult, ctx: CommandContext): Unit = {
  if (r.message.nonEmpty && !ctx.output.jsonOutput) { println(r.message) }
  if (ctx.output.jsonOutput) {
    val arr = r.memories.zipWithIndex
      .map { (m, i) =>
        val scopeJson = m.scope match {
          case MemoryScope.Global(p) =>
            s"""{"type":"global","patternName":"${jsonEscape(p)}"}"""
          case MemoryScope.FileScoped(p, fp) =>
            s"""{"type":"file","patternName":"${jsonEscape(p)}","filePattern":"${jsonEscape(fp)}"}"""
          case MemoryScope.MethodScoped(p, fp, mn) =>
            s"""{"type":"method","patternName":"${jsonEscape(p)}","filePattern":"${jsonEscape(
                fp
              )}","methodName":"${jsonEscape(mn)}"}"""
        }
        s"""{"index":$i,"patternName":"${jsonEscape(m.patternName)}","scope":$scopeJson,"reason":"${jsonEscape(
            m.reason
          )}","suppressionType":"${m.suppressionType.toString.toLowerCase}","contextLine":"${jsonEscape(
            m.contextLine
          )}","createdAt":${m.createdAt}}"""
      }
      .mkString("[", ",", "]")
    println(arr)
  } else if (r.memories.isEmpty) {
    if (r.message.isEmpty) { println("No suppression memories.") }
  } else {
    val header = s"Suppression memories (${r.memories.size}):"
    println(header)
    r.memories.zipWithIndex.foreach { (m, i) =>
      val scopeLabel = m.scope match {
        case MemoryScope.Global(_)               => "global"
        case MemoryScope.FileScoped(_, fp)       => s"file:$fp"
        case MemoryScope.MethodScoped(_, fp, mn) => s"method:$mn in $fp"
      }
      val typeLabel = m.suppressionType.toString.toLowerCase
      println(s"  [$i] ${m.patternName} ($typeLabel, $scopeLabel)")
      if m.reason.nonEmpty then println(s"      reason: ${m.reason}")
    }
  }
}

private[output] def renderPatternValidation(r: CmdResult.PatternValidation, ctx: CommandContext): Unit = {
  if ctx.output.jsonOutput then {
    val arr = r.results
      .map { v =>
        val sup = v.suppressedPass.map(b => s""""suppressedPass":$b,""").getOrElse("")
        val errs = v.errors.map(e => s""""${jsonEscape(e)}"""").mkString("[", ",", "]")
        s"""{"patternName":"${jsonEscape(
            v.patternName
          )}","positivePass":${v.positivePass},"negativePass":${v.negativePass},${sup}"errors":$errs}"""
      }
      .mkString("[", ",", "]")
    println(arr)
  } else {
    val allPass = r.results.forall(v => v.positivePass && v.negativePass && v.suppressedPass.forall(identity))
    r.results.foreach { v =>
      val status = if v.positivePass && v.negativePass && v.suppressedPass.forall(identity) then "PASS" else "FAIL"
      println(s"$status  ${v.patternName}")
      if v.errors.nonEmpty then v.errors.foreach(e => println(s"  - $e"))
    }
    val passCount = r.results.count(v => v.positivePass && v.negativePass && v.suppressedPass.forall(identity))
    println(s"\n${passCount}/${r.results.size} pattern specs passed.")
  }
}
