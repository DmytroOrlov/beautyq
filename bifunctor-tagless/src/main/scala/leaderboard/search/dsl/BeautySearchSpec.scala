package leaderboard.search.dsl

import leaderboard.search.document.{SearchDocumentPayloadSpec, VariantSearchDocument}

/** BeautyQ runtime spec aggregate. This is the application/BeautyQ-owned adapter over the generic
  * search DSL: it pins the document type to [[VariantSearchDocument]] and bundles the BeautyQ schema,
  * intent vocabulary, carousel, facet, request, embedding, and vector specs. It lives in package
  * `leaderboard.search.dsl` for source compatibility, but it is intentionally NOT part of the generic
  * DSL core (which stays document-type agnostic in `SearchDsl.scala`).
  */
final case class BeautySearchSpec(
  variantDocument: SearchDocumentSpec[VariantSearchDocument],
  intentVocabulary: SearchIntentVocabulary,
  carouselSpec: CarouselSpec[VariantSearchDocument],
  facetSpec: FacetSpec[VariantSearchDocument],
  requestSpec: SearchRequestSpec = SearchRequestSpec(),
  querySchema: SearchQuerySchema[VariantSearchDocument],
  embeddingSpec: Option[EmbeddingSpec[VariantSearchDocument]] = None,
  vectorSearchSpec: Option[VectorSearchSpec] = None,
) {
  def runtimeSpec(
    payloadSpecs: Map[String, SearchDocumentPayloadSpec[VariantSearchDocument]]
  ): SearchRuntimeSpec[VariantSearchDocument] =
    SearchRuntimeSpec(
      documentSpec = variantDocument,
      querySchema = querySchema,
      requestSpec = requestSpec,
      facetSpec = facetSpec,
      carouselSpec = carouselSpec,
      payloadSpecs = payloadSpecs,
      embeddingSpec = embeddingSpec,
      vectorSearchSpec = vectorSearchSpec,
    )
}
