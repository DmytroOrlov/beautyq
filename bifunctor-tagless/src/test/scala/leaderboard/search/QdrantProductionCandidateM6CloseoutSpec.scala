package leaderboard.search

import leaderboard.search.qdrant.{
  QdrantProductionCandidateActivationApprovalStatus,
  QdrantProductionCandidateActivationPolicy,
  QdrantProductionCandidateActivationRequirementStatus,
  QdrantProductionCandidateActivationScope,
  QdrantProductionCandidateIndexingEvidence,
  QdrantProductionCandidateIndexingReadiness,
  QdrantProductionCandidateObservabilityEvidence,
  QdrantProductionCandidateObservabilityReadiness,
  QdrantProductionCandidateParityReport,
  QdrantProductionCandidateQualityGate,
  QdrantProductionCandidateQualityRule,
  QdrantProductionCandidateReadiness,
  QdrantProductionCandidateReadinessState,
  QdrantProductionCandidateReadinessStatus,
  QdrantProductionCandidateRollbackEvidence,
  QdrantProductionCandidateRollbackReadiness,
  QdrantProductionCandidateSearchReadiness,
}
import org.scalatest.wordspec.AnyWordSpec

final class QdrantProductionCandidateM6CloseoutSpec extends AnyWordSpec {
  import QdrantProductionCandidateActivationApprovalStatus.NotRequired
  import QdrantProductionCandidateActivationRequirementStatus.Satisfied
  import QdrantProductionCandidateActivationScope.CandidateReadinessOnly
  import QdrantProductionCandidateReadinessStatus.*

  "M6 Qdrant production-candidate readiness foundation" should {
    "represent all eight required readiness categories without serving behavior" in {
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

      assert(fieldNames == Set(
        "qdrantActive",
        "collectionIdentity",
        "contractParity",
        "indexing",
        "search",
        "qualityEval",
        "observability",
        "rollbackDisable",
        "activationPolicy",
      ))
      assert(!fieldNames.exists(field => forbiddenTerms.exists(field.toLowerCase.contains)))
    }

    "keep the conservative default not production-candidate-ready" in {
      val report = QdrantProductionCandidateReadiness.evaluate(QdrantProductionCandidateReadiness.conservativeDefault)

      assert(report.state.qdrantActive)
      assert(report.state.collectionIdentity == Unknown)
      assert(report.state.contractParity == Unknown)
      assert(report.state.indexing == Unknown)
      assert(report.state.search == Unknown)
      assert(report.state.qualityEval == NotEvaluated)
      assert(report.state.observability == NotConfigured)
      assert(report.state.rollbackDisable == NotConfigured)
      assert(report.state.activationPolicy == NotApproved)
      assert(!report.productionCandidateReady)
    }

    "block readiness when any required category is missing or not ready" in {
      val categoryUpdates = List[(String, QdrantProductionCandidateReadinessStatus, QdrantProductionCandidateReadinessState => QdrantProductionCandidateReadinessState)](
        ("collection/identity", Unknown, _.copy(collectionIdentity = Unknown)),
        ("collection/identity", NotReady(List("collection mismatch")), _.copy(collectionIdentity = NotReady(List("collection mismatch")))),
        ("contract parity", Unknown, _.copy(contractParity = Unknown)),
        ("contract parity", NotReady(List("contract mismatch")), _.copy(contractParity = NotReady(List("contract mismatch")))),
        ("indexing", Unknown, _.copy(indexing = Unknown)),
        ("indexing", NotReady(List("index incomplete")), _.copy(indexing = NotReady(List("index incomplete")))),
        ("search", Unknown, _.copy(search = Unknown)),
        ("search", NotReady(List("search incomplete")), _.copy(search = NotReady(List("search incomplete")))),
        ("quality/eval", NotEvaluated, _.copy(qualityEval = NotEvaluated)),
        ("quality/eval", NotReady(List("quality gate failed")), _.copy(qualityEval = NotReady(List("quality gate failed")))),
        ("observability", NotConfigured, _.copy(observability = NotConfigured)),
        ("observability", NotReady(List("reports missing")), _.copy(observability = NotReady(List("reports missing")))),
        ("rollback/disable", NotConfigured, _.copy(rollbackDisable = NotConfigured)),
        ("rollback/disable", NotReady(List("disable control missing")), _.copy(rollbackDisable = NotReady(List("disable control missing")))),
        ("activation policy", NotApproved, _.copy(activationPolicy = NotApproved)),
        ("activation policy", NotReady(List("activation controls missing")), _.copy(activationPolicy = NotReady(List("activation controls missing")))),
      )

      categoryUpdates.foreach { case (category, status, update) =>
        val report = QdrantProductionCandidateReadiness.evaluate(update(allReadyState))
        assert(!report.productionCandidateReady, s"$category with status $status must block production-candidate readiness")
      }
    }

    "compose every source-backed category adapter into an all-ready candidate state" in {
      val qualityReport = QdrantProductionCandidateQualityGate.evaluate(
        parity = QdrantProductionCandidateParityReport(
          baselineLabel = "Elasticsearch",
          candidateLabel = "Qdrant",
          evaluatedQueryCount = 10,
          baselineRecallCount = 8,
          candidateRecallCount = 8,
          qdrantNoiseCount = 0,
        ),
        rule = QdrantProductionCandidateQualityRule(
          minimumEvaluatedQueryCount = 10,
          maximumRecallDeficit = 0,
          maximumQdrantNoiseCount = 0,
        ),
      )
      val indexingReport = QdrantProductionCandidateIndexingReadiness.evaluate(
        QdrantProductionCandidateIndexingEvidence(
          expectedDocumentCount = 10,
          preparedDocumentCount = 10,
          indexedDocumentCount = Some(10),
          collectionIdentityReadiness = Ready,
          embeddingVectorReady = true,
        )
      )
      val searchReport = QdrantProductionCandidateSearchReadiness.fromExistingSourceContracts(
        beautySearchContractParityReady = true
      )
      val observabilityReport = QdrantProductionCandidateObservabilityReadiness.evaluate(
        QdrantProductionCandidateObservabilityEvidence(
          readinessStatusReportAvailable = true,
          qualityEvalReportAvailable = true,
          activationDecisionReportAvailable = true,
        )
      )
      val rollbackReport = QdrantProductionCandidateRollbackReadiness.evaluate(
        QdrantProductionCandidateRollbackEvidence(
          disableControlDocumentedOrConfigured = true,
          rollbackPathDocumentedOrConfigured = true,
          noRegressionEvidenceAvailable = true,
        )
      )

      val collectionReady = QdrantProductionCandidateReadiness.withCollectionCompatibility(adapterBaseState, Right(()))
      val qualityReady = QdrantProductionCandidateReadiness.withQualityReport(collectionReady, Some(qualityReport))
      val activationReady = QdrantProductionCandidateReadiness.withActivationPolicy(qualityReady, Some(candidateReadinessPolicy))
      val indexingReady = QdrantProductionCandidateReadiness.withIndexingReadiness(activationReady, Some(indexingReport))
      val searchReady = QdrantProductionCandidateReadiness.withSearchReadiness(indexingReady, Some(searchReport))
      val observabilityReady =
        QdrantProductionCandidateReadiness.withObservabilityReadiness(searchReady, Some(observabilityReport))
      val completeState =
        QdrantProductionCandidateReadiness.withRollbackDisableReadiness(observabilityReady, Some(rollbackReport))
      val report = QdrantProductionCandidateReadiness.evaluate(completeState)

      assert(completeState == allReadyState)
      assert(!rollbackReport.decision.productionRouteActivationApproved)
      assert(candidateReadinessPolicy.scope == CandidateReadinessOnly)
      assert(candidateReadinessPolicy.routeServingApproval == NotRequired)
      assert(report.productionCandidateReady)
    }

    "remain not production-candidate-ready when Qdrant is inactive even if every category is ready" in {
      val report = QdrantProductionCandidateReadiness.evaluate(allReadyState.copy(qdrantActive = false))

      assert(!report.productionCandidateReady)
    }
  }

  private val candidateReadinessPolicy =
    QdrantProductionCandidateActivationPolicy(
      explicitlyApproved = true,
      scope = CandidateReadinessOnly,
      routeServingApproval = NotRequired,
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

  private val adapterBaseState =
    QdrantProductionCandidateReadiness.conservativeDefault.copy(contractParity = Ready)
}
