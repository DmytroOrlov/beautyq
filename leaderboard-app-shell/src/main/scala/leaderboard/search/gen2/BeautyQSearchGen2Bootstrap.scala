package leaderboard.search.gen2

import leaderboard.search.beautyq.gen2.materialization.{BeautyQMaterializationError, BeautyQVariantMaterializer}
import leaderboard.search.beautyq.gen2.wiring.*
import leaderboard.search.beautyq.gen2.wiring.SupplementStartupPolicy.{Disabled, Preferred, Required}
import leaderboard.search.gen2.qdrant.{QdrantGenerationLifecycle, QdrantQueryEmbeddingPort}
import zio.{IO, ZIO}

sealed trait BeautyQSearchGen2BootstrapError
object BeautyQSearchGen2BootstrapError {
  final case class Materialization(error: BeautyQMaterializationError) extends BeautyQSearchGen2BootstrapError
  final case class Activation(error: BeautyQSearchGenerationActivationError) extends BeautyQSearchGen2BootstrapError
  final case class Blocking(error: Throwable) extends BeautyQSearchGen2BootstrapError
}

final class BeautyQSearchGen2Bootstrap private (
  materializer: BeautyQVariantMaterializer[IO],
  val elasticsearchService: BeautyQElasticsearchBaselineService,
  val supplementStartupPolicy: SupplementStartupPolicy,
  private[gen2] val qdrantLifecycle: Option[QdrantGenerationLifecycle],
  private[gen2] val embeddingPort: Option[QdrantQueryEmbeddingPort[BeautyQEmbeddingRequestError]],
) {
  def activate: IO[BeautyQSearchGen2BootstrapError, BeautyQSearchGenerationApplication.Activation] =
    materializer.load
      .mapError(BeautyQSearchGen2BootstrapError.Materialization.apply)
      .flatMap { materialized =>
        supplementStartupPolicy match {
          case Disabled =>
            ZIO.attemptBlocking(
              BeautyQSearchGenerationApplication.activateBaselineOnly(materialized, elasticsearchService)
            )
              .mapError(BeautyQSearchGen2BootstrapError.Blocking.apply)
              .flatMap {
                case Left(error) => ZIO.fail(BeautyQSearchGen2BootstrapError.Activation(error))
                case Right(value) => ZIO.succeed(value)
              }
          case Required | Preferred =>
            val qdrant = qdrantLifecycle.getOrElse(throw new IllegalStateException("Qdrant lifecycle not available for full supplement startup"))
            val embedding = embeddingPort.getOrElse(throw new IllegalStateException("Embedding port not available for full supplement startup"))
            ZIO.attemptBlocking(
              BeautyQSearchGenerationApplication.activate(materialized, elasticsearchService, qdrant, embedding)
            )
              .mapError(BeautyQSearchGen2BootstrapError.Blocking.apply)
              .flatMap {
                case Left(error) => ZIO.fail(BeautyQSearchGen2BootstrapError.Activation(error))
                case Right(value) => ZIO.succeed(value)
              }
        }
      }
}

object BeautyQSearchGen2Bootstrap {
  def make(
    materializer: BeautyQVariantMaterializer[IO],
    elasticsearch: BeautyQElasticsearchBaselineService,
    supplementStartupPolicy: SupplementStartupPolicy,
    qdrant: QdrantGenerationLifecycle,
    embedding: QdrantQueryEmbeddingPort[BeautyQEmbeddingRequestError],
  ): BeautyQSearchGen2Bootstrap =
    new BeautyQSearchGen2Bootstrap(materializer, elasticsearch, supplementStartupPolicy, Some(qdrant), Some(embedding))

  def makeBaselineOnly(
    materializer: BeautyQVariantMaterializer[IO],
    elasticsearch: BeautyQElasticsearchBaselineService,
    supplementStartupPolicy: SupplementStartupPolicy,
  ): BeautyQSearchGen2Bootstrap =
    new BeautyQSearchGen2Bootstrap(materializer, elasticsearch, supplementStartupPolicy, None, None)
}
