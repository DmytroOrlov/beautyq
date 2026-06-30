package leaderboard.search.qdrant

import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.dsl.{SearchField, VectorSearchSpec}
import leaderboard.search.embedding.EmbeddingClient
import zio.IO

final class QdrantSemanticCandidateSearch(
  embeddingClient: EmbeddingClient,
  qdrantSearchClient: QdrantSearchClient,
  variantIdPayloadField: SearchField[VariantSearchDocument],
) {
  private val documentSearch =
    new QdrantSemanticDocumentSearch[VariantSearchDocument, MasterServiceOfferVariantId](
      embeddingClient,
      qdrantSearchClient,
      variantIdPayloadField,
    )

  def search(queryText: String, spec: VectorSearchSpec): IO[QueryFailure, List[QdrantCandidateHit]] =
    documentSearch.search(queryText, spec).map { hits =>
      hits.map(hit => QdrantCandidateHit(variantId = hit.documentId, score = hit.score))
    }
}
