package leaderboard.search

import leaderboard.search.qdrant.{
  QdrantProductionCandidateActivationApprovalStatus,
  QdrantProductionCandidateActivationDecisionStatus,
  QdrantProductionCandidateActivationPolicy,
  QdrantProductionCandidateActivationRequirementStatus,
  QdrantProductionCandidateActivationScope,
  QdrantProductionCandidateReadiness,
  QdrantProductionCandidateReadinessState,
  QdrantProductionCandidateReadinessStatus,
}
import org.scalatest.wordspec.AnyWordSpec

final class QdrantProductionCandidateActivationPolicySpec extends AnyWordSpec {
  import QdrantProductionCandidateActivationApprovalStatus.*
  import QdrantProductionCandidateActivationRequirementStatus.*
  import QdrantProductionCandidateActivationScope.*

  "QdrantProductionCandidateActivationPolicy" should {
    "map a missing activation policy to NotApproved" in {
      assert(QdrantProductionCandidateActivationPolicy.readinessStatus(None) == QdrantProductionCandidateReadinessStatus.NotApproved)
    }

    "keep the conservative default not approved" in {
      val policy = QdrantProductionCandidateActivationPolicy.conservativeDefault
      val report = QdrantProductionCandidateActivationPolicy.evaluate(policy)

      assert(policy.scope == NoActivation)
      assert(report.decision.status == QdrantProductionCandidateActivationDecisionStatus.NotApproved)
      assert(report.decision.blockingReasons == List(
        "Activation is not explicitly approved",
        "Activation scope is NoActivation",
        "Rollback/disable controls are missing",
        "No-regression evidence requirements status is unknown",
        "Observability controls are missing",
      ))
      assert(QdrantProductionCandidateActivationPolicy.readinessStatus(Some(policy)) == QdrantProductionCandidateReadinessStatus.NotApproved)
    }

    "keep candidate readiness not approved without explicit approval" in {
      val policy = approvedCandidatePolicy.copy(explicitlyApproved = false)
      val report = evaluate(policy)

      assert(report.decision.status == QdrantProductionCandidateActivationDecisionStatus.NotApproved)
      assert(report.decision.blockingReasons == List("Activation is not explicitly approved"))
    }

    "represent every activation scope explicitly" in {
      val scopes = List(
        NoActivation,
        CandidateReadinessOnly,
        FutureExplicitOptInRouteOnly,
        FutureProductionRouteNotApprovedHere,
      )

      assert(scopes.toSet.size == 4)
    }

    "allow candidate-readiness-only approval when every required control is present" in {
      val report = evaluate(approvedCandidatePolicy)

      assert(report.decision.status == QdrantProductionCandidateActivationDecisionStatus.Ready)
      assert(report.decision.blockingReasons.isEmpty)
      assert(QdrantProductionCandidateActivationPolicy.readinessStatus(Some(approvedCandidatePolicy)) == QdrantProductionCandidateReadinessStatus.Ready)
    }

    "represent a future explicit opt-in route only with separate route approval" in {
      val withoutRouteApproval = approvedCandidatePolicy.copy(
        scope = FutureExplicitOptInRouteOnly,
        routeServingApproval = QdrantProductionCandidateActivationApprovalStatus.NotApproved,
      )
      val withRouteApproval = withoutRouteApproval.copy(routeServingApproval = Approved)

      assert(evaluate(withoutRouteApproval).decision.status == QdrantProductionCandidateActivationDecisionStatus.NotApproved)
      assert(evaluate(withRouteApproval).decision.status == QdrantProductionCandidateActivationDecisionStatus.Ready)
    }

    "keep future production route activation blocked even when controls are present" in {
      val policy = approvedCandidatePolicy.copy(
        scope = FutureProductionRouteNotApprovedHere,
        routeServingApproval = Approved,
      )
      val report = evaluate(policy)

      assert(report.decision.status == QdrantProductionCandidateActivationDecisionStatus.NotApproved)
      assert(report.decision.blockingReasons == List("Production route activation requires separate approval outside this policy"))
      assert(QdrantProductionCandidateActivationPolicy.readinessStatus(Some(policy)) == QdrantProductionCandidateReadinessStatus.NotApproved)
    }

    "block readiness when rollback and disable controls are missing" in {
      val report = evaluate(approvedCandidatePolicy.copy(rollbackDisableControls = Missing))

      assert(report.decision.status == QdrantProductionCandidateActivationDecisionStatus.NotReady)
      assert(report.decision.blockingReasons == List("Rollback/disable controls are missing"))
      assert(QdrantProductionCandidateActivationPolicy.readinessStatus(Some(report.policy)) == QdrantProductionCandidateReadinessStatus.NotReady(report.decision.blockingReasons))
    }

    "block readiness when observability is missing" in {
      val report = evaluate(approvedCandidatePolicy.copy(observability = Missing))

      assert(report.decision.status == QdrantProductionCandidateActivationDecisionStatus.NotReady)
      assert(report.decision.blockingReasons == List("Observability controls are missing"))
    }

    "block readiness when no-regression evidence is missing" in {
      val report = evaluate(approvedCandidatePolicy.copy(noRegressionEvidence = Missing))

      assert(report.decision.status == QdrantProductionCandidateActivationDecisionStatus.NotReady)
      assert(report.decision.blockingReasons == List("No-regression evidence requirements are missing"))
    }

    "feed activation readiness without making the overall candidate ready when another category is not ready" in {
      val state = QdrantProductionCandidateReadiness.withActivationPolicy(
        allReadyState.copy(search = QdrantProductionCandidateReadinessStatus.Unknown, activationPolicy = QdrantProductionCandidateReadinessStatus.NotApproved),
        Some(approvedCandidatePolicy),
      )

      assert(state.activationPolicy == QdrantProductionCandidateReadinessStatus.Ready)
      assert(!QdrantProductionCandidateReadiness.evaluate(state).productionCandidateReady)
    }

    "allow overall candidate readiness when activation and every other category are ready" in {
      val state = QdrantProductionCandidateReadiness.withActivationPolicy(
        allReadyState.copy(activationPolicy = QdrantProductionCandidateReadinessStatus.NotApproved),
        Some(approvedCandidatePolicy),
      )

      assert(state.activationPolicy == QdrantProductionCandidateReadinessStatus.Ready)
      assert(QdrantProductionCandidateReadiness.evaluate(state).productionCandidateReady)
    }

    "contain policy evidence only and no serving behavior fields" in {
      val fieldNames = approvedCandidatePolicy.productElementNames.toSet
      val forbiddenTerms = List(
        "shadow",
        "mirror",
        "traffic",
        "switch",
        "fallback",
        "fusion",
        "rerank",
        "hybrid",
        "supplement",
      )

      assert(fieldNames == Set(
        "explicitlyApproved",
        "scope",
        "routeServingApproval",
        "rollbackDisableControls",
        "noRegressionEvidence",
        "observability",
      ))
      assert(!fieldNames.exists(field => forbiddenTerms.exists(field.toLowerCase.contains)))
    }
  }

  private val approvedCandidatePolicy =
    QdrantProductionCandidateActivationPolicy(
      explicitlyApproved = true,
      scope = CandidateReadinessOnly,
      routeServingApproval = NotRequired,
      rollbackDisableControls = Satisfied,
      noRegressionEvidence = Satisfied,
      observability = Satisfied,
    )

  private def evaluate(policy: QdrantProductionCandidateActivationPolicy) =
    QdrantProductionCandidateActivationPolicy.evaluate(policy)

  private val allReadyState =
    QdrantProductionCandidateReadinessState(
      qdrantActive = true,
      collectionIdentity = QdrantProductionCandidateReadinessStatus.Ready,
      contractParity = QdrantProductionCandidateReadinessStatus.Ready,
      indexing = QdrantProductionCandidateReadinessStatus.Ready,
      search = QdrantProductionCandidateReadinessStatus.Ready,
      qualityEval = QdrantProductionCandidateReadinessStatus.Ready,
      observability = QdrantProductionCandidateReadinessStatus.Ready,
      rollbackDisable = QdrantProductionCandidateReadinessStatus.Ready,
      activationPolicy = QdrantProductionCandidateReadinessStatus.Ready,
    )
}
