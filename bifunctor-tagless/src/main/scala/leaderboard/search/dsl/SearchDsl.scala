package leaderboard.search.dsl

import leaderboard.model.QueryFailure
import leaderboard.search.document.VariantSearchDocument

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

sealed trait SearchFieldSemantic extends Product with Serializable
object SearchFieldSemantic {
  case object VariantId extends SearchFieldSemantic
  case object MasterServiceOfferId extends SearchFieldSemantic
  case object MasterLocationId extends SearchFieldSemantic
  case object MasterId extends SearchFieldSemantic
  case object ServiceId extends SearchFieldSemantic
  case object ServiceName extends SearchFieldSemantic
  case object CategoryId extends SearchFieldSemantic
  case object CategoryName extends SearchFieldSemantic
  case object PriceFrom extends SearchFieldSemantic
  case object PriceTo extends SearchFieldSemantic
  case object DurationMin extends SearchFieldSemantic
  case object Location extends SearchFieldSemantic
  case object AllText extends SearchFieldSemantic
  case object ServiceText extends SearchFieldSemantic
  case object AttributeText extends SearchFieldSemantic
  case object ProviderText extends SearchFieldSemantic
  case object LocationText extends SearchFieldSemantic
  final case class EnumAttribute(attributeCode: String) extends SearchFieldSemantic
  final case class BooleanAttribute(attributeCode: String) extends SearchFieldSemantic
  final case class IntAttribute(attributeCode: String) extends SearchFieldSemantic
  final case class DecimalAttribute(attributeCode: String) extends SearchFieldSemantic
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

sealed trait SearchConstraint extends Product with Serializable
object SearchConstraint {
  final case class ServiceAny(names: Set[String]) extends SearchConstraint
  final case class CategoryAny(names: Set[String]) extends SearchConstraint
  final case class EnumAttr(attributeCode: String, values: Set[String]) extends SearchConstraint
  final case class BoolAttr(attributeCode: String, value: Boolean) extends SearchConstraint
  final case class IntRange(attributeCode: String, min: Option[Int], max: Option[Int]) extends SearchConstraint
  final case class DecimalRange(attributeCode: String, min: Option[BigDecimal], max: Option[BigDecimal]) extends SearchConstraint
  final case class PriceRange(min: Option[BigDecimal], max: Option[BigDecimal]) extends SearchConstraint
  final case class DurationRange(min: Option[Int], max: Option[Int]) extends SearchConstraint
  case object NearUser extends SearchConstraint
}

sealed trait SynonymMatchMode extends Product with Serializable
object SynonymMatchMode {
  case object Phrase extends SynonymMatchMode
  case object Token extends SynonymMatchMode
}

final case class SearchSynonym(
  tokens: Set[String],
  constraints: List[SearchConstraint] = Nil,
  softBoosts: List[SearchConstraint] = Nil,
  boost: Double = 1.0,
  matchMode: SynonymMatchMode = SynonymMatchMode.Phrase,
  requires: List[SearchConstraint] = Nil,
  excludes: List[SearchConstraint] = Nil,
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

final case class FacetField(
  path: String,
  mode: FacetFieldMode,
  limit: Int = 10,
  inferable: Boolean = true,
)

final case class FacetSpec(
  enabled: Boolean,
  fields: List[FacetField],
  inferredFilterDominanceThreshold: BigDecimal = BigDecimal("0.70"),
  inferredFilterMinCount: Int = 2,
)

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

final case class RankingSpec(
  textScoreWeight: Double = 1.0,
  serviceBoostWeight: Double = 2.0,
  attributeBoostWeight: Double = 1.5,
  providerDistanceWeight: Double = 1.0,
  providerMatchingVariantCountWeight: Double = 1.0,
)

final case class CarouselSpec(
  variantSize: Int = 10,
  providerSize: Int = 10,
  serviceIntentSize: Int = 10,
  providerGroupField: String = "masterLocationId",
  serviceIntentGroupField: String = "serviceId",
  ranking: RankingSpec = RankingSpec(),
)

final case class BeautySearchSpec(
  variantDocument: SearchDocumentSpec[VariantSearchDocument],
  synonyms: List[SearchSynonym],
  carouselSpec: CarouselSpec,
  facetSpec: FacetSpec,
  requestSpec: SearchRequestSpec = SearchRequestSpec(),
)
