package leaderboard.search.gen2.elasticsearch.lifecycle

import leaderboard.search.gen2.elasticsearch.*

/** A generation selected by the lifecycle owner. The constructor is scoped to this package so domain
  * wiring can carry the value but cannot invent a physical target or generation identity. */
final class LifecycleResolvedElasticsearchGeneration private[lifecycle] (
  val reference: ElasticsearchGenerationReference,
  val physicalTarget: ElasticsearchSearchTarget,
  val identity: ElasticsearchGenerationIdentity,
)

sealed trait ElasticsearchSearchRequestAuthorizationError

object ElasticsearchSearchRequestAuthorizationError {
  final case class GenerationReferenceMismatch(expected: ElasticsearchGenerationReference, actual: ElasticsearchGenerationReference)
      extends ElasticsearchSearchRequestAuthorizationError
  final case class GenerationContractFingerprintMismatch(expected: leaderboard.search.gen2.core.plan.ContractFingerprint, actual: leaderboard.search.gen2.core.plan.ContractFingerprint)
      extends ElasticsearchSearchRequestAuthorizationError
}

type AuthorizedElasticsearchSearchRequest[Document, Id] = ElasticsearchSearchRequestAuthorization.AuthorizedElasticsearchSearchRequest[Document, Id]

/** The executable request boundary. Only lifecycle code in this package can bind a prepared request to a
  * resolved retained physical generation. The request body, target, cursor reference and generation
  * identity therefore remain one compiler/lifecycle-owned value. */
object ElasticsearchSearchRequestAuthorization {

  private[lifecycle] def authorize[Document, Id](
    prepared: PreparedElasticsearchSearchRequest[Document, Id],
    resolved: LifecycleResolvedElasticsearchGeneration,
  ): Either[ElasticsearchSearchRequestAuthorizationError, AuthorizedElasticsearchSearchRequest[Document, Id]] =
    for {
      _ <- prepared.generationRequirement match {
             case ElasticsearchGenerationRequirement.Active => Right(())
             case ElasticsearchGenerationRequirement.Pinned(expected) if expected == resolved.reference => Right(())
             case ElasticsearchGenerationRequirement.Pinned(expected) =>
               Left(ElasticsearchSearchRequestAuthorizationError.GenerationReferenceMismatch(expected, resolved.reference))
           }
      _ <- if (prepared.boundPlan.identity.contractFingerprint == resolved.identity.contractFingerprint) Right(())
           else Left(
             ElasticsearchSearchRequestAuthorizationError.GenerationContractFingerprintMismatch(
               prepared.boundPlan.identity.contractFingerprint,
               resolved.identity.contractFingerprint,
             )
           )
    } yield new AuthorizedElasticsearchSearchRequest(prepared, resolved)

  /** Final immutable lifecycle-bound request. It is not a case class: no public `copy`, factory or
    * subclassing path exists. */
  final class AuthorizedElasticsearchSearchRequest[Document, Id] private[lifecycle] (
    val prepared: PreparedElasticsearchSearchRequest[Document, Id],
    val generation: LifecycleResolvedElasticsearchGeneration,
  ) {
    def body: io.circe.Json = prepared.body
    def target: ElasticsearchSearchTarget = generation.physicalTarget
    def generationReference: ElasticsearchGenerationReference = generation.reference
  }
}
