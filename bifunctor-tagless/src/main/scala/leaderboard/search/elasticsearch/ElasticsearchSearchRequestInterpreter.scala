package leaderboard.search.elasticsearch

import io.circe.Json
import leaderboard.model.QueryFailure
import leaderboard.search.{ParsedSearchIntent, UserSearchInput}
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.dsl.*

object ElasticsearchSearchRequestInterpreter {
  def request(
    spec: BeautySearchSpec,
    input: UserSearchInput,
    intent: ParsedSearchIntent,
  ): Either[QueryFailure, Json] = {
    for {
      filterClauses <- sequence(intent.explicitConstraints.map(constraintClause(spec, _)))
      textClause = textQuery(spec, intent.remainingText)
      softBoostClauses <- sequence(intent.softBoosts.map(softBoostClause(spec, spec.carouselSpec.ranking, _)))
      baseQuery = boolQuery(filterClauses, textClause.toList, softBoostClauses)
      query = geoQuery(spec, input, baseQuery)
    } yield Json.obj(
      "track_total_hits" -> Json.fromBoolean(true),
      "size" -> Json.fromInt(math.max(spec.requestSpec.hitWindowSize, input.limit)),
      "query" -> query,
      "aggs" -> aggregations(spec),
    )
  }

  private def textQuery(spec: BeautySearchSpec, remainingText: String): Option[Json] = {
    val query = remainingText.trim
    if (query.isEmpty) {
      None
    } else {
      val fields = spec.variantDocument.fields
        .filter(field => field.searchable)
        .map(field => s"${field.path}^${field.boost}")
      Some(
        Json.obj(
          "multi_match" -> Json.obj(
            "query" -> Json.fromString(query),
            "fields" -> Json.arr(fields.map(Json.fromString): _*),
            "operator" -> Json.fromString(spec.requestSpec.textOperator.value),
          )
        )
      )
    }
  }

  private def aggregations(spec: BeautySearchSpec): Json = {
    val facetAggs = spec.facetSpec.fields.map { facetField =>
      aggName(facetField.path) -> facetAggregation(spec, facetField)
    }
    val groupAggs = List(
      aggName(spec.carouselSpec.providerGroupField.path) -> termsAggregation(spec.carouselSpec.providerGroupField.path, spec.requestSpec.aggregationSize),
      aggName(spec.carouselSpec.serviceIntentGroupField.path) -> termsAggregation(spec.carouselSpec.serviceIntentGroupField.path, spec.requestSpec.aggregationSize),
    )
    Json.obj((facetAggs ++ groupAggs).map { case (name, value) => name -> value }: _*)
  }

  private def facetAggregation(spec: BeautySearchSpec, facetField: FacetField[VariantSearchDocument]): Json =
    facetField.mode match {
      case FacetFieldMode.Terms =>
        termsAggregation(facetField.path, spec.requestSpec.aggregationSize)
      case FacetFieldMode.Ranges(buckets) =>
        Json.obj(
          "range" -> Json.obj(
            "field" -> Json.fromString(facetField.path),
            "ranges" -> Json.arr(
              buckets.map { bucket =>
                Json.obj(
                  "key" -> Json.fromString(bucket.key),
                ).deepMerge(Json.obj())
                  .deepMerge(bucket.min.map(value => Json.obj("from" -> Json.fromBigDecimal(value))).getOrElse(Json.obj()))
                  .deepMerge(bucket.max.map(value => Json.obj("to" -> Json.fromBigDecimal(value))).getOrElse(Json.obj()))
              }: _*
            ),
          )
        )
    }

  private def termsAggregation(path: String, size: Int): Json =
    Json.obj(
      "terms" -> Json.obj(
        "field" -> Json.fromString(path),
        "size" -> Json.fromInt(size),
      )
    )

  private def geoQuery(spec: BeautySearchSpec, input: UserSearchInput, baseQuery: Json): Json =
    (input.userLat, input.userLon) match {
      case (Some(lat), Some(lon)) =>
        Json.obj(
          "function_score" -> Json.obj(
            "query" -> baseQuery,
            "functions" -> Json.arr(
              Json.obj(
                "gauss" -> Json.obj(
                  spec.querySchema.location.path -> Json.obj(
                    "origin" -> Json.obj(
                      "lat" -> Json.fromBigDecimal(lat),
                      "lon" -> Json.fromBigDecimal(lon),
                    ),
                    "scale" -> Json.fromString(spec.requestSpec.geoDistanceScale),
                    "offset" -> Json.fromString(spec.requestSpec.geoDistanceOffset),
                    "decay" -> Json.fromDoubleOrNull(spec.requestSpec.geoDistanceDecay),
                  )
                ),
                "weight" -> Json.fromDoubleOrNull(spec.carouselSpec.ranking.providerDistanceWeight),
              )
            ),
            "score_mode" -> Json.fromString("sum"),
            "boost_mode" -> Json.fromString("sum"),
          )
        )
      case _ =>
        baseQuery
    }

  private def constraintClause(
    spec: BeautySearchSpec,
    constraint: SearchConstraint,
  ): Either[QueryFailure, Json] =
    spec.querySchema.resolve(constraint).flatMap(resolvedConstraintClause)

  private def resolvedConstraintClause(
    constraint: ResolvedSearchConstraint[VariantSearchDocument]
  ): Either[QueryFailure, Json] =
    constraint match {
      case ResolvedSearchConstraint.Terms(field, values, _) =>
        termsClause(field.path, values)
      case ResolvedSearchConstraint.BooleanTerm(field, value, _) =>
        Right(termClause(field.path, Json.fromBoolean(value)))
      case ResolvedSearchConstraint.Range(field, min, max, _) =>
        Right(rangeClause(field.path, min, max))
      case ResolvedSearchConstraint.NearUser(_) =>
        Right(Json.obj())
    }

  private def softBoostClause(
    spec: BeautySearchSpec,
    ranking: RankingSpec,
    constraint: SearchConstraint,
  ): Either[QueryFailure, Json] =
    for {
      resolved <- spec.querySchema.resolve(constraint)
      clause <- resolvedConstraintClause(resolved)
    } yield {
        Json.obj(
          "constant_score" -> Json.obj(
            "filter" -> clause,
            "boost" -> Json.fromDoubleOrNull(boostWeight(ranking, resolved.boostRole)),
          )
        )
    }

  private def boostWeight(ranking: RankingSpec, boostRole: SearchConstraintBoostRole): Double =
    boostRole match {
      case SearchConstraintBoostRole.Service => ranking.serviceBoostWeight
      case SearchConstraintBoostRole.Attribute => ranking.attributeBoostWeight
      case SearchConstraintBoostRole.Distance => ranking.providerDistanceWeight
    }

  private def termClause(path: String, value: Json): Json =
    Json.obj("term" -> Json.obj(path -> value))

  private def termsClause(path: String, values: Set[String]): Either[QueryFailure, Json] =
    values.toList.sorted match {
      case single :: Nil =>
        Right(Json.obj("term" -> Json.obj(path -> Json.fromString(single))))
      case sorted =>
        Right(Json.obj("terms" -> Json.obj(path -> Json.arr(sorted.map(Json.fromString): _*))))
    }

  private def rangeClause(
    path: String,
    min: Option[BigDecimal],
    max: Option[BigDecimal],
  ): Json =
    Json.obj(
      "range" -> Json.obj(
        path -> Json.obj(
          min.map(value => "gte" -> Json.fromBigDecimal(value)).toList ++
            max.map(value => "lte" -> Json.fromBigDecimal(value)).toList: _*
        )
      )
    )

  private def boolQuery(filterClauses: List[Json], mustClauses: List[Json], shouldClauses: List[Json]): Json =
    val fields =
      (filterClauses match {
        case Nil => Nil
        case values => List("filter" -> Json.arr(values: _*))
      }) ++
        (mustClauses match {
          case Nil => Nil
          case values => List("must" -> Json.arr(values: _*))
        }) ++
        (shouldClauses match {
          case Nil => Nil
          case values =>
            List(
              "should" -> Json.arr(values: _*),
              "minimum_should_match" -> Json.fromInt(0),
            )
        })

    Json.obj(
      "bool" -> Json.obj(fields: _*)
    )

  private def aggName(path: String): String =
    s"agg_${path.replaceAll("[^A-Za-z0-9]+", "_")}"

  private def sequence(values: List[Either[QueryFailure, Json]]): Either[QueryFailure, List[Json]] =
    values.foldRight[Either[QueryFailure, List[Json]]](Right(Nil)) {
      (next, acc) =>
        for {
          head <- next
          tail <- acc
        } yield {
          if (head.isObject && head.asObject.exists(_.isEmpty)) tail else head :: tail
        }
    }
}
