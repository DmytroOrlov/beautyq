package leaderboard.search

import leaderboard.search.qdrant.{
  QdrantProductionCandidateActivationApprovalStatus,
  QdrantProductionCandidateServingApprovalRequest,
  QdrantProductionCandidateServingApprovalRequestEvidence,
  QdrantProductionCandidateServingApprovalRequestStatus,
  QdrantProductionCandidateServingApprovalRequestTargetScope,
}
import org.scalatest.wordspec.AnyWordSpec

final class QdrantProductionCandidateServingApprovalRequestSpec extends AnyWordSpec {
  import QdrantProductionCandidateActivationApprovalStatus.*
  import QdrantProductionCandidateServingApprovalRequestStatus.*
  import QdrantProductionCandidateServingApprovalRequestTargetScope.*

  "QdrantProductionCandidateServingApprovalRequest" should {
    "mark the current evidence package ready to request explicit opt-in route approval only" in {
      val report = QdrantProductionCandidateServingApprovalRequest.evaluate(currentEvidence)

      assert(report.status == ReadyToRequestExplicitApproval)
      assert(report.blockingReasons.isEmpty)
      assert(report.evidence.explicitServingApproval == NotApproved)
      assert(report.evidence.targetScope == FutureExplicitOptInRouteRequest)
    }

    "block when required evidence is missing and keep deterministic reasons" in {
      val report = QdrantProductionCandidateServingApprovalRequest.evaluate(currentEvidence.copy(
        m7CloseoutEvidencePresent = false,
        offlineEvalNoRegressionEvidencePresent = false,
        postM7NoServingGuardrailPresent = false,
        routeBoundaryEvidencePresent = false,
        configNoRegressionGateEvidencePresent = false,
      ))

      assert(report.status == Blocked)
      assert(report.blockingReasons == List(
        "M7 closeout evidence is missing",
        "Offline eval/no-regression evidence is missing",
        "Post-M7 no-serving guardrail is missing",
        "Route-boundary evidence is missing",
        "Config/no-regression gate evidence is missing",
      ))
    }

    "block broader serving targets and already-approved serving states" in {
      val productionRouteReport = QdrantProductionCandidateServingApprovalRequest.evaluate(
        currentEvidence.copy(targetScope = ProductionRouteActivationRequest)
      )
      val hybridReport = QdrantProductionCandidateServingApprovalRequest.evaluate(
        currentEvidence.copy(targetScope = HybridServingRequest)
      )
      val approvedReport = QdrantProductionCandidateServingApprovalRequest.evaluate(
        currentEvidence.copy(explicitServingApproval = Approved)
      )

      assert(productionRouteReport.status == Blocked)
      assert(productionRouteReport.blockingReasons == List(
        "Target scope is not limited to a future explicit opt-in route request"
      ))
      assert(hybridReport.status == Blocked)
      assert(hybridReport.blockingReasons == List(
        "Target scope is not limited to a future explicit opt-in route request"
      ))
      assert(approvedReport.status == Blocked)
      assert(approvedReport.blockingReasons == List(
        "Explicit serving approval is already approved outside this request-readiness model"
      ))
    }

    "remain a pure evidence model without route implementation behavior" in {
      val fieldNames = currentEvidence.productElementNames.toSet
      val routeBoundarySpecs = Set(
        classOf[BeautySearchProductionRouteExposureSpec].getSimpleName,
        classOf[BeautySearchOptInRouteModuleSpec].getSimpleName,
        classOf[QdrantProductionCandidateM7CloseoutSpec].getSimpleName,
        classOf[QdrantProductionCandidateOfflineEvalEvidenceSpec].getSimpleName,
        classOf[QdrantProductionCandidatePostM7NoServingGuardrailSpec].getSimpleName,
      )
      val forbiddenTerms = List(
        "switch",
        "fallback",
        "fusion",
        "rerank",
        "supplement",
        "mirror",
        "traffic",
      )

      assert(fieldNames == Set(
        "m7CloseoutEvidencePresent",
        "offlineEvalNoRegressionEvidencePresent",
        "postM7NoServingGuardrailPresent",
        "routeBoundaryEvidencePresent",
        "configNoRegressionGateEvidencePresent",
        "explicitServingApproval",
        "targetScope",
      ))
      assert(routeBoundarySpecs == Set(
        "BeautySearchProductionRouteExposureSpec",
        "BeautySearchOptInRouteModuleSpec",
        "QdrantProductionCandidateM7CloseoutSpec",
        "QdrantProductionCandidateOfflineEvalEvidenceSpec",
        "QdrantProductionCandidatePostM7NoServingGuardrailSpec",
      ))
      assert(!fieldNames.exists(field => forbiddenTerms.exists(field.toLowerCase.contains)))
    }
  }

  private val currentEvidence =
    QdrantProductionCandidateServingApprovalRequestEvidence(
      m7CloseoutEvidencePresent = true,
      offlineEvalNoRegressionEvidencePresent = true,
      postM7NoServingGuardrailPresent = true,
      routeBoundaryEvidencePresent = true,
      configNoRegressionGateEvidencePresent = true,
      explicitServingApproval = NotApproved,
      targetScope = FutureExplicitOptInRouteRequest,
    )
}
