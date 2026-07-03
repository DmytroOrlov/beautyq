package leaderboard.search.contract

/** Physical/logical name of a declared search field (e.g. "title",
  * "priceRange"). Never a raw string at call sites once wrapped.
  */
final case class SearchFieldName(value: String) extends AnyVal

/** The generic shape a declared search field can take. Purely descriptive -
  * an interpreter (ES/Qdrant/in-memory) decides how to realize each kind;
  * this module has no opinion on physical mapping.
  */
enum SearchFieldKind {

  /** Free-text, analyzed field. */
  case Text

  /** Exact-match/filterable, non-analyzed field. */
  case Keyword

  /** Aggregatable facet field. */
  case Facet

  /** Sortable/comparable numeric field. */
  case Numeric

  /** Bounded numeric range field (e.g. price range, duration range). */
  case Range

  /** Geospatial point/shape field. */
  case Geo

  /** Semantic text/vector candidate field (embedding-backed). */
  case SemanticText
}

/** One declared document field: its name and kind. Domains compose lists of
  * these to describe a document's searchable/filterable/facetable surface;
  * no field here carries business meaning.
  */
final case class SearchField(
  name: SearchFieldName,
  kind: SearchFieldKind,
)
