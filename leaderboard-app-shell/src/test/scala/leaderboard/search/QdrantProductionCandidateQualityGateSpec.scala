package leaderboard.search

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.eval.{EngineEvalAggregateReport, EngineEvalEngine, EngineEvalQueryReport, EngineEvalResult, EngineExpectedRole}
import leaderboard.search.qdrant.{
  QdrantProductionCandidateParityReport,
  QdrantProductionCandidateQualityDecisionStatus,
  QdrantProductionCandidateQualityGate,
  QdrantProductionCandidateQualityReport,
  QdrantProductionCandidateQualityRule,
  QdrantProductionCandidateParityOutcome,
  QdrantProductionCandidateReadiness,
  QdrantProductionCandidateReadinessState,
  QdrantProductionCandidateReadinessStatus,
}
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

final class QdrantProductionCandidateQualityGateSpec extends AnyWordSpec {
  import QdrantProductionCandidateQualityDecisionStatus.*
  import QdrantProductionCandidateReadinessStatus.*

  "QdrantProductionCandidateQualityGate" should {
    "map a missing quality report to NotEvaluated" in {
      assert(QdrantProductionCandidateQualityGate.readinessStatus(None) == NotEvaluated)
    }

    "fail when evaluated query count is below the required minimum" in {
      val report = evaluate(parity(evaluatedQueryCount = 4), defaultRule.copy(minimumEvaluatedQueryCount = 5))

      assert(report.decision.status == Failed)
      assert(report.parityOutcome == QdrantProductionCandidateParityOutcome.Failed)
      assert(report.decision.reasons == List("Evaluated query count 4 is below minimum 5"))
      assert(QdrantProductionCandidateQualityGate.readinessStatus(Some(report)) == NotReady(report.decision.reasons))
    }

    "fail parity deterministically when recall and noise thresholds are missed" in {
      val report = evaluate(
        parity(baselineRecallCount = 8, candidateRecallCount = 6, qdrantNoiseCount = 2),
        defaultRule.copy(maximumRecallDeficit = 1, maximumQdrantNoiseCount = 1),
      )

      assert(report.decision.status == Failed)
      assert(report.decision.reasons == List(
        "Candidate Qdrant recall 6 is below baseline Elasticsearch recall 8 minus allowed deficit 1",
        "Candidate Qdrant noise count 2 exceeds maximum 1",
      ))
      assert(QdrantProductionCandidateQualityGate.readinessStatus(Some(report)) == NotReady(report.decision.reasons))
    }

    "pass parity when query coverage, recall, and noise thresholds are satisfied" in {
      val report = evaluate(parity(), defaultRule)

      assert(report.decision.status == Passed)
      assert(report.parityOutcome == QdrantProductionCandidateParityOutcome.Passed)
      assert(report.decision.reasons.isEmpty)
      assert(QdrantProductionCandidateQualityGate.readinessStatus(Some(report)) == Ready)
    }

    "preserve baseline and candidate labels" in {
      val report = evaluate(parity(baselineLabel = "current-es", candidateLabel = "qdrant-v1"), defaultRule)

      assert(report.parity.baselineLabel == "current-es")
      assert(report.parity.candidateLabel == "qdrant-v1")
    }

    "adapt existing EngineEval aggregate evidence without duplicating its metrics" in {
      val engineReport = EngineEvalAggregateReport.from(List(
        queryReport("q1", expected = Set(variantId(1)), esIds = List(variantId(1)), qdrantIds = List(variantId(1))),
        queryReport("q2", expected = Set(variantId(2)), esIds = List(variantId(2)), qdrantIds = List(variantId(2))),
      ))

      val report = QdrantProductionCandidateQualityGate.fromEngineEval(
        report = engineReport,
        rule = defaultRule.copy(minimumEvaluatedQueryCount = 2),
        baselineLabel = "es-eval",
        candidateLabel = "qdrant-eval",
      )

      assert(report.parity == QdrantProductionCandidateParityReport(
        baselineLabel = "es-eval",
        candidateLabel = "qdrant-eval",
        evaluatedQueryCount = 2,
        baselineRecallCount = 2,
        candidateRecallCount = 2,
        qdrantNoiseCount = 0,
      ))
      assert(report.decision.status == Passed)
    }

    "map incomplete evidence to Unknown" in {
      val report = evaluate(parity(candidateLabel = " "), defaultRule)

      assert(report.decision.status == Incomplete)
      assert(report.decision.reasons == List("Candidate label must be non-blank"))
      assert(QdrantProductionCandidateQualityGate.readinessStatus(Some(report)) == Unknown)
    }

    "keep production-candidate readiness false until the quality gate passes" in {
      val failed = evaluate(parity(candidateRecallCount = 5), defaultRule)
      val state = QdrantProductionCandidateReadiness.withQualityReport(allReadyState, Some(failed))

      assert(state.qualityEval == NotReady(failed.decision.reasons))
      assert(!QdrantProductionCandidateReadiness.evaluate(state).productionCandidateReady)
    }

    "allow production-candidate readiness when all categories are ready and the quality gate passes" in {
      val passed = evaluate(parity(), defaultRule)
      val state = QdrantProductionCandidateReadiness.withQualityReport(allReadyState.copy(qualityEval = NotEvaluated), Some(passed))

      assert(state.qualityEval == Ready)
      assert(QdrantProductionCandidateReadiness.evaluate(state).productionCandidateReady)
    }

    "require no serving, route, shadow, mirroring, fallback, fusion, reranking, or auto-supplement fields" in {
      val reportFields = evaluate(parity(), defaultRule).productElementNames.toSet
      val parityFields = parity().productElementNames.toSet
      val allFields = reportFields ++ parityFields
      val forbiddenTerms = List("serving", "route", "shadow", "mirror", "traffic", "fallback", "fusion", "rerank", "hybrid", "supplement")

      assert(reportFields == Set("parity", "rule", "parityOutcome", "decision"))
      assert(parityFields == Set(
        "baselineLabel",
        "candidateLabel",
        "evaluatedQueryCount",
        "baselineRecallCount",
        "candidateRecallCount",
        "qdrantNoiseCount",
      ))
      assert(!allFields.exists(field => forbiddenTerms.exists(field.toLowerCase.contains)))
    }
  }

  private val defaultRule = QdrantProductionCandidateQualityRule(
    minimumEvaluatedQueryCount = 10,
    maximumRecallDeficit = 0,
    maximumQdrantNoiseCount = 0,
  )

  private def parity(
    baselineLabel: String = "Elasticsearch",
    candidateLabel: String = "Qdrant",
    evaluatedQueryCount: Int = 10,
    baselineRecallCount: Int = 8,
    candidateRecallCount: Int = 8,
    qdrantNoiseCount: Int = 0,
  ): QdrantProductionCandidateParityReport =
    QdrantProductionCandidateParityReport(
      baselineLabel = baselineLabel,
      candidateLabel = candidateLabel,
      evaluatedQueryCount = evaluatedQueryCount,
      baselineRecallCount = baselineRecallCount,
      candidateRecallCount = candidateRecallCount,
      qdrantNoiseCount = qdrantNoiseCount,
    )

  private def evaluate(
    parity: QdrantProductionCandidateParityReport,
    rule: QdrantProductionCandidateQualityRule,
  ): QdrantProductionCandidateQualityReport =
    QdrantProductionCandidateQualityGate.evaluate(parity, rule)

  private def queryReport(
    queryId: String,
    expected: Set[MasterServiceOfferVariantId],
    esIds: List[MasterServiceOfferVariantId],
    qdrantIds: List[MasterServiceOfferVariantId],
  ): EngineEvalQueryReport =
    EngineEvalQueryReport.from(
      expectedRole = EngineExpectedRole.QdrantMayComplement,
      expectedVariantIds = expected,
      es = EngineEvalResult(EngineEvalEngine.Elasticsearch, queryId, esIds),
      qdrant = EngineEvalResult(EngineEvalEngine.Qdrant, queryId, qdrantIds),
    )

  private def variantId(slot: Int): MasterServiceOfferVariantId =
    UUID.fromString(f"00000000-0000-0000-0000-00000000${slot}%04x")

  private val allReadyState =
    QdrantProductionCandidateReadinessState(
      qdrantActive = true,
      collectionIdentity = Ready,
      contractParity = Ready,
      indexing = Ready,
      search = Ready,
      qualityEval = Ready,
      observability = Ready,
      rollbackDisable = Ready,
      activationPolicy = Ready,
    )
}
