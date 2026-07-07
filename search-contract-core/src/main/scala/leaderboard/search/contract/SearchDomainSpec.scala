package leaderboard.search.contract

/** Opaque identifier for a declared search domain (e.g. one product line's
  * full search contract). Domain-agnostic: never carries business meaning.
  */
final case class SearchDomainId(value: String) extends AnyVal

/** BCP-47-ish language code a domain declares support for (e.g. "en", "de"). */
final case class SearchLanguage(code: String) extends AnyVal

/** Identifier for a declared vocabulary (e.g. one synonym/attribute group). */
final case class SearchVocabularyId(value: String) extends AnyVal

/** Identifier for a declared search backend runtime (e.g. one ES/Qdrant/in-memory instance). */
final case class SearchBackendId(value: String) extends AnyVal

/** The catalog topology section: a reference/descriptor for whatever a
  * concrete domain's catalog declaration looks like (e.g. a repo-core-backed
  * catalog graph). Left fully generic in `Catalog` - this module has no
  * opinion on catalog mechanics, only that a domain contract carries one.
  */
final case class CatalogSection[Catalog](descriptor: Catalog)

/** One declared vocabulary: a named set of terms with optional synonym
  * expansions. Generic - a concrete domain supplies its own vocabularies
  * (e.g. attribute vocabularies, category/service vocabulary groups) as data,
  * never as new types here.
  */
final case class SearchVocabulary(
  id: SearchVocabularyId,
  terms: List[String],
  synonyms: Map[String, List[String]],
)

/** A named group of vocabularies (e.g. "attributes", "categories",
  * "services" - but the group name and members are domain-supplied data, not
  * hardcoded here).
  */
final case class SearchVocabularyGroup(
  name: String,
  vocabularies: List[SearchVocabulary],
)

/** A declared negative/noise control: terms or patterns a domain wants
  * intent parsing to disregard. Purely descriptive.
  */
final case class NoiseControl(
  description: String,
  excludedTerms: List[String],
)

/** The intent section: supported languages, declared vocabularies/synonym
  * groups, and negative/noise controls. No parser or interpreter lives here -
  * only the declarative shape an interpreter would consume.
  */
final case class IntentSection(
  languages: List[SearchLanguage],
  vocabularies: List[SearchVocabulary],
  vocabularyGroups: List[SearchVocabularyGroup],
  noiseControls: List[NoiseControl],
)

/** The document section: the domain's search document model (`Document`) and
  * result unit model (`ResultUnit`), plus the generic field declarations that
  * describe it. Left generic in both type parameters - this module has no
  * opinion on what a document or result unit actually is.
  */
final case class DocumentSection[Document, ResultUnit](
  document: Document,
  resultUnit: ResultUnit,
  fields: List[SearchField],
)

/** Declared grouping policy for a response (e.g. group hits by a field, cap
  * group size). Fully optional/descriptive - no grouping algorithm lives here.
  */
final case class GroupingPolicy(
  groupByField: Option[SearchFieldName],
  maxGroupSize: Option[Int],
)

/** Declared carousel presentation policy. */
final case class CarouselPolicy(
  enabled: Boolean,
  maxItems: Option[Int],
)

/** Declared facet response policy: which fields are offered as facets. */
final case class FacetResponsePolicy(
  fields: List[SearchFieldName],
)

/** Declared inferred-filter policy: whether/how a response may surface
  * filters inferred from intent parsing, described only, not implemented.
  */
final case class InferredFilterPolicy(
  enabled: Boolean,
  description: String,
)

/** Free-form presentation metadata (e.g. UI labels), kept generic. */
final case class PresentationMetadata(
  labels: Map[String, String],
)

/** Declared debug/explanation flags a response may choose to expose. */
final case class DebugPolicy(
  includeExplanation: Boolean,
  includeScoreBreakdown: Boolean,
)

/** The response section: grouping/carousel/facet/inferred-filter policy plus
  * presentation and debug metadata. Purely declarative response shape - no
  * response construction logic.
  */
final case class ResponseSection(
  grouping: Option[GroupingPolicy],
  carousel: Option[CarouselPolicy],
  facets: Option[FacetResponsePolicy],
  inferredFilters: Option[InferredFilterPolicy],
  presentation: PresentationMetadata,
  debug: DebugPolicy,
)

/** The role a declared evaluation query plays (e.g. a golden expected-hit
  * query vs. a negative/must-not-match query). Purely descriptive metadata
  * for offline evaluation, never consulted by production routing.
  */
enum EvalQueryRole {
  case Golden
  case Negative
  case Exploratory
}

/** A declared expectation for one backend's evaluation behavior (e.g. a
  * minimum recall bound), kept generic across backend kinds.
  */
final case class EvalBackendExpectation(
  backendId: SearchBackendId,
  expectedMinRecall: Option[Double],
)

/** Declared scorecard/evidence configuration: which metrics an offline
  * evaluation run is expected to compute. No computation lives here.
  */
final case class EvalScorecardConfig(
  metrics: List[String],
)

/** Marker proving evaluation declarations never activate production routing.
  * The sealed trait has exactly one inhabitant ([[EvalProductionRoutingEffect.None]]),
  * so no [[EvalSection]] can be constructed that claims otherwise - the
  * "never activates production routing" rule is enforced at the type level,
  * not by a boolean flag that a future declaration could flip to `true`.
  */
sealed trait EvalProductionRoutingEffect
object EvalProductionRoutingEffect {
  case object None extends EvalProductionRoutingEffect
}

/** The evaluation section: accepted query roles, negative controls, per-backend
  * expectation metadata, and scorecard config. [[productionRoutingEffect]] is
  * always [[EvalProductionRoutingEffect.None]] - evaluation declarations are
  * offline evidence/metadata only, never production routing input.
  */
final case class EvalSection(
  acceptedQueryRoles: List[EvalQueryRole],
  negativeControls: List[String],
  backendExpectations: List[EvalBackendExpectation],
  scorecard: EvalScorecardConfig,
  productionRoutingEffect: EvalProductionRoutingEffect = EvalProductionRoutingEffect.None,
)

/** The full generic search domain contract: catalog topology reference,
  * document/result-unit model plus fields, intent declarations, backend
  * runtime declarations, response policy, and evaluation metadata.
  *
  * Fully generic in `Catalog`, `Document`, and `ResultUnit` - a concrete
  * domain supplies its own types for these and assembles a
  * `SearchDomainSpec` from them; this module never encodes domain-specific
  * values or types.
  */
final case class SearchDomainSpec[Catalog, Document, ResultUnit](
  id: SearchDomainId,
  catalog: CatalogSection[Catalog],
  document: DocumentSection[Document, ResultUnit],
  intent: IntentSection,
  runtime: RuntimeSection,
  response: ResponseSection,
  evaluation: EvalSection,
)
