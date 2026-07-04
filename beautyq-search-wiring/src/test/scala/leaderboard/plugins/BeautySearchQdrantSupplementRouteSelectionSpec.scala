package leaderboard.plugins

import leaderboard.api.BeautySearchServingGate
import org.scalatest.wordspec.AnyWordSpec

final class BeautySearchQdrantSupplementRouteSelectionSpec extends AnyWordSpec {
  import BeautySearchQdrantSupplementRouteSelection._

  "BeautySearchQdrantSupplementRouteSelectionPolicy.selectionFor" should {
    "map EsOnlyRollback to EsOnlyRollbackRoute with no serving gate and no Qdrant route selected" in {
      assert(BeautySearchQdrantSupplementRouteSelectionPolicy.selectionFor(BeautySearchQdrantSupplementActivation.EsOnlyRollback) == EsOnlyRollbackRoute)
      assert(EsOnlyRollbackRoute.qdrantSupplementRouteSelected == false)
      assert(EsOnlyRollbackRoute.servingGate == None)
    }

    "map QdrantSupplementNotReady to QdrantSupplementNotReadyRoute with the not-ready serving gate" in {
      assert(BeautySearchQdrantSupplementRouteSelectionPolicy.selectionFor(BeautySearchQdrantSupplementActivation.QdrantSupplementNotReady) == QdrantSupplementNotReadyRoute)
      assert(QdrantSupplementNotReadyRoute.qdrantSupplementRouteSelected == true)
      assert(QdrantSupplementNotReadyRoute.servingGate.contains(BeautySearchServingGate.enabledNotReady))
    }

    "map QdrantSupplementReady to QdrantSupplementReadyRoute with the ready serving gate" in {
      assert(BeautySearchQdrantSupplementRouteSelectionPolicy.selectionFor(BeautySearchQdrantSupplementActivation.QdrantSupplementReady) == QdrantSupplementReadyRoute)
      assert(QdrantSupplementReadyRoute.qdrantSupplementRouteSelected == true)
      assert(QdrantSupplementReadyRoute.servingGate.contains(BeautySearchServingGate.enabledReady))
    }
  }

  "BeautySearchQdrantSupplementRouteSelectionPolicy.selectionForOperatorValue" should {
    "default to EsOnlyRollbackRoute when the operator value is absent" in {
      assert(BeautySearchQdrantSupplementRouteSelectionPolicy.selectionForOperatorValue(None) == Right(EsOnlyRollbackRoute))
    }

    "map the es-only-rollback operator value to EsOnlyRollbackRoute" in {
      assert(
        BeautySearchQdrantSupplementRouteSelectionPolicy.selectionForOperatorValue(
          Some(BeautySearchQdrantSupplementActivationConfig.EsOnlyRollbackOperatorValue)
        ) == Right(EsOnlyRollbackRoute)
      )
    }

    "map the qdrant-supplement-not-ready operator value to QdrantSupplementNotReadyRoute" in {
      assert(
        BeautySearchQdrantSupplementRouteSelectionPolicy.selectionForOperatorValue(
          Some(BeautySearchQdrantSupplementActivationConfig.QdrantSupplementNotReadyOperatorValue)
        ) == Right(QdrantSupplementNotReadyRoute)
      )
    }

    "map the qdrant-supplement-ready operator value to QdrantSupplementReadyRoute" in {
      assert(
        BeautySearchQdrantSupplementRouteSelectionPolicy.selectionForOperatorValue(
          Some(BeautySearchQdrantSupplementActivationConfig.QdrantSupplementReadyOperatorValue)
        ) == Right(QdrantSupplementReadyRoute)
      )
    }

    "fail closed on an unrecognized operator value" in {
      BeautySearchQdrantSupplementRouteSelectionPolicy.selectionForOperatorValue(Some("not-a-real-value")) match {
        case Left(failure) =>
          assert(failure.message.contains("Unrecognized"))
        case Right(selection) =>
          fail(s"Expected a rejected parse, got $selection")
      }
    }
  }
}
