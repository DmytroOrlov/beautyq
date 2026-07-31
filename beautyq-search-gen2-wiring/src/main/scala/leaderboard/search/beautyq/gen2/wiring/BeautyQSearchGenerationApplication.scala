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
  ): Either[BeautyQSearchGenerationActivationError, Activation] =
    for {
      esGeneration <- BeautyQElasticsearchGeneration.compile(materialized)
        .left.map(BeautyQSearchGenerationActivationError.ElasticsearchCompile.apply)
      qdrantPrepared <- prepareQdrant(materialized, embedding)
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
  ): Either[BeautyQSearchGenerationActivationError, Either[BeautyQEmbeddingRequestError, QdrantCompiledGeneration]] =
    for {
      prepared <- QdrantGenerationCompiler.prepare(BeautyQQdrantPolicy.policy, materialized)
        .left.map(BeautyQSearchGenerationActivationError.Compile.apply)
      embedded = collectEmbeddings(prepared.points, embedding)
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
    prepared: Either[BeautyQEmbeddingRequestError, QdrantCompiledGeneration],
    lifecycle: QdrantGenerationLifecycle,
  ): Either[BeautyQSearchGenerationActivationError, (Option[ActiveQdrantGeneration], Option[BeautyQSearchGenerationActivationError])] =
    prepared match {
      case Left(error) => Right((None, Some(BeautyQSearchGenerationActivationError.Embedding(error))))
      case Right(compiled) =>
        lifecycle.activate(compiled) match {
          case Right(value) => Right((Some(value), None))
          case Left(error: QdrantGenerationLifecycleError.Transport) => Right((None, Some(BeautyQSearchGenerationActivationError.Qdrant(error))))
          case Left(error) => Left(BeautyQSearchGenerationActivationError.Qdrant(error))
        }
    }

  private def collectEmbeddings(
    points: Vector[QdrantPreparedPoint],
    embedding: QdrantQueryEmbeddingPort[BeautyQEmbeddingRequestError],
  ): Either[BeautyQSearchGenerationActivationError, Either[BeautyQEmbeddingRequestError, Vector[QdrantEmbeddingResult]]] =
    points.foldLeft[Either[BeautyQSearchGenerationActivationError, Either[BeautyQEmbeddingRequestError, Vector[QdrantEmbeddingResult]]]](Right(Right(Vector.empty[QdrantEmbeddingResult]))) {
      case (Right(Left(error)), _) => Right(Left[BeautyQEmbeddingRequestError, Vector[QdrantEmbeddingResult]](error))
      case (Left(error), _) => Left(error)
      case (Right(Right(values)), point) =>
        embedding.embed(point.embeddingInput) match {
          case Right(value) => Right(Right(values :+ value))
          case Left(error: BeautyQEmbeddingRequestError.InvalidResult) => Left(BeautyQSearchGenerationActivationError.Embedding(error))
          case Left(error) => Right(Left[BeautyQEmbeddingRequestError, Vector[QdrantEmbeddingResult]](error))
        }
    }
}
