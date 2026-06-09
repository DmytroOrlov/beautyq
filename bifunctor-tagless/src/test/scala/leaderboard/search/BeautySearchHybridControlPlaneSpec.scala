package leaderboard.search

import leaderboard.model.QueryFailure
import leaderboard.search.dsl.VectorDistance
import leaderboard.search.hybrid.control._
import org.scalatest.wordspec.AnyWordSpec
import izumi.functional.bio._
import zio.{IO, Runtime, Unsafe, ZIO}

final class BeautySearchHybridControlPlaneSpec extends AnyWordSpec {

  "BeautySearchHybridRuntimeMode" should {
    "default to SeedCatalogOnly" in {
      assert(BeautySearchHybridRuntimeMode.default == BeautySearchHybridRuntimeMode.SeedCatalogOnly)
    }
  }

  "BeautySearchHybridServingPolicy" should {
    "default to SeedCatalogOnly mode with readiness required" in {
      val policy = BeautySearchHybridServingPolicy.default

      assert(policy.mode == BeautySearchHybridRuntimeMode.SeedCatalogOnly)
      assert(policy.requireReadyForServing == true)
    }
  }

  "BeautySearchHybridReadinessStatus.Ready" should {
    "preserve snapshot and collection identity" in {
      val snapshot = BeautySearchHybridSnapshotIdentity(
        sourceId = "seed-catalog",
        snapshotVersion = "v1",
        documentCount = 42,
      )

      val collection = BeautySearchHybridCollectionIdentity(
        collectionName = "beauty_hybrid_v1",
        vectorName = "variant-embedding",
        embeddingModelName = "llama-cpp-embedding",
        embeddingDimension = 1024,
        distance = VectorDistance.Cosine,
      )

      val status = BeautySearchHybridReadinessStatus.Ready(
        snapshot = snapshot,
        collection = collection,
        indexedDocumentCount = 42,
      )

      assert(status.snapshot == snapshot)
      assert(status.collection == collection)
      assert(status.indexedDocumentCount == 42)
    }
  }

  "BeautySearchHybridReadinessStatus.NotReady" should {
    "preserve reason" in {
      val reason = "collection does not exist"
      val status = BeautySearchHybridReadinessStatus.NotReady(reason)

      assert(status.reason == reason)
    }
  }

  "BeautySearchHybridReadinessStatus.isReady" should {
    "return true for Ready status" in {
      val ready = BeautySearchHybridReadinessStatus.Ready(
        snapshot = BeautySearchHybridSnapshotIdentity("src", "v1", 0),
        collection = BeautySearchHybridCollectionIdentity("c", "v", "m", 1024, VectorDistance.Cosine),
        indexedDocumentCount = 0,
      )

      assert(BeautySearchHybridReadinessStatus.isReady(ready) == true)
    }

    "return false for NotReady status" in {
      val notReady = BeautySearchHybridReadinessStatus.NotReady("missing collection")

      assert(BeautySearchHybridReadinessStatus.isReady(notReady) == false)
    }
  }

  "BeautySearchHybridCollectionIdentity" should {
    "preserve embedding model, vector, dimension, and distance" in {
      val collection = BeautySearchHybridCollectionIdentity(
        collectionName = "beauty_hybrid_v1",
        vectorName = "variant-embedding",
        embeddingModelName = "qwen3-embedding",
        embeddingDimension = 1536,
        distance = VectorDistance.Dot,
      )

      assert(collection.collectionName == "beauty_hybrid_v1")
      assert(collection.vectorName == "variant-embedding")
      assert(collection.embeddingModelName == "qwen3-embedding")
      assert(collection.embeddingDimension == 1536)
      assert(collection.distance == VectorDistance.Dot)
    }
  }

  "BeautySearchHybridSnapshotIdentity" should {
    "preserve source, version, and document count" in {
      val snapshot = BeautySearchHybridSnapshotIdentity(
        sourceId = "seed-catalog",
        snapshotVersion = "v2",
        documentCount = 100,
      )

      assert(snapshot.sourceId == "seed-catalog")
      assert(snapshot.snapshotVersion == "v2")
      assert(snapshot.documentCount == 100)
    }
  }

  "BeautySearchHybridFreshnessPolicy" should {
    "preserve max staleness" in {
      val policy = BeautySearchHybridFreshnessPolicy(maxStalenessSeconds = 3600)

      assert(policy.maxStalenessSeconds == 3600)
    }
  }

  "BeautySearchHybridRuntimeMode" should {
    "distinguish all three modes" in {
      assert(BeautySearchHybridRuntimeMode.SeedCatalogOnly != BeautySearchHybridRuntimeMode.HybridShadow)
      assert(BeautySearchHybridRuntimeMode.HybridShadow != BeautySearchHybridRuntimeMode.HybridServe)
      assert(BeautySearchHybridRuntimeMode.SeedCatalogOnly != BeautySearchHybridRuntimeMode.HybridServe)
    }
  }

  "BeautySearchHybridServingDecision.decide" should {
    "use seed catalog when mode is SeedCatalogOnly and readiness is Ready" in {
      val ready = BeautySearchHybridReadinessStatus.Ready(
        snapshot = BeautySearchHybridSnapshotIdentity("seed", "v1", 10),
        collection = BeautySearchHybridCollectionIdentity("c", "v", "m", 1024, VectorDistance.Cosine),
        indexedDocumentCount = 10,
      )
      val policy = BeautySearchHybridServingPolicy(
        mode = BeautySearchHybridRuntimeMode.SeedCatalogOnly,
        requireReadyForServing = true,
      )

      val decision = BeautySearchHybridServingDecision.decide(policy, ready)

      assert(decision == BeautySearchHybridServingDecision.UseSeedCatalogOnly)
    }

    "use seed catalog when mode is SeedCatalogOnly and readiness is NotReady" in {
      val notReady = BeautySearchHybridReadinessStatus.NotReady("collection missing")
      val policy = BeautySearchHybridServingPolicy(
        mode = BeautySearchHybridRuntimeMode.SeedCatalogOnly,
        requireReadyForServing = false,
      )

      val decision = BeautySearchHybridServingDecision.decide(policy, notReady)

      assert(decision == BeautySearchHybridServingDecision.UseSeedCatalogOnly)
    }

    "return RunHybridShadow when mode is HybridShadow and readiness is Ready" in {
      val ready = BeautySearchHybridReadinessStatus.Ready(
        snapshot = BeautySearchHybridSnapshotIdentity("seed", "v1", 10),
        collection = BeautySearchHybridCollectionIdentity("c", "v", "m", 1024, VectorDistance.Cosine),
        indexedDocumentCount = 10,
      )
      val policy = BeautySearchHybridServingPolicy(
        mode = BeautySearchHybridRuntimeMode.HybridShadow,
        requireReadyForServing = true,
      )

      val decision = BeautySearchHybridServingDecision.decide(policy, ready)

      assert(decision == BeautySearchHybridServingDecision.RunHybridShadow)
    }

    "return RunHybridShadow when mode is HybridShadow and readiness is NotReady" in {
      val notReady = BeautySearchHybridReadinessStatus.NotReady("not ready yet")
      val policy = BeautySearchHybridServingPolicy(
        mode = BeautySearchHybridRuntimeMode.HybridShadow,
        requireReadyForServing = true,
      )

      val decision = BeautySearchHybridServingDecision.decide(policy, notReady)

      assert(decision == BeautySearchHybridServingDecision.RunHybridShadow)
    }

    "serve hybrid when mode is HybridServe and readiness is Ready" in {
      val ready = BeautySearchHybridReadinessStatus.Ready(
        snapshot = BeautySearchHybridSnapshotIdentity("seed", "v1", 10),
        collection = BeautySearchHybridCollectionIdentity("c", "v", "m", 1024, VectorDistance.Cosine),
        indexedDocumentCount = 10,
      )
      val policy = BeautySearchHybridServingPolicy(
        mode = BeautySearchHybridRuntimeMode.HybridServe,
        requireReadyForServing = true,
      )

      val decision = BeautySearchHybridServingDecision.decide(policy, ready)

      decision match {
        case BeautySearchHybridServingDecision.ServeHybrid(actualReady) =>
          assert(actualReady == ready)

        case other =>
          fail(s"Expected ServeHybrid decision, got $other")
      }
    }

    "use seed catalog when mode is HybridServe, readiness is NotReady, and requireReadyForServing is true" in {
      val notReady = BeautySearchHybridReadinessStatus.NotReady("collection missing")
      val policy = BeautySearchHybridServingPolicy(
        mode = BeautySearchHybridRuntimeMode.HybridServe,
        requireReadyForServing = true,
      )

      val decision = BeautySearchHybridServingDecision.decide(policy, notReady)

      assert(decision == BeautySearchHybridServingDecision.UseSeedCatalogOnly)
    }

    "use seed catalog when mode is HybridServe, readiness is NotReady, and requireReadyForServing is false" in {
      val notReady = BeautySearchHybridReadinessStatus.NotReady("collection missing")
      val policy = BeautySearchHybridServingPolicy(
        mode = BeautySearchHybridRuntimeMode.HybridServe,
        requireReadyForServing = false,
      )

      val decision = BeautySearchHybridServingDecision.decide(policy, notReady)

      assert(decision == BeautySearchHybridServingDecision.UseSeedCatalogOnly)
    }
  }

  "BeautySearchHybridReadiness" should {
    "return Ready status from readiness interface" in {
      val ready = BeautySearchHybridReadinessStatus.Ready(
        snapshot = BeautySearchHybridSnapshotIdentity("seed", "v1", 10),
        collection = BeautySearchHybridCollectionIdentity("c", "v", "m", 1024, VectorDistance.Cosine),
        indexedDocumentCount = 10,
      )
      val readiness = new BeautySearchHybridReadiness[IO] {
        override def status(): IO[QueryFailure, BeautySearchHybridReadinessStatus] = ZIO.succeed(ready)
      }

      val result = runIO(readiness.status())

      assert(result == ready)
    }

    "return NotReady status from readiness interface" in {
      val notReady = BeautySearchHybridReadinessStatus.NotReady("collection missing")
      val readiness = new BeautySearchHybridReadiness[IO] {
        override def status(): IO[QueryFailure, BeautySearchHybridReadinessStatus] = ZIO.succeed(notReady)
      }

      val result = runIO(readiness.status())

      assert(result == notReady)
    }

    "propagate QueryFailure from readiness interface" in {
      val failure = QueryFailure.domain("readiness check failed")
      val readiness = new BeautySearchHybridReadiness[IO] {
        override def status(): IO[QueryFailure, BeautySearchHybridReadinessStatus] = ZIO.fail(failure)
      }

      val result = runIO(readiness.status().either)

      assert(result == Left(failure))
    }
  }

  "BeautySearchHybridDiagnosticsEvent.DecisionEvaluated" should {
    "preserve decision evaluation fields" in {
      val policy = BeautySearchHybridServingPolicy.default
      val readiness = BeautySearchHybridReadinessStatus.Ready(
        snapshot = BeautySearchHybridSnapshotIdentity("seed", "v1", 10),
        collection = BeautySearchHybridCollectionIdentity("c", "v", "m", 1024, VectorDistance.Cosine),
        indexedDocumentCount = 10,
      )
      val decision = BeautySearchHybridServingDecision.ServeHybrid(readiness)
      val event = BeautySearchHybridDiagnosticsEvent.DecisionEvaluated(policy, readiness, decision)

      event match {
        case BeautySearchHybridDiagnosticsEvent.DecisionEvaluated(actualPolicy, actualReadiness, actualDecision) =>
          assert(actualPolicy == policy)
          assert(actualReadiness == readiness)
          assert(actualDecision == decision)

        case other =>
          fail(s"Expected DecisionEvaluated, got $other")
      }
    }
  }

  "BeautySearchHybridDiagnosticsSink" should {
    "accept decision event" in {
      val sink = new BeautySearchHybridDiagnosticsSink[IO] {
        override def report(event: BeautySearchHybridDiagnosticsEvent): IO[Nothing, Unit] = ZIO.unit
      }

      val event = BeautySearchHybridDiagnosticsEvent.DecisionEvaluated(
        policy = BeautySearchHybridServingPolicy.default,
        readiness = BeautySearchHybridReadinessStatus.Ready(
          snapshot = BeautySearchHybridSnapshotIdentity("seed", "v1", 10),
          collection = BeautySearchHybridCollectionIdentity("c", "v", "m", 1024, VectorDistance.Cosine),
          indexedDocumentCount = 10,
        ),
        decision = BeautySearchHybridServingDecision.UseSeedCatalogOnly,
      )

      val result = runIO(sink.report(event))

      assert(result == ())
    }
  }

  "BeautySearchHybridDecisionEvaluator" should {
    "return UseSeedCatalogOnly for default policy with Ready readiness" in {
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

      val result = runIO(evaluator.evaluate())

      assert(result == BeautySearchHybridServingDecision.UseSeedCatalogOnly)
    }

    "return RunHybridShadow for HybridShadow policy" in {
      val notReady = BeautySearchHybridReadinessStatus.NotReady("collection missing")
      val policy = BeautySearchHybridServingPolicy(
        mode = BeautySearchHybridRuntimeMode.HybridShadow,
        requireReadyForServing = true,
      )
      val readiness = new BeautySearchHybridReadiness[IO] {
        override def status(): IO[QueryFailure, BeautySearchHybridReadinessStatus] = ZIO.succeed(notReady)
      }
      val sink = new BeautySearchHybridDiagnosticsSink[IO] {
        override def report(event: BeautySearchHybridDiagnosticsEvent): IO[Nothing, Unit] = ZIO.unit
      }
      val evaluator = new BeautySearchHybridDecisionEvaluator[IO](policy, readiness, sink)

      val result = runIO(evaluator.evaluate())

      assert(result == BeautySearchHybridServingDecision.RunHybridShadow)
    }

    "return ServeHybrid for HybridServe policy with Ready readiness" in {
      val ready = BeautySearchHybridReadinessStatus.Ready(
        snapshot = BeautySearchHybridSnapshotIdentity("seed", "v1", 10),
        collection = BeautySearchHybridCollectionIdentity("c", "v", "m", 1024, VectorDistance.Cosine),
        indexedDocumentCount = 10,
      )
      val policy = BeautySearchHybridServingPolicy(
        mode = BeautySearchHybridRuntimeMode.HybridServe,
        requireReadyForServing = true,
      )
      val readiness = new BeautySearchHybridReadiness[IO] {
        override def status(): IO[QueryFailure, BeautySearchHybridReadinessStatus] = ZIO.succeed(ready)
      }
      val sink = new BeautySearchHybridDiagnosticsSink[IO] {
        override def report(event: BeautySearchHybridDiagnosticsEvent): IO[Nothing, Unit] = ZIO.unit
      }
      val evaluator = new BeautySearchHybridDecisionEvaluator[IO](policy, readiness, sink)

      val result = runIO(evaluator.evaluate())

      result match {
        case BeautySearchHybridServingDecision.ServeHybrid(serveReady) =>
          assert(serveReady == ready)

        case other =>
          fail(s"Expected ServeHybrid, got $other")
      }
    }

    "return UseSeedCatalogOnly for HybridServe policy with NotReady readiness" in {
      val notReady = BeautySearchHybridReadinessStatus.NotReady("collection missing")
      val policy = BeautySearchHybridServingPolicy(
        mode = BeautySearchHybridRuntimeMode.HybridServe,
        requireReadyForServing = true,
      )
      val readiness = new BeautySearchHybridReadiness[IO] {
        override def status(): IO[QueryFailure, BeautySearchHybridReadinessStatus] = ZIO.succeed(notReady)
      }
      val sink = new BeautySearchHybridDiagnosticsSink[IO] {
        override def report(event: BeautySearchHybridDiagnosticsEvent): IO[Nothing, Unit] = ZIO.unit
      }
      val evaluator = new BeautySearchHybridDecisionEvaluator[IO](policy, readiness, sink)

      val result = runIO(evaluator.evaluate())

      assert(result == BeautySearchHybridServingDecision.UseSeedCatalogOnly)
    }

    "propagate readiness QueryFailure and not require diagnostics success" in {
      val failure = QueryFailure.domain("readiness check failed")
      val readiness = new BeautySearchHybridReadiness[IO] {
        override def status(): IO[QueryFailure, BeautySearchHybridReadinessStatus] = ZIO.fail(failure)
      }
      val sink = new BeautySearchHybridDiagnosticsSink[IO] {
        override def report(event: BeautySearchHybridDiagnosticsEvent): IO[Nothing, Unit] = ZIO.unit
      }
      val evaluator = new BeautySearchHybridDecisionEvaluator[IO](BeautySearchHybridServingPolicy.default, readiness, sink)

      val result = runIO(evaluator.evaluate().either)

      assert(result == Left(failure))
    }

    "report DecisionEvaluated event through sink" in {
      val expectedReady = BeautySearchHybridReadinessStatus.Ready(
        snapshot = BeautySearchHybridSnapshotIdentity("seed", "v1", 10),
        collection = BeautySearchHybridCollectionIdentity("c", "v", "m", 1024, VectorDistance.Cosine),
        indexedDocumentCount = 10,
      )
      val expectedDecision = BeautySearchHybridServingDecision.UseSeedCatalogOnly
      val expectedEvent = BeautySearchHybridDiagnosticsEvent.DecisionEvaluated(
        policy = BeautySearchHybridServingPolicy.default,
        readiness = expectedReady,
        decision = expectedDecision,
      )
      val readiness = new BeautySearchHybridReadiness[IO] {
        override def status(): IO[QueryFailure, BeautySearchHybridReadinessStatus] = ZIO.succeed(expectedReady)
      }
      val sink = new BeautySearchHybridDiagnosticsSink[IO] {
        override def report(event: BeautySearchHybridDiagnosticsEvent): IO[Nothing, Unit] = {
          if (event != expectedEvent) {
            ZIO.dieMessage(s"Unexpected event: $event")
          } else {
            ZIO.unit
          }
        }
      }
      val evaluator = new BeautySearchHybridDecisionEvaluator[IO](
        policy = BeautySearchHybridServingPolicy.default,
        readiness = readiness,
        diagnosticsSink = sink,
      )

      val result = runIO(evaluator.evaluate())

      assert(result == expectedDecision)
    }
  }

  private def runIO[E, A](effect: IO[E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
