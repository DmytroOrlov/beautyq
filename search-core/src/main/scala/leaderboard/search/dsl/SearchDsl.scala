package leaderboard.search.dsl

import leaderboard.model.QueryFailure

sealed trait VectorDistance extends Product with Serializable
object VectorDistance {
  case object Cosine extends VectorDistance
  case object Dot extends VectorDistance
  case object Euclidean extends VectorDistance
}

final case class EmbeddingSpec[A](
  vectorName: String,
  modelName: String,
  dimension: Int,
  distance: VectorDistance,
  sourceTextFields: List[SearchField[A]],
) {
  def sourceTextFieldPaths: List[String] = sourceTextFields.map(_.path)
}

final case class VectorSearchSpec(
  collectionName: String,
  vectorName: String,
  topK: Int,
  scoreThreshold: Option[Double],
)

sealed trait SearchFieldKind extends Product with Serializable
object SearchFieldKind {
  case object Text extends SearchFieldKind
  case object Keyword extends SearchFieldKind
  case object Integer extends SearchFieldKind
  case object Decimal extends SearchFieldKind
  case object Boolean extends SearchFieldKind
  case object GeoPoint extends SearchFieldKind
}

final case class SearchGeoPoint(
  lat: BigDecimal,
  lon: BigDecimal,
)

sealed trait SearchValue extends Product with Serializable {
  def render: String
}
object SearchValue {
  final case class Text(value: String) extends SearchValue {
    override def render: String = value
  }

  final case class Keyword(value: String) extends SearchValue {
    override def render: String = value
  }

  final case class Integer(value: Int) extends SearchValue {
    override def render: String = value.toString
  }

  final case class Decimal(value: BigDecimal) extends SearchValue {
    override def render: String = value.toString()
  }

  final case class Boolean(value: scala.Boolean) extends SearchValue {
    override def render: String = value.toString
  }

  final case class GeoPoint(value: SearchGeoPoint) extends SearchValue {
    override def render: String = s"${value.lat},${value.lon}"
  }
}

final case class SearchFieldSemantic(value: String) extends AnyVal {
  override def toString: String = value
}

final case class SearchField[A](
  path: String,
  kind: SearchFieldKind,
  extract: A => Option[SearchValue],
  semantic: Option[SearchFieldSemantic] = None,
  searchable: Boolean = false,
  filterable: Boolean = false,
  facetable: Boolean = false,
  sortable: Boolean = false,
  boost: Double = 1.0,
  analyzer: Option[String] = None,
)

/** Semi-auto, Caliban-style helpers for declaring [[SearchField]] handles.
  *
  * The schema owner still explicitly chooses every field and its kind/semantics/flags; these helpers only
  * derive the external `path` from a Scala field selector (`_.fieldName`) so the path is never written as a
  * raw string for static direct document fields. Field kind is visible at the call site through the helper
  * name, so a `String` field is never ambiguously both text and keyword. Computed/dynamic fields with paths
  * that do not equal a direct document field label must keep using the explicit [[SearchField]] apply via
  * [[SearchField.computed]].
  */
object SearchField {

  /** Explicit, named alias for the raw constructor, for computed/dynamic fields whose path is not a direct
    * field label.
    */
  def computed[A](
    path: String,
    kind: SearchFieldKind,
    extract: A => Option[SearchValue],
    semantic: Option[SearchFieldSemantic] = None,
    searchable: Boolean = false,
    filterable: Boolean = false,
    facetable: Boolean = false,
    sortable: Boolean = false,
    boost: Double = 1.0,
    analyzer: Option[String] = None,
  ): SearchField[A] =
    SearchField(path, kind, extract, semantic, searchable, filterable, facetable, sortable, boost, analyzer)

  inline def keyword[A](
    inline selector: A => String,
    semantic: Option[SearchFieldSemantic] = None,
    searchable: Boolean = false,
    filterable: Boolean = false,
    facetable: Boolean = false,
    sortable: Boolean = false,
    boost: Double = 1.0,
    analyzer: Option[String] = None,
  ): SearchField[A] =
    SearchField(
      path = SearchFieldMacro.label[A, String](selector),
      kind = SearchFieldKind.Keyword,
      extract = a => Some(SearchValue.Keyword(selector(a))),
      semantic = semantic,
      searchable = searchable,
      filterable = filterable,
      facetable = facetable,
      sortable = sortable,
      boost = boost,
      analyzer = analyzer,
    )

  inline def keywordRendered[A, B](
    inline selector: A => B,
    render: B => String,
    semantic: Option[SearchFieldSemantic] = None,
    searchable: Boolean = false,
    filterable: Boolean = false,
    facetable: Boolean = false,
    sortable: Boolean = false,
    boost: Double = 1.0,
    analyzer: Option[String] = None,
  ): SearchField[A] =
    SearchField(
      path = SearchFieldMacro.label[A, B](selector),
      kind = SearchFieldKind.Keyword,
      extract = a => Some(SearchValue.Keyword(render(selector(a)))),
      semantic = semantic,
      searchable = searchable,
      filterable = filterable,
      facetable = facetable,
      sortable = sortable,
      boost = boost,
      analyzer = analyzer,
    )

  inline def text[A](
    inline selector: A => String,
    semantic: Option[SearchFieldSemantic] = None,
    searchable: Boolean = false,
    filterable: Boolean = false,
    facetable: Boolean = false,
    sortable: Boolean = false,
    boost: Double = 1.0,
    analyzer: Option[String] = None,
  ): SearchField[A] =
    SearchField(
      path = SearchFieldMacro.label[A, String](selector),
      kind = SearchFieldKind.Text,
      extract = a => Some(SearchValue.Text(selector(a))),
      semantic = semantic,
      searchable = searchable,
      filterable = filterable,
      facetable = facetable,
      sortable = sortable,
      boost = boost,
      analyzer = analyzer,
    )

  inline def integer[A](
    inline selector: A => Int,
    semantic: Option[SearchFieldSemantic] = None,
    searchable: Boolean = false,
    filterable: Boolean = false,
    facetable: Boolean = false,
    sortable: Boolean = false,
    boost: Double = 1.0,
    analyzer: Option[String] = None,
  ): SearchField[A] =
    SearchField(
      path = SearchFieldMacro.label[A, Int](selector),
      kind = SearchFieldKind.Integer,
      extract = a => Some(SearchValue.Integer(selector(a))),
      semantic = semantic,
      searchable = searchable,
      filterable = filterable,
      facetable = facetable,
      sortable = sortable,
      boost = boost,
      analyzer = analyzer,
    )

  inline def decimal[A](
    inline selector: A => BigDecimal,
    semantic: Option[SearchFieldSemantic] = None,
    searchable: Boolean = false,
    filterable: Boolean = false,
    facetable: Boolean = false,
    sortable: Boolean = false,
    boost: Double = 1.0,
    analyzer: Option[String] = None,
  ): SearchField[A] =
    SearchField(
      path = SearchFieldMacro.label[A, BigDecimal](selector),
      kind = SearchFieldKind.Decimal,
      extract = a => Some(SearchValue.Decimal(selector(a))),
      semantic = semantic,
      searchable = searchable,
      filterable = filterable,
      facetable = facetable,
      sortable = sortable,
      boost = boost,
      analyzer = analyzer,
    )

  inline def boolean[A](
    inline selector: A => Boolean,
    semantic: Option[SearchFieldSemantic] = None,
    searchable: Boolean = false,
    filterable: Boolean = false,
    facetable: Boolean = false,
    sortable: Boolean = false,
    boost: Double = 1.0,
    analyzer: Option[String] = None,
  ): SearchField[A] =
    SearchField(
      path = SearchFieldMacro.label[A, Boolean](selector),
      kind = SearchFieldKind.Boolean,
      extract = a => Some(SearchValue.Boolean(selector(a))),
      semantic = semantic,
      searchable = searchable,
      filterable = filterable,
      facetable = facetable,
      sortable = sortable,
      boost = boost,
      analyzer = analyzer,
    )

  inline def geoPoint[A](
    inline selector: A => SearchGeoPoint,
    semantic: Option[SearchFieldSemantic] = None,
    searchable: Boolean = false,
    filterable: Boolean = false,
    facetable: Boolean = false,
    sortable: Boolean = false,
    boost: Double = 1.0,
    analyzer: Option[String] = None,
  ): SearchField[A] =
    SearchField(
      path = SearchFieldMacro.label[A, SearchGeoPoint](selector),
      kind = SearchFieldKind.GeoPoint,
      extract = a => Some(SearchValue.GeoPoint(selector(a))),
      semantic = semantic,
      searchable = searchable,
      filterable = filterable,
      facetable = facetable,
      sortable = sortable,
      boost = boost,
      analyzer = analyzer,
    )
}

final case class SearchDocumentSpec[A](
  indexName: String,
  id: A => String,
  fields: List[SearchField[A]],
) {
  lazy val fieldsByPath: Map[String, SearchField[A]] =
    fields.iterator.map(field => field.path -> field).toMap

  lazy val fieldsBySemantic: Map[SearchFieldSemantic, SearchField[A]] =
    fields.iterator.flatMap(field => field.semantic.map(_ -> field)).toMap

  def fieldByPath(path: String): Either[QueryFailure, SearchField[A]] =
    fieldsByPath.get(path).toRight(QueryFailure.domain(s"Search field '$path' is not defined for index '$indexName'"))

  def fieldBySemantic(semantic: SearchFieldSemantic): Either[QueryFailure, SearchField[A]] =
    fieldsBySemantic.get(semantic).toRight(QueryFailure.domain(s"Search semantic '$semantic' is not defined for index '$indexName'"))
}

sealed trait IntentMatchMode extends Product with Serializable
object IntentMatchMode {
  case object Phrase extends IntentMatchMode
  case object Token extends IntentMatchMode
}

/** A single search intent-vocabulary rule. These are structured intent aliases that map query phrases to
  * hard service/category/attribute constraints, soft boosts, requires, and excludes; they are not lexical
  * backend analyzer synonyms. Lexical recall/tokenization/synonym filters remain a backend analyzer
  * concern; structured service/category/attribute intent mapping is schema/data ownership.
  */
sealed trait SearchIntentRule[C] extends Product with Serializable {
  def tokens: Set[String]
  def matchMode: IntentMatchMode
  def requires: List[C]
  def excludes: List[C]
  def constraints: List[C]
  def softBoosts: List[C]
  def boost: Double
}

object SearchIntentRule {

  /** A phrase that maps to structured intent: hard constraints and/or soft boosts, optionally gated by
    * `requires`/`excludes`.
    */
  final case class StructuredAlias[C](
    tokens: Set[String],
    constraints: List[C],
    softBoosts: List[C] = Nil,
    boost: Double = 1.0,
    matchMode: IntentMatchMode = IntentMatchMode.Phrase,
    requires: List[C] = Nil,
    excludes: List[C] = Nil,
  ) extends SearchIntentRule[C]

  /** A residual-noise phrase that carries no constraints or boosts. It exists to consume query text (and
    * optionally to be gated by contextual `requires`/`excludes`) without contributing intent.
    */
  final case class QueryNoisePhrase[C](
    tokens: Set[String],
    matchMode: IntentMatchMode = IntentMatchMode.Phrase,
    requires: List[C] = Nil,
    excludes: List[C] = Nil,
  ) extends SearchIntentRule[C] {
    override val constraints: List[C] = Nil
    override val softBoosts: List[C] = Nil
    override val boost: Double = 1.0
  }
}

final case class SearchIntentVocabulary[C](
  rules: List[SearchIntentRule[C]],
)

sealed trait FacetFieldMode extends Product with Serializable
object FacetFieldMode {
  case object Terms extends FacetFieldMode
  final case class Ranges(buckets: List[FacetRangeBucket]) extends FacetFieldMode
}

final case class FacetRangeBucket(
  key: String,
  min: Option[BigDecimal] = None,
  max: Option[BigDecimal] = None,
)

final case class FacetField[A](
  field: SearchField[A],
  mode: FacetFieldMode,
  limit: Int = 10,
  inferable: Boolean = true,
) {
  def path: String = field.path
}

final case class FacetSpec[A](
  enabled: Boolean,
  fields: List[FacetField[A]],
  inferredFilterDominanceThreshold: BigDecimal = BigDecimal("0.70"),
  inferredFilterMinCount: Int = 2,
)

final case class SearchBoostRole(value: String) extends AnyVal {
  override def toString: String = value
}

sealed trait ResolvedSearchConstraint[A] extends Product with Serializable {
  def boostRole: SearchBoostRole
}
object ResolvedSearchConstraint {
  final case class Terms[A](field: SearchField[A], values: Set[String], boostRole: SearchBoostRole) extends ResolvedSearchConstraint[A]
  final case class BooleanTerm[A](field: SearchField[A], value: Boolean, boostRole: SearchBoostRole) extends ResolvedSearchConstraint[A]
  final case class Range[A](field: SearchField[A], min: Option[BigDecimal], max: Option[BigDecimal], boostRole: SearchBoostRole) extends ResolvedSearchConstraint[A]
  final case class GeoDistance[A](field: SearchField[A], boostRole: SearchBoostRole) extends ResolvedSearchConstraint[A]
}

type ResolvedQueryConstraint[A] = ResolvedSearchConstraint[A]
val ResolvedQueryConstraint: ResolvedSearchConstraint.type = ResolvedSearchConstraint

final case class SearchQueryField[A](
  name: String,
  field: SearchField[A],
)

final case class SearchQuerySchema[A, C](
  fields: List[SearchQueryField[A]],
  geoScoringField: Option[SearchField[A]],
  resolve: C => Either[QueryFailure, ResolvedSearchConstraint[A]],
  facetConstraint: (FacetField[A], String) => Either[QueryFailure, C],
) {
  lazy val fieldsByName: Map[String, SearchField[A]] =
    fields.iterator.map(entry => entry.name -> entry.field).toMap

  def field(name: String): Either[QueryFailure, SearchField[A]] =
    fieldsByName.get(name).toRight(QueryFailure.domain(s"Search query field '$name' is not defined"))
}

sealed trait TextOperator extends Product with Serializable {
  def value: String
}
object TextOperator {
  case object And extends TextOperator {
    override val value: String = "and"
  }

  case object Or extends TextOperator {
    override val value: String = "or"
  }
}

final case class SearchRequestSpec(
  hitWindowSize: Int = 256,
  textOperator: TextOperator = TextOperator.And,
  aggregationSize: Int = 20,
  geoDistanceScale: String = "5km",
  geoDistanceOffset: String = "0km",
  geoDistanceDecay: Double = 0.5d,
)

final case class RankingWeight(
  name: String,
  value: Double,
  boostRoles: Set[SearchBoostRole] = Set.empty,
)

final case class RankingSpec(
  weights: List[RankingWeight],
) {
  lazy val weightsByName: Map[String, RankingWeight] =
    weights.iterator.map(weight => weight.name -> weight).toMap

  lazy val boostRoleWeights: Map[SearchBoostRole, RankingWeight] =
    weights.flatMap(weight => weight.boostRoles.map(role => role -> weight)).toMap

  def weight(name: String): Either[QueryFailure, Double] =
    weightsByName.get(name).map(_.value).toRight(QueryFailure.domain(s"Ranking weight '$name' is not defined"))

  def boostWeight(role: SearchBoostRole): Either[QueryFailure, Double] =
    boostRoleWeights.get(role).map(_.value).toRight(QueryFailure.domain(s"Ranking boost role '${role.value}' is not defined"))
}

final case class CarouselLimit(
  name: String,
  size: Int,
)

final case class CarouselGroup[A](
  name: String,
  field: SearchField[A],
)

final case class CarouselSpec[A](
  limits: List[CarouselLimit],
  groups: List[CarouselGroup[A]],
  ranking: RankingSpec,
  geoScoringBoostRole: Option[SearchBoostRole] = None,
) {
  lazy val limitsByName: Map[String, CarouselLimit] =
    limits.iterator.map(limit => limit.name -> limit).toMap

  lazy val groupsByName: Map[String, CarouselGroup[A]] =
    groups.iterator.map(group => group.name -> group).toMap

  def limit(name: String): Either[QueryFailure, Int] =
    limitsByName.get(name).map(_.size).toRight(QueryFailure.domain(s"Carousel limit '$name' is not defined"))

  def group(name: String): Either[QueryFailure, SearchField[A]] =
    groupsByName.get(name).map(_.field).toRight(QueryFailure.domain(s"Carousel group '$name' is not defined"))
}
