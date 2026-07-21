package leaderboard.search.gen2

import leaderboard.search.beautyq.gen2.materialization.{BeautyQMaterializationError, BeautyQVariantMaterializer}
import leaderboard.search.beautyq.gen2.wiring.*
import leaderboard.search.gen2.qdrant.QdrantGenerationLifecycle
import zio.{IO, ZIO}

sealed trait BeautyQSearchGen2BootstrapError
object BeautyQSearchGen2BootstrapError {
  final case class Materialization(error: BeautyQMaterializationError) extends BeautyQSearchGen2BootstrapError
  final case class Activation(error: BeautyQSearchGenerationActivationError) extends BeautyQSearchGen2BootstrapError
  final case class Blocking(error: Throwable) extends BeautyQSearchGen2BootstrapError
}

/** Startup owner for the complete snapshot -> materialization -> generation
  * activation path. It publishes no serving composition until materialization
  * and activation have both succeeded. */
final class BeautyQSearchGen2Bootstrap private (
  materializer: BeautyQVariantMaterializer[IO],
  elasticsearch: BeautyQElasticsearchBaselineService,
  qdrant: QdrantGenerationLifecycle,
  embedding: BeautyQGen2EmbeddingClient,
) {
  def activate: IO[BeautyQSearchGen2BootstrapError, BeautyQSearchGenerationApplication.Activation] =
    materializer.load
      .mapError(BeautyQSearchGen2BootstrapError.Materialization.apply)
      .flatMap { materialized =>
        ZIO.attemptBlocking(
          BeautyQSearchGenerationApplication.activate(materialized, elasticsearch, qdrant, embedding)
        )
          .mapError(BeautyQSearchGen2BootstrapError.Blocking.apply)
          .flatMap {
            case Left(error) => ZIO.fail(BeautyQSearchGen2BootstrapError.Activation(error))
            case Right(value) => ZIO.succeed(value)
          }
      }
}

object BeautyQSearchGen2Bootstrap {
  def make(
    materializer: BeautyQVariantMaterializer[IO],
    elasticsearch: BeautyQElasticsearchBaselineService,
    qdrant: QdrantGenerationLifecycle,
    embedding: BeautyQGen2EmbeddingClient,
  ): BeautyQSearchGen2Bootstrap =
    new BeautyQSearchGen2Bootstrap(materializer, elasticsearch, qdrant, embedding)
}
