package leaderboard.search.beautyq.gen2.materialization

import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*
import scala.util.Using

/** Compiler-invisible guard that BeautyQ materialization imports the shared generic
  * materialization-identity types from `search-gen2-core` instead of declaring a competing local
  * `class`/`trait`/`object`/`type` of the same name.
  */
final class BeautyQGen2MaterializationFirewallSpec extends AnyWordSpec {
  private val moduleDir = "beautyq-search-gen2-materialization"

  private val movedGenericIdentityTypeNames: List[String] = List(
    "ContentFingerprint",
    "ProjectedDocumentsFingerprint",
    "ProjectionFormatVersion",
    "VersionedSnapshot",
    "SearchSnapshotSource",
  )

  "BeautyQ Gen2 materialization generic-identity redefinition firewall" should {
    "reject a local redefinition of any moved generic snapshot/fingerprint identity type" in {
      val violations = scalaFilesUnder(s"$moduleDir/src/main/scala").flatMap { path =>
        val content = stripComments(read(path))
        movedGenericIdentityTypeNames.filter(name => containsDeclarationOf(content, name)).map {
          name => s"${relative(path)}: local redefinition of moved generic identity type: $name"
        }
      }

      assertNoViolations("BeautyQ Gen2 materialization generic-identity redefinition violations", violations)
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
        .resource(Files.walk(root)) { stream =>
          stream.iterator.asScala.toList.filter { path =>
            val normalized = path.toString.replace('\\', '/')
            Files.isRegularFile(path) && normalized.endsWith(".scala") && !normalized.contains("/target/")
          }
        }
        .sortBy(_.toString)
    }
  }

  private def stripComments(content: String): String = {
    val withoutBlockComments = content.replaceAll("(?s)/\\*.*?\\*/", "")
    withoutBlockComments.linesIterator
      .map { line =>
        line.indexOf("//") match {
          case -1    => line
          case index => line.substring(0, index)
        }
      }
      .mkString("\n")
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
    val pattern  = s"""(?m)^\\s*($modifier)*(opaque\\s+)?(class|trait|object|type)\\s+${java.util.regex.Pattern.quote(name)}(?![A-Za-z0-9_])""".r
    pattern.findFirstIn(content).isDefined
  }

  private def assertNoViolations(context: String, violations: List[String]): Unit =
    if (violations.nonEmpty) {
      fail((context :: violations.sorted).mkString("\n"))
    }
}
