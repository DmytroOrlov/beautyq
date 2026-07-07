package leaderboard.search.beautyq.contract

/** Shared BeautyQ response provenance labels and route diagnostic reason categories: the exact JSON
  * field names / JSON values rendered in `BeautySearchResponse` (`beautyq-search-wiring`) and the exact
  * reason-category strings produced by `ExperimentalHybridRouteDiagnostics` (`beautyq-search-wiring`).
  * This is a name-only declaration: it centralizes the repeated string labels, not any response shape,
  * encoder/decoder behavior, or route decision.
  */
object BeautyQSearchResponseProvenanceContract {

  object JsonFields {
    val ExecutionMode: String = "executionMode"
    val ResultOrigin: String = "resultOrigin"
  }

  object ExecutionModes {
    val EsOnly: String = "es_only"
    val EsPlusQdrantSupplement: String = "es_plus_qdrant_supplement"
    val All: List[String] = List(EsOnly, EsPlusQdrantSupplement)
  }

  object ResultOrigins {
    val EsBaseline: String = "es_baseline"
    val QdrantSupplement: String = "qdrant_supplement"
    val All: List[String] = List(EsBaseline, QdrantSupplement)
  }

  object RouteDiagnosticReasonCategories {
    val LexicalOnly: String = "lexical-only"
    val SemanticCandidates: String = "semantic-candidates"
    val FallbackNotImplemented: String = "fallback-not-implemented"
    val LexicalWithQdrantVariantSupplement: String = "lexical-with-qdrant-variant-supplement"

    val All: List[String] =
      List(
        LexicalOnly,
        SemanticCandidates,
        FallbackNotImplemented,
        LexicalWithQdrantVariantSupplement,
      )
  }
}
