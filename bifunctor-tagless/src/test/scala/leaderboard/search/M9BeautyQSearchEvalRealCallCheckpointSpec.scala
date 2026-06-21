package leaderboard.search

import leaderboard.search.eval.{
  M9BeautyQSearchEvalRealCallCheckpoint,
  M9BeautyQSearchEvalRealCallCheckpointDecision,
  M9BeautyQSearchEvalRealCallCheckpointInput,
  M9BeautyQSearchEvalRealCallCheckpointReason,
  M9BeautyQSearchEvalRealCallCheckpointRenderer,
  M9BeautyQSearchEvalStaticScorecard,
}
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M9BeautyQSearchEvalRealCallCheckpointSpec extends AnyWordSpec {

  private val ready = M9BeautyQSearchEvalStaticScorecard.DefaultSummary

  "M9BeautyQSearchEvalRealCallCheckpoint" should {

    "block real calls by default when no explicit resource config is present" in {
      val result = M9BeautyQSearchEvalRealCallCheckpoint.DefaultResult

      assert(result.decision == M9BeautyQSearchEvalRealCallCheckpointDecision.NotEligibleNoExplicitResourceConfig)
      assert(result.decision.render == "not_eligible_no_explicit_resource_config")
      assert(!result.eligibleForResourceGatedSmoke)
      assert(!result.realBackendCallImplemented)
      assert(result.reasons.contains(M9BeautyQSearchEvalRealCallCheckpointReason.NoExplicitResourceConfig))
    }

    "enable only ES-only offline smoke eligibility for ES-only config" in {
      val result = M9BeautyQSearchEvalRealCallCheckpoint.decide(
        M9BeautyQSearchEvalRealCallCheckpointInput.Default.copy(
          esResourceConfigPresent = true,
          qdrantResourceConfigPresent = false,
        )
      )

      assert(result.decision == M9BeautyQSearchEvalRealCallCheckpointDecision.EligibleForEsOnlyResourceGatedSmoke)
      assert(result.decision.render == "eligible_for_es_only_resource_gated_smoke")
      assert(result.eligibleForResourceGatedSmoke)
      assert(result.reasons.contains(M9BeautyQSearchEvalRealCallCheckpointReason.OfflineResourceGatedSmokeOnly))
      assert(!result.realBackendCallImplemented)
    }

    "enable only Qdrant-only offline smoke eligibility for Qdrant-only config" in {
      val result = M9BeautyQSearchEvalRealCallCheckpoint.decide(
        M9BeautyQSearchEvalRealCallCheckpointInput.Default.copy(
          esResourceConfigPresent = false,
          qdrantResourceConfigPresent = true,
        )
      )

      assert(result.decision == M9BeautyQSearchEvalRealCallCheckpointDecision.EligibleForQdrantOnlyResourceGatedSmoke)
      assert(result.decision.render == "eligible_for_qdrant_only_resource_gated_smoke")
      assert(result.eligibleForResourceGatedSmoke)
      assert(result.reasons.contains(M9BeautyQSearchEvalRealCallCheckpointReason.OfflineResourceGatedSmokeOnly))
    }

    "require operator approval before both-config comparison eligibility" in {
      val withoutApproval = M9BeautyQSearchEvalRealCallCheckpoint.decide(
        M9BeautyQSearchEvalRealCallCheckpointInput.Default.copy(
          esResourceConfigPresent = true,
          qdrantResourceConfigPresent = true,
          operatorApprovalGranted = false,
        )
      )
      assert(withoutApproval.decision == M9BeautyQSearchEvalRealCallCheckpointDecision.NotEligibleOperatorApprovalRequired)
      assert(!withoutApproval.eligibleForResourceGatedSmoke)

      val withApproval = M9BeautyQSearchEvalRealCallCheckpoint.decide(
        M9BeautyQSearchEvalRealCallCheckpointInput.Default.copy(
          esResourceConfigPresent = true,
          qdrantResourceConfigPresent = true,
          operatorApprovalGranted = true,
        )
      )
      assert(withApproval.decision == M9BeautyQSearchEvalRealCallCheckpointDecision.EligibleForEsQdrantResourceGatedComparison)
      assert(withApproval.decision.render == "eligible_for_es_qdrant_resource_gated_comparison")
      assert(withApproval.eligibleForResourceGatedSmoke)
    }

    "block when the static scorecard is not ready" in {
      val notReady = ready.copy(verdict = "dataset_static_rows_not_ready")
      val result = M9BeautyQSearchEvalRealCallCheckpoint.decide(
        M9BeautyQSearchEvalRealCallCheckpointInput.Default.copy(
          scorecard = notReady,
          esResourceConfigPresent = true,
          qdrantResourceConfigPresent = true,
          operatorApprovalGranted = true,
        )
      )

      assert(result.decision == M9BeautyQSearchEvalRealCallCheckpointDecision.BlockedStaticScorecardNotReady)
      assert(!result.staticScorecardReady)
      assert(!result.eligibleForResourceGatedSmoke)
      assert(result.reasons.contains(M9BeautyQSearchEvalRealCallCheckpointReason.StaticScorecardNotReady))
    }

    "keep production activation not approved even when an approval flag is forced true" in {
      val forced = M9BeautyQSearchEvalRealCallCheckpoint.decide(
        M9BeautyQSearchEvalRealCallCheckpointInput.Default.copy(
          esResourceConfigPresent = true,
          qdrantResourceConfigPresent = true,
          operatorApprovalGranted = true,
          productionActivationApproved = true,
        )
      )

      assert(!forced.productionActivationApproved)
      assert(!forced.productionActivationReady)
      assert(forced.reasons.contains(M9BeautyQSearchEvalRealCallCheckpointReason.ProductionActivationNotApproved))
      assert(M9BeautyQSearchEvalRealCallCheckpointInput.Default.productionActivationApproved == false)
    }

    "never claim production readiness, activation, hybrid serving, fallback, fusion, reranking, or telemetry" in {
      val rendered = M9BeautyQSearchEvalRealCallCheckpoint.MarkdownArtifact.contents
      val forbidden = List(
        "production_ready",
        "production_activation_ready",
        "qdrant_activation",
        "hybrid",
        "fallback",
        "fusion",
        "rerank",
        "production_telemetry",
        "route_switch",
      )

      assert(forbidden.forall(claim => !rendered.contains(claim)))

      val decisions = M9BeautyQSearchEvalRealCallCheckpointDecision.values.toList
      assert(decisions.forall(decision => forbidden.forall(claim => !decision.render.contains(claim))))
    }

    "render a byte-for-byte stable checked-in artifact" in {
      val artifact = M9BeautyQSearchEvalRealCallCheckpoint.MarkdownArtifact
      val expected = readResource("/leaderboard/search/eval/m9-beautyq-real-call-checkpoint.md")

      assert(artifact.filename == "m9-beautyq-real-call-checkpoint.md")
      assert(artifact.contentType == "text/markdown; charset=utf-8")
      assert(
        artifact.contents == M9BeautyQSearchEvalRealCallCheckpointRenderer.renderMarkdown(
          M9BeautyQSearchEvalRealCallCheckpoint.DefaultResult
        )
      )
      assert(artifact.contents == expected)
    }

    "require no real backend call to evaluate any decision" in {
      val all = List(
        M9BeautyQSearchEvalRealCallCheckpointInput.Default,
        M9BeautyQSearchEvalRealCallCheckpointInput.Default.copy(esResourceConfigPresent = true),
        M9BeautyQSearchEvalRealCallCheckpointInput.Default.copy(qdrantResourceConfigPresent = true),
        M9BeautyQSearchEvalRealCallCheckpointInput.Default
          .copy(esResourceConfigPresent = true, qdrantResourceConfigPresent = true, operatorApprovalGranted = true),
      )

      assert(all.map(M9BeautyQSearchEvalRealCallCheckpoint.decide).forall(!_.realBackendCallImplemented))
    }
  }

  private def readResource(path: String): String =
    Option(getClass.getResourceAsStream(path)) match {
      case Some(value) =>
        Using.resource(Source.fromInputStream(value, StandardCharsets.UTF_8.name()))(_.mkString)
      case None =>
        fail(s"missing checked-in resource $path")
    }
}
