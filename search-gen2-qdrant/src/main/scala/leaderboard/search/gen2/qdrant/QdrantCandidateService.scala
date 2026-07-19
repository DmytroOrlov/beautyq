package leaderboard.search.gen2.qdrant

import leaderboard.search.gen2.transport.Gen2HttpTransportError

final class QdrantCandidateServiceConfig private (
  val alias: QdrantResourceName,
  val physicalCollectionPrefix: String,
)

object QdrantCandidateServiceConfig {
  sealed trait Error
  object Error {
    final case class InvalidAlias(error: QdrantResourceNameError) extends Error
    final case class InvalidPrefix(value: String) extends Error
  }

  def create(alias: QdrantResourceName, physicalCollectionPrefix: String): Either[Error, QdrantCandidateServiceConfig] =
    if (physicalCollectionPrefix.trim.isEmpty || physicalCollectionPrefix.contains("/") || physicalCollectionPrefix.contains("*") || !physicalCollectionPrefix.startsWith(alias.value)) Left(Error.InvalidPrefix(physicalCollectionPrefix))
    else Right(new QdrantCandidateServiceConfig(alias, physicalCollectionPrefix))
}

sealed trait QdrantCandidateServiceError
object QdrantCandidateServiceError {
  final case class ContractFingerprintMismatch(expected: String, actual: String) extends QdrantCandidateServiceError
  final case class AliasMissing(alias: String) extends QdrantCandidateServiceError
  final case class AliasAmbiguous(alias: String, targets: Vector[String]) extends QdrantCandidateServiceError
  final case class Transport(operation: String, error: Gen2HttpTransportError) extends QdrantCandidateServiceError
  final case class Malformed(operation: String, message: String) extends QdrantCandidateServiceError
  final case class UnauthorizedCollection(target: String, reason: String) extends QdrantCandidateServiceError
  final case class Response(error: QdrantCandidateResponseError) extends QdrantCandidateServiceError
}

/** Resolves and authorizes a physical collection internally; callers never supply an executable target. */
final class QdrantCandidateService(
  client: QdrantGen2Client,
  config: QdrantCandidateServiceConfig,
) {
  import QdrantCandidateServiceError.*

  def execute[Document, Id](
    policy: QdrantPolicy[Document, Id],
    request: QdrantCompiledCandidateRequest,
  ): Either[QdrantCandidateServiceError, QdrantCandidateSearchResult[Id]] =
    for {
      _ <- Either.cond(request.candidateContractFingerprint == policy.candidateContractFingerprint.value, (), ContractFingerprintMismatch(policy.candidateContractFingerprint.value, request.candidateContractFingerprint))
      aliases <- client.listAliases().left.map(Transport("list-aliases", _)).flatMap(raw => QdrantCollectionWire.aliases(raw).left.map(error => Malformed("list-aliases", error.toString)))
      targets = aliases.filter(_.alias == config.alias.value).map(_.collection).distinct.sorted
      target <- targets match {
        case Vector()       => Left(AliasMissing(config.alias.value))
        case Vector(value)   => Right(value)
        case many            => Left(AliasAmbiguous(config.alias.value, many))
      }
      targetName <- QdrantResourceName.from(target).left.map(_ => UnauthorizedCollection(target, "alias target is not a safe collection name"))
      _ <- Either.cond(target.startsWith(config.physicalCollectionPrefix), (), UnauthorizedCollection(target, "alias target is outside the configured physical prefix"))
      _ <- Either.cond(target.drop(config.physicalCollectionPrefix.length).matches("[0-9a-f]{64}"), (), UnauthorizedCollection(target, "physical suffix must be 64 lowercase hexadecimal characters"))
      raw <- client.getCollection(targetName).left.map(Transport("get-collection", _))
      details <- QdrantCollectionWire.details(raw, target).left.map(error => UnauthorizedCollection(target, error.toString))
      _ <- authorizeTarget(details, target, policy)
      response <- client.queryPoints(targetName, request.body).left.map(Transport("query-points", _))
      result <- QdrantCandidateResponseDecoder.decode(policy.identity, request, response).left.map(Response.apply)
    } yield result

  private def authorizeTarget[Document, Id](details: QdrantCollectionWire.Details, target: String, policy: QdrantPolicy[Document, Id]): Either[QdrantCandidateServiceError, Unit] = {
    val generationId = QdrantGenerationNaming.generationId(details.metadata.identity)
    val suffix = target.drop(config.physicalCollectionPrefix.length)
    for {
      _ <- Either.cond(details.metadata.generationId == generationId, (), UnauthorizedCollection(target, "generation id is not reproducible from persisted identity"))
      _ <- Either.cond(suffix == details.metadata.generationId, (), UnauthorizedCollection(target, "physical name suffix differs from generation id"))
      _ <- Either.cond(QdrantGenerationNaming.physicalName(config.physicalCollectionPrefix, details.metadata.identity) == target, (), UnauthorizedCollection(target, "physical name is not deterministic"))
      _ <- QdrantCollectionWire.validatePolicy(details, policy).left.map(error => UnauthorizedCollection(target, error.toString))
    } yield ()
  }
}
