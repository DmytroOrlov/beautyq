package leaderboard.search.gen2

import leaderboard.search.beautyq.gen2.materialization.{BeautyQMaterializationError, BeautyQVariantMaterializer}
import leaderboard.search.beautyq.gen2.wiring.*
import leaderboard.search.beautyq.gen2.wiring.SupplementStartupPolicy.{Disabled, Preferred, Required}
import leaderboard.search.gen2.qdrant.{QdrantGenerationLifecycle, QdrantGenerationWorkPolicy, QdrantQueryEmbeddingPort}
import zio.{Clock, IO, ZIO}

import java.time.Instant

sealed trait BeautyQSearchGen2BootstrapError
object BeautyQSearchGen2BootstrapError {
  final case class Materialization(error: BeautyQMaterializationError) extends BeautyQSearchGen2BootstrapError
  final case class Activation(error: BeautyQSearchGenerationActivationError) extends BeautyQSearchGen2BootstrapError
  final case class Blocking(error: Throwable) extends BeautyQSearchGen2BootstrapError
}

final class BeautyQSearchStartupEvidence private[gen2] (
  val snapshotCapturedAt: Instant,
  val sourceRevision: Option[String],
  val materializationDurationNanos: Long,
  val activationDurationNanos: Long,
  val activatedAt: Instant,
)

final class BeautyQSearchGen2BootstrapResult private[gen2] (
  val activation: BeautyQSearchGenerationApplication.Activation,
  val evidence: BeautyQSearchStartupEvidence,
)

final class BeautyQSearchGen2Bootstrap private (
  materializer: BeautyQVariantMaterializer[IO],
  val elasticsearchService: BeautyQElasticsearchBaselineService,
  val supplementStartupPolicy: SupplementStartupPolicy,
  private[gen2] val qdrantLifecycle: Option[QdrantGenerationLifecycle],
  private[gen2] val embeddingPort: Option[QdrantQueryEmbeddingPort[BeautyQEmbeddingRequestError]],
  private val qdrantWorkPolicy: QdrantGenerationWorkPolicy,
) {
  def activate: IO[BeautyQSearchGen2BootstrapError, BeautyQSearchGen2BootstrapResult] =
    for {
      materializationStarted <- Clock.nanoTime
      materialized <- materializer.load
      .mapError(BeautyQSearchGen2BootstrapError.Materialization.apply)
      materializationFinished <- Clock.nanoTime
      activationStarted <- Clock.nanoTime
      activation <- {
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
              BeautyQSearchGenerationApplication.activate(materialized, elasticsearchService, qdrant, embedding, qdrantWorkPolicy)
            )
              .mapError(BeautyQSearchGen2BootstrapError.Blocking.apply)
              .flatMap {
                case Left(error) => ZIO.fail(BeautyQSearchGen2BootstrapError.Activation(error))
                case Right(value) => ZIO.succeed(value)
              }
        }
      }
      activationFinished <- Clock.nanoTime
      activatedAt <- Clock.instant
      evidence = new BeautyQSearchStartupEvidence(
        snapshotCapturedAt = materialized.sourceSnapshot.capturedAt,
        sourceRevision = materialized.sourceSnapshot.sourceRevision.map(_.value),
        materializationDurationNanos = nonNegativeDuration(materializationStarted, materializationFinished),
        activationDurationNanos = nonNegativeDuration(activationStarted, activationFinished),
        activatedAt = activatedAt,
      )
    } yield new BeautyQSearchGen2BootstrapResult(activation, evidence)

  private def nonNegativeDuration(started: Long, finished: Long): Long =
    math.max(0L, finished - started)
}

object BeautyQSearchGen2Bootstrap {
  def make(
    materializer: BeautyQVariantMaterializer[IO],
    elasticsearch: BeautyQElasticsearchBaselineService,
    supplementStartupPolicy: SupplementStartupPolicy,
    qdrant: QdrantGenerationLifecycle,
    embedding: QdrantQueryEmbeddingPort[BeautyQEmbeddingRequestError],
    workPolicy: QdrantGenerationWorkPolicy = QdrantGenerationWorkPolicy.Default,
  ): BeautyQSearchGen2Bootstrap =
    new BeautyQSearchGen2Bootstrap(materializer, elasticsearch, supplementStartupPolicy, Some(qdrant), Some(embedding), workPolicy)

  def makeBaselineOnly(
    materializer: BeautyQVariantMaterializer[IO],
    elasticsearch: BeautyQElasticsearchBaselineService,
    supplementStartupPolicy: SupplementStartupPolicy,
  ): BeautyQSearchGen2Bootstrap =
    new BeautyQSearchGen2Bootstrap(
      materializer,
      elasticsearch,
      supplementStartupPolicy,
      None,
      None,
      QdrantGenerationWorkPolicy.Default,
    )
}
