package leaderboard.search.gen2.contract

import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*
import scala.util.Using

/** Small compiler-invisible firewall for the current Gen2 module constitution.
  *
  * The Scala compiler and the sbt project graph already enforce most coupling. These checks cover
  * the handful of properties that compilation cannot see: generic main-source neutrality, root
  * aggregate test coverage, eval classpath isolation, SQL confinement, and BeautyQ production
  * package ownership.
  */
final class SearchGen2ModuleFirewallSpec extends AnyWordSpec {

  private val genericModuleDirs: List[String] =
    List("search-gen2-contract", "search-gen2-core", "search-gen2-transport", "search-gen2-elasticsearch", "search-gen2-qdrant", "search-gen2-eval")

  private val beautyqGen2ModuleDirs: List[String] =
    List("beautyq-search-gen2-contract", "beautyq-search-gen2-materialization", "beautyq-search-gen2-wiring", "beautyq-search-gen2-eval")

  private val servingModuleSbtIds: List[String] =
    List("searchGen2Contract", "searchGen2Core", "searchGen2Transport", "searchGen2Elasticsearch", "searchGen2Qdrant", "searchGen2Eval", "beautyqSearchGen2Contract", "beautyqSearchGen2Materialization", "beautyqSearchGen2Wiring")

  private val gen2AggregateSbtIds: List[String] = servingModuleSbtIds :+ "beautyqSearchGen2Eval"

  private val retainedSharedAggregateSbtIds: List[String] =
    List("repoCore", "beautyqModel", "beautyqSearchRepositories")

  private val beautyqEvalSbtId = "beautyqSearchGen2Eval"

  private val beautyqEvalReferencePattern =
    ("""(?<![A-Za-z0-9_])""" + java.util.regex.Pattern.quote(beautyqEvalSbtId) + """(?![A-Za-z0-9_])""").r

  private val SqlPackagePrefix = "leaderboard.sql"

  // Small fail-closed ownership detector for the `leaderboard.sql` package. It recognizes two
  // families - a direct `leaderboard.sql` selection (ordinary or backticked) and a grouped
  // `leaderboard.{ ... sql ... }` selection - with tolerant whitespace. It does not model Scala's
  // full import grammar: grouped, backticked, wildcard and aliased spellings are always non-exact.
  private val DirectSqlAccessPattern =
    """(?<![A-Za-z0-9_])leaderboard\s*\.\s*(?:`sql`|sql)(?![A-Za-z0-9_])""".r

  private val GroupedSqlAccessPattern =
    """(?<![A-Za-z0-9_])leaderboard\s*\.\s*\{[^{}]*?(?:`sql`|\bsql\b)[^{}]*?\}""".r

  // The one accepted carve-out: the plain direct `leaderboard.sql.SQL` type, not a deeper path and
  // not a renamed import of it.
  private val AcceptedSqlFormPattern =
    """(?<![A-Za-z0-9_])leaderboard\s*\.\s*sql\s*\.\s*SQL(?![A-Za-z0-9_.])(?!\s+(?:as|=>)(?![A-Za-z0-9_]))""".r

  "Gen2 generic main-source neutrality" should {
    "keep every generic search-gen2 main source free of BeautyQ-specific names" in {
      val violations = genericModuleDirs.flatMap { moduleDir =>
        scalaFilesUnder(s"$moduleDir/src/main/scala").flatMap { path =>
          if (stripComments(read(path)).toLowerCase.contains("beautyq"))
            List(s"${relative(path)}: generic main source references BeautyQ")
          else Nil
        }
      }
      assertNoViolations("Gen2 generic main-source neutrality violations", violations)
    }
  }

  "Gen2 root aggregate coverage" should {
    "keep every retained Gen2 and shared project in the root aggregate" in {
      val aggregate = stripComments(buildBlock("`distage-example`"))
      val violations = (gen2AggregateSbtIds ++ retainedSharedAggregateSbtIds).filterNot(aggregate.contains).map { id =>
        s"$id is missing from the `distage-example` aggregate"
      }
      assertNoViolations("Gen2 aggregate membership violations", violations)
    }
  }

  "Gen2 eval isolation" should {
    "keep every serving Gen2 project free of a BeautyQ eval build dependency" in {
      val violations = servingModuleSbtIds.filter(id => stripComments(buildBlock(id)).contains(beautyqEvalSbtId)).map { id =>
        s"$id: build.sbt block unexpectedly depends on $beautyqEvalSbtId"
      }
      assertNoViolations("Gen2 eval build-dependency violations", violations)
    }

    // The compiler cannot see this boundary: a compile/unscoped eval edge added next to the accepted
    // test edge, or a second eval edge, would still compile. This is a deliberately special-purpose
    // negative check - not a generic dependsOn parser - so it counts the single reference and pins its
    // immediate scope mapping.
    "keep leaderboard-app-shell's BeautyQ eval dependency to exactly one test-scope edge" in {
      val block = stripComments(buildBlock("`leaderboard-app-shell`"))
      val matches = beautyqEvalReferencePattern.findAllMatchIn(block).toList

      val countViolation =
        if (matches.size == 1) Nil
        else List(s"leaderboard-app-shell must reference $beautyqEvalSbtId exactly once, found ${matches.size}")

      val scopeViolation = matches match {
        case single :: Nil =>
          if (testScopeMappingAt(block, single.end)) Nil
          else List(s"leaderboard-app-shell's single $beautyqEvalSbtId edge must be exactly % \"test->test\"")
        case _ => Nil
      }

      assertNoViolations("leaderboard-app-shell eval firewall violations", countViolation ++ scopeViolation)
    }
  }

  "Gen2 SQL confinement" should {
    "allow direct leaderboard.sql.SQL usage only in BeautyQ materialization" in {
      val (materialization, others) = (genericModuleDirs ++ beautyqGen2ModuleDirs).partition(_ == "beautyq-search-gen2-materialization")

      val outsideViolations = others.flatMap { moduleDir =>
        scalaFilesUnder(s"$moduleDir/src/main/scala").flatMap { path =>
          sqlConfinementViolations(relative(path), read(path), allowExactSql = false)
        }
      }
      assertNoViolations("Gen2 SQL confinement violations", outsideViolations)

      val materializationViolations = materialization.flatMap { moduleDir =>
        scalaFilesUnder(s"$moduleDir/src/main/scala").flatMap { path =>
          sqlConfinementViolations(relative(path), read(path), allowExactSql = true)
        }
      }
      assertNoViolations("BeautyQ materialization SQL confinement violations", materializationViolations)

      val materializationUsesExactSql = materialization.exists { moduleDir =>
        scalaFilesUnder(s"$moduleDir/src/main/scala").exists(path => containsExactSqlReference(read(path)))
      }
      assert(materializationUsesExactSql, "BeautyQ materialization is expected to own the one direct leaderboard.sql.SQL usage")
    }
  }

  "BeautyQ Gen2 production package ownership" should {
    "keep BeautyQ production classes out of the generic leaderboard.search.gen2 namespace" in {
      val violations = beautyqGen2ModuleDirs.flatMap { moduleDir =>
        scalaFilesUnder(s"$moduleDir/src/main/scala").flatMap { path =>
          packageDeclarationsOf(read(path)).collect {
            case pkg if pkg == "leaderboard.search.gen2" || pkg.startsWith("leaderboard.search.gen2.") =>
              s"${relative(path)}: BeautyQ production source declares generic package '$pkg'"
          }
        }
      }
      assertNoViolations("BeautyQ Gen2 production package ownership violations", violations)
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

  private def packageDeclarationsOf(content: String): List[String] =
    content.linesIterator
      .map(_.trim)
      .collect {
        case line if line.startsWith("package ") =>
          line
            .stripPrefix("package ")
            .stripSuffix("{")
            .trim
      }
      .toList

  private def buildBlock(projectId: String): String = {
    val lines      = read(repoRoot.resolve("build.sbt")).linesIterator.toList
    val startIndex = lines.indexWhere(_.trim.startsWith(s"lazy val $projectId "))

    if (startIndex < 0) {
      fail(s"Could not find build.sbt block for lazy val $projectId")
    }

    val remaining = lines.drop(startIndex + 1)
    val nextIndex = remaining.indexWhere(_.trim.startsWith("lazy val "))
    val blockLines =
      if (nextIndex < 0) lines.drop(startIndex)
      else lines.slice(startIndex, startIndex + 1 + nextIndex)

    blockLines.mkString("\n")
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

  // The accepted mapping is exactly `% "test->test"` immediately after the single reference; any other
  // scope, an unscoped reference, or trailing `.`/member access is not accepted.
  private def testScopeMappingAt(text: String, from: Int): Boolean =
    """^\s*%\s*"test->test"""".r.findPrefixOf(text.substring(from)).isDefined

  private def sqlAccesses(text: String): List[(String, Boolean)] = {
    val acceptedStarts = AcceptedSqlFormPattern.findAllMatchIn(text).map(_.start).toSet
    val direct = DirectSqlAccessPattern.findAllMatchIn(text).map(m => m.matched -> acceptedStarts.contains(m.start))
    val grouped = GroupedSqlAccessPattern.findAllMatchIn(text).map(m => m.matched -> false)
    (direct ++ grouped).toList
  }

  private def containsExactSqlReference(content: String): Boolean =
    sqlAccesses(stripComments(content)).exists(_._2)

  private def sqlConfinementViolations(displayPath: String, content: String, allowExactSql: Boolean): List[String] = {
    val text = stripComments(content)
    sqlAccesses(text).flatMap {
      case (_, true) if allowExactSql => Nil
      case (matched, _) =>
        val expected = if (allowExactSql) s"$SqlPackagePrefix.SQL" else "no direct leaderboard.sql usage"
        List(s"$displayPath: expected $expected, found '$matched'")
    }
  }

  private def assertNoViolations(context: String, violations: List[String]): Unit =
    if (violations.nonEmpty) {
      fail((context :: violations.sorted).mkString("\n"))
    }
}
