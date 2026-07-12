package leaderboard.search.beautyq.gen2.materialization

import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*
import scala.util.Using

/** Diagnostic firewall over `beautyq-search-gen2-materialization/src`: rejects any forbidden Gen1
  * package prefix or forbidden Gen1 symbol name occurring anywhere in a source file - as an import, or
  * as an inline fully-qualified reference - and proves the historical `ModuleMarker.scala` placeholder
  * was removed. The app-shell integration test is explicitly allowed to use seed helpers
  * (`BeautyQSeedLoader`/`BeautyQSeedData`); this module is not, since it must build its own snapshot
  * independently of Gen1 seed/materialization code.
  */
final class BeautyQGen2MaterializationFirewallSpec extends AnyWordSpec {
  private val moduleDir = "beautyq-search-gen2-materialization"

  private val forbiddenPackagePrefixes: List[String] = List(
    "leaderboard.search.document",
    "leaderboard.search.dsl",
    "leaderboard.search.beautyq.contract",
    "leaderboard.repo.BeautyQCatalogGraph",
  )

  private val forbiddenSymbols: List[String] = List(
    "BeautyQSearchCatalogSnapshot",
    "BeautyQVariantSearchDocumentMaterialization",
    "VariantSearchDocument",
    "SearchGeoPoint",
    "BeautyQSeedData",
    "BeautyQSeedLoader",
  )

  private val thisSpecPath =
    s"$moduleDir/src/test/scala/leaderboard/search/beautyq/gen2/materialization/BeautyQGen2MaterializationFirewallSpec.scala"

  // Commit 3D moved these identity/orchestration types to the reusable search-gen2-core kernel; BeautyQ
  // main sources must only import and use them, never declare a competing local `class`/`trait`/`object`
  // of the same name.
  private val movedGenericIdentityTypeNames: List[String] = List(
    "ContentFingerprint",
    "SourceRevision",
    "ProjectedDocumentsFingerprint",
    "ProjectionFormatVersion",
    "VersionedSnapshot",
    "SearchSnapshotSource",
  )

  "BeautyQ Gen2 materialization firewall" should {
    "keep every main and test source free of forbidden Gen1 package prefixes and symbols" in {
      // Excludes this spec's own file: it legitimately names every forbidden token as data (the lists
      // above, and the diagnostic scanner's example strings below), which is not a compiled reference.
      val files = (scalaFilesUnder(s"$moduleDir/src/main/scala") ++ scalaFilesUnder(s"$moduleDir/src/test/scala"))
        .filterNot(path => relative(path) == thisSpecPath)

      val violations = files.flatMap {
        path =>
          val content = stripComments(read(path))
          val prefixViolations = forbiddenPackagePrefixes.filter(containsForbiddenToken(content, _)).map {
            token => s"${relative(path)}: forbidden package reference: $token"
          }
          val symbolViolations = forbiddenSymbols.filter(containsForbiddenToken(content, _)).map {
            token => s"${relative(path)}: forbidden symbol reference: $token"
          }
          prefixViolations ++ symbolViolations
      }

      assertNoViolations("BeautyQ Gen2 materialization firewall violations", violations)
    }

    "no longer contain the historical ModuleMarker.scala placeholder" in {
      val markerPath = repoRoot.resolve(s"$moduleDir/src/main/scala/leaderboard/search/beautyq/gen2/materialization/ModuleMarker.scala")
      assert(!Files.exists(markerPath), s"$markerPath must not exist - Brick 3 removed the placeholder marker")
    }
  }

  "BeautyQ Gen2 materialization generic-identity redefinition firewall" should {
    "reject a local redefinition of any moved generic snapshot/fingerprint identity type" in {
      val files = scalaFilesUnder(s"$moduleDir/src/main/scala")

      val violations = files.flatMap {
        path =>
          val content = stripComments(read(path))
          movedGenericIdentityTypeNames.filter(name => containsDeclarationOf(content, name)).map {
            name => s"${relative(path)}: local redefinition of moved generic identity type: $name"
          }
      }

      assertNoViolations("BeautyQ Gen2 materialization generic-identity redefinition violations", violations)
    }
  }

  "BeautyQ Gen2 materialization firewall diagnostic scanner" should {
    "reject an obvious forbidden import and an inline fully-qualified reference, and accept unrelated source" in {
      assert(containsForbiddenToken("import leaderboard.search.dsl.SearchField", "leaderboard.search.dsl"))
      assert(containsForbiddenToken("val x = leaderboard.repo.BeautyQCatalogGraph.Nodes", "leaderboard.repo.BeautyQCatalogGraph"))
      assert(containsForbiddenToken("val doc: VariantSearchDocument = ???", "VariantSearchDocument"))
      assert(!containsForbiddenToken("val doc: VariantSearchDocumentGen2 = ???", "VariantSearchDocument"))
      assert(!containsForbiddenToken("import leaderboard.search.dslish.SomethingElse", "leaderboard.search.dsl"))
      assert(!containsForbiddenToken("import leaderboard.search.gen2.contract.SearchField", "leaderboard.search.dsl"))
    }
  }

  "BeautyQ Gen2 materialization generic-identity redefinition scanner" should {
    "reject an obvious local declaration and accept ordinary imports/usages of the same name" in {
      assert(containsDeclarationOf("final case class ContentFingerprint(value: String)", "ContentFingerprint"))
      assert(containsDeclarationOf("case class VersionedSnapshot[A](value: A)", "VersionedSnapshot"))
      assert(containsDeclarationOf("trait SearchSnapshotSource[F[_, _], LoadError, Snapshot] {", "SearchSnapshotSource"))
      assert(containsDeclarationOf("  private object ProjectionFormatVersion {", "ProjectionFormatVersion"))
      assert(containsDeclarationOf("type ContentFingerprint = String", "ContentFingerprint"))
      assert(containsDeclarationOf("private opaque type VersionedSnapshot[A] = (A, String)", "VersionedSnapshot"))
      assert(!containsDeclarationOf("import leaderboard.search.gen2.core.materialization.ContentFingerprint", "ContentFingerprint"))
      assert(!containsDeclarationOf("val fingerprint: ContentFingerprint = x.contentFingerprint", "ContentFingerprint"))
      assert(!containsDeclarationOf("source: SearchSnapshotSource[F, SnapshotLoadError, BeautyQSearchSnapshot]", "SearchSnapshotSource"))
      assert(!containsDeclarationOf("def compute(): ContentFingerprint = ContentFingerprint(sha256Hex(x))", "ContentFingerprint"))
    }
  }

  private def repoRoot: Path = {
    @tailrec
    def loop(path: Path): Path =
      if (Files.isRegularFile(path.resolve("build.sbt"))) {
        path
      } else {
        path.getParent match {
          case parent: Path => loop(parent)
          case null         => fail(s"Could not find repo root from ${Paths.get("").toAbsolutePath}; expected build.sbt")
        }
      }

    loop(Paths.get("").toAbsolutePath)
  }

  private def relative(path: Path): String =
    repoRoot.relativize(path).toString.replace('\\', '/')

  private def read(path: Path): String =
    Files.readString(path, StandardCharsets.UTF_8)

  private def scalaFilesUnder(relativeDir: String): List[Path] = {
    val root = repoRoot.resolve(relativeDir)
    if (!Files.isDirectory(root)) {
      Nil
    } else {
      Using
        .resource(Files.walk(root)) {
          stream =>
            stream.iterator.asScala.toList.filter {
              path =>
                val normalized = path.toString.replace('\\', '/')
                Files.isRegularFile(path) && normalized.endsWith(".scala") && !normalized.contains("/target/")
            }
        }
        .sortBy(_.toString)
    }
  }

  private def isIdentifierChar(ch: Char): Boolean = ch.isLetterOrDigit || ch == '_'

  // A firewall polices compiled references, not prose: strips /** ... */ and /* ... */ block comments
  // (non-nested; sufficient for this codebase's style) and then // line comments, so a scaladoc
  // attribution such as "reimplements Gen1's `BeautyQVariantSearchDocumentMaterialization`, evidence
  // only" does not trip the scanner the way an actual import or code reference would.
  private def stripComments(content: String): String = {
    val withoutBlockComments = content.replaceAll("(?s)/\\*.*?\\*/", "")
    withoutBlockComments.linesIterator
      .map {
        line =>
          line.indexOf("//") match {
            case -1    => line
            case index => line.substring(0, index)
          }
      }
      .mkString("\n")
  }

  // A word-boundary-safe substring search: `token` must not be immediately preceded or followed by an
  // identifier character, so a forbidden package prefix still matches a longer qualified reference that
  // continues with '.' (e.g. "leaderboard.repo.BeautyQCatalogGraph.Nodes"), but never matches an
  // unrelated longer identifier that merely starts with the same characters (e.g. "...dslish").
  private def containsForbiddenToken(content: String, token: String): Boolean = {
    @tailrec
    def loop(fromIndex: Int): Boolean = {
      val index = content.indexOf(token, fromIndex)
      if (index < 0) {
        false
      } else {
        val beforeOk = index == 0 || !isIdentifierChar(content.charAt(index - 1))
        val afterIndex = index + token.length
        val afterOk = afterIndex >= content.length || !isIdentifierChar(content.charAt(afterIndex))

        if (beforeOk && afterOk) true else loop(index + 1)
      }
    }

    loop(0)
  }

  // Detects a local `class`/`trait`/`object`/`type`/`opaque type` declaration of exactly `name`, not an
  // import or an ordinary use of the type - anchored at the start of a (trimmed) line, tolerating the
  // usual `final`/`sealed`/`abstract`/`case`/`private`/`private[scope]`/`protected` modifiers and an
  // optional `opaque` immediately before the declaration keyword (so both a competing case class/trait
  // and a competing type alias or opaque type of the same name are caught), and requiring a
  // non-identifier character (or end of input) immediately after `name` so a longer identifier that
  // merely starts with it (e.g. `ContentFingerprintV2`) does not match.
  private def containsDeclarationOf(content: String, name: String): Boolean = {
    val modifier = """(final|sealed|abstract|case|private(?:\[[^\]]*\])?|protected(?:\[[^\]]*\])?)\s+"""
    val pattern = s"""(?m)^\\s*($modifier)*(opaque\\s+)?(class|trait|object|type)\\s+${java.util.regex.Pattern.quote(name)}(?![A-Za-z0-9_])""".r
    pattern.findFirstIn(content).isDefined
  }

  private def assertNoViolations(context: String, violations: List[String]): Unit =
    if (violations.nonEmpty) {
      fail((context :: violations.sorted).mkString("\n"))
    }
}
