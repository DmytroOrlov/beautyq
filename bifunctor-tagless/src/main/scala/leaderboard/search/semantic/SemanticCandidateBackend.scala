package leaderboard.search.semantic

import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.qdrant.QdrantCandidateHit
import leaderboard.search.{ParsedSearchIntent, UserSearchInput}

trait SemanticCandidateBackend[F[_, _]] {
  def candidates(input: UserSearchInput, intent: ParsedSearchIntent): F[QueryFailure, List[QdrantCandidateHit]]
}

trait VariantSearchDocumentLookup[F[_, _]] {
  def lookup(variantIds: List[MasterServiceOfferVariantId]): F[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]]
}
