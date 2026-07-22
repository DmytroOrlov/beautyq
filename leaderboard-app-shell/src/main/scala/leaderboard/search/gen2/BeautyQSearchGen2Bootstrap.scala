package leaderboard.search.gen2

import leaderboard.search.beautyq.gen2.materialization.{BeautyQMaterializationError, BeautyQVariantMaterializer}
import leaderboard.search.beautyq.gen2.wiring.*
import leaderboard.search.gen2.qdrant.{QdrantGenerationLifecycle, QdrantQueryEmbeddingPort}
import zio.{IO, ZIO}

sealed trait BeautyQSearchGen2BootstrapError
object BeautyQSearchGen2BootstrapError {
  final case class Materialization(error: BeautyQMaterializationError) extends BeautyQSearchGen2BootstrapError
  final case class Activation(error: BeautyQSearchGenerationActivationError) extends BeautyQSearchGen2BootstrapError
  final case class Blocking(error: Throwable) extends BeautyQSearchGen2BootstrapError
}

/** Startup owner for the complete snapshot -> materialization -> generation activation path.
  * It publishes no serving composition until materialization and activation have both succeeded.
  *
  * The wrapped services are exposed only so the trusted single-activation app-shell owner can
  * derive the application and runtime from the exact same activation the bootstrap returns. They
  * are not part of the public contract and never reach the HTTP route layer. */
final class BeautyQSearchGen2Bootstrap private (
  materializer: BeautyQVariantMaterializer[IO],
  val elasticsearchService: BeautyQElasticsearchBaselineService,
  val qdrantLifecycle: QdrantGenerationLifecycle,
  val embeddingPort: QdrantQueryEmbeddingPort[BeautyQEmbeddingRequestError],
) {
  def activate: IO[BeautyQSearchGen2BootstrapError, BeautyQSearchGenerationApplication.Activation] =
    materializer.load
      .mapError(BeautyQSearchGen2BootstrapError.Materialization.apply)
      .flatMap { materialized =>
        ZIO.attemptBlocking(
          BeautyQSearchGenerationApplication.activate(materialized, elasticsearchService, qdrantLifecycle, embeddingPort)
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
    embedding: QdrantQueryEmbeddingPort[BeautyQEmbeddingRequestError],
  ): BeautyQSearchGen2Bootstrap =
    new BeautyQSearchGen2Bootstrap(materializer, elasticsearch, qdrant, embedding)
}
