package leaderboard.search.gen2.qdrant

final class QdrantGenerationWorkPolicy private (
  val embeddingBatchSize: Int,
  val upsertBatchSize: Int,
  val maximumInFlightBatches: Int,
)

object QdrantGenerationWorkPolicy {
  sealed trait Error
  object Error {
    final case class NonPositiveEmbeddingBatchSize(value: Int) extends Error
    final case class NonPositiveUpsertBatchSize(value: Int) extends Error
    final case class NonPositiveMaximumInFlightBatches(value: Int) extends Error
    final case class UnsupportedParallelism(value: Int) extends Error
  }

  val Default: QdrantGenerationWorkPolicy =
    create(16, 64, 1).fold(error => throw new IllegalStateException(s"invalid default Qdrant work policy: $error"), identity)

  def create(
    embeddingBatchSize: Int,
    upsertBatchSize: Int,
    maximumInFlightBatches: Int,
  ): Either[Error, QdrantGenerationWorkPolicy] =
    if (embeddingBatchSize <= 0) Left(Error.NonPositiveEmbeddingBatchSize(embeddingBatchSize))
    else if (upsertBatchSize <= 0) Left(Error.NonPositiveUpsertBatchSize(upsertBatchSize))
    else if (maximumInFlightBatches <= 0) Left(Error.NonPositiveMaximumInFlightBatches(maximumInFlightBatches))
    else if (maximumInFlightBatches != 1) Left(Error.UnsupportedParallelism(maximumInFlightBatches))
    else Right(new QdrantGenerationWorkPolicy(embeddingBatchSize, upsertBatchSize, maximumInFlightBatches))
}
