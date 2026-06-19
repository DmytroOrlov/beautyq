package leaderboard.search

import leaderboard.search.dsl.VectorDistance
import leaderboard.search.qdrant.{
  QdrantCollectionCompatibilityMismatch,
  QdrantProductionCandidateReadiness,
  QdrantProductionCandidateReadinessState,
  QdrantProductionCandidateReadinessStatus,
}
import org.scalatest.wordspec.AnyWordSpec

final class QdrantProductionCandidateReadinessSpec extends AnyWordSpec {
  import QdrantProductionCandidateReadinessStatus.*

  "QdrantProductionCandidateReadiness" should {
    "keep the active conservative default not production-candidate-ready" in {
      val report = QdrantProductionCandidateReadiness.evaluate(QdrantProductionCandidateReadiness.conservativeDefault)

      assert(report.state.qdrantActive)
      assert(report.state.qualityEval == NotEvaluated)
      assert(report.state.rollbackDisable == NotConfigured)
      assert(report.state.activationPolicy == NotApproved)
      assert(!report.productionCandidateReady)
    }

    "be production-candidate-ready only when every required category is explicitly ready" in {
      val report = QdrantProductionCandidateReadiness.evaluate(allReadyState)

      assert(report.productionCandidateReady)
    }

    "stay not ready for each missing required category" in {
      val statesWithMissingCategory = List(
        allReadyState.copy(collectionIdentity = Unknown),
        allReadyState.copy(contractParity = Unknown),
        allReadyState.copy(indexing = Unknown),
        allReadyState.copy(search = Unknown),
        allReadyState.copy(qualityEval = Unknown),
        allReadyState.copy(observability = Unknown),
        allReadyState.copy(rollbackDisable = Unknown),
        allReadyState.copy(activationPolicy = Unknown),
      )

      assert(statesWithMissingCategory.forall { state =>
        !QdrantProductionCandidateReadiness.evaluate(state).productionCandidateReady
      })
    }

    "stay not ready when quality evaluation has not been evaluated" in {
      val report = QdrantProductionCandidateReadiness.evaluate(allReadyState.copy(qualityEval = NotEvaluated))

      assert(!report.productionCandidateReady)
    }

    "stay not ready when activation policy has not been approved" in {
      val report = QdrantProductionCandidateReadiness.evaluate(allReadyState.copy(activationPolicy = NotApproved))

      assert(!report.productionCandidateReady)
    }

    "stay not ready when rollback and disable controls have not been configured" in {
      val report = QdrantProductionCandidateReadiness.evaluate(allReadyState.copy(rollbackDisable = NotConfigured))

      assert(!report.productionCandidateReady)
    }

    "stay not ready when Qdrant is inactive even if every category is ready" in {
      val report = QdrantProductionCandidateReadiness.evaluate(allReadyState.copy(qdrantActive = false))

      assert(!report.productionCandidateReady)
    }

    "adapt a compatible existing collection result without changing its success semantics" in {
      val state = QdrantProductionCandidateReadiness.withCollectionCompatibility(allReadyState, Right(()))

      assert(state.collectionIdentity == Ready)
    }

    "adapt existing collection mismatches without changing their order or meaning" in {
      val mismatches = List(
        QdrantCollectionCompatibilityMismatch.DimensionMismatch(1024, 768),
        QdrantCollectionCompatibilityMismatch.DistanceMismatch(VectorDistance.Cosine, VectorDistance.Dot),
      )

      val state = QdrantProductionCandidateReadiness.withCollectionCompatibility(allReadyState, Left(mismatches))

      assert(state.collectionIdentity == NotReady(List(
        "DimensionMismatch(expected=1024, observed=768)",
        "DistanceMismatch(expected=Cosine, observed=Dot)",
      )))
      assert(!QdrantProductionCandidateReadiness.evaluate(state).productionCandidateReady)
    }

    "require only production-candidate categories and no shadow or traffic-mirroring fields" in {
      val fieldNames = QdrantProductionCandidateReadiness.conservativeDefault.productElementNames.toSet

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
      assert(!fieldNames.exists(_.toLowerCase.contains("shadow")))
      assert(!fieldNames.exists(_.toLowerCase.contains("mirror")))
      assert(!fieldNames.exists(_.toLowerCase.contains("traffic")))
    }
  }

  private val allReadyState: QdrantProductionCandidateReadinessState =
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
