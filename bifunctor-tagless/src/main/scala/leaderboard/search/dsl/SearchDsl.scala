package leaderboard.search.dsl

import io.circe.{Codec, Decoder, DecodingFailure, Encoder, HCursor, Json}
import io.circe.syntax.*
import leaderboard.model.QueryFailure
import leaderboard.search.document.VariantSearchDocument

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
  sourceTextFieldPaths: List[String],
)

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

  implicit val codec: Codec[SearchConstraint] = Codec.from(
    Decoder.instance {
      cursor =>
        cursor.get[String]("type").flatMap {
          case "service_any" =>
            cursor.get[Set[String]]("names").map(ServiceAny.apply)
          case "category_any" =>
            cursor.get[Set[String]]("names").map(CategoryAny.apply)
          case "enum_attr" =>
            for {
              attributeCode <- cursor.get[String]("attributeCode")
              values        <- cursor.get[Set[String]]("values")
            } yield EnumAttr(attributeCode, values)
          case "bool_attr" =>
            for {
              attributeCode <- cursor.get[String]("attributeCode")
              value         <- cursor.get[Boolean]("value")
            } yield BoolAttr(attributeCode, value)
          case "int_range" =>
            decodeRange[Int](cursor).map {
              case (attributeCode, min, max) => IntRange(attributeCode, min, max)
            }
          case "decimal_range" =>
            decodeRange[BigDecimal](cursor).map {
              case (attributeCode, min, max) => DecimalRange(attributeCode, min, max)
            }
          case "price_range" =>
            for {
              min <- cursor.get[Option[BigDecimal]]("min")
              max <- cursor.get[Option[BigDecimal]]("max")
            } yield PriceRange(min, max)
          case "duration_range" =>
            for {
              min <- cursor.get[Option[Int]]("min")
              max <- cursor.get[Option[Int]]("max")
            } yield DurationRange(min, max)
          case "near_user" =>
            Right(NearUser)
          case other =>
            Left(DecodingFailure(s"Unknown SearchConstraint type '$other'", cursor.history))
        }
    },
    Encoder.instance {
      case ServiceAny(names) =>
        Json.obj("type" -> "service_any".asJson, "names" -> names.asJson)
      case CategoryAny(names) =>
        Json.obj("type" -> "category_any".asJson, "names" -> names.asJson)
      case EnumAttr(attributeCode, values) =>
        Json.obj("type" -> "enum_attr".asJson, "attributeCode" -> attributeCode.asJson, "values" -> values.asJson)
      case BoolAttr(attributeCode, value) =>
        Json.obj("type" -> "bool_attr".asJson, "attributeCode" -> attributeCode.asJson, "value" -> value.asJson)
      case IntRange(attributeCode, min, max) =>
        Json.obj("type" -> "int_range".asJson, "attributeCode" -> attributeCode.asJson, "min" -> min.asJson, "max" -> max.asJson)
      case DecimalRange(attributeCode, min, max) =>
        Json.obj("type" -> "decimal_range".asJson, "attributeCode" -> attributeCode.asJson, "min" -> min.asJson, "max" -> max.asJson)
      case PriceRange(min, max) =>
        Json.obj("type" -> "price_range".asJson, "min" -> min.asJson, "max" -> max.asJson)
      case DurationRange(min, max) =>
        Json.obj("type" -> "duration_range".asJson, "min" -> min.asJson, "max" -> max.asJson)
      case NearUser =>
        Json.obj("type" -> "near_user".asJson)
    },
  )

  private def decodeRange[A: Decoder](
    cursor: HCursor
  ): Decoder.Result[(String, Option[A], Option[A])] =
    for {
      attributeCode <- cursor.get[String]("attributeCode")
      min           <- cursor.get[Option[A]]("min")
      max           <- cursor.get[Option[A]]("max")
    } yield (attributeCode, min, max)
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
  embeddingSpec: Option[EmbeddingSpec[VariantSearchDocument]] = None,
  vectorSearchSpec: Option[VectorSearchSpec] = None,
)
