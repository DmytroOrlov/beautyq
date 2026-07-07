package leaderboard.search

import leaderboard.search.qdrant.{
  QdrantProductionCandidateActivationApprovalStatus,
  QdrantProductionCandidateActivationPlanning,
  QdrantProductionCandidateActivationPlanningStatus,
  QdrantProductionCandidateActivationPolicy,
  QdrantProductionCandidateActivationPrerequisites,
  QdrantProductionCandidateActivationRequirementStatus,
  QdrantProductionCandidateActivationScope,
  QdrantProductionCandidateActivationTargetScope,
  QdrantProductionCandidateReadiness,
  QdrantProductionCandidateReadinessState,
  QdrantProductionCandidateReadinessStatus,
}
import org.scalatest.wordspec.AnyWordSpec

final class QdrantProductionCandidateM7ActivationPlanningSpec extends AnyWordSpec {
  import QdrantProductionCandidateActivationApprovalStatus.*
  import QdrantProductionCandidateActivationPlanningStatus.*
  import QdrantProductionCandidateActivationRequirementStatus.*
  import QdrantProductionCandidateActivationTargetScope.*
  import QdrantProductionCandidateReadinessStatus.Ready

  "QdrantProductionCandidateActivationPlanning" should {
    "represent all future activation scopes without enabling serving" in {
      val scopes = List(
        QdrantProductionCandidateActivationTargetScope.CandidateReadinessOnly,
        ExplicitOptInRoute,
        ProductionRouteActivation,
        HybridServing,
      )

      assert(scopes.toSet.size == 4)
    }

    "allow candidate-readiness planning when M6 and activation policy are ready" in {
      val decision = evaluate(QdrantProductionCandidateActivationTargetScope.CandidateReadinessOnly, completePrerequisites.copy(
        configGate = Missing,
        noRegressionEvidence = Missing,
        observabilityStatus = Missing,
        rollbackDisableControl = Missing,
        servingApproval = NotRequired,
      ))

      assert(decision.status == ReadyForSeparateImplementationDecision)
      assert(decision.blockingReasons.isEmpty)
    }

    "require M6 productionCandidateReady and a Ready activation policy" in {
      val decision = evaluate(ExplicitOptInRoute, completePrerequisites.copy(
        m6ReadinessReport = Some(QdrantProductionCandidateReadiness.evaluate(allReadyState.copy(search = QdrantProductionCandidateReadinessStatus.Unknown))),
        activationPolicyReport = Some(QdrantProductionCandidateActivationPolicy.evaluate(explicitOptInPolicy.copy(explicitlyApproved = false))),
      ))

      assert(decision.status == Blocked)
      assert(decision.blockingReasons == List(
        "M6 production-candidate readiness report is not ready",
        "Activation policy decision is not Ready",
      ))
    }

    "require every pre-wiring control for an explicit opt-in route" in {
      val decision = evaluate(ExplicitOptInRoute, completePrerequisites.copy(
        configGate = Missing,
        noRegressionEvidence = Unknown,
        observabilityStatus = Missing,
        rollbackDisableControl = Unknown,
        servingApproval = NotApproved,
      ))

      assert(decision.status == Blocked)
      assert(decision.blockingReasons == List(
        "Config gate is missing",
        "No-regression evidence status is unknown",
        "Observability/status evidence is missing",
        "Rollback/disable control status is unknown",
        "Separate route/serving approval is not approved",
      ))
    }

    "treat complete opt-in prerequisites as evidence for a separate implementation decision only" in {
      val decision = evaluate(ExplicitOptInRoute, completePrerequisites)

      assert(decision.status == ReadyForSeparateImplementationDecision)
      assert(decision.blockingReasons.isEmpty)
    }

    "keep current production-route activation blocked without separate serving approval" in {
      val decision = evaluate(ProductionRouteActivation, completePrerequisites.copy(servingApproval = NotApproved))

      assert(decision.status == Blocked)
      assert(decision.blockingReasons == List("Separate route/serving approval is not approved"))
    }

    "keep hybrid serving conditional on the same explicit prerequisites and serving approval" in {
      val decision = evaluate(HybridServing, completePrerequisites.copy(servingApproval = NotRequired))

      assert(decision.status == Blocked)
      assert(decision.blockingReasons == List("Separate route/serving approval is required"))
    }

    "contain planning evidence only and no route implementation behavior" in {
      val fieldNames = completePrerequisites.productElementNames.toSet
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
        "m6ReadinessReport",
        "activationPolicyReport",
        "configGate",
        "noRegressionEvidence",
        "observabilityStatus",
        "rollbackDisableControl",
        "servingApproval",
      ))
      assert(!fieldNames.exists(field => forbiddenTerms.exists(field.toLowerCase.contains)))
    }
  }

  private val explicitOptInPolicy =
    QdrantProductionCandidateActivationPolicy(
      explicitlyApproved = true,
      scope = QdrantProductionCandidateActivationScope.FutureExplicitOptInRouteOnly,
      routeServingApproval = Approved,
      rollbackDisableControls = Satisfied,
      noRegressionEvidence = Satisfied,
      observability = Satisfied,
    )

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

  private val completePrerequisites =
    QdrantProductionCandidateActivationPrerequisites(
      m6ReadinessReport = Some(QdrantProductionCandidateReadiness.evaluate(allReadyState)),
      activationPolicyReport = Some(QdrantProductionCandidateActivationPolicy.evaluate(explicitOptInPolicy)),
      configGate = Satisfied,
      noRegressionEvidence = Satisfied,
      observabilityStatus = Satisfied,
      rollbackDisableControl = Satisfied,
      servingApproval = Approved,
    )

  private def evaluate(
    targetScope: QdrantProductionCandidateActivationTargetScope,
    prerequisites: QdrantProductionCandidateActivationPrerequisites,
  ) =
    QdrantProductionCandidateActivationPlanning.evaluate(targetScope, prerequisites)
}
