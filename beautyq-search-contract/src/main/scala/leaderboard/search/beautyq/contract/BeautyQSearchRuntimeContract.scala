package leaderboard.search.beautyq.contract

import leaderboard.search.contract.{RuntimeSection, SearchBackendCapabilities, SearchBackendId, SearchBackendKind, SearchRuntimeDeclaration}
import leaderboard.search.dsl.VectorDistance

/** Generic BeautyQ runtime capability declarations `RuntimeSection` requires.
  * `ElasticsearchSearchRequestInterpreter` (in `search-elasticsearch`)
  * source-confirms ES full-text query construction, facet aggregations, and
  * geo query/scoring; BeautyQ has no source-confirmed ES semantic-vector
  * capability. Qdrant's current role is a vector/semantic candidate/supplement
  * source - it does not own BeautyQ full-text, facets, or geo response policy.
  */
object BeautyQSearchRuntimeContract {
  val ElasticsearchBackendId: SearchBackendId = SearchBackendId("elasticsearch")
  val QdrantBackendId: SearchBackendId = SearchBackendId("qdrant")

  /** Canonical managed-local BeautyQ Qdrant runtime defaults: the embedding
    * model/dimension/distance the local managed launcher's Qdrant supplement
    * collection is built against (see `BeautyQManagedLocalSearchBootstrapPlan`
    * in `beautyq-search-wiring` and the real Qdrant/Llama client shell in
    * `bifunctor-tagless`). Not a claim about generic Qdrant/production
    * defaults elsewhere - only the managed-local BeautyQ launcher path.
    */
  val ManagedLocalQdrantEmbeddingModelName: String = "local-llama-cpp-embedding"
  val ManagedLocalQdrantExpectedVectorDimension: Int = 1024
  val ManagedLocalQdrantVectorDistance: VectorDistance = VectorDistance.Cosine

  val elasticsearch: SearchRuntimeDeclaration =
    SearchRuntimeDeclaration(
      backendId = ElasticsearchBackendId,
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
      backendId = QdrantBackendId,
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
