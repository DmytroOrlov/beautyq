package leaderboard.search.qdrant

import leaderboard.model.QueryFailure
import leaderboard.search.dsl.VectorSearchSpec
import leaderboard.search.semantic.{SemanticCandidateBackend, SemanticCandidateHit}
import leaderboard.search.{ParsedSearchIntent, UserSearchInput}
import zio.IO

final class QdrantSemanticCandidateBackend(
  qdrantSemanticCandidateSearch: QdrantSemanticCandidateSearch,
  vectorSearchSpec: VectorSearchSpec,
) extends SemanticCandidateBackend[IO] {

  override def candidates(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, List[SemanticCandidateHit]] =
    qdrantSemanticCandidateSearch.search(input.query, vectorSearchSpec).map {
      _.map(hit => SemanticCandidateHit(hit.variantId, hit.score))
    }
}
