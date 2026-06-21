package leaderboard.search

import leaderboard.search.eval.{
  CandidateSource,
  M9BeautyQSearchEvalStaticScorecard,
  M9BeautyQSearchEvalStaticScorecardRenderer,
  ServingMode,
}
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M9BeautyQSearchEvalStaticScorecardSpec extends AnyWordSpec {

  "M9BeautyQSearchEvalStaticScorecard" should {

    "consume all 63 static rows and accept all rows through the static runner" in {
      val summary = M9BeautyQSearchEvalStaticScorecard.DefaultSummary

      assert(summary.datasetQueryCount == 63)
      assert(summary.mappedRowCount == 63)
      assert(summary.staticRunnerAcceptedRowCount == 63)
      assert(metricValue(summary.metrics, "dataset_query_count") == "63")
      assert(metricValue(summary.metrics, "mapped_row_count") == "63")
      assert(metricValue(summary.metrics, "static_runner_accepted_row_count") == "63")
    }

    "preserve manual static source and unknown serving mode attribution only" in {
      val summary = M9BeautyQSearchEvalStaticScorecard.DefaultSummary

      assert(summary.candidateSources == List(CandidateSource.Manual))
      assert(summary.servingModes == List(ServingMode.Unknown))
      assert(summary.manualStaticOnly)
      assert(summary.unknownServingModeOnly)
      assert(metricValue(summary.metrics, "candidate_source") == "manual")
      assert(metricValue(summary.metrics, "serving_mode") == "unknown")
      assert(metricValue(summary.metrics, "manual_static_only") == "true")
      assert(metricValue(summary.metrics, "unknown_serving_mode_only") == "true")
    }

    "count representative anchors and placeholder-only rows" in {
      val summary = M9BeautyQSearchEvalStaticScorecard.DefaultSummary

      assert(summary.representativeAnchorRowCount == 3)
      assert(summary.placeholderOnlyRowCount == 60)
      assert(metricValue(summary.metrics, "representative_anchor_row_count") == "3")
      assert(metricValue(summary.metrics, "placeholder_only_row_count") == "60")
    }

    "record zero real backend evidence and no real backend call requirement" in {
      val summary = M9BeautyQSearchEvalStaticScorecard.DefaultSummary

      assert(summary.realBackendEvidenceRowCount == 0)
      assert(summary.esBackendEvidenceRowCount == 0)
      assert(summary.qdrantBackendEvidenceRowCount == 0)
      assert(!summary.realBackendCallRequired)
      assert(metricValue(summary.metrics, "real_backend_evidence_row_count") == "0")
      assert(metricValue(summary.metrics, "es_backend_evidence_row_count") == "0")
      assert(metricValue(summary.metrics, "qdrant_backend_evidence_row_count") == "0")
      assert(metricValue(summary.metrics, "real_backend_call_required") == "false")
    }

    "keep the verdict to dataset and static-row readiness only" in {
      val summary = M9BeautyQSearchEvalStaticScorecard.DefaultSummary
      val rendered = M9BeautyQSearchEvalStaticScorecard.MarkdownArtifact.contents
      val forbiddenClaims = List(
        "production_ready",
        "qdrant_ready",
        "hybrid_ready",
        "quality_green",
        "activation_approved",
      )

      assert(summary.verdict == "dataset_static_rows_ready")
      assert(metricValue(summary.metrics, "verdict") == "dataset_static_rows_ready")
      assert(forbiddenClaims.forall(claim => summary.verdict != claim))
      assert(forbiddenClaims.forall(claim => !rendered.contains(claim)))
      assert(rendered.contains("not backend quality evidence or activation approval"))
    }

    "preserve production non-approval and route plugin DI HTTP non-involvement" in {
      val summary = M9BeautyQSearchEvalStaticScorecard.DefaultSummary
      val rendered = M9BeautyQSearchEvalStaticScorecard.MarkdownArtifact.contents

      assert(!summary.productionActivationApproval)
      assert(!summary.routePluginDiHttpInvolved)
      assert(metricValue(summary.metrics, "production_activation_approval") == "false")
      assert(metricValue(summary.metrics, "route_plugin_di_http_involved") == "false")
      assert(rendered.contains("No route, plugin, DI, or HTTP source is involved."))
      assert(rendered.contains("Default /beauty-search remains ES-backed; Qdrant production activation remains not approved."))
    }

    "record full static expansion while full JSON parsing stays deferred" in {
      val summary = M9BeautyQSearchEvalStaticScorecard.DefaultSummary
      val rendered = M9BeautyQSearchEvalStaticScorecard.MarkdownArtifact.contents

      assert(!summary.fullJsonParsingImplemented)
      assert(summary.full63QueryStaticExpansionImplemented)
      assert(metricValue(summary.metrics, "full_json_parsing_implemented") == "false")
      assert(metricValue(summary.metrics, "full_63_query_static_expansion_implemented") == "true")
      assert(rendered.contains("Full JSON parsing remains intentionally deferred"))
      assert(rendered.contains("63-query static-row expansion is implemented"))
    }

    "render byte-for-byte stable checked-in artifact" in {
      val expected = readResource("/leaderboard/search/eval/m9-beautyq-eval-static-scorecard.md")
      val artifact = M9BeautyQSearchEvalStaticScorecard.MarkdownArtifact

      assert(artifact.filename == "m9-beautyq-eval-static-scorecard.md")
      assert(artifact.contentType == "text/markdown; charset=utf-8")
      assert(artifact.contents == M9BeautyQSearchEvalStaticScorecardRenderer.renderMarkdown(M9BeautyQSearchEvalStaticScorecard.DefaultSummary))
      assert(artifact.contents == expected)
    }
  }

  private def metricValue(
    metrics: List[leaderboard.search.eval.M9BeautyQSearchEvalStaticScorecardMetric],
    name: String,
  ): String =
    metrics.find(_.name == name) match {
      case Some(metric) => metric.value
      case None         => fail(s"missing scorecard metric $name")
    }

  private def readResource(path: String): String = {
    val stream = Option(getClass.getResourceAsStream(path))

    stream match {
      case Some(value) =>
        Using.resource(Source.fromInputStream(value, StandardCharsets.UTF_8.name()))(_.mkString)
      case None =>
        fail(s"missing checked-in resource $path")
    }
  }
}
