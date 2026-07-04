package leaderboard.search.beautyq.contract

import leaderboard.search.contract.{RuntimeSection, SearchBackendCapabilities, SearchBackendId, SearchBackendKind, SearchRuntimeDeclaration}

/** Generic BeautyQ runtime capability declarations `RuntimeSection` requires.
  * `ElasticsearchSearchRequestInterpreter` (in `search-elasticsearch`)
  * source-confirms ES full-text query construction, facet aggregations, and
  * geo query/scoring; BeautyQ has no source-confirmed ES semantic-vector
  * capability. Qdrant's current role is a vector/semantic candidate/supplement
  * source - it does not own BeautyQ full-text, facets, or geo response policy.
  */
object BeautyQSearchRuntimeContract {
  val elasticsearch: SearchRuntimeDeclaration =
    SearchRuntimeDeclaration(
      backendId = SearchBackendId("elasticsearch"),
      kind = SearchBackendKind.Elasticsearch,
      capabilities = SearchBackendCapabilities(
        supportsFullText = true,
        supportsFacets = true,
        supportsGeo = true,
        supportsSemanticVector = false,
      ),
    )

  val qdrant: SearchRuntimeDeclaration =
    SearchRuntimeDeclaration(
      backendId = SearchBackendId("qdrant"),
      kind = SearchBackendKind.Qdrant,
      capabilities = SearchBackendCapabilities(
        supportsFullText = false,
        supportsFacets = false,
        supportsGeo = false,
        supportsSemanticVector = true,
      ),
    )

  val section: RuntimeSection =
    RuntimeSection(List(elasticsearch, qdrant))
}
