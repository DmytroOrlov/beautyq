package leaderboard.search.contract

/** The kind of backend a runtime declaration describes. Purely a label - no
  * client, request, or response type from any concrete backend is imported or
  * referenced here.
  */
enum SearchBackendKind {
  case Elasticsearch
  case Qdrant
  case InMemory
}

/** Declared capabilities of one backend runtime, described only as booleans a
  * planner/interpreter can branch on - never as concrete client behavior.
  */
final case class SearchBackendCapabilities(
  supportsFullText: Boolean,
  supportsFacets: Boolean,
  supportsGeo: Boolean,
  supportsSemanticVector: Boolean,
)

/** One declared backend runtime: its id, kind, and declared capabilities.
  * A domain contract lists these to say "this search domain may run against
  * an ES backend with these capabilities, a Qdrant backend with those, and/or
  * an in-memory backend" - without depending on any ES/Qdrant client module.
  */
final case class SearchRuntimeDeclaration(
  backendId: SearchBackendId,
  kind: SearchBackendKind,
  capabilities: SearchBackendCapabilities,
)

/** The runtime section: the list of backend runtime declarations a domain
  * contract exposes. Purely declarative - no wiring, no client construction.
  */
final case class RuntimeSection(
  declarations: List[SearchRuntimeDeclaration],
)
