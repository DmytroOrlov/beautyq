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
    valuesByPath(spec.runtimeSpec(Map.empty), document)

  def valuesByPath[A](
    runtimeSpec: SearchRuntimeSpec[A, ?],
    document: A,
  ): Map[String, SearchValue] =
    runtimeSpec.documentSpec.fields.flatMap(field => field.extract(document).map(field.path -> _)).toMap

  def valueByPath(
    spec: BeautySearchSpec,
    document: VariantSearchDocument,
    path: String,
  ): Either[QueryFailure, Option[SearchValue]] =
    valueByPath(spec.runtimeSpec(Map.empty), document, path)

  def valueByPath[A](
    runtimeSpec: SearchRuntimeSpec[A, ?],
    document: A,
    path: String,
  ): Either[QueryFailure, Option[SearchValue]] =
    runtimeSpec.documentSpec.fieldByPath(path).map(field => field.extract(document))

  def valueByField(
    spec: BeautySearchSpec,
    document: VariantSearchDocument,
    field: SearchField[VariantSearchDocument],
  ): Either[QueryFailure, Option[SearchValue]] =
    valueByField(spec.runtimeSpec(Map.empty), document, field)

  def valueByField[A](
    runtimeSpec: SearchRuntimeSpec[A, ?],
    document: A,
    field: SearchField[A],
  ): Either[QueryFailure, Option[SearchValue]] =
    runtimeSpec.documentSpec.fieldByPath(field.path).map(_ => field.extract(document))

  def valueBySemantic(
    spec: BeautySearchSpec,
    document: VariantSearchDocument,
    semantic: SearchFieldSemantic,
  ): Either[QueryFailure, Option[SearchValue]] =
    spec.variantDocument.fieldBySemantic(semantic).map(field => field.extract(document))

  def groupValue(
    spec: BeautySearchSpec,
    document: VariantSearchDocument,
    field: SearchField[VariantSearchDocument],
  ): Either[QueryFailure, String] =
    valueByField(spec, document, field).flatMap {
      case Some(value) =>
        Right(value.render)
      case None =>
        Left(QueryFailure.domain(s"Document ${document.variantId} does not contain group field '${field.path}'"))
    }

  def matchesConstraint(
    spec: BeautySearchSpec,
    document: VariantSearchDocument,
    constraint: SearchConstraint,
  ): Either[QueryFailure, Boolean] =
    spec.querySchema.resolve(constraint).flatMap(resolvedConstraintMatches(spec.runtimeSpec(Map.empty), document, _))

  def matchesConstraint[A](
    runtimeSpec: SearchRuntimeSpec[A, SearchConstraint],
    document: A,
    constraint: SearchConstraint,
  ): Either[QueryFailure, Boolean] =
    runtimeSpec.querySchema.resolve(constraint).flatMap(resolvedConstraintMatches(runtimeSpec, document, _))

  def resolvedConstraintMatches(
    spec: BeautySearchSpec,
    document: VariantSearchDocument,
    constraint: ResolvedSearchConstraint[VariantSearchDocument],
  ): Either[QueryFailure, Boolean] =
    resolvedConstraintMatches(spec.runtimeSpec(Map.empty), document, constraint)

  def resolvedConstraintMatches[A](
    runtimeSpec: SearchRuntimeSpec[A, ?],
    document: A,
    constraint: ResolvedSearchConstraint[A],
  ): Either[QueryFailure, Boolean] =
    constraint match {
      case ResolvedSearchConstraint.Terms(field, values, _) =>
        valueByField(runtimeSpec, document, field).map {
          case Some(value) => values.contains(value.render)
          case None => false
        }
      case ResolvedSearchConstraint.BooleanTerm(field, expected, _) =>
        valueByField(runtimeSpec, document, field).map {
          case Some(SearchValue.Boolean(actual)) => actual == expected
          case _ => false
        }
      case ResolvedSearchConstraint.Range(field, min, max, _) =>
        valueByField(runtimeSpec, document, field).map {
          case Some(SearchValue.Integer(actual)) => rangeMatches(BigDecimal(actual), min, max)
          case Some(SearchValue.Decimal(actual)) => rangeMatches(actual, min, max)
          case _ => false
        }
      case ResolvedSearchConstraint.GeoDistance(_, _) =>
        Right(true)
    }

  def constraintBoostWeight(spec: BeautySearchSpec, constraint: SearchConstraint): Either[QueryFailure, Double] =
    spec.querySchema.resolve(constraint).flatMap(resolved => spec.carouselSpec.ranking.boostWeight(resolved.boostRole))

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
    facetField: FacetField[VariantSearchDocument],
    value: String,
  ): Either[QueryFailure, SearchConstraint] =
    spec.querySchema.facetConstraint(facetField, value)

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
