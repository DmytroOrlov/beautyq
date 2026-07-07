package leaderboard.search

import leaderboard.search.eval.{
  M9OfflineEvalExampleArtifacts,
  M9OfflineEvalStaticFixtures,
  M9OfflineEvalStaticRunError,
  M9OfflineEvalStaticRunner,
  M9OfflineEvalStaticRunnerConfig,
  QueryClass,
}
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M9OfflineEvalStaticFixturesSpec extends AnyWordSpec {

  "M9OfflineEvalStaticFixtures" should {

    "contain stable exact, semantic, ambiguous, and negative query classes" in {
      val queryClasses = M9OfflineEvalStaticFixtures.Dataset.queries.map(_.queryClass)

      assert(queryClasses == List(
        QueryClass.ExactProductNameBrand,
        QueryClass.SemanticDescriptive,
        QueryClass.Ambiguous,
        QueryClass.NegativeOutOfCatalog,
      ))
      assert(M9OfflineEvalStaticFixtures.Dataset.queries.map(_.queryId) == List(
        "m9_static_exact_001",
        "m9_static_semantic_001",
        "m9_static_ambiguous_001",
        "m9_static_negative_001",
      ))
      assert(M9OfflineEvalStaticFixtures.Dataset.queries.map(_.negativeOutOfCatalog) == List(false, false, false, true))
    }

    "run through the static runner and preserve fixture order" in {
      val result = M9OfflineEvalStaticRunner.run(
        input = M9OfflineEvalStaticFixtures.Input,
        config = M9OfflineEvalStaticRunnerConfig(M9OfflineEvalExampleArtifacts.MarkdownFilename),
      )

      result match {
        case Right(staticRun) =>
          assert(staticRun.report.rows.map(_.queryId) == M9OfflineEvalStaticFixtures.Rows.map(_.queryId))
          assert(staticRun.markdownArtifact.filename == "m9-static-example-report.md")
          assert(staticRun.qualityGateSummary.totalRows == 4)
          assert(staticRun.qualityGateSummary.hasNegativeOutOfCatalogQuery)
        case Left(M9OfflineEvalStaticRunError(reasons)) =>
          fail(s"expected canonical static fixture to run, got validation errors $reasons")
      }
    }

    "generate markdown that exactly matches the checked-in resource artifact" in {
      assert(M9OfflineEvalExampleArtifacts.MarkdownArtifact.contents == checkedInArtifact)
    }

    "keep the example quality gate decision outside activation approval" in {
      val decision = M9OfflineEvalExampleArtifacts.StaticRun.report.qualityGateDecision
      val lower = decision.toLowerCase

      assert(decision == "sample_not_for_activation")
      assert(!lower.contains("approved"))
      assert(!lower.contains("ready"))
      assert(!lower.contains("activate"))
    }

    "include explicit non-serving boundary text" in {
      val artifact = M9OfflineEvalExampleArtifacts.MarkdownArtifact.contents

      assert(artifact.contains("Backend execution is not represented by this artifact."))
      assert(artifact.contains("Non-serving boundary: this artifact was generated from static fixture rows only; it did not query Elasticsearch, Qdrant, HTTP, routes, Distage, Docker, or production backends."))
    }

    "avoid positive claims that ES, Qdrant, or hybrid execution happened" in {
      val artifact = M9OfflineEvalExampleArtifacts.MarkdownArtifact.contents.toLowerCase
      val forbiddenClaims = List(
        "elasticsearch query executed",
        "es query executed",
        "qdrant query executed",
        "hybrid execution",
        "hybrid serving executed",
        "queried elasticsearch",
        "queried qdrant",
        "production telemetry emitted",
      )

      assert(!forbiddenClaims.exists(artifact.contains))
    }
  }

  private def checkedInArtifact: String = {
    val path = "/leaderboard/search/eval/m9-static-example-report.md"
    val stream = Option(getClass.getResourceAsStream(path))

    stream match {
      case Some(value) =>
        Using.resource(Source.fromInputStream(value, StandardCharsets.UTF_8.name()))(_.mkString)
      case None =>
        fail(s"missing checked-in resource artifact $path")
    }
  }
}
