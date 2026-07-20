package leaderboard.search.gen2.elasticsearch

import leaderboard.search.gen2.core.candidate.SearchGenerationEvidence
import leaderboard.search.gen2.core.plan.ContractFingerprint.value

import io.circe.{Json, JsonObject}

import java.time.{Instant, DateTimeException}

final case class ElasticsearchGenerationMetadataSchemaVersion(value: String)
object ElasticsearchGenerationMetadataSchemaVersion {
  val Current: ElasticsearchGenerationMetadataSchemaVersion = ElasticsearchGenerationMetadataSchemaVersion("es-generation-metadata-v1")
}

final case class ElasticsearchPersistedGenerationIdentity(
  sourceContentFingerprint: String,
  projectedDocumentsFingerprint: String,
  contractFingerprint: String,
  projectionFormatVersion: String,
  compilerVersion: String,
  indexFormatVersion: String,
)

object ElasticsearchPersistedGenerationIdentity {
  def fromTrusted(identity: ElasticsearchGenerationIdentity): ElasticsearchPersistedGenerationIdentity =
    ElasticsearchPersistedGenerationIdentity(
      identity.sourceContentFingerprint.value,
      identity.projectedDocumentsFingerprint.value,
      identity.contractFingerprint.value,
      identity.projectionFormatVersion.value,
      identity.compilerVersion.value,
      identity.indexFormatVersion.value,
    )
}

final case class ElasticsearchGenerationMetadata(
  schemaVersion: ElasticsearchGenerationMetadataSchemaVersion,
  generationId: ElasticsearchGenerationId,
  builtAt: Instant,
  documentCount: Long,
  identity: ElasticsearchPersistedGenerationIdentity,
) extends SearchGenerationEvidence {
  def sourceContentFingerprint: String = identity.sourceContentFingerprint
  def projectedDocumentsFingerprint: String = identity.projectedDocumentsFingerprint
  def projectionFormatVersion: String = identity.projectionFormatVersion
}

sealed trait ElasticsearchGenerationMetadataError
object ElasticsearchGenerationMetadataError {
  final case class ExpectedObject(context: String) extends ElasticsearchGenerationMetadataError
  final case class UnexpectedFields(actual: Set[String]) extends ElasticsearchGenerationMetadataError
  final case class MissingOrInvalidField(name: String) extends ElasticsearchGenerationMetadataError
  final case class UnsupportedSchemaVersion(actual: String) extends ElasticsearchGenerationMetadataError
  final case class InvalidGenerationId(actual: String) extends ElasticsearchGenerationMetadataError
  final case class NonCanonicalBuiltAt(actual: String) extends ElasticsearchGenerationMetadataError
  final case class NegativeDocumentCount(actual: Long) extends ElasticsearchGenerationMetadataError
}

object ElasticsearchGenerationMetadataCodec {
  private val ExpectedFields = Set(
    "schemaVersion", "generationId", "builtAt", "documentCount",
    "sourceContentFingerprint", "projectedDocumentsFingerprint", "contractFingerprint",
    "projectionFormatVersion", "compilerVersion", "indexFormatVersion",
  )

  def encode(metadata: ElasticsearchGenerationMetadata): Json =
    Json.obj(
      "schemaVersion" -> Json.fromString(metadata.schemaVersion.value),
      "generationId" -> Json.fromString(metadata.generationId.value),
      "builtAt" -> Json.fromString(metadata.builtAt.toString),
      "documentCount" -> Json.fromLong(metadata.documentCount),
      "sourceContentFingerprint" -> Json.fromString(metadata.identity.sourceContentFingerprint),
      "projectedDocumentsFingerprint" -> Json.fromString(metadata.identity.projectedDocumentsFingerprint),
      "contractFingerprint" -> Json.fromString(metadata.identity.contractFingerprint),
      "projectionFormatVersion" -> Json.fromString(metadata.identity.projectionFormatVersion),
      "compilerVersion" -> Json.fromString(metadata.identity.compilerVersion),
      "indexFormatVersion" -> Json.fromString(metadata.identity.indexFormatVersion),
    )

  def decode(json: Json): Either[ElasticsearchGenerationMetadataError, ElasticsearchGenerationMetadata] =
    for {
      obj <- json.asObject.toRight(ElasticsearchGenerationMetadataError.ExpectedObject("_meta"))
      _ <- Either.cond(obj.keys.toSet == ExpectedFields, (), ElasticsearchGenerationMetadataError.UnexpectedFields(obj.keys.toSet))
      schema <- string(obj, "schemaVersion")
      _ <- Either.cond(schema == ElasticsearchGenerationMetadataSchemaVersion.Current.value, (), ElasticsearchGenerationMetadataError.UnsupportedSchemaVersion(schema))
      generationIdRaw <- string(obj, "generationId")
      generationId <- ElasticsearchGenerationNaming.generationIdFromPersisted(generationIdRaw).left.map(_ => ElasticsearchGenerationMetadataError.InvalidGenerationId(generationIdRaw))
      builtAtRaw <- string(obj, "builtAt")
      builtAt <- parseCanonicalInstant(builtAtRaw)
      count <- long(obj, "documentCount")
      _ <- Either.cond(count >= 0L, (), ElasticsearchGenerationMetadataError.NegativeDocumentCount(count))
      source <- string(obj, "sourceContentFingerprint")
      projected <- string(obj, "projectedDocumentsFingerprint")
      contract <- string(obj, "contractFingerprint")
      projection <- string(obj, "projectionFormatVersion")
      compiler <- string(obj, "compilerVersion")
      index <- string(obj, "indexFormatVersion")
    } yield ElasticsearchGenerationMetadata(
      ElasticsearchGenerationMetadataSchemaVersion.Current,
      generationId,
      builtAt,
      count,
      ElasticsearchPersistedGenerationIdentity(source, projected, contract, projection, compiler, index),
    )

  private def string(obj: JsonObject, name: String): Either[ElasticsearchGenerationMetadataError, String] =
    obj(name).flatMap(_.asString).toRight(ElasticsearchGenerationMetadataError.MissingOrInvalidField(name))

  private def long(obj: JsonObject, name: String): Either[ElasticsearchGenerationMetadataError, Long] =
    obj(name).flatMap(_.asNumber).flatMap(_.toLong).toRight(ElasticsearchGenerationMetadataError.MissingOrInvalidField(name))

  private def parseCanonicalInstant(value: String): Either[ElasticsearchGenerationMetadataError, Instant] =
    try {
      val parsed = Instant.parse(value)
      Either.cond(parsed.toString == value, parsed, ElasticsearchGenerationMetadataError.NonCanonicalBuiltAt(value))
    } catch {
      case _: DateTimeException => Left(ElasticsearchGenerationMetadataError.NonCanonicalBuiltAt(value))
    }
}
