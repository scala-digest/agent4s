package scalex.commands

import scalex.*

import java.io.IOException
import java.nio.file.{AtomicMoveNotSupportedException, Files, Path, StandardCopyOption}
import scala.collection.mutable

// ── Rename command ──────────────────────────────────────────────────────────

def cmdRename(args: List[String], ctx: CommandContext): CmdResult =
  args match {
    case oldName :: newName :: _ =>
      val preview = if (ctx.refactoring.semantic) { semanticRename(oldName, newName, ctx) }
      else { textRename(oldName, newName, ctx) }
      preview match {
        case r: CmdResult.RenameResult if ctx.refactoring.applyEdits =>
          r.copy(application = Some(applyRenameEdits(r.edits, ctx.workspace)))
        case other => other
      }
    case _ =>
      CmdResult.UsageError("Usage: scalex rename <OldName> <NewName> [--semantic]")
  }

/** Text-based rename: word-boundary replacement across all files. Fast, but may hit wrong symbol if name is ambiguous.
  */
private def textRename(oldName: String, newName: String, ctx: CommandContext): CmdResult = {
  val defs = filterSymbols(ctx.idx.findDefinition(oldName), ctx)
  if defs.isEmpty then
    CmdResult.NotFound(s"""No definition of "$oldName" found""", mkNotFoundWithSuggestions(oldName, ctx, "rename"))
  else {
    val defFiles = defs.map(_.file).toSet
    val scan = ctx.idx.findReferences(oldName, strict = true)
    if (scan.timedOut) { CmdResult.Failure("Rename timed out before all references could be checked.") }
    else {
      val edits = buildTextEdits(filterRefs(scan.results, ctx), oldName, newName, defFiles)
      CmdResult.RenameResult(oldName, newName, defs.head, edits)
    }
  }
}

/** Semantic rename: uses SemanticDB for type-aware, package-precise renaming. Two symbols named "Config" in different
  * packages? Only renames the right one.
  */
private def semanticRename(oldName: String, newName: String, ctx: CommandContext): CmdResult = {
  val defs = filterSymbols(ctx.idx.findDefinition(oldName), ctx)
  if defs.isEmpty then
    CmdResult.NotFound(s"""No definition of "$oldName" found""", mkNotFoundWithSuggestions(oldName, ctx, "rename"))
  else
    ctx.idx.semanticIndex match {
      case None =>
        System.err.println(
          "No SemanticDB data found. Compile with -Xsemanticdb (Scala 3) or semanticdbEnabled := true (sbt)."
        )
        System.err.println("Falling back to text-based rename.")
        textRename(oldName, newName, ctx)
      case Some(semIdx) =>
        val targetDef = defs.head
        // Map scalex definition to SemanticDB symbol
        semIdx.matchSymbol(targetDef) match {
          case None =>
            System.err.println(s"Could not resolve '$oldName' in SemanticDB — falling back to text-based rename.")
            textRename(oldName, newName, ctx)
          case Some(sdbSymbol) =>
            // Find ALL occurrences of this exact symbol — type-aware, no ambiguity
            val semRefs = semIdx.findRefsBySymbol(sdbSymbol)
            if semRefs.isEmpty then {
              System.err.println(s"No semantic refs for symbol $sdbSymbol — falling back to text-based rename.")
              textRename(oldName, newName, ctx)
            } else {
              // Show disambiguation info
              val allSymbols = semIdx.resolveSymbols(oldName)
              if allSymbols.size > 1 then {
                System.err.println(s"${allSymbols.size} symbols named '$oldName' found. Renaming only:")
                System.err.println(s"  $sdbSymbol")
                System.err.println(s"Untouched:")
                allSymbols.filter(_.symbol != sdbSymbol).foreach { s =>
                  System.err.println(s"  ${s.symbol} (${s.pkg})")
                }
              }
              System.err.println(s"Using semantic rename (${semIdx.fileCount} compiled files) — type-aware, precise")

              val edits = List.newBuilder[RenameEdit]
              var stale = false
              semRefs.groupBy(_.file).foreach { (file, refs) =>
                val lines = Files.readAllLines(file)
                refs.groupBy(_.line).foreach { (line, occurrences) =>
                  val offsets = occurrences.map(_.character).distinct.sorted.reverse
                  if (line < 1 || line > lines.size()) { stale = true }
                  else {
                    val source = lines.get(line - 1)
                    if (!offsets.forall(offset => source.startsWith(oldName, offset))) { stale = true }
                    else {
                      val updated = offsets.foldLeft(source) { (text, offset) =>
                        text.patch(offset, newName, oldName.length)
                      }
                      val category = if (occurrences.exists(_.role == SemanticRole.Definition)) { "definition" }
                      else if (source.trim.startsWith("import ")) { "import" }
                      else { "usage" }
                      edits += RenameEdit(file, line, source.trim, updated.trim, category)
                    }
                  }
                }
              }
              if (stale) {
                CmdResult.Failure("SemanticDB positions differ from the source. Recompile before renaming.")
              } else {
                CmdResult.RenameResult(
                  oldName,
                  newName,
                  targetDef,
                  edits.result().sortBy(e => (e.file.toString, e.line))
                )
              }
            }
        }
    }
}

private def buildTextEdits(
    refs: List[Reference],
    oldName: String,
    newName: String,
    defFiles: Set[Path]
): List[RenameEdit] = {
  val edits = mutable.ListBuffer.empty[RenameEdit]
  val seenKeys = mutable.HashSet.empty[String]
  refs.foreach { ref =>
    val key = s"${ref.file}:${ref.line}"
    if seenKeys.add(key) then {
      val newLine = replaceWord(ref.contextLine, oldName, newName)
      if newLine != ref.contextLine then {
        val category =
          if defFiles.contains(ref.file) && isDefinitionLine(ref.contextLine, oldName) then "definition"
          else if ref.contextLine.trim.startsWith("import ") then "import"
          else if ref.contextLine.matches("""^\s*(//|/\*|\*).*""") then "comment"
          else "usage"
        edits += RenameEdit(ref.file, ref.line, ref.contextLine, newLine, category)
      }
    }
  }
  edits.toList.sortBy(e => (e.file.toString, e.line))
}

private def isDefinitionLine(line: String, name: String): Boolean = {
  val trimmed = line.trim
  trimmed.matches(s"""^(trait|class|object|enum|def|val|var|type|given)\\s+.*""") &&
  (trimmed.contains(s"trait $name") || trimmed.contains(s"class $name") || trimmed.contains(s"object $name") ||
    trimmed.contains(s"enum $name") || trimmed.contains(s"def $name") || trimmed.contains(s"val $name") ||
    trimmed.contains(s"var $name") || trimmed.contains(s"type $name"))
}

private def replaceWord(text: String, oldWord: String, newWord: String): String = {
  val sb = StringBuilder()
  var i = 0
  while i < text.length do {
    val idx = text.indexOf(oldWord, i)
    if idx < 0 then {
      sb.append(text.substring(i))
      i = text.length
    } else {
      val before = idx == 0 || !text(idx - 1).isLetterOrDigit
      val afterPos = idx + oldWord.length
      val after = afterPos >= text.length || !text(afterPos).isLetterOrDigit
      sb.append(text.substring(i, idx))
      if before && after then sb.append(newWord)
      else sb.append(oldWord)
      i = afterPos
    }
  }
  sb.toString
}

/** Check each file against the preview before applying its edits. Keep its line endings and attributes. */
private def applyRenameEdits(edits: List[RenameEdit], workspace: Path): RenameApplication = {
  var applied = 0
  var changedFiles = 0
  val issues = List.newBuilder[String]
  edits.groupBy(_.file).toList.sortBy(_._1.toString).foreach { (file, fileEdits) =>
    try {
      val target = file.toRealPath()
      val original = Files.readString(target)
      val lines = original.split("(?<=\\n)|(?<=\\r)(?!\\n)", -1)
      val stale = fileEdits.exists { e => !lines.lift(e.line - 1).exists(_.trim == e.oldLine) }
      if (stale) { issues += s"Skipped ${workspace.relativize(file)} because its contents changed." }
      else {
        fileEdits.foreach { e => lines(e.line - 1) = lines(e.line - 1).replace(e.oldLine, e.newLine) }
        val temp = Files.createTempFile(target.getParent, ".agent4s-rename-", ".tmp")
        try {
          Files.copy(target, temp, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
          Files.writeString(temp, lines.mkString)
          if (Files.readString(target) != original) {
            issues += s"Skipped ${workspace.relativize(file)} because its contents changed."
          } else {
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch {
              case _: AtomicMoveNotSupportedException => Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
            applied += fileEdits.size
            changedFiles += 1
          }
        } finally Files.deleteIfExists(temp)
      }
    } catch { case e: IOException => issues += s"Skipped ${workspace.relativize(file)}: ${e.getMessage}" }
  }
  RenameApplication(applied, changedFiles, edits.size - applied, issues.result())
}
