package scalex

import java.nio.file.Path

// ── Rename types ──────────────────────────────────────────────────────────

case class RenameApplication(applied: Int, files: Int, skipped: Int, issues: List[String])

case class RenameEdit(file: Path, line: Int, oldLine: String, newLine: String, category: String)

// ── Call graph types ──────────────────────────────────────────────────────

case class CalleeInfo(
    name: String,
    kind: SymbolKind,
    file: Option[Path],
    line: Option[Int],
    packageName: String,
    signature: String
)

// ── Bug hunt types ─────────────────────────────────────────────────────────

enum BugSeverity {
  case Critical, High, Medium
}

enum BugCategory {
  case Security, TypeSafety, Concurrency, Resource, Effect
}

enum BugPattern(val severity: BugSeverity, val category: BugCategory, val description: String) {
  case OptionGet
      extends BugPattern(
        BugSeverity.Medium,
        BugCategory.TypeSafety,
        ".get on Option — throws NoSuchElementException if empty"
      )
  case CollectionHead
      extends BugPattern(BugSeverity.Medium, BugCategory.TypeSafety, ".head on collection — throws on empty collection")
  case AsInstanceOf
      extends BugPattern(BugSeverity.Medium, BugCategory.TypeSafety, "asInstanceOf — ClassCastException at runtime")
  case NullLiteral
      extends BugPattern(BugSeverity.Medium, BugCategory.TypeSafety, "null literal — NullPointerException risk")
  case ReturnInLambda
      extends BugPattern(
        BugSeverity.High,
        BugCategory.TypeSafety,
        "return in lambda — non-local return, deprecated in Scala 3"
      )
  case ThrowInZioSucceed
      extends BugPattern(BugSeverity.High, BugCategory.Effect, "throw inside ZIO.succeed/UIO — unhandled defect")
  case AwaitInfinite
      extends BugPattern(BugSeverity.High, BugCategory.Concurrency, "Await.result with Duration.Inf — blocks forever")
  case ThreadSleepAsync
      extends BugPattern(
        BugSeverity.Medium,
        BugCategory.Concurrency,
        "Thread.sleep in async context — blocks fiber scheduler"
      )
  case SenderInFuture
      extends BugPattern(BugSeverity.High, BugCategory.Concurrency, "sender() inside Future — captures stale ActorRef")
  case FragmentConst
      extends BugPattern(
        BugSeverity.Critical,
        BugCategory.Security,
        "Fragment.const with interpolation — SQL injection"
      )
  case SpliceInterpolation
      extends BugPattern(BugSeverity.Critical, BugCategory.Security, "sql\"...#$...\" — unparameterized SQL splice")
  case ObjectInputStream
      extends BugPattern(
        BugSeverity.Critical,
        BugCategory.Security,
        "ObjectInputStream.readObject — insecure deserialization"
      )
  case JacksonDefaultTyping
      extends BugPattern(
        BugSeverity.Critical,
        BugCategory.Security,
        "enableDefaultTyping — arbitrary class instantiation"
      )
  case HardcodedSecret
      extends BugPattern(
        BugSeverity.Critical,
        BugCategory.Security,
        "hardcoded password/secret/token in string literal"
      )
  case ResourceLeak
      extends BugPattern(
        BugSeverity.Medium,
        BugCategory.Resource,
        "Source.fromFile without Using/close — resource leak"
      )
  case CommandInjection
      extends BugPattern(
        BugSeverity.Critical,
        BugCategory.Security,
        "Runtime.exec/ProcessBuilder with non-literal arg — command injection"
      )
  case PathTraversal
      extends BugPattern(
        BugSeverity.High,
        BugCategory.Security,
        "new File/Paths.get with non-literal arg — path traversal"
      )
  case XSS
      extends BugPattern(BugSeverity.High, BugCategory.Security, "Html() with non-literal arg — cross-site scripting")
  case OpenRedirect
      extends BugPattern(BugSeverity.High, BugCategory.Security, "Redirect with non-literal URL — open redirect")
  case SSRF
      extends BugPattern(
        BugSeverity.High,
        BugCategory.Security,
        "HTTP request with non-literal URL — server-side request forgery"
      )
  // ── Weak cryptography ──
  case WeakHash
      extends BugPattern(
        BugSeverity.High,
        BugCategory.Security,
        "MD5/SHA1 hash — cryptographically broken, use SHA-256+"
      )
  case WeakCipher
      extends BugPattern(BugSeverity.High, BugCategory.Security, "DES/RC4/Blowfish cipher — broken, use AES-256")
  case WeakRandom
      extends BugPattern(BugSeverity.High, BugCategory.Security, "java.util.Random for security — use SecureRandom")
  case HardcodedIV
      extends BugPattern(
        BugSeverity.High,
        BugCategory.Security,
        "hardcoded IV/nonce in crypto — must be random per encryption"
      )
  case ECBMode
      extends BugPattern(
        BugSeverity.High,
        BugCategory.Security,
        "ECB cipher mode — patterns visible in ciphertext, use CBC/GCM"
      )
  case NoPadding
      extends BugPattern(
        BugSeverity.Medium,
        BugCategory.Security,
        "NoPadding with block cipher — potential padding oracle"
      )
  // ── XML/XXE ──
  case XXE
      extends BugPattern(
        BugSeverity.Critical,
        BugCategory.Security,
        "XML parsing without disabling external entities — XXE attack"
      )
  case XPathInjection
      extends BugPattern(BugSeverity.High, BugCategory.Security, "XPath with non-literal expression — XPath injection")
  // ── Logging ──
  case LogInjection
      extends BugPattern(
        BugSeverity.Medium,
        BugCategory.Security,
        "user input in log message — log injection / log forging"
      )
  // ── ZIO / effect-specific ──
  case ZioDie
      extends BugPattern(
        BugSeverity.High,
        BugCategory.Effect,
        "ZIO.die in production code — unrecoverable defect, prefer fail"
      )
  case UnsafeRun
      extends BugPattern(
        BugSeverity.High,
        BugCategory.Effect,
        "Unsafe.unsafe / unsafeRun — breaks referential transparency"
      )
  case BlockingInEffect
      extends BugPattern(BugSeverity.High, BugCategory.Effect, "blocking call inside ZIO/IO — use ZIO.attemptBlocking")
  case IgnoredFuture
      extends BugPattern(BugSeverity.Medium, BugCategory.Effect, "Future result discarded — errors silently swallowed")
  case FutureOnComplete
      extends BugPattern(
        BugSeverity.Medium,
        BugCategory.Effect,
        "Future.onComplete ignoring Failure — silent error swallowing"
      )
  // ── Concurrency extended ──
  case SynchronizedNested
      extends BugPattern(BugSeverity.High, BugCategory.Concurrency, "nested synchronized blocks — potential deadlock")
  case VarInConcurrent
      extends BugPattern(
        BugSeverity.High,
        BugCategory.Concurrency,
        "var field in class with Future/ZIO — race condition risk"
      )
  case MutableShared
      extends BugPattern(
        BugSeverity.Medium,
        BugCategory.Concurrency,
        "mutable collection in object/class field — thread-safety risk"
      )
  // ── Type safety extended ──
  case CollectionLast
      extends BugPattern(BugSeverity.Medium, BugCategory.TypeSafety, ".tail on collection — throws on empty collection")
  case UnsafeGet
      extends BugPattern(BugSeverity.Medium, BugCategory.TypeSafety, "Try.get / Future.value.get — throws on failure")
  case PartialFunction
      extends BugPattern(
        BugSeverity.Medium,
        BugCategory.TypeSafety,
        "match without default case on non-sealed type — MatchError risk"
      )
  // ── Resource management ──
  case ConnectionLeak
      extends BugPattern(
        BugSeverity.High,
        BugCategory.Resource,
        "Connection/Statement opened without close — connection leak"
      )
  case StreamNotClosed
      extends BugPattern(
        BugSeverity.Medium,
        BugCategory.Resource,
        "InputStream/OutputStream without close — resource leak"
      )
  // ── Regex ──
  case RegexDoS
      extends BugPattern(
        BugSeverity.High,
        BugCategory.Security,
        "Regex compiled from user input — ReDoS (regex denial of service)"
      )
  case LDAPInjection
      extends BugPattern(BugSeverity.High, BugCategory.Security, "LDAP query with non-literal filter — LDAP injection")
  // ── Credential value detection ──
  case HardcodedCredential
      extends BugPattern(
        BugSeverity.Critical,
        BugCategory.Security,
        "hardcoded credential detected in string literal (regex match)"
      )
}

case class BugFinding(
    file: Path,
    line: Int,
    pattern: BugPattern,
    contextLine: String,
    enclosingSymbol: String,
    message: String,
    taintFlow: Option[TaintFlow] = None,
    reachableFrom: Option[List[String]] = None,
    callDepth: Option[Int] = None
)

case class TaintSource(name: String, description: String, file: Path, line: Int)
case class TaintStep(varName: String, file: Path, line: Int, operation: String)
case class TaintFlow(source: TaintSource, steps: List[TaintStep], confidence: Double)

case class HotspotInfo(
    file: String,
    findingCount: Int,
    churnScore: Int,
    complexityScore: Int,
    riskScore: Double
)

// ── Suppression memories ──────────────────────────────────────────────────

case class SuppressionMemory(
    patternName: String,
    scope: MemoryScope,
    reason: String,
    source: MemorySource,
    suppressionType: SuppressionType,
    contextLine: String,
    createdAt: Long
)

enum MemoryScope {
  case Global(patternName: String)
  case FileScoped(patternName: String, filePattern: String)
  case MethodScoped(patternName: String, filePattern: String, methodName: String)
}

enum MemorySource {
  case LlmTriage(skillRun: String)
  case UserManual
  case CommunitySubmit(issueNumber: Int)
}

enum SuppressionType {
  case Ignore, Lower, Review
}
