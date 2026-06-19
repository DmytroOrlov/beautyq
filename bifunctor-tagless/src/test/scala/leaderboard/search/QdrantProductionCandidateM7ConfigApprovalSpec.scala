package leaderboard.search

import leaderboard.search.qdrant.{
  QdrantProductionCandidateActivationApprovalStatus,
  QdrantProductionCandidateActivationConfigApproval,
  QdrantProductionCandidateActivationConfigApprovalStatus,
  QdrantProductionCandidateActivationConfigGate,
  QdrantProductionCandidateActivationPlanning,
  QdrantProductionCandidateActivationPlanningStatus,
  QdrantProductionCandidateActivationPolicy,
  QdrantProductionCandidateActivationPrerequisites,
  QdrantProductionCandidateActivationRequirementStatus,
  QdrantProductionCandidateActivationScope,
  QdrantProductionCandidateActivationTargetScope,
  QdrantProductionCandidateNoRegressionApproval,
  QdrantProductionCandidateReadiness,
  QdrantProductionCandidateReadinessState,
  QdrantProductionCandidateReadinessStatus,
}
import org.scalatest.wordspec.AnyWordSpec

final class QdrantProductionCandidateM7ConfigApprovalSpec extends AnyWordSpec {
  import QdrantProductionCandidateActivationApprovalStatus.*
  import QdrantProductionCandidateActivationConfigApprovalStatus.*
  import QdrantProductionCandidateActivationConfigGate.*
  import QdrantProductionCandidateActivationRequirementStatus.*
  import QdrantProductionCandidateReadinessStatus.Ready

  "QdrantProductionCandidateActivationConfigApproval" should {
    "be disabled and blocked by default" in {
      val report = evaluate(QdrantProductionCandidateActivationConfigApproval.conservativeDefault)

      assert(report.config.configGate == Disabled)
      assert(report.planningConfigGate == Missing)
      assert(report.planningNoRegressionEvidence == Unknown)
      assert(report.decision.status == Blocked)
      assert(report.decision.blockingReasons == List(
        "Qdrant activation config gate is disabled",
        "No-regression evidence status is unknown",
        "No-regression evidence is not approved",
      ))
    }

    "require approved no-regression evidence even when the config gate is enabled" in {
      val report = evaluate(completeConfig.copy(
        noRegression = QdrantProductionCandidateNoRegressionApproval(
          evidence = Satisfied,
          approval = NotApproved,
        )
      ))

      assert(report.planningConfigGate == Satisfied)
      assert(report.planningNoRegressionEvidence == Missing)
      assert(report.decision.status == Blocked)
      assert(report.decision.blockingReasons == List("No-regression evidence is not approved"))
    }

    "preserve deterministic blockers for disabled config and incomplete evidence" in {
      val report = evaluate(QdrantProductionCandidateActivationConfigApproval(
        configGate = Disabled,
        noRegression = QdrantProductionCandidateNoRegressionApproval(
          evidence = Missing,
          approval = NotRequired,
        ),
      ))

      assert(report.decision.blockingReasons == List(
        "Qdrant activation config gate is disabled",
        "No-regression evidence is missing",
        "No-regression evidence approval is required",
      ))
    }

    "map enabled config and approved evidence to planning prerequisites" in {
      val report = evaluate(completeConfig)
      val prerequisites =
        QdrantProductionCandidateActivationConfigApproval.applyToPlanningPrerequisites(report, basePrerequisites)

      assert(report.decision.status == ReadyForPlanning)
      assert(report.decision.blockingReasons.isEmpty)
      assert(prerequisites.configGate == Satisfied)
      assert(prerequisites.noRegressionEvidence == Satisfied)
    }

    "compose with M7 planning without approving production-route activation" in {
      val report = evaluate(completeConfig)
      val prerequisites =
        QdrantProductionCandidateActivationConfigApproval.applyToPlanningPrerequisites(
          report,
          basePrerequisites.copy(servingApproval = NotApproved),
        )
      val decision = QdrantProductionCandidateActivationPlanning.evaluate(
        QdrantProductionCandidateActivationTargetScope.ProductionRouteActivation,
        prerequisites,
      )

      assert(report.decision.status == ReadyForPlanning)
      assert(decision.status == QdrantProductionCandidateActivationPlanningStatus.Blocked)
      assert(decision.blockingReasons == List("Separate route/serving approval is not approved"))
    }

    "contain config and approval evidence only without route implementation behavior" in {
      val fieldNames =
        completeConfig.productElementNames.toSet ++
          completeConfig.noRegression.productElementNames.toSet
      val forbiddenTerms = List(
        "switch",
        "fallback",
        "fusion",
        "rerank",
        "supplement",
        "mirror",
        "traffic",
      )

      assert(fieldNames == Set("configGate", "noRegression", "evidence", "approval"))
      assert(!fieldNames.exists(field => forbiddenTerms.exists(field.toLowerCase.contains)))
    }
  }

  private val completeConfig =
    QdrantProductionCandidateActivationConfigApproval(
      configGate = Enabled,
      noRegression = QdrantProductionCandidateNoRegressionApproval(
        evidence = Satisfied,
        approval = Approved,
      ),
    )

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

  private val basePrerequisites =
    QdrantProductionCandidateActivationPrerequisites(
      m6ReadinessReport = Some(QdrantProductionCandidateReadiness.evaluate(allReadyState)),
      activationPolicyReport = Some(QdrantProductionCandidateActivationPolicy.evaluate(explicitOptInPolicy)),
      configGate = Missing,
      noRegressionEvidence = Unknown,
      observabilityStatus = Satisfied,
      rollbackDisableControl = Satisfied,
      servingApproval = Approved,
    )

  private def evaluate(config: QdrantProductionCandidateActivationConfigApproval) =
    QdrantProductionCandidateActivationConfigApproval.evaluate(config)
}
