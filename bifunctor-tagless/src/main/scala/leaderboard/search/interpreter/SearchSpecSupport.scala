package leaderboard.search.interpreter

import leaderboard.model.QueryFailure
import leaderboard.search.UserSearchInput
import leaderboard.search.dsl.*
import leaderboard.search.document.VariantSearchDocument

import java.util.Locale

object SearchSpecSupport {
  final case class ScoredDocument(
    document: VariantSearchDocument,
    textScore: Double,
    boostScore: Double,
    distanceKm: Option[BigDecimal],
  ) {
    def totalScore: Double = textScore + boostScore
  }

  def valuesByPath(
    spec: BeautySearchSpec,
    document: VariantSearchDocument,
  ): Map[String, SearchValue] =
    spec.variantDocument.fields.flatMap(field => field.extract(document).map(field.path -> _)).toMap

  def valueByPath(
    spec: BeautySearchSpec,
    document: VariantSearchDocument,
    path: String,
  ): Either[QueryFailure, Option[SearchValue]] =
    spec.variantDocument.fieldByPath(path).map(field => field.extract(document))

  def valueBySemantic(
    spec: BeautySearchSpec,
    document: VariantSearchDocument,
    semantic: SearchFieldSemantic,
  ): Either[QueryFailure, Option[SearchValue]] =
    spec.variantDocument.fieldBySemantic(semantic).map(field => field.extract(document))

  def groupValue(
    spec: BeautySearchSpec,
    document: VariantSearchDocument,
    path: String,
  ): Either[QueryFailure, String] =
    valueByPath(spec, document, path).flatMap {
      case Some(value) =>
        Right(value.render)
      case None =>
        Left(QueryFailure.domain(s"Document ${document.variantId} does not contain group field '$path'"))
    }

  def matchesConstraint(
    spec: BeautySearchSpec,
    document: VariantSearchDocument,
    constraint: SearchConstraint,
  ): Either[QueryFailure, Boolean] =
    constraint match {
      case SearchConstraint.ServiceAny(names) =>
        matchesTextSemantic(spec, document, SearchFieldSemantic.ServiceName, names)
      case SearchConstraint.CategoryAny(names) =>
        matchesTextSemantic(spec, document, SearchFieldSemantic.CategoryName, names)
      case SearchConstraint.EnumAttr(attributeCode, values) =>
        matchesTextSemantic(spec, document, SearchFieldSemantic.EnumAttribute(attributeCode), values)
      case SearchConstraint.BoolAttr(attributeCode, value) =>
        valueBySemantic(spec, document, SearchFieldSemantic.BooleanAttribute(attributeCode)).map {
          case Some(SearchValue.Boolean(actual)) => actual == value
          case _ => false
        }
      case SearchConstraint.IntRange(attributeCode, min, max) =>
        valueBySemantic(spec, document, SearchFieldSemantic.IntAttribute(attributeCode)).map {
          case Some(SearchValue.Integer(actual)) => rangeMatches(BigDecimal(actual), min.map(BigDecimal(_)), max.map(BigDecimal(_)))
          case _ => false
        }
      case SearchConstraint.DecimalRange(attributeCode, min, max) =>
        valueBySemantic(spec, document, SearchFieldSemantic.DecimalAttribute(attributeCode)).map {
          case Some(SearchValue.Decimal(actual)) => rangeMatches(actual, min, max)
          case _ => false
        }
      case SearchConstraint.PriceRange(min, max) =>
        valueBySemantic(spec, document, SearchFieldSemantic.PriceFrom).map {
          case Some(SearchValue.Decimal(actual)) => rangeMatches(actual, min, max)
          case _ => false
        }
      case SearchConstraint.DurationRange(min, max) =>
        valueBySemantic(spec, document, SearchFieldSemantic.DurationMin).map {
          case Some(SearchValue.Integer(actual)) => rangeMatches(BigDecimal(actual), min.map(BigDecimal(_)), max.map(BigDecimal(_)))
          case _ => false
        }
      case SearchConstraint.NearUser =>
        Right(true)
    }

  def constraintBoostWeight(ranking: RankingSpec, constraint: SearchConstraint): Double =
    constraint match {
      case SearchConstraint.ServiceAny(_) | SearchConstraint.CategoryAny(_) =>
        ranking.serviceBoostWeight
      case SearchConstraint.EnumAttr(_, _) | SearchConstraint.BoolAttr(_, _) | SearchConstraint.IntRange(_, _, _) |
          SearchConstraint.DecimalRange(_, _, _) | SearchConstraint.PriceRange(_, _) | SearchConstraint.DurationRange(_, _) =>
        ranking.attributeBoostWeight
      case SearchConstraint.NearUser =>
        ranking.providerDistanceWeight
    }

  def computeDistanceKm(
    input: UserSearchInput,
    document: VariantSearchDocument,
  ): Option[BigDecimal] =
    for {
      userLat <- input.userLat
      userLon <- input.userLon
    } yield {
      haversineKm(userLat.toDouble, userLon.toDouble, document.lat.toDouble, document.lon.toDouble)
    }

  def textScore(
    spec: BeautySearchSpec,
    document: VariantSearchDocument,
    remainingText: String,
  ): Double = {
    val queryTokens = normalizeText(remainingText)
    if (queryTokens.isEmpty) {
      1.0
    } else {
      spec.variantDocument.fields.iterator
        .filter(field => field.searchable && field.kind == SearchFieldKind.Text)
        .flatMap(field => field.extract(document).map(value => field -> value.render))
        .map {
          case (field, rawValue) =>
            val normalizedValue = normalizeText(rawValue)
            val matches = queryTokens.count(token => normalizedValue.contains(token))
            if (matches == 0) 0.0 else field.boost * matches.toDouble / queryTokens.size.toDouble
        }.sum
    }
  }

  def facetConstraint(
    spec: BeautySearchSpec,
    facetField: FacetField,
    value: String,
  ): Either[QueryFailure, SearchConstraint] = {
    val field = spec.variantDocument.fieldByPath(facetField.path)
    field.flatMap { resolvedField =>
      resolvedField.semantic match {
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
    }
  }

  private def rangeConstraint(
    facetField: FacetField,
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

  private def matchesTextSemantic(
    spec: BeautySearchSpec,
    document: VariantSearchDocument,
    semantic: SearchFieldSemantic,
    values: Set[String],
  ): Either[QueryFailure, Boolean] =
    valueBySemantic(spec, document, semantic).map {
      case Some(value) => values.contains(value.render)
      case None => false
    }

  private def rangeMatches(value: BigDecimal, min: Option[BigDecimal], max: Option[BigDecimal]): Boolean = {
    val minOk = min.forall(bound => value >= bound)
    val maxOk = max.forall(bound => value <= bound)
    minOk && maxOk
  }

  private def haversineKm(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double,
  ): BigDecimal = {
    val earthRadiusKm = 6371.0d
    val dLat = math.toRadians(lat2 - lat1)
    val dLon = math.toRadians(lon2 - lon1)
    val a = math.sin(dLat / 2.0d) * math.sin(dLat / 2.0d) +
      math.cos(math.toRadians(lat1)) * math.cos(math.toRadians(lat2)) *
      math.sin(dLon / 2.0d) * math.sin(dLon / 2.0d)
    val c = 2.0d * math.atan2(math.sqrt(a), math.sqrt(1.0d - a))
    BigDecimal(earthRadiusKm * c).setScale(2, scala.math.BigDecimal.RoundingMode.HALF_UP)
  }

  private def normalizeText(value: String): List[String] =
    value
      .toLowerCase(Locale.ROOT)
      .replace('ё', 'е')
      .replace('-', ' ')
      .replace('_', ' ')
      .replaceAll("[^\\p{L}\\p{N} ]", " ")
      .trim
      .split("\\s+")
      .toList
      .map(_.trim)
      .filter(_.nonEmpty)
}
