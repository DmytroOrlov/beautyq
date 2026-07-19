package leaderboard.search.gen2.qdrant

import io.circe.{Json, JsonObject}

/** One executable owner for the persisted generation metadata wire shape. */
private[qdrant] object QdrantGenerationMetadataCodec {
  val CurrentSchemaVersion: String = "qdrant-generation-metadata-v1"

  sealed trait Error
  object Error {
    final case class Malformed(path: String, message: String) extends Error
    final case class UnsupportedSchema(value: String) extends Error
  }

  def encode(metadata: QdrantGenerationMetadata): Json =
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

  def decode(raw: Json): Either[Error, QdrantGenerationMetadata] =
    raw.asObject.toRight(Error.Malformed("metadata", "must be an object")).flatMap { obj =>
      for {
        schema <- string(obj, "schema_version")
        _      <- Either.cond(schema == CurrentSchemaVersion, (), Error.UnsupportedSchema(schema))
        id     <- string(obj, "generation_id")
        count  <- integer(obj, "point_count")
        identityObj <- obj("identity").flatMap(_.asObject).toRight(Error.Malformed("identity", "must be an object"))
        identity <- decodeIdentity(identityObj)
      } yield QdrantGenerationMetadata(schema, id, identity, count)
    }

  private def decodeIdentity(obj: JsonObject): Either[Error, QdrantGenerationIdentity] =
    for {
      source     <- string(obj, "source_content_fingerprint")
      projected  <- string(obj, "projected_documents_fingerprint")
      contract   <- string(obj, "collection_contract_fingerprint")
      projection <- string(obj, "projection_format_version")
      compiler   <- string(obj, "compiler_version")
      format     <- string(obj, "collection_format_version")
      modelObj   <- obj("embedding_model").flatMap(_.asObject).toRight(Error.Malformed("identity.embedding_model", "must be an object"))
      provider   <- string(modelObj, "provider")
      model      <- string(modelObj, "model")
      revision   <- string(modelObj, "revision")
      dimension  <- integer(modelObj, "dimension")
      textFormat <- string(modelObj, "text_format_version")
      embedded   <- string(obj, "embedded_points_fingerprint")
    } yield QdrantGenerationIdentity(source, projected, contract, projection, compiler, format, QdrantEmbeddingModelIdentity(provider, model, revision, dimension, textFormat), embedded)

  private def string(obj: JsonObject, name: String): Either[Error, String] =
    obj(name).flatMap(_.asString).toRight(Error.Malformed(name, "must be a string"))

  private def integer(obj: JsonObject, name: String): Either[Error, Int] =
    obj(name).flatMap(_.asNumber).flatMap(_.toInt).filter(_ >= 0).toRight(Error.Malformed(name, "must be a non-negative integer"))
}
