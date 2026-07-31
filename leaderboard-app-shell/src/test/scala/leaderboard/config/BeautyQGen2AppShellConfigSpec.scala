package leaderboard.config

import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration.*

final class BeautyQGen2AppShellConfigSpec extends AnyWordSpec {
  "BeautyQGen2AppShellConfig" should {
    "accept the bounded sequential Qdrant generation defaults" in {
      val config = BeautyQGen2AppShellConfig(5.seconds, 60.seconds, 100, 1048576L, 16, 64, 1)
      assert(BeautyQGen2AppShellConfig.validate(config) == Right(config))
    }

    "reject non-positive batch sizes and any parallel batch execution" in {
      val valid = BeautyQGen2AppShellConfig(5.seconds, 60.seconds, 100, 1048576L, 16, 64, 1)
      assert(BeautyQGen2AppShellConfig.validate(valid.copy(qdrantEmbeddingBatchSize = 0)) ==
        Left(BeautyQGen2AppShellConfigError.InvalidQdrantWorkPolicy(
          leaderboard.search.gen2.qdrant.QdrantGenerationWorkPolicy.Error.NonPositiveEmbeddingBatchSize(0)
        )))
      assert(BeautyQGen2AppShellConfig.validate(valid.copy(qdrantUpsertBatchSize = 0)) ==
        Left(BeautyQGen2AppShellConfigError.InvalidQdrantWorkPolicy(
          leaderboard.search.gen2.qdrant.QdrantGenerationWorkPolicy.Error.NonPositiveUpsertBatchSize(0)
        )))
      assert(BeautyQGen2AppShellConfig.validate(valid.copy(qdrantMaximumInFlightBatches = 2)) ==
        Left(BeautyQGen2AppShellConfigError.InvalidQdrantWorkPolicy(
          leaderboard.search.gen2.qdrant.QdrantGenerationWorkPolicy.Error.UnsupportedParallelism(2)
        )))
    }
  }
}
