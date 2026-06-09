package leaderboard.search

import leaderboard.model.QueryFailure
import leaderboard.search.dsl.VectorDistance
import leaderboard.search.hybrid.control._
import leaderboard.search.hybrid.production._
import org.scalatest.wordspec.AnyWordSpec
import izumi.functional.bio._
import zio.{IO, ZIO}

final class BeautySearchHybridProductionActivationSpec extends AnyWordSpec {

  "default activation" should {
    "be Disabled" in {
      assert(BeautySearchHybridProductionActivation.default == BeautySearchHybridProductionActivation.Disabled)
    }
  }

  "Disabled activation" should {
    "carry no serving policy" in {
      val activation: BeautySearchHybridProductionActivation = BeautySearchHybridProductionActivation.Disabled

      val extractedPolicy: Option[BeautySearchHybridServingPolicy] = activation match {
        case BeautySearchHybridProductionActivation.Enabled(p) => Some(p)
        case BeautySearchHybridProductionActivation.Disabled => None
      }

      assert(extractedPolicy.isEmpty)
    }
  }

  "Enabled activation" should {
    "preserve serving policy" in {
      val policy = BeautySearchHybridServingPolicy(
        mode = BeautySearchHybridRuntimeMode.HybridShadow,
        requireReadyForServing = false,
      )
      val activation: BeautySearchHybridProductionActivation = BeautySearchHybridProductionActivation.Enabled(policy)

      activation match {
        case BeautySearchHybridProductionActivation.Enabled(actualPolicy) =>
          assert(actualPolicy == policy)

        case other =>
          fail(s"Expected Enabled, got $other")
      }
    }
  }

  "disabled handle" should {
    "have no evaluator" in {
      val handle = BeautySearchHybridProductionHandle.disabled[IO]

      assert(handle.evaluator.isEmpty)
    }
  }

  "enabled handle" should {
    "preserve evaluator reference" in {
      val ready = BeautySearchHybridReadinessStatus.Ready(
        snapshot = BeautySearchHybridSnapshotIdentity("seed", "v1", 10),
        collection = BeautySearchHybridCollectionIdentity("c", "v", "m", 1024, VectorDistance.Cosine),
        indexedDocumentCount = 10,
      )
      val policy = BeautySearchHybridServingPolicy.default
      val readiness = new BeautySearchHybridReadiness[IO] {
        override def status(): IO[QueryFailure, BeautySearchHybridReadinessStatus] = ZIO.succeed(ready)
      }
      val sink = new BeautySearchHybridDiagnosticsSink[IO] {
        override def report(event: BeautySearchHybridDiagnosticsEvent): IO[Nothing, Unit] = ZIO.unit
      }
      val evaluator = new BeautySearchHybridDecisionEvaluator[IO](policy, readiness, sink)
      val handle = BeautySearchHybridProductionHandle.enabled[IO](evaluator)

      handle.evaluator match {
        case Some(actual) =>
          assert(actual eq evaluator)

        case None =>
          fail("Expected enabled handle with evaluator")
      }
    }
  }
}
