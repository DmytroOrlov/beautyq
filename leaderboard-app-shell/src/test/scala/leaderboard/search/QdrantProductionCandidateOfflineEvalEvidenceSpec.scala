package leaderboard.search

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.eval.{
  BeautySearchEvalQuery,
  BeautySearchEvalReport,
  EngineEvalReportAssembly,
  EngineEvalReportJson,
  EngineExpectedRole,
  EvalCarouselWeights,
  EvalProviderExpectation,
  EvalScoring,
  EvalServiceExpectation,
  EvalVariantExpectation,
}
import leaderboard.search.qdrant.{
  QdrantEmbeddingBenchmarkQueryResult,
  QdrantProductionCandidateActivationApprovalStatus,
  QdrantProductionCandidateActivationConfigApproval,
  QdrantProductionCandidateActivationConfigApprovalStatus,
  QdrantProductionCandidateActivationConfigGate,
  QdrantProductionCandidateActivationRequirementStatus,
  QdrantProductionCandidateNoRegressionApproval,
  QdrantProductionCandidateParityReport,
  QdrantProductionCandidateQualityGate,
  QdrantProductionCandidateQualityRule,
}
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

final class QdrantProductionCandidateOfflineEvalEvidenceSpec extends AnyWordSpec {
  import QdrantProductionCandidateActivationApprovalStatus.*
  import QdrantProductionCandidateActivationConfigApprovalStatus.*
  import QdrantProductionCandidateActivationConfigGate.*
  import QdrantProductionCandidateActivationRequirementStatus.*
  import leaderboard.search.qdrant.QdrantProductionCandidateQualityDecisionStatus.Passed

  "offline EngineEval evidence" should {
    "map assembled and saved aggregate evidence into quality and separately approved no-regression planning input" in {
      val assembled = EngineEvalReportAssembly.fromOutputs(
        queries = List(
          evalQuery("q_offline_recall", variantId(1)),
          evalQuery("q_offline_silent", variantId(2)),
        ),
        expectedRolesByQueryId = Map(
          "q_offline_recall" -> EngineExpectedRole.QdrantMayComplement,
          "q_offline_silent" -> EngineExpectedRole.QdrantShouldStaySilent,
        ),
        esReports = List(
          esReport("q_offline_recall", List(variantId(1))),
          esReport("q_offline_silent", Nil),
        ),
        qdrantResults = List(
          qdrantResult("q_offline_recall", List(variantId(1))),
          qdrantResult("q_offline_silent", Nil),
        ),
      )

      val savedReport = assembled.flatMap(report =>
        EngineEvalReportJson.decodeReportString(EngineEvalReportJson.encodeReportString(report))
      )

      savedReport match {
        case Right(engineEvalReport) =>
          val qualityReport = QdrantProductionCandidateQualityGate.fromEngineEval(
            report = engineEvalReport,
            rule = QdrantProductionCandidateQualityRule(
              minimumEvaluatedQueryCount = 2,
              maximumRecallDeficit = 0,
              maximumQdrantNoiseCount = 0,
            ),
          )
          val noRegressionEvidence =
            QdrantProductionCandidateActivationConfigApproval.noRegressionEvidenceFromQuality(Some(qualityReport))

          val unapproved = QdrantProductionCandidateActivationConfigApproval.evaluate(
            config(noRegressionEvidence, NotApproved)
          )
          val approved = QdrantProductionCandidateActivationConfigApproval.evaluate(
            config(noRegressionEvidence, Approved)
          )

          assert(qualityReport.decision.status == Passed)
          assert(noRegressionEvidence == Satisfied)
          assert(unapproved.planningNoRegressionEvidence == Missing)
          assert(unapproved.decision.status == Blocked)
          assert(approved.planningNoRegressionEvidence == Satisfied)
          assert(approved.decision.status == ReadyForPlanning)
        case Left(failure) =>
          fail(s"expected assembled and saved EngineEval evidence, got $failure")
      }
    }

    "keep absent, failed, and incomplete quality evidence from satisfying no-regression input" in {
      val failed = QdrantProductionCandidateQualityGate.evaluate(
        parity = QdrantProductionCandidateParityReport(
          baselineLabel = "Elasticsearch",
          candidateLabel = "Qdrant",
          evaluatedQueryCount = 2,
          baselineRecallCount = 2,
          candidateRecallCount = 1,
          qdrantNoiseCount = 0,
        ),
        rule = QdrantProductionCandidateQualityRule(2, 0, 0),
      )
      val incomplete = QdrantProductionCandidateQualityGate.evaluate(
        parity = failed.parity.copy(candidateLabel = " "),
        rule = failed.rule,
      )

      assert(QdrantProductionCandidateActivationConfigApproval.noRegressionEvidenceFromQuality(None) == Unknown)
      assert(QdrantProductionCandidateActivationConfigApproval.noRegressionEvidenceFromQuality(Some(failed)) == Missing)
      assert(QdrantProductionCandidateActivationConfigApproval.noRegressionEvidenceFromQuality(Some(incomplete)) == Unknown)
    }
  }

  private def config(
    evidence: QdrantProductionCandidateActivationRequirementStatus,
    approval: QdrantProductionCandidateActivationApprovalStatus,
  ): QdrantProductionCandidateActivationConfigApproval =
    QdrantProductionCandidateActivationConfigApproval(
      configGate = Enabled,
      noRegression = QdrantProductionCandidateNoRegressionApproval(evidence, approval),
    )

  private def variantId(slot: Int): MasterServiceOfferVariantId =
    MasterServiceOfferVariantId(UUID.fromString(f"00000000-0000-0000-0000-00000000${slot}%04x"))

  private def evalQuery(
    id: String,
    acceptableVariantId: MasterServiceOfferVariantId,
  ): BeautySearchEvalQuery =
    BeautySearchEvalQuery(
      id = id,
      query = s"query $id",
      language = "de",
      queryTypes = Nil,
      expectedVariantCarousel = EvalVariantExpectation(acceptableVariantIds = List(acceptableVariantId)),
      expectedProviderCarousel = EvalProviderExpectation(),
      expectedServiceIntentCarousel = EvalServiceExpectation(),
      scoring = EvalScoring(EvalCarouselWeights(), EvalCarouselWeights(), EvalCarouselWeights()),
    )

  private def esReport(
    queryId: String,
    topVariantIds: List[MasterServiceOfferVariantId],
  ): BeautySearchEvalReport =
    BeautySearchEvalReport(
      queryId = queryId,
      query = s"query $queryId",
      score = 0,
      failedAssertions = Nil,
      topVariantIds = topVariantIds,
      topProviderLocationIds = Nil,
      topServiceIds = Nil,
    )

  private def qdrantResult(
    queryId: String,
    topVariantIds: List[MasterServiceOfferVariantId],
  ): QdrantEmbeddingBenchmarkQueryResult =
    QdrantEmbeddingBenchmarkQueryResult(
      candidateId = "offline-candidate",
      queryId = queryId,
      queryText = s"query $queryId",
      topVariantIds = topVariantIds,
      topProviderIds = Nil,
      topServiceIds = Nil,
      scores = Nil,
    )
}
