package leaderboard.search.gen2.qdrant

import io.circe.Json
import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.materialization.MaterializedSearchDocuments

final case class QdrantGenerationIdentity(
  sourceContentFingerprint: String,
  projectedDocumentsFingerprint: String,
  collectionContractFingerprint: String,
  projectionFormatVersion: String,
  compilerVersion: String,
  collectionFormatVersion: String,
  embeddingModel: QdrantEmbeddingModelIdentity,
  embeddedPointsFingerprint: String,
)

final case class QdrantGenerationMetadata(
  schemaVersion: String,
  generationId: String,
  identity: QdrantGenerationIdentity,
  pointCount: Int,
)

sealed trait QdrantGenerationCompileError
object QdrantGenerationCompileError {
  final case class Payload(error: QdrantPayloadError) extends QdrantGenerationCompileError
  final case class InvalidPointId(documentIndex: Int, error: QdrantPointIdError) extends QdrantGenerationCompileError
  final case class EmbeddingInput(error: QdrantEmbeddingError) extends QdrantGenerationCompileError
  final case class EmbeddingCount(expected: Int, actual: Int) extends QdrantGenerationCompileError
  final case class EmbeddingMismatch(index: Int, error: QdrantEmbeddingError) extends QdrantGenerationCompileError
  final case class DuplicatePointId(id: String) extends QdrantGenerationCompileError
  final case class InvalidPrefix(value: String) extends QdrantGenerationCompileError
}

final class QdrantPreparedPoint private[qdrant] (
  val id: QdrantPointId,
  val payload: Json,
  val embeddingInput: QdrantEmbeddingInput,
)

/** Prepared generation output deliberately contains no network client or transport state. It is the
  * exact ordered input for a later embedding/upsert owner. */
final class QdrantPreparedGeneration private[qdrant] (
  val policy: QdrantPolicy[?, ?],
  val points: Vector[QdrantPreparedPoint],
  val collectionContractFingerprint: String,
  val sourceContentFingerprint: String,
  val projectedDocumentsFingerprint: String,
  val projectionFormatVersion: String,
)

final class QdrantCompiledPoint private[qdrant] (
  val id: QdrantPointId,
  val vectorName: QdrantVectorName,
  val vector: Vector[Double],
  val payload: Json,
)

/** Compiler-owned completed generation. Its identity, collection JSON and point vector cannot be
  * independently supplied or copied by a caller. */
final class QdrantCompiledGeneration private[qdrant] (
  val identity: QdrantGenerationIdentity,
  val physicalCollectionName: String,
  val collectionJson: Json,
  val payloadIndexRequests: Vector[Json],
  val points: Vector[QdrantCompiledPoint],
  val metadata: QdrantGenerationMetadata,
)

object QdrantGenerationCompiler {
  private val MetadataSchemaVersion = "qdrant-generation-metadata-v1"

  def prepare[Snapshot, Document, Id](
    policy: QdrantPolicy[Document, Id],
    materialized: MaterializedSearchDocuments[Snapshot, Document],
  ): Either[QdrantGenerationCompileError, QdrantPreparedGeneration] = {
    val prepared = materialized.documents.zipWithIndex.foldLeft[Either[QdrantGenerationCompileError, Vector[QdrantPreparedPoint]]](Right(Vector.empty)) {
      case (acc, (document, index)) =>
        acc.flatMap { points =>
          identityFor(policy.identity, document) match {
            case Left(error) => Left(error match {
              case QdrantPointIdError.UnsupportedCanonicalValue(_, _) => QdrantGenerationCompileError.InvalidPointId(index, error)
            })
            case Right(pointId) =>
              QdrantPayloadCompiler.compile(policy, document).left.map(QdrantGenerationCompileError.Payload.apply).flatMap { payload =>
                val subject = QdrantPointId.canonical(pointId)
                policy.embeddingField.extract(document) match {
                  case None => Left(QdrantGenerationCompileError.EmbeddingInput(QdrantEmbeddingError.BlankText(subject)))
                  case Some(text) =>
                    QdrantEmbeddingInput.from(QdrantEmbeddingPurpose.Document, subject, text, policy.embeddingModel)
                      .left.map(QdrantGenerationCompileError.EmbeddingInput.apply)
                      .map(input => points :+ new QdrantPreparedPoint(pointId, payload, input))
                }
              }
          }
        }
    }

    prepared.map { points =>
      val ordered = points.sortBy(point => QdrantPointId.canonical(point.id))
      new QdrantPreparedGeneration(
        policy,
        ordered,
        policy.collectionContractFingerprint.value,
        materialized.sourceSnapshot.contentFingerprint.value,
        materialized.projectedDocumentsFingerprint.value,
        materialized.projectionFormatVersion.value,
      )
    }
  }

  def complete(
    prepared: QdrantPreparedGeneration,
    embeddings: Vector[QdrantEmbeddingResult],
    physicalCollectionPrefix: String,
  ): Either[QdrantGenerationCompileError, QdrantCompiledGeneration] = {
    if (physicalCollectionPrefix.trim.isEmpty || physicalCollectionPrefix.contains("*")) Left(QdrantGenerationCompileError.InvalidPrefix(physicalCollectionPrefix))
    else if (embeddings.length != prepared.points.length) Left(QdrantGenerationCompileError.EmbeddingCount(prepared.points.length, embeddings.length))
    else {
      val completed = prepared.points.zip(embeddings).foldLeft[Either[QdrantGenerationCompileError, Vector[QdrantCompiledPoint]]](Right(Vector.empty)) {
        case (acc, (point, embedding)) =>
          acc.flatMap { points =>
            if (embedding.inputFingerprint != point.embeddingInput.fingerprint) Left(QdrantGenerationCompileError.EmbeddingMismatch(points.length, QdrantEmbeddingError.InputMismatch(point.embeddingInput.fingerprint, embedding.inputFingerprint)))
            else if (embedding.model != point.embeddingInput.modelValue) Left(QdrantGenerationCompileError.EmbeddingMismatch(points.length, QdrantEmbeddingError.ModelMismatch(point.embeddingInput.modelValue, embedding.model)))
            else Right(points :+ new QdrantCompiledPoint(point.id, policyVectorName(prepared), embedding.values, point.payload))
          }
      }
      completed.flatMap { points =>
        val duplicateIds = points.groupBy(point => QdrantPointId.canonical(point.id)).collect { case (id, values) if values.size > 1 => id }.toVector.sorted
        duplicateIds.headOption match {
          case Some(id) => Left(QdrantGenerationCompileError.DuplicatePointId(id))
          case None =>
            val embeddedFingerprint = QdrantCanonical.sha256(
              points.flatMap { point =>
                Vector(
                  QdrantPointId.canonical(point.id),
                  Json.fromValues(point.vector.map(value => Json.fromBigDecimal(BigDecimal(value)))).noSpaces,
                  point.payload.noSpaces,
                )
              }
            )
            val generationIdentity = QdrantGenerationIdentity(
              sourceContentFingerprint = prepared.sourceContentFingerprint,
              projectedDocumentsFingerprint = prepared.projectedDocumentsFingerprint,
              collectionContractFingerprint = prepared.collectionContractFingerprint,
              projectionFormatVersion = prepared.projectionFormatVersion,
              compilerVersion = QdrantCompilerVersion.Current.value,
              collectionFormatVersion = QdrantCollectionFormatVersion.Current.value,
              embeddingModel = policyModel(prepared),
              embeddedPointsFingerprint = embeddedFingerprint,
            )
            val generationId = QdrantCanonical.sha256(
              Vector(
                generationIdentity.sourceContentFingerprint,
                generationIdentity.projectedDocumentsFingerprint,
                generationIdentity.collectionContractFingerprint,
                generationIdentity.projectionFormatVersion,
                generationIdentity.compilerVersion,
                generationIdentity.collectionFormatVersion,
                generationIdentity.embeddingModel.provider,
                generationIdentity.embeddingModel.model,
                generationIdentity.embeddingModel.revision,
                generationIdentity.embeddingModel.dimension.toString,
                generationIdentity.embeddingModel.textFormatVersion,
                generationIdentity.embeddedPointsFingerprint,
              )
            )
            val physicalName = s"$physicalCollectionPrefix$generationId"
            val metadata = QdrantGenerationMetadata(MetadataSchemaVersion, generationId, generationIdentity, points.size)
            Right(new QdrantCompiledGeneration(generationIdentity, physicalName, collectionJson(prepared, metadata), payloadIndexRequests(prepared), points, metadata))
        }
      }
    }
  }

  private def policyVectorName(prepared: QdrantPreparedGeneration): QdrantVectorName =
    prepared.policy match {
      case policy: QdrantPolicy[?, ?] => policy.vectorName
    }

  private def policyModel(prepared: QdrantPreparedGeneration): QdrantEmbeddingModelIdentity =
    prepared.policy match {
      case policy: QdrantPolicy[?, ?] => policy.embeddingModel
    }

  private def identityFor[Document, Id](field: SearchField[Document, Id], document: Document): Either[QdrantPointIdError, QdrantPointId] =
    field.extract(document) match {
      case None => Left(QdrantPointIdError.UnsupportedCanonicalValue(field.id, "missing"))
      case Some(value) => QdrantPointId.fromCanonical(field, field.codec.encodeCanonical(value))
    }

  private def collectionJson(prepared: QdrantPreparedGeneration, metadata: QdrantGenerationMetadata): Json =
    prepared.policy match {
      case policy: QdrantPolicy[?, ?] =>
        Json.obj(
          "vectors" -> Json.obj(policy.vectorName.value -> Json.obj(
            "size" -> Json.fromInt(policy.embeddingModel.dimension),
            "distance" -> Json.fromString(policy.distance.wireValue),
          )),
          "metadata" -> Json.obj("search_gen2" -> metadataJson(metadata)),
        )
    }

  private def metadataJson(metadata: QdrantGenerationMetadata): Json =
    Json.obj(
      "schema_version" -> Json.fromString(metadata.schemaVersion),
      "generation_id" -> Json.fromString(metadata.generationId),
      "point_count" -> Json.fromInt(metadata.pointCount),
      "identity" -> Json.obj(
        "source_content_fingerprint" -> Json.fromString(metadata.identity.sourceContentFingerprint),
        "projected_documents_fingerprint" -> Json.fromString(metadata.identity.projectedDocumentsFingerprint),
        "collection_contract_fingerprint" -> Json.fromString(metadata.identity.collectionContractFingerprint),
        "projection_format_version" -> Json.fromString(metadata.identity.projectionFormatVersion),
        "compiler_version" -> Json.fromString(metadata.identity.compilerVersion),
        "collection_format_version" -> Json.fromString(metadata.identity.collectionFormatVersion),
        "embedding_model" -> Json.obj(
          "provider" -> Json.fromString(metadata.identity.embeddingModel.provider),
          "model" -> Json.fromString(metadata.identity.embeddingModel.model),
          "revision" -> Json.fromString(metadata.identity.embeddingModel.revision),
          "dimension" -> Json.fromInt(metadata.identity.embeddingModel.dimension),
          "text_format_version" -> Json.fromString(metadata.identity.embeddingModel.textFormatVersion),
        ),
        "embedded_points_fingerprint" -> Json.fromString(metadata.identity.embeddedPointsFingerprint),
      ),
    )

  private def payloadIndexRequests(prepared: QdrantPreparedGeneration): Vector[Json] =
    prepared.policy match {
      case policy: QdrantPolicy[?, ?] =>
        policy.payloadIndexes.map(index => Json.obj(
          "field_name" -> Json.fromString(index.path.value),
          "field_schema" -> Json.fromString(index.schema.wireValue),
        ))
    }
}
