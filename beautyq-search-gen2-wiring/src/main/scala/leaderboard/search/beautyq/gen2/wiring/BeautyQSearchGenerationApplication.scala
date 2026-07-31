package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.beautyq.gen2.materialization.MaterializedBeautyQVariantDocuments
import leaderboard.search.gen2.elasticsearch.ElasticsearchGenerationCompileError
import leaderboard.search.gen2.elasticsearch.lifecycle.LifecycleResolvedElasticsearchGeneration
import leaderboard.search.gen2.qdrant.*

sealed trait BeautyQSearchGenerationActivationError
object BeautyQSearchGenerationActivationError {
  final case class ElasticsearchCompile(error: ElasticsearchGenerationCompileError) extends BeautyQSearchGenerationActivationError
  final case class Elasticsearch(error: BeautyQElasticsearchBaselineServiceError) extends BeautyQSearchGenerationActivationError
  final case class Qdrant(error: QdrantGenerationLifecycleError) extends BeautyQSearchGenerationActivationError
  final case class Embedding(error: BeautyQEmbeddingRequestError) extends BeautyQSearchGenerationActivationError
  final case class EmbeddingBatch(
    batchIndex: Int,
    pointIndex: Int,
    subject: String,
    error: BeautyQEmbeddingRequestError,
  ) extends BeautyQSearchGenerationActivationError
  final case class Compile(error: QdrantGenerationCompileError) extends BeautyQSearchGenerationActivationError
}

object BeautyQSearchGenerationApplication {
  final class Activation private[BeautyQSearchGenerationApplication] (
    val materialized: MaterializedBeautyQVariantDocuments,
    val elasticsearchGeneration: LifecycleResolvedElasticsearchGeneration,
    val qdrantGeneration: Option[ActiveQdrantGeneration],
    val qdrantFailure: Option[BeautyQSearchGenerationActivationError],
  )

  def activate(
    materialized: MaterializedBeautyQVariantDocuments,
    elasticsearch: BeautyQElasticsearchBaselineService,
    qdrant: QdrantGenerationLifecycle,
    embedding: QdrantQueryEmbeddingPort[BeautyQEmbeddingRequestError],
    workPolicy: QdrantGenerationWorkPolicy = QdrantGenerationWorkPolicy.Default,
  ): Either[BeautyQSearchGenerationActivationError, Activation] =
    for {
      esGeneration <- BeautyQElasticsearchGeneration.compile(materialized)
        .left.map(BeautyQSearchGenerationActivationError.ElasticsearchCompile.apply)
      qdrantPrepared <- prepareQdrant(materialized, embedding, workPolicy)
      esActive <- elasticsearch.activate(esGeneration)
        .left.map(BeautyQSearchGenerationActivationError.Elasticsearch.apply)
      qdrantOutcome <- activatePreparedQdrant(qdrantPrepared, qdrant)
    } yield new Activation(
      materialized,
      esActive,
      qdrantOutcome._1,
      qdrantOutcome._2,
    )

  def activateBaselineOnly(
    materialized: MaterializedBeautyQVariantDocuments,
    elasticsearch: BeautyQElasticsearchBaselineService,
  ): Either[BeautyQSearchGenerationActivationError, Activation] =
    for {
      esGeneration <- BeautyQElasticsearchGeneration.compile(materialized)
        .left.map(BeautyQSearchGenerationActivationError.ElasticsearchCompile.apply)
      esActive <- elasticsearch.activate(esGeneration)
        .left.map(BeautyQSearchGenerationActivationError.Elasticsearch.apply)
    } yield new Activation(
      materialized,
      esActive,
      qdrantGeneration = None,
      qdrantFailure = None,
    )

  private def prepareQdrant(
    materialized: MaterializedBeautyQVariantDocuments,
    embedding: QdrantQueryEmbeddingPort[BeautyQEmbeddingRequestError],
    workPolicy: QdrantGenerationWorkPolicy,
  ): Either[BeautyQSearchGenerationActivationError, Either[BeautyQSearchGenerationActivationError, QdrantCompiledGeneration]] =
    for {
      prepared <- QdrantGenerationCompiler.prepare(BeautyQQdrantPolicy.policy, materialized)
        .left.map(BeautyQSearchGenerationActivationError.Compile.apply)
      embedded = collectEmbeddings(prepared.points, embedding, workPolicy)
      outcome <- embedded match {
        case Left(error) => Left(error)
        case Right(Left(error)) => Right(Left(error))
        case Right(Right(embeddings)) =>
          QdrantGenerationCompiler.complete(
            prepared,
            embeddings,
            BeautyQSearchGen2ResourceNames.QdrantPhysicalCollectionPrefix,
          ).left.map(BeautyQSearchGenerationActivationError.Compile.apply).map(Right.apply)
      }
    } yield outcome

  private def activatePreparedQdrant(
    prepared: Either[BeautyQSearchGenerationActivationError, QdrantCompiledGeneration],
    lifecycle: QdrantGenerationLifecycle,
  ): Either[BeautyQSearchGenerationActivationError, (Option[ActiveQdrantGeneration], Option[BeautyQSearchGenerationActivationError])] =
    prepared match {
      case Left(error) => Right((None, Some(error)))
      case Right(compiled) =>
        lifecycle.activate(compiled) match {
          case Right(value) => Right((Some(value), None))
          case Left(error) if isTransportFailure(error) => Right((None, Some(BeautyQSearchGenerationActivationError.Qdrant(error))))
          case Left(error) => Left(BeautyQSearchGenerationActivationError.Qdrant(error))
        }
    }

  private def isTransportFailure(error: QdrantGenerationLifecycleError): Boolean = error match {
    case _: QdrantGenerationLifecycleError.Transport => true
    case QdrantGenerationLifecycleError.UpsertBatchFailed(_, _, _, cause) => isTransportFailure(cause)
    case _ => false
  }

  private def collectEmbeddings(
    points: Vector[QdrantPreparedPoint],
    embedding: QdrantQueryEmbeddingPort[BeautyQEmbeddingRequestError],
    workPolicy: QdrantGenerationWorkPolicy,
  ): Either[BeautyQSearchGenerationActivationError, Either[BeautyQSearchGenerationActivationError, Vector[QdrantEmbeddingResult]]] =
    points.grouped(workPolicy.embeddingBatchSize).toVector.zipWithIndex
      .foldLeft[Either[BeautyQSearchGenerationActivationError, Either[BeautyQSearchGenerationActivationError, Vector[QdrantEmbeddingResult]]]](Right(Right(Vector.empty))) {
        case (Right(Left(error)), _) => Right(Left(error))
        case (Left(error), _) => Left(error)
        case (Right(Right(values)), (batch, batchIndex)) =>
          batch.zipWithIndex.foldLeft[Either[BeautyQSearchGenerationActivationError, Either[BeautyQSearchGenerationActivationError, Vector[QdrantEmbeddingResult]]]](Right(Right(values))) {
            case (Right(Left(error)), _) => Right(Left(error))
            case (Left(error), _) => Left(error)
            case (Right(Right(done)), (point, withinBatchIndex)) =>
              val pointIndex = batchIndex * workPolicy.embeddingBatchSize + withinBatchIndex
              embedding.embed(point.embeddingInput) match {
                case Right(value) => Right(Right(done :+ value))
                case Left(error: BeautyQEmbeddingRequestError.InvalidResult) =>
                  Left(BeautyQSearchGenerationActivationError.EmbeddingBatch(batchIndex, pointIndex, point.embeddingInput.subjectValue, error))
                case Left(error) =>
                  Right(Left(BeautyQSearchGenerationActivationError.EmbeddingBatch(batchIndex, pointIndex, point.embeddingInput.subjectValue, error)))
              }
          }
      }
}
