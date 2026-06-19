package leaderboard.search

import leaderboard.search.qdrant.{
  QdrantProductionCandidateObservabilityDecisionStatus,
  QdrantProductionCandidateObservabilityEvidence,
  QdrantProductionCandidateObservabilityReadiness,
  QdrantProductionCandidateReadiness,
  QdrantProductionCandidateReadinessState,
  QdrantProductionCandidateReadinessStatus,
  QdrantProductionCandidateRollbackDecisionStatus,
  QdrantProductionCandidateRollbackEvidence,
  QdrantProductionCandidateRollbackReadiness,
}
import org.scalatest.wordspec.AnyWordSpec

final class QdrantProductionCandidateControlsReadinessSpec extends AnyWordSpec {
  import QdrantProductionCandidateReadinessStatus.*

  "QdrantProductionCandidateObservabilityReadiness" should {
    "map a missing report to NotConfigured" in {
      assert(QdrantProductionCandidateObservabilityReadiness.readinessStatus(None) == NotConfigured)
    }

    "block a missing readiness/status report" in {
      val report = evaluateObservability(completeObservabilityEvidence.copy(readinessStatusReportAvailable = false))

      assert(report.decision.blockingReasons == List("Readiness/status report is missing"))
    }

    "block a missing quality/eval report" in {
      val report = evaluateObservability(completeObservabilityEvidence.copy(qualityEvalReportAvailable = false))

      assert(report.decision.blockingReasons == List("Quality/eval report is missing"))
    }

    "block a missing activation decision report" in {
      val report = evaluateObservability(completeObservabilityEvidence.copy(activationDecisionReportAvailable = false))

      assert(report.decision.blockingReasons == List("Activation decision report is missing"))
    }

    "preserve deterministic blocking-reason order" in {
      val report = evaluateObservability(QdrantProductionCandidateObservabilityEvidence(
        readinessStatusReportAvailable = false,
        qualityEvalReportAvailable = false,
        activationDecisionReportAvailable = false,
      ))

      assert(report.decision.status == QdrantProductionCandidateObservabilityDecisionStatus.NotReady)
      assert(report.decision.blockingReasons == List(
        "Readiness/status report is missing",
        "Quality/eval report is missing",
        "Activation decision report is missing",
      ))
      assert(QdrantProductionCandidateObservabilityReadiness.readinessStatus(Some(report)) == NotReady(report.decision.blockingReasons))
    }

    "map complete observability evidence to Ready without traffic mirroring or shadow telemetry" in {
      val report = evaluateObservability(completeObservabilityEvidence)
      val fieldNames = report.evidence.productElementNames.toSet

      assert(report.decision.status == QdrantProductionCandidateObservabilityDecisionStatus.Ready)
      assert(report.decision.blockingReasons.isEmpty)
      assert(QdrantProductionCandidateObservabilityReadiness.readinessStatus(Some(report)) == Ready)
      assert(!fieldNames.exists(field => List("shadow", "mirror", "traffic").exists(field.toLowerCase.contains)))
    }
  }

  "QdrantProductionCandidateRollbackReadiness" should {
    "map a missing report to NotConfigured" in {
      assert(QdrantProductionCandidateRollbackReadiness.readinessStatus(None) == NotConfigured)
    }

    "block a missing disable control" in {
      val report = evaluateRollback(completeRollbackEvidence.copy(disableControlDocumentedOrConfigured = false))

      assert(report.decision.blockingReasons == List("Disable control is missing"))
    }

    "block a missing rollback path" in {
      val report = evaluateRollback(completeRollbackEvidence.copy(rollbackPathDocumentedOrConfigured = false))

      assert(report.decision.blockingReasons == List("Rollback path is missing"))
    }

    "block missing no-regression evidence" in {
      val report = evaluateRollback(completeRollbackEvidence.copy(noRegressionEvidenceAvailable = false))

      assert(report.decision.blockingReasons == List("No-regression evidence is missing"))
    }

    "preserve deterministic blocking-reason order" in {
      val report = evaluateRollback(QdrantProductionCandidateRollbackEvidence(
        disableControlDocumentedOrConfigured = false,
        rollbackPathDocumentedOrConfigured = false,
        noRegressionEvidenceAvailable = false,
      ))

      assert(report.decision.status == QdrantProductionCandidateRollbackDecisionStatus.NotReady)
      assert(report.decision.blockingReasons == List(
        "Disable control is missing",
        "Rollback path is missing",
        "No-regression evidence is missing",
      ))
      assert(QdrantProductionCandidateRollbackReadiness.readinessStatus(Some(report)) == NotReady(report.decision.blockingReasons))
    }

    "map complete rollback and disable evidence to Ready without approving production-route activation" in {
      val report = evaluateRollback(completeRollbackEvidence)

      assert(report.decision.status == QdrantProductionCandidateRollbackDecisionStatus.Ready)
      assert(report.decision.blockingReasons.isEmpty)
      assert(!report.decision.productionRouteActivationApproved)
      assert(QdrantProductionCandidateRollbackReadiness.readinessStatus(Some(report)) == Ready)
    }

    "keep production-route activation approval outside the evidence input" in {
      val fieldNames = completeRollbackEvidence.productElementNames.toSet

      assert(fieldNames == Set(
        "disableControlDocumentedOrConfigured",
        "rollbackPathDocumentedOrConfigured",
        "noRegressionEvidenceAvailable",
      ))
      assert(!fieldNames.exists(_.toLowerCase.contains("activation")))
    }
  }

  "Qdrant production-candidate controls readiness integration" should {
    "remain false when observability is not ready" in {
      val report = evaluateObservability(completeObservabilityEvidence.copy(qualityEvalReportAvailable = false))
      val state = QdrantProductionCandidateReadiness.withObservabilityReadiness(allReadyState, Some(report))

      assert(state.observability == NotReady(report.decision.blockingReasons))
      assert(!QdrantProductionCandidateReadiness.evaluate(state).productionCandidateReady)
    }

    "remain false when rollback and disable readiness is not ready" in {
      val report = evaluateRollback(completeRollbackEvidence.copy(rollbackPathDocumentedOrConfigured = false))
      val state = QdrantProductionCandidateReadiness.withRollbackDisableReadiness(allReadyState, Some(report))

      assert(state.rollbackDisable == NotReady(report.decision.blockingReasons))
      assert(!QdrantProductionCandidateReadiness.evaluate(state).productionCandidateReady)
    }

    "keep missing control reports conservative" in {
      val withoutObservability = QdrantProductionCandidateReadiness.withObservabilityReadiness(allReadyState, None)
      val withoutRollback = QdrantProductionCandidateReadiness.withRollbackDisableReadiness(allReadyState, None)

      assert(withoutObservability.observability == NotConfigured)
      assert(withoutRollback.rollbackDisable == NotConfigured)
      assert(!QdrantProductionCandidateReadiness.evaluate(withoutObservability).productionCandidateReady)
      assert(!QdrantProductionCandidateReadiness.evaluate(withoutRollback).productionCandidateReady)
    }

    "become true when observability, rollback and disable, and every other category are ready" in {
      val withObservability = QdrantProductionCandidateReadiness.withObservabilityReadiness(
        allReadyState.copy(observability = NotConfigured, rollbackDisable = NotConfigured),
        Some(evaluateObservability(completeObservabilityEvidence)),
      )
      val withRollback = QdrantProductionCandidateReadiness.withRollbackDisableReadiness(
        withObservability,
        Some(evaluateRollback(completeRollbackEvidence)),
      )

      assert(withRollback.observability == Ready)
      assert(withRollback.rollbackDisable == Ready)
      assert(QdrantProductionCandidateReadiness.evaluate(withRollback).productionCandidateReady)
    }

    "keep serving behavior outside the readiness state" in {
      val fieldNames = QdrantProductionCandidateReadiness.conservativeDefault.productElementNames.toSet
      val forbiddenTerms = List(
        "shadow",
        "mirror",
        "traffic",
        "route",
        "fallback",
        "fusion",
        "rerank",
        "hybrid",
        "supplement",
      )

      assert(!fieldNames.exists(field => forbiddenTerms.exists(field.toLowerCase.contains)))
    }
  }

  private val completeObservabilityEvidence =
    QdrantProductionCandidateObservabilityEvidence(
      readinessStatusReportAvailable = true,
      qualityEvalReportAvailable = true,
      activationDecisionReportAvailable = true,
    )

  private val completeRollbackEvidence =
    QdrantProductionCandidateRollbackEvidence(
      disableControlDocumentedOrConfigured = true,
      rollbackPathDocumentedOrConfigured = true,
      noRegressionEvidenceAvailable = true,
    )

  private def evaluateObservability(evidence: QdrantProductionCandidateObservabilityEvidence) =
    QdrantProductionCandidateObservabilityReadiness.evaluate(evidence)

  private def evaluateRollback(evidence: QdrantProductionCandidateRollbackEvidence) =
    QdrantProductionCandidateRollbackReadiness.evaluate(evidence)

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
