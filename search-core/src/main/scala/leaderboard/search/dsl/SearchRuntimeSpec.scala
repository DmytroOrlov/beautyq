package leaderboard.search.dsl

import leaderboard.search.document.SearchDocumentPayloadSpec

final case class SearchRuntimeSpec[A](
  documentSpec: SearchDocumentSpec[A],
  querySchema: SearchQuerySchema[A],
  requestSpec: SearchRequestSpec,
  facetSpec: FacetSpec[A],
  carouselSpec: CarouselSpec[A],
  payloadSpecs: Map[String, SearchDocumentPayloadSpec[A]],
  embeddingSpec: Option[EmbeddingSpec[A]],
  vectorSearchSpec: Option[VectorSearchSpec],
)
