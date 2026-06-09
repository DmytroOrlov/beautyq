package leaderboard.search

import leaderboard.search.dsl.VectorDistance
import leaderboard.search.hybrid.control._
import org.scalatest.wordspec.AnyWordSpec

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
}
