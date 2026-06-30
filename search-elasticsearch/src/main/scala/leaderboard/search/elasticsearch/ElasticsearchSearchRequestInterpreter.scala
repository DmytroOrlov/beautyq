package leaderboard.search.elasticsearch

import io.circe.Json
import leaderboard.model.QueryFailure
import leaderboard.search.dsl.*

final case class ElasticsearchSearchInput[A](
  remainingText: String,
  explicitConstraints: List[ResolvedQueryConstraint[A]],
  softBoosts: List[ResolvedQueryConstraint[A]],
  userLat: Option[BigDecimal],
  userLon: Option[BigDecimal],
  limit: Int,
)

object ElasticsearchSearchRequestInterpreter {
  def request[A, C](
    runtimeSpec: SearchRuntimeSpec[A, C],
    input: ElasticsearchSearchInput[A],
  ): Either[QueryFailure, Json] = {
    for {
      filterClauses <- sequence(input.explicitConstraints.map(resolvedConstraintClause))
      textClause = textQuery(runtimeSpec, input.remainingText)
      softBoostClauses <- sequence(input.softBoosts.map(softBoostClause(runtimeSpec.carouselSpec.ranking, _)))
      baseQuery = boolQuery(filterClauses, textClause.toList, softBoostClauses)
      query <- geoQuery(runtimeSpec, input, baseQuery)
    } yield Json.obj(
      "track_total_hits" -> Json.fromBoolean(true),
      "size" -> Json.fromInt(math.max(runtimeSpec.requestSpec.hitWindowSize, input.limit)),
      "query" -> query,
      "aggs" -> aggregations(runtimeSpec),
    )
  }

  private def textQuery[A, C](runtimeSpec: SearchRuntimeSpec[A, C], remainingText: String): Option[Json] = {
    val query = remainingText.trim
    if (query.isEmpty) {
      None
    } else {
      val fields = runtimeSpec.documentSpec.fields
        .filter(field => field.searchable)
        .map(field => s"${field.path}^${field.boost}")
      Some(
        Json.obj(
          "multi_match" -> Json.obj(
            "query" -> Json.fromString(query),
            "fields" -> Json.arr(fields.map(Json.fromString): _*),
            "operator" -> Json.fromString(runtimeSpec.requestSpec.textOperator.value),
          )
        )
      )
    }
  }

  private def aggregations[A, C](runtimeSpec: SearchRuntimeSpec[A, C]): Json = {
    val facetAggs = runtimeSpec.facetSpec.fields.map { facetField =>
      aggName(facetField.path) -> facetAggregation(runtimeSpec, facetField)
    }
    val groupAggs = List(
      aggName(runtimeSpec.carouselSpec.providerGroupField.path) -> termsAggregation(runtimeSpec.carouselSpec.providerGroupField.path, runtimeSpec.requestSpec.aggregationSize),
      aggName(runtimeSpec.carouselSpec.serviceIntentGroupField.path) -> termsAggregation(runtimeSpec.carouselSpec.serviceIntentGroupField.path, runtimeSpec.requestSpec.aggregationSize),
    )
    Json.obj((facetAggs ++ groupAggs).map { case (name, value) => name -> value }: _*)
  }

  private def facetAggregation[A, C](runtimeSpec: SearchRuntimeSpec[A, C], facetField: FacetField[A]): Json =
    facetField.mode match {
      case FacetFieldMode.Terms =>
        termsAggregation(facetField.path, runtimeSpec.requestSpec.aggregationSize)
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

  private def geoQuery[A, C](runtimeSpec: SearchRuntimeSpec[A, C], input: ElasticsearchSearchInput[A], baseQuery: Json): Either[QueryFailure, Json] =
    (input.userLat, input.userLon) match {
      case (Some(lat), Some(lon)) =>
        runtimeSpec.querySchema.geoScoringField match {
          case Some(field) =>
            Right(
              Json.obj(
                "function_score" -> Json.obj(
                  "query" -> baseQuery,
                  "functions" -> Json.arr(
                    Json.obj(
                      "gauss" -> Json.obj(
                        field.path -> Json.obj(
                          "origin" -> Json.obj(
                            "lat" -> Json.fromBigDecimal(lat),
                            "lon" -> Json.fromBigDecimal(lon),
                          ),
                          "scale" -> Json.fromString(runtimeSpec.requestSpec.geoDistanceScale),
                          "offset" -> Json.fromString(runtimeSpec.requestSpec.geoDistanceOffset),
                          "decay" -> Json.fromDoubleOrNull(runtimeSpec.requestSpec.geoDistanceDecay),
                        )
                      ),
                      "weight" -> Json.fromDoubleOrNull(runtimeSpec.carouselSpec.ranking.providerDistanceWeight),
                    )
                  ),
                  "score_mode" -> Json.fromString("sum"),
                  "boost_mode" -> Json.fromString("sum"),
                )
              )
            )
          case None =>
            Left(QueryFailure.domain("Geo scoring field is not defined for search query schema"))
        }
      case _ =>
        Right(baseQuery)
    }

  private def resolvedConstraintClause[A](
    constraint: ResolvedQueryConstraint[A]
  ): Either[QueryFailure, Json] =
    constraint match {
      case ResolvedQueryConstraint.Terms(field, values, _) =>
        termsClause(field.path, values)
      case ResolvedQueryConstraint.BooleanTerm(field, value, _) =>
        Right(termClause(field.path, Json.fromBoolean(value)))
      case ResolvedQueryConstraint.Range(field, min, max, _) =>
        Right(rangeClause(field.path, min, max))
      case ResolvedQueryConstraint.GeoDistance(_) =>
        Right(Json.obj())
    }

  private def softBoostClause(
    ranking: RankingSpec,
    resolved: ResolvedQueryConstraint[?],
  ): Either[QueryFailure, Json] =
    resolvedConstraintClause(resolved).map {
      clause =>
        Json.obj(
          "constant_score" -> Json.obj(
            "filter" -> clause,
            "boost" -> Json.fromDoubleOrNull(boostWeight(ranking, resolved.boostRole)),
          )
        )
    }

  private def boostWeight(ranking: RankingSpec, boostRole: QueryConstraintBoostRole): Double =
    boostRole match {
      case QueryConstraintBoostRole.Service => ranking.serviceBoostWeight
      case QueryConstraintBoostRole.Attribute => ranking.attributeBoostWeight
      case QueryConstraintBoostRole.Distance => ranking.providerDistanceWeight
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
