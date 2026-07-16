package leaderboard.search.gen2.elasticsearch

import leaderboard.search.gen2.core.materialization.CanonicalFingerprint

import java.nio.charset.StandardCharsets

final case class ElasticsearchGenerationId private[elasticsearch] (value: String)

sealed trait ElasticsearchGenerationNamingError
object ElasticsearchGenerationNamingError {
  final case class InvalidPrefix(value: String) extends ElasticsearchGenerationNamingError
  final case class InvalidPhysicalName(value: String) extends ElasticsearchGenerationNamingError
  final case class GenerationReferenceMismatch(expected: String, actual: String) extends ElasticsearchGenerationNamingError
}

object ElasticsearchGenerationNaming {
  val GenerationIdEncodingVersion: String = "es-generation-id-v1"

  def generationId(identity: ElasticsearchGenerationIdentity): ElasticsearchGenerationId =
    generationId(ElasticsearchPersistedGenerationIdentity.fromTrusted(identity))

  private[elasticsearch] def generationId(identity: ElasticsearchPersistedGenerationIdentity): ElasticsearchGenerationId =
    generationId(identity, ElasticsearchGenerationMetadataSchemaVersion.Current)

  private[elasticsearch] def generationId(
    identity: ElasticsearchPersistedGenerationIdentity,
    metadataSchemaVersion: ElasticsearchGenerationMetadataSchemaVersion,
  ): ElasticsearchGenerationId =
    ElasticsearchGenerationId(
      CanonicalFingerprint.sha256HexTokens(
        Vector(
          CanonicalFingerprint.token("generation-id-encoding-version", GenerationIdEncodingVersion),
          CanonicalFingerprint.token("generation-metadata-schema-version", metadataSchemaVersion.value),
          CanonicalFingerprint.token("source-content-fingerprint", identity.sourceContentFingerprint),
          CanonicalFingerprint.token("projected-documents-fingerprint", identity.projectedDocumentsFingerprint),
          CanonicalFingerprint.token("contract-fingerprint", identity.contractFingerprint),
          CanonicalFingerprint.token("projection-format-version", identity.projectionFormatVersion),
          CanonicalFingerprint.token("compiler-version", identity.compilerVersion),
          CanonicalFingerprint.token("index-format-version", identity.indexFormatVersion),
        )
      )
    )

  def physicalIndexName(
    prefix: String,
    identity: ElasticsearchGenerationIdentity,
  ): Either[ElasticsearchGenerationNamingError, ElasticsearchSearchTarget] =
    physicalIndexName(prefix, ElasticsearchPersistedGenerationIdentity.fromTrusted(identity))

  private[elasticsearch] def physicalIndexName(
    prefix: String,
    identity: ElasticsearchPersistedGenerationIdentity,
  ): Either[ElasticsearchGenerationNamingError, ElasticsearchSearchTarget] = {
    val candidate = s"$prefix${generationId(identity).value}"
    if (!validPrefix(prefix)) Left(ElasticsearchGenerationNamingError.InvalidPrefix(prefix))
    else if (!validIndexName(candidate)) Left(ElasticsearchGenerationNamingError.InvalidPhysicalName(candidate))
    else Right(ElasticsearchSearchTarget(candidate))
  }

  def reference(target: ElasticsearchSearchTarget): ElasticsearchGenerationReference =
    ElasticsearchGenerationReference(target.value)

  def validateReference(
    prefix: String,
    reference: ElasticsearchGenerationReference,
    metadata: ElasticsearchGenerationMetadata,
  ): Either[ElasticsearchGenerationNamingError, ElasticsearchSearchTarget] =
    for {
      expectedTarget <- physicalIndexName(prefix, metadata.identity)
      expectedId = generationId(metadata.identity)
      _ <- Either.cond(metadata.generationId == expectedId, (), ElasticsearchGenerationNamingError.GenerationReferenceMismatch(expectedId.value, metadata.generationId.value))
      _ <- Either.cond(reference.value == expectedTarget.value, (), ElasticsearchGenerationNamingError.GenerationReferenceMismatch(expectedTarget.value, reference.value))
    } yield expectedTarget

  def validateCandidateReference(
    prefix: String,
    reference: ElasticsearchGenerationReference,
  ): Either[ElasticsearchGenerationNamingError, Unit] = {
    val suffix = reference.value.stripPrefix(prefix)
    if (!validPrefix(prefix) || !reference.value.startsWith(prefix) || !suffix.matches("[0-9a-f]{64}") || !validIndexName(reference.value))
      Left(ElasticsearchGenerationNamingError.InvalidPhysicalName(reference.value))
    else Right(())
  }

  def validIndexName(value: String): Boolean =
    value.nonEmpty && value.getBytes(StandardCharsets.UTF_8).length <= 255 && value == value.toLowerCase &&
      !value.startsWith("-") && !value.startsWith("_") && !value.startsWith("+") &&
      !value.exists("\\/*?\"<>| ,#:".contains(_)) && value != "." && value != ".."

  private[elasticsearch] def generationIdFromPersisted(value: String): Either[ElasticsearchGenerationNamingError, ElasticsearchGenerationId] =
    if (value.matches("[0-9a-f]{64}")) Right(ElasticsearchGenerationId(value))
    else Left(ElasticsearchGenerationNamingError.InvalidPhysicalName(value))

  private def validPrefix(value: String): Boolean = value.nonEmpty && validIndexName(s"${value}a")
}
