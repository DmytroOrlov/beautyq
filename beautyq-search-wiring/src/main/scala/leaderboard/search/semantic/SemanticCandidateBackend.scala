package leaderboard.search.semantic

import izumi.functional.bio.{Error2, F}
import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.{ParsedSearchIntent, UserSearchInput}

final case class SemanticCandidateHit(
  variantId: MasterServiceOfferVariantId,
  score: Double,
)

object SemanticCandidateHit {
  def toDocumentHit(hit: SemanticCandidateHit): SemanticDocumentHit[MasterServiceOfferVariantId] =
    SemanticDocumentHit(hit.variantId, hit.score)
}

trait SemanticDocumentBackend[F[_, _], Id] {
  def documentHits(input: UserSearchInput, intent: ParsedSearchIntent): F[QueryFailure, List[SemanticDocumentHit[Id]]]
}

trait SemanticCandidateBackend[F[_, _]] {
  def candidates(input: UserSearchInput, intent: ParsedSearchIntent): F[QueryFailure, List[SemanticCandidateHit]]
}

trait VariantSearchDocumentLookup[F[_, _]] extends SemanticDocumentLookup[F, MasterServiceOfferVariantId, VariantSearchDocument] {
  def lookup(variantIds: List[MasterServiceOfferVariantId]): F[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]]
}

final class InMemoryVariantSearchDocumentLookup[F[+_, +_]: Error2](documents: List[VariantSearchDocument]) extends VariantSearchDocumentLookup[F] {
  private val documentsById: Map[MasterServiceOfferVariantId, VariantSearchDocument] =
    documents.foldLeft(Map.empty[MasterServiceOfferVariantId, VariantSearchDocument]) {
      case (known, document) if known.contains(document.variantId) => known
      case (known, document) => known.updated(document.variantId, document)
    }

  override def lookup(variantIds: List[MasterServiceOfferVariantId]): F[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]] =
    F.pure(variantIds.iterator.flatMap(variantId => documentsById.get(variantId).map(document => variantId -> document)).toMap)
}
