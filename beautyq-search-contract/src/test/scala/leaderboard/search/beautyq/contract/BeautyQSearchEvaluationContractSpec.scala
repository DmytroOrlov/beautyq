package leaderboard.search.beautyq.contract

import leaderboard.search.contract.{EvalProductionRoutingEffect, SearchBackendId}
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQSearchEvaluationContractSpec extends AnyWordSpec {

  "BeautyQSearchEvaluationContract.datasetMetadata" should {
    "describe the 89-query BeautyQ seed eval dataset" in {
      assert(BeautyQSearchEvaluationContract.datasetMetadata.queryCount == 89)
      assert(BeautyQSearchEvaluationContract.datasetMetadata.datasetId == "wandsbek_hamburg_beauty_services_seed_ready")
    }
  }

  "BeautyQSearchEvaluationContract.staticRows" should {
    "map exactly as many rows as the dataset metadata query count" in {
      assert(BeautyQSearchEvaluationContract.staticRows.summary.mappedRowCount == BeautyQSearchEvaluationContract.datasetMetadata.queryCount)
      assert(BeautyQSearchEvaluationContract.staticRows.summary.mappedRowCount == 89)
    }
  }

  "BeautyQSearchEvaluationContract.staticScorecard" should {
    "stay static/offline, never claiming production activation approval" in {
      assert(BeautyQSearchEvaluationContract.staticScorecard.productionActivationApproval == false)
      assert(BeautyQSearchEvaluationContract.staticScorecard.realBackendCallRequired == false)
      assert(BeautyQSearchEvaluationContract.staticScorecard.routePluginDiHttpInvolved == false)
    }
  }

  "BeautyQSearchEvaluationContract.fullClassificationCoverage" should {
    "cover all 89 dataset rows" in {
      assert(BeautyQSearchEvaluationContract.fullClassificationCoverage.totalQueryCount == 89)
      assert(BeautyQSearchEvaluationContract.fullClassificationCoverage.mappedRowCount == 89)
    }
  }

  "BeautyQSearchEvaluationContract.offlineRoutingBoundary" should {
    "have every production activation / execution / behavior-change claim false" in {
      val boundary = BeautyQSearchEvaluationContract.offlineRoutingBoundary
      assert(boundary.qdrantProductionActivationApproved == false)
      assert(boundary.productionRouteActivated == false)
      assert(boundary.defaultRouteSwitched == false)
      assert(boundary.productionBeautySearchCalled == false)
      assert(boundary.esClientCreated == false)
      assert(boundary.qdrantClientCreated == false)
      assert(boundary.esExecuted == false)
      assert(boundary.qdrantExecuted == false)
      assert(boundary.hybridServingImplied == false)
      assert(boundary.fallbackImplied == false)
      assert(boundary.scoreFusionImplied == false)
      assert(boundary.rerankingImplied == false)
      assert(boundary.productionTelemetryImplied == false)
      assert(boundary.routeActivationClaimed == false)
      assert(boundary.servingApprovalClaimed == false)
    }
  }

  "BeautyQSearchEvaluationContract.section" should {
    "never activate production routing" in {
      assert(BeautyQSearchEvaluationContract.section.productionRoutingEffect == EvalProductionRoutingEffect.None)
    }

    "declare ES and Qdrant backend expectations with no minimum recall claimed" in {
      val backendIds = BeautyQSearchEvaluationContract.section.backendExpectations.map(_.backendId)
      assert(backendIds.contains(SearchBackendId("elasticsearch")))
      assert(backendIds.contains(SearchBackendId("qdrant")))
      assert(BeautyQSearchEvaluationContract.section.backendExpectations.forall(_.expectedMinRecall.isEmpty))
    }

    "include representative static-scorecard and full-classification-coverage metrics" in {
      val metrics = BeautyQSearchEvaluationContract.section.scorecard.metrics
      assert(metrics.contains("verdict"))
      assert(metrics.contains("dataset_query_count"))
      assert(metrics.contains("total_query_count"))
      assert(metrics.contains("m11_backend_candidate_inputs_ready"))
    }

    "never activate production routing regardless of full SearchDomainSpec declaration" in {
      assert(BeautyQSearchDomainContract.fullSearchDomainSpecDeclared == true)
      assert(BeautyQSearchEvaluationContract.section.productionRoutingEffect == EvalProductionRoutingEffect.None)
    }

    "declare the same backend ids as BeautyQSearchRuntimeContract" in {
      val evaluationBackendIds = BeautyQSearchEvaluationContract.section.backendExpectations.map(_.backendId)
      val runtimeBackendIds = BeautyQSearchRuntimeContract.section.declarations.map(_.backendId)
      assert(evaluationBackendIds.forall(runtimeBackendIds.contains))
      assert(runtimeBackendIds.forall(evaluationBackendIds.contains))
    }
  }
}
