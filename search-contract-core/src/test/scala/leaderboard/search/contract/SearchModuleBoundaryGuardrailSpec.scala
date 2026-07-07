package leaderboard.search.contract

import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*
import scala.util.Using

final class SearchModuleBoundaryGuardrailSpec extends AnyWordSpec {

  private val contractForbiddenImports = List(
    "leaderboard.search.elasticsearch",
    "leaderboard.search.qdrant",
    "leaderboard.api",
    "leaderboard.http",
    "leaderboard.plugins",
    "leaderboard.repo.",
    "BeautyQSearchCatalogSnapshot",
    "BeautyQSearchCatalogSnapshotLoader",
    "BeautyQVariantSearchDocumentMaterialization",
    "SearchDocumentProjection",
    "BeautyQCatalogGraph",
    "HttpApi",
    "Tapir",
    "ModuleDef",
    "Lifecycle",
    "leaderboard.config",
    "leaderboard.sql",
    "leaderboard.services",
    "leaderboard.search.hybrid",
    "leaderboard.search.startup",
    "leaderboard.search.embedding",
    "leaderboard.search.inmemory",
    "leaderboard.search.parser",
    "leaderboard.search.interpreter",
    "leaderboard.search.semantic",
    "doobie",
    "http4s",
    "tapir",
    "zio",
    "cats.effect",
    "izumi.functional.bio",
    "ElasticsearchClient",
    "QdrantClient",
  )

  private val contractAllowedImports = List(
    "leaderboard.repo.catalog",
  )

  private val genericBackendForbiddenImports = List(
    "leaderboard.repo",
    "leaderboard.api",
    "leaderboard.http",
    "leaderboard.plugins",
    "leaderboard.search.beautyq",
  )

  private val genericBackendForbiddenSourceText = List(
    "BeautyQ",
    "beautyq",
  )

  private val appHttpForbiddenImports = List(
    "leaderboard.repo.BeautyQCatalogGraph",
    "BeautyQVariantSearchDocumentMaterialization",
    "BeautyQSearchCatalogSnapshot",
    "BeautyQSearchCatalogSnapshotLoader",
    "SearchDocumentProjection",
    "leaderboard.search.qdrant",
    "leaderboard.search.hybrid",
    "leaderboard.search.elasticsearch",
  )

  private val appHttpAllowedImports = List(
    "leaderboard.search.elasticsearch.ElasticsearchStartupReadinessStatusResponse",
    "leaderboard.search.elasticsearch.ElasticsearchStartupReadinessTransition",
  )

  private val searchContractCoreForbiddenImports = List(
    "leaderboard.model",
    "leaderboard.repo",
    "leaderboard.api",
    "leaderboard.http",
    "leaderboard.plugins",
    "leaderboard.sql",
    "leaderboard.config",
    "leaderboard.search.beautyq",
    "leaderboard.search.elasticsearch",
    "leaderboard.search.qdrant",
    "leaderboard.search.hybrid",
    "leaderboard.search.startup",
    "leaderboard.search.embedding",
    "beautyq",
    "BeautyQ",
    "ModuleDef",
    "Lifecycle",
    "Tapir",
    "HttpApi",
    "doobie",
    "http4s",
  )

  "BeautyQ search module boundaries" should {
    "keep beautyq-search-contract main sources free of runtime, app, and materialization imports" in {
      val violations = scalaMainFiles("beautyq-search-contract/src/main/scala")
        .flatMap(path => containsForbiddenImport(path, contractForbiddenImports, contractAllowedImports))

      assertNoViolations("beautyq-search-contract boundary violations", violations)
    }

    "keep search-elasticsearch main sources generic" in {
      val violations = genericBackendViolations("search-elasticsearch/src/main/scala")
      assertNoViolations("search-elasticsearch boundary violations", violations)
    }

    "keep search-qdrant main sources generic" in {
      val violations = genericBackendViolations("search-qdrant/src/main/scala")
      assertNoViolations("search-qdrant boundary violations", violations)
    }

    "keep app-http main sources free of materialization, repo internals, and backend semantics except ES lifecycle DTOs" in {
      val violations = scalaMainFiles("app-http/src/main/scala")
        .flatMap(path => containsForbiddenImport(path, appHttpForbiddenImports, appHttpAllowedImports))

      assertNoViolations("app-http boundary violations", violations)
    }

    "keep search-contract-core main sources generic and free of domain, app, runtime, and resource imports" in {
      val violations = scalaMainFiles("search-contract-core/src/main/scala")
        .flatMap(path => containsForbiddenImport(path, searchContractCoreForbiddenImports, allowedPatterns = Nil))

      assertNoViolations("search-contract-core boundary violations", violations)
    }

    "keep build.sbt in the closeout split DAG shape" in {
      assertBlockContains("`search-elasticsearch`", ".dependsOn(`leaderboard-core`, `search-core`, searchContractCore)")
      assertBlockContains("`search-qdrant`", ".dependsOn(`leaderboard-core`, `search-core`, searchContractCore)")
      assertBlockContains("beautyqSearchContract", ".dependsOn(searchContractCore, beautyqModel, repoCore, `search-core`)")
      assertBlockContains("beautyqSearchRepositories", ".dependsOn(`leaderboard-core`, repoCore, beautyqModel)")
      assertBlockContains("beautyqSearchMaterialization", ".dependsOn(beautyqSearchContract, beautyqSearchRepositories, repoCore, beautyqModel)")
      assertBlockContains("beautyqSearchWiring", ".dependsOn(beautyqSearchContract, beautyqSearchMaterialization, `search-elasticsearch`, `search-qdrant`)")
      assertBlockContains("appServices", ".dependsOn(beautyqSearchRepositories)")
      assertBlockContains("appHttp", ".dependsOn(beautyqSearchWiring, appServices)")
      assertBlockContains("`bifunctor-tagless`", ".dependsOn(beautyqSearchWiring, appHttp, appServices)")
    }

    "prove forbidden imports fail and allowed exceptions pass" in {
      val forbiddenContractImport = importLineViolations(
        displayPath       = "synthetic/Contract.scala",
        lines             = List("import leaderboard.search.qdrant.QdrantClient"),
        forbiddenPatterns = contractForbiddenImports,
        allowedPatterns   = contractAllowedImports,
      )
      assert(forbiddenContractImport.nonEmpty)

      val forbiddenContractRepoImport = importLineViolations(
        displayPath       = "synthetic/Contract.scala",
        lines             = List("import leaderboard.repo.Categories"),
        forbiddenPatterns = contractForbiddenImports,
        allowedPatterns   = contractAllowedImports,
      )
      assert(forbiddenContractRepoImport.nonEmpty)

      val allowedContractImport = importLineViolations(
        displayPath       = "synthetic/Contract.scala",
        lines             = List("import leaderboard.repo.catalog"),
        forbiddenPatterns = contractForbiddenImports,
        allowedPatterns   = contractAllowedImports,
      )
      assert(allowedContractImport.isEmpty)

      val forbiddenContractConfigImport = importLineViolations(
        displayPath       = "synthetic/Contract.scala",
        lines             = List("import leaderboard.config.ElasticsearchPortCfg"),
        forbiddenPatterns = contractForbiddenImports,
        allowedPatterns   = contractAllowedImports,
      )
      assert(forbiddenContractConfigImport.nonEmpty)

      val forbiddenContractSqlImport = importLineViolations(
        displayPath       = "synthetic/Contract.scala",
        lines             = List("import leaderboard.sql.SQL"),
        forbiddenPatterns = contractForbiddenImports,
        allowedPatterns   = contractAllowedImports,
      )
      assert(forbiddenContractSqlImport.nonEmpty)

      val forbiddenContractHybridImport = importLineViolations(
        displayPath       = "synthetic/Contract.scala",
        lines             = List("import leaderboard.search.hybrid.BeautyQNonProductionHybridRunnerRealClientInputs"),
        forbiddenPatterns = contractForbiddenImports,
        allowedPatterns   = contractAllowedImports,
      )
      assert(forbiddenContractHybridImport.nonEmpty)

      val forbiddenContractDoobieImport = importLineViolations(
        displayPath       = "synthetic/Contract.scala",
        lines             = List("import doobie.ConnectionIO"),
        forbiddenPatterns = contractForbiddenImports,
        allowedPatterns   = contractAllowedImports,
      )
      assert(forbiddenContractDoobieImport.nonEmpty)

      val forbiddenContractZioImport = importLineViolations(
        displayPath       = "synthetic/Contract.scala",
        lines             = List("import zio.Task"),
        forbiddenPatterns = contractForbiddenImports,
        allowedPatterns   = contractAllowedImports,
      )
      assert(forbiddenContractZioImport.nonEmpty)

      val forbiddenAppHttpImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import leaderboard.search.qdrant.QdrantClient"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(forbiddenAppHttpImport.nonEmpty)

      val allowedDirectAppHttpImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import leaderboard.search.elasticsearch.ElasticsearchStartupReadinessStatusResponse"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(allowedDirectAppHttpImport.isEmpty)

      val spoofedAppHttpImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import leaderboard.search.elasticsearch.ElasticsearchStartupReadinessStatusResponseExtra"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(spoofedAppHttpImport.nonEmpty)

      val allowedAppHttpImports = importLineViolations(
        displayPath = "synthetic/AppHttp.scala",
        lines = List(
          "import leaderboard.search.elasticsearch.ElasticsearchStartupReadinessStatusResponse",
          "import leaderboard.search.elasticsearch.ElasticsearchStartupReadinessTransition",
        ),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(allowedAppHttpImports.isEmpty)

      val allowedGroupedAppHttpImport = importLineViolations(
        displayPath = "synthetic/AppHttp.scala",
        lines = List(
          "import leaderboard.search.elasticsearch.{ElasticsearchStartupReadinessStatusResponse, ElasticsearchStartupReadinessTransition}",
        ),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(allowedGroupedAppHttpImport.isEmpty)

      val mixedGroupedAppHttpImport = importLineViolations(
        displayPath = "synthetic/AppHttp.scala",
        lines = List(
          "import leaderboard.search.elasticsearch.{ElasticsearchStartupReadinessStatusResponse, ElasticsearchClient}",
        ),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(mixedGroupedAppHttpImport.nonEmpty)

      val forbiddenSearchContractCoreBeautyQImport = importLineViolations(
        displayPath       = "synthetic/SearchContractCore.scala",
        lines             = List("import leaderboard.search.beautyq.contract.BeautyQSearchDomainContract"),
        forbiddenPatterns = searchContractCoreForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenSearchContractCoreBeautyQImport.nonEmpty)

      val forbiddenSearchContractCoreRepoImport = importLineViolations(
        displayPath       = "synthetic/SearchContractCore.scala",
        lines             = List("import leaderboard.repo.Categories"),
        forbiddenPatterns = searchContractCoreForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenSearchContractCoreRepoImport.nonEmpty)
    }
  }

  private def repoRoot: Path = {
    def loop(path: Path): Path = {
      val hasBuild = Files.isRegularFile(path.resolve("build.sbt"))
      val hasPlan  = Files.isRegularFile(path.resolve("docs/search/BEAUTYQ_SEARCH_CONTRACT_MODULE_SPLIT_PLAN.md"))

      if (hasBuild && hasPlan) {
        path
      } else {
        Option(path.getParent) match {
          case Some(parent) => loop(parent)
          case None =>
            fail(s"Could not find repo root from ${Paths.get("").toAbsolutePath}; expected build.sbt and docs/search/BEAUTYQ_SEARCH_CONTRACT_MODULE_SPLIT_PLAN.md")
        }
      }
    }

    loop(Paths.get("").toAbsolutePath)
  }

  private def scalaMainFiles(relativeDir: String): List[Path] = {
    val root = repoRoot.resolve(relativeDir)
    if (!Files.isDirectory(root)) {
      fail(s"Expected Scala source directory to exist: $relativeDir")
    }

    Using.resource(Files.walk(root)) { stream =>
      stream.iterator.asScala.toList.filter { path =>
        val normalized = path.toString.replace('\\', '/')
        Files.isRegularFile(path) && normalized.endsWith(".scala") && !normalized.contains("/target/")
      }
    }
  }

  private def read(path: Path): String =
    Files.readString(path, StandardCharsets.UTF_8)

  private def importLines(path: Path): List[String] =
    read(path).linesIterator.map(_.trim).filter(_.startsWith("import ")).toList

  private def containsForbiddenImport(path: Path, forbiddenPatterns: List[String], allowedPatterns: List[String]): List[String] =
    importLineViolations(relative(path), importLines(path), forbiddenPatterns, allowedPatterns)

  private def importLineViolations(
    displayPath: String,
    lines: List[String],
    forbiddenPatterns: List[String],
    allowedPatterns: List[String],
  ): List[String] =
    lines.filter { line =>
      forbiddenPatterns.exists(line.contains) && !isAllowedImportLine(line, allowedPatterns)
    }.map(line => s"$displayPath: forbidden import: $line")

  private def isAllowedImportLine(line: String, allowedPatterns: List[String]): Boolean =
    expandedImportTargets(line).exists { targets =>
      targets.nonEmpty && targets.forall(allowedPatterns.contains)
    }

  private def expandedImportTargets(line: String): Option[List[String]] = {
    val importTarget = line.stripPrefix("import ").trim
    val braceStart   = importTarget.indexOf(".{")
    val braceEnd     = importTarget.lastIndexOf("}")

    if (braceStart < 0 || braceEnd < braceStart) {
      Some(List(importTarget))
    } else {
      val prefix = importTarget.substring(0, braceStart)
      val names  = importTarget.substring(braceStart + 2, braceEnd).split(",").toList.map(_.trim).filter(_.nonEmpty)
      Some(names.map(name => s"$prefix.$name"))
    }
  }

  private def relative(path: Path): String =
    repoRoot.relativize(path).toString.replace('\\', '/')

  private def buildBlock(projectId: String): String = {
    val lines      = read(repoRoot.resolve("build.sbt")).linesIterator.toList
    val startIndex = lines.indexWhere(_.trim.startsWith(s"lazy val $projectId "))

    if (startIndex < 0) {
      fail(s"Could not find build.sbt block for lazy val $projectId")
    }

    val remaining = lines.drop(startIndex + 1)
    val nextIndex = remaining.indexWhere(_.trim.startsWith("lazy val "))
    val blockLines =
      if (nextIndex < 0) {
        lines.drop(startIndex)
      } else {
        lines.slice(startIndex, startIndex + 1 + nextIndex)
      }

    blockLines.mkString("\n")
  }

  private def assertBlockContains(projectId: String, required: String): Unit = {
    val block = buildBlock(projectId)
    if (!block.contains(required)) {
      fail(s"Expected build.sbt block for $projectId to contain: $required\nActual block:\n$block")
    }
  }

  private def genericBackendViolations(relativeDir: String): List[String] =
    scalaMainFiles(relativeDir).flatMap { path =>
      val importViolations = containsForbiddenImport(path, genericBackendForbiddenImports, allowedPatterns = Nil)
      val sourceViolations = genericBackendForbiddenSourceText.filter(read(path).contains).map { forbiddenText =>
        s"${relative(path)}: forbidden BeautyQ-specific source text: $forbiddenText"
      }

      importViolations ++ sourceViolations
    }

  private def assertNoViolations(context: String, violations: List[String]): Unit =
    if (violations.nonEmpty) {
      fail((context :: violations).mkString("\n"))
    }
}
