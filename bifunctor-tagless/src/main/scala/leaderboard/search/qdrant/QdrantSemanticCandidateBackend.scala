package leaderboard.search.qdrant

import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.dsl.VectorSearchSpec
import leaderboard.search.semantic.{SemanticCandidateBackend, SemanticDocumentBackend, SemanticCandidateHit, SemanticDocumentHit}
import leaderboard.search.{ParsedSearchIntent, UserSearchInput}
import zio.IO

final class QdrantSemanticCandidateBackend(
  qdrantSemanticCandidateSearch: QdrantSemanticCandidateSearch,
  vectorSearchSpec: VectorSearchSpec,
) extends SemanticCandidateBackend[IO] with SemanticDocumentBackend[IO, MasterServiceOfferVariantId] {

  override def candidates(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, List[SemanticCandidateHit]] =
    qdrantSemanticCandidateSearch.search(input.query, vectorSearchSpec).map {
      _.map(hit => SemanticCandidateHit(hit.variantId, hit.score))
    }

  override def documentHits(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, List[SemanticDocumentHit[MasterServiceOfferVariantId]]] =
    candidates(input, intent).map(_.map(SemanticCandidateHit.toDocumentHit))
}
