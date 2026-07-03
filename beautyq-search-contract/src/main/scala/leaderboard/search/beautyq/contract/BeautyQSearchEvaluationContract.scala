package leaderboard.search.beautyq.contract

import leaderboard.search.contract.*
import leaderboard.search.eval.*

/** BeautyQ static/offline evaluation contract section.
  *
  * This is declarative evaluation metadata only. It does not parse the checked-in
  * JSON resource, does not execute ES/Qdrant, does not create clients, does not
  * activate routes, and does not approve Qdrant production serving.
  */
object BeautyQSearchEvaluationContract {

  val datasetMetadata: M9BeautyQSearchEvalQueryDatasetMetadata =
    M9BeautyQSearchEvalQueryDataset.Metadata

  val staticRows: M9BeautyQSearchEvalQueryDatasetStaticRowsResult =
    M9BeautyQSearchEvalQueryDatasetStaticRows.DefaultResult

  val staticScorecard: M9BeautyQSearchEvalStaticScorecardSummary =
    M9BeautyQSearchEvalStaticScorecard.DefaultSummary

  val fullClassificationCoverage: M10BeautyQSearchFullClassificationCoverageSummary =
    M10BeautyQSearchFullQueryClassificationCoverageScorecard.DefaultSummary

  val offlineRoutingBoundary: M10BeautyQSearchOfflineRoutingBoundary =
    M10BeautyQSearchOfflineRoutingBoundary.Standing

  val section: EvalSection =
    EvalSection(
      acceptedQueryRoles = List(
        EvalQueryRole.Golden,
        EvalQueryRole.Negative,
        EvalQueryRole.Exploratory,
      ),
      negativeControls =
        M10BeautyQSearchFullQueryClassification.AcceptedNegativeControlQueryIds,
      backendExpectations = List(
        EvalBackendExpectation(
          backendId = SearchBackendId("elasticsearch"),
          expectedMinRecall = None,
        ),
        EvalBackendExpectation(
          backendId = SearchBackendId("qdrant"),
          expectedMinRecall = None,
        ),
      ),
      scorecard = EvalScorecardConfig(
        metrics =
          (
            staticScorecard.metrics.map(_.name) ++
              fullClassificationCoverage.metrics.map(_.name)
          ).distinct,
      ),
      productionRoutingEffect = EvalProductionRoutingEffect.None,
    )
}
