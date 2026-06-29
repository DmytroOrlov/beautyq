package leaderboard.search.dsl

import io.circe.{Codec, Decoder, DecodingFailure, Encoder, HCursor, Json}
import io.circe.syntax.*
import leaderboard.model.QueryFailure
import leaderboard.search.document.{SearchDocumentPayloadSpec, VariantSearchDocument}

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

sealed trait IntentMatchMode extends Product with Serializable
object IntentMatchMode {
  case object Phrase extends IntentMatchMode
  case object Token extends IntentMatchMode
}

/** A single BeautyQ intent-vocabulary rule. These are structured intent aliases that map query phrases to
  * hard service/category/attribute constraints, soft boosts, requires, and excludes; they are not lexical
  * Elasticsearch analyzer synonyms. Lexical recall/tokenization/synonym filters remain an ES analyzer
  * concern; structured service/category/attribute intent mapping is schema/data ownership.
  */
sealed trait SearchIntentRule extends Product with Serializable {
  def tokens: Set[String]
  def matchMode: IntentMatchMode
  def requires: List[SearchConstraint]
  def excludes: List[SearchConstraint]
  def constraints: List[SearchConstraint]
  def softBoosts: List[SearchConstraint]
  def boost: Double
}

object SearchIntentRule {

  /** A phrase that maps to structured intent: hard constraints and/or soft boosts, optionally gated by
    * `requires`/`excludes`.
    */
  final case class StructuredAlias(
    tokens: Set[String],
    constraints: List[SearchConstraint],
    softBoosts: List[SearchConstraint] = Nil,
    boost: Double = 1.0,
    matchMode: IntentMatchMode = IntentMatchMode.Phrase,
    requires: List[SearchConstraint] = Nil,
    excludes: List[SearchConstraint] = Nil,
  ) extends SearchIntentRule

  /** A residual-noise phrase that carries no constraints or boosts. It exists to consume query text (and
    * optionally to be gated by contextual `requires`/`excludes`) without contributing intent.
    */
  final case class QueryNoisePhrase(
    tokens: Set[String],
    matchMode: IntentMatchMode = IntentMatchMode.Phrase,
    requires: List[SearchConstraint] = Nil,
    excludes: List[SearchConstraint] = Nil,
  ) extends SearchIntentRule {
    override val constraints: List[SearchConstraint] = Nil
    override val softBoosts: List[SearchConstraint] = Nil
    override val boost: Double = 1.0
  }
}

final case class SearchIntentVocabulary(
  rules: List[SearchIntentRule],
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

sealed trait SearchConstraintBoostRole extends Product with Serializable
object SearchConstraintBoostRole {
  case object Service extends SearchConstraintBoostRole
  case object Attribute extends SearchConstraintBoostRole
  case object Distance extends SearchConstraintBoostRole
}

sealed trait ResolvedSearchConstraint[A] extends Product with Serializable {
  def boostRole: SearchConstraintBoostRole
}
object ResolvedSearchConstraint {
  final case class Terms[A](field: SearchField[A], values: Set[String], boostRole: SearchConstraintBoostRole) extends ResolvedSearchConstraint[A]
  final case class BooleanTerm[A](field: SearchField[A], value: Boolean, boostRole: SearchConstraintBoostRole) extends ResolvedSearchConstraint[A]
  final case class Range[A](field: SearchField[A], min: Option[BigDecimal], max: Option[BigDecimal], boostRole: SearchConstraintBoostRole) extends ResolvedSearchConstraint[A]
  final case class NearUser[A](field: SearchField[A]) extends ResolvedSearchConstraint[A] {
    override val boostRole: SearchConstraintBoostRole = SearchConstraintBoostRole.Distance
  }
}

final case class SearchQuerySchema[A](
  serviceName: SearchField[A],
  categoryName: SearchField[A],
  priceFrom: SearchField[A],
  durationMin: SearchField[A],
  location: SearchField[A],
  enumAttribute: String => Either[QueryFailure, SearchField[A]],
  booleanAttribute: String => Either[QueryFailure, SearchField[A]],
  intAttribute: String => Either[QueryFailure, SearchField[A]],
  decimalAttribute: String => Either[QueryFailure, SearchField[A]],
) {
  def resolve(constraint: SearchConstraint): Either[QueryFailure, ResolvedSearchConstraint[A]] =
    constraint match {
      case SearchConstraint.ServiceAny(names) =>
        Right(ResolvedSearchConstraint.Terms(serviceName, names, SearchConstraintBoostRole.Service))
      case SearchConstraint.CategoryAny(names) =>
        Right(ResolvedSearchConstraint.Terms(categoryName, names, SearchConstraintBoostRole.Service))
      case SearchConstraint.EnumAttr(attributeCode, values) =>
        enumAttribute(attributeCode).map(ResolvedSearchConstraint.Terms(_, values, SearchConstraintBoostRole.Attribute))
      case SearchConstraint.BoolAttr(attributeCode, value) =>
        booleanAttribute(attributeCode).map(ResolvedSearchConstraint.BooleanTerm(_, value, SearchConstraintBoostRole.Attribute))
      case SearchConstraint.IntRange(attributeCode, min, max) =>
        intAttribute(attributeCode).map(ResolvedSearchConstraint.Range(_, min.map(BigDecimal(_)), max.map(BigDecimal(_)), SearchConstraintBoostRole.Attribute))
      case SearchConstraint.DecimalRange(attributeCode, min, max) =>
        decimalAttribute(attributeCode).map(ResolvedSearchConstraint.Range(_, min, max, SearchConstraintBoostRole.Attribute))
      case SearchConstraint.PriceRange(min, max) =>
        Right(ResolvedSearchConstraint.Range(priceFrom, min, max, SearchConstraintBoostRole.Attribute))
      case SearchConstraint.DurationRange(min, max) =>
        Right(ResolvedSearchConstraint.Range(durationMin, min.map(BigDecimal(_)), max.map(BigDecimal(_)), SearchConstraintBoostRole.Attribute))
      case SearchConstraint.NearUser =>
        Right(ResolvedSearchConstraint.NearUser(location))
    }

  def facetConstraint(facetField: FacetField[A], value: String): Either[QueryFailure, SearchConstraint] =
    facetField.field.semantic match {
      case Some(SearchFieldSemantic.ServiceName) =>
        Right(SearchConstraint.ServiceAny(Set(value)))
      case Some(SearchFieldSemantic.CategoryName) =>
        Right(SearchConstraint.CategoryAny(Set(value)))
      case Some(SearchFieldSemantic.EnumAttribute(attributeCode)) =>
        Right(SearchConstraint.EnumAttr(attributeCode, Set(value)))
      case Some(SearchFieldSemantic.BooleanAttribute(attributeCode)) =>
        value.toBooleanOption match {
          case Some(boolValue) => Right(SearchConstraint.BoolAttr(attributeCode, boolValue))
          case None => Left(QueryFailure.domain(s"Facet value '$value' is not a boolean for ${facetField.path}"))
        }
      case Some(SearchFieldSemantic.PriceFrom) =>
        rangeConstraint(facetField, value, SearchConstraint.PriceRange.apply)
      case Some(SearchFieldSemantic.DurationMin) =>
        rangeConstraint(facetField, value, (min, max) => SearchConstraint.DurationRange(min.map(_.toInt), max.map(_.toInt)))
      case Some(SearchFieldSemantic.IntAttribute(attributeCode)) =>
        rangeConstraint(facetField, value, (min, max) => SearchConstraint.IntRange(attributeCode, min.map(_.toInt), max.map(_.toInt)))
      case Some(SearchFieldSemantic.DecimalAttribute(attributeCode)) =>
        rangeConstraint(facetField, value, (min, max) => SearchConstraint.DecimalRange(attributeCode, min, max))
      case other =>
        Left(QueryFailure.domain(s"Facet field '${facetField.path}' with semantic $other cannot be converted into a search constraint"))
    }

  private def rangeConstraint(
    facetField: FacetField[A],
    value: String,
    build: (Option[BigDecimal], Option[BigDecimal]) => SearchConstraint,
  ): Either[QueryFailure, SearchConstraint] =
    facetField.mode match {
      case FacetFieldMode.Ranges(buckets) =>
        buckets.find(_.key == value) match {
          case Some(bucket) => Right(build(bucket.min, bucket.max))
          case None => Left(QueryFailure.domain(s"Range bucket '$value' is not defined for facet '${facetField.path}'"))
        }
      case _ =>
        Left(QueryFailure.domain(s"Facet '${facetField.path}' is not range-based"))
    }
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

final case class RankingSpec(
  textScoreWeight: Double = 1.0,
  serviceBoostWeight: Double = 2.0,
  attributeBoostWeight: Double = 1.5,
  providerDistanceWeight: Double = 1.0,
  providerMatchingVariantCountWeight: Double = 1.0,
)

final case class CarouselSpec[A](
  variantSize: Int = 10,
  providerSize: Int = 10,
  serviceIntentSize: Int = 10,
  providerGroupField: SearchField[A],
  serviceIntentGroupField: SearchField[A],
  ranking: RankingSpec = RankingSpec(),
)

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
