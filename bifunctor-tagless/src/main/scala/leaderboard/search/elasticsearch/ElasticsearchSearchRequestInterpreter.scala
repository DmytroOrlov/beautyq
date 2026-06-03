package leaderboard.search.elasticsearch

import io.circe.Json
import leaderboard.model.QueryFailure
import leaderboard.search.{ParsedSearchIntent, UserSearchInput}
import leaderboard.search.dsl.*
import leaderboard.search.interpreter.SearchSpecSupport

object ElasticsearchSearchRequestInterpreter {
  private val RequestedHitCount = 256

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
      "size" -> Json.fromInt(math.max(RequestedHitCount, input.limit)),
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
            "operator" -> Json.fromString("and"),
          )
        )
      )
    }
  }

  private def aggregations(spec: BeautySearchSpec): Json = {
    val facetAggs = spec.facetSpec.fields.map { facetField =>
      aggName(facetField.path) -> facetAggregation(facetField)
    }
    val groupAggs = List(
      aggName(spec.carouselSpec.providerGroupField) -> termsAggregation(spec.carouselSpec.providerGroupField, spec.carouselSpec.providerSize * 4),
      aggName(spec.carouselSpec.serviceIntentGroupField) -> termsAggregation(spec.carouselSpec.serviceIntentGroupField, spec.carouselSpec.serviceIntentSize * 4),
    )
    Json.obj((facetAggs ++ groupAggs).map { case (name, value) => name -> value }: _*)
  }

  private def facetAggregation(facetField: FacetField): Json =
    facetField.mode match {
      case FacetFieldMode.Terms =>
        termsAggregation(facetField.path, facetField.limit)
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
    (input.userLat, input.userLon, spec.variantDocument.fieldsBySemantic.get(SearchFieldSemantic.Location)) match {
      case (Some(lat), Some(lon), Some(field)) =>
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
                    "scale" -> Json.fromString("5km"),
                    "offset" -> Json.fromString("0km"),
                    "decay" -> Json.fromDoubleOrNull(0.5d),
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
    constraint match {
      case SearchConstraint.ServiceAny(names) =>
        termsClause(spec.variantDocument.fieldBySemantic(SearchFieldSemantic.ServiceName).map(_.path), names)
      case SearchConstraint.CategoryAny(names) =>
        termsClause(spec.variantDocument.fieldBySemantic(SearchFieldSemantic.CategoryName).map(_.path), names)
      case SearchConstraint.EnumAttr(attributeCode, values) =>
        termsClause(spec.variantDocument.fieldBySemantic(SearchFieldSemantic.EnumAttribute(attributeCode)).map(_.path), values)
      case SearchConstraint.BoolAttr(attributeCode, value) =>
        termClause(spec.variantDocument.fieldBySemantic(SearchFieldSemantic.BooleanAttribute(attributeCode)).map(_.path), Json.fromBoolean(value))
      case SearchConstraint.IntRange(attributeCode, min, max) =>
        rangeClause(spec.variantDocument.fieldBySemantic(SearchFieldSemantic.IntAttribute(attributeCode)).map(_.path), min.map(BigDecimal(_)), max.map(BigDecimal(_)))
      case SearchConstraint.DecimalRange(attributeCode, min, max) =>
        rangeClause(spec.variantDocument.fieldBySemantic(SearchFieldSemantic.DecimalAttribute(attributeCode)).map(_.path), min, max)
      case SearchConstraint.PriceRange(min, max) =>
        rangeClause(spec.variantDocument.fieldBySemantic(SearchFieldSemantic.PriceFrom).map(_.path), min, max)
      case SearchConstraint.DurationRange(min, max) =>
        rangeClause(spec.variantDocument.fieldBySemantic(SearchFieldSemantic.DurationMin).map(_.path), min.map(BigDecimal(_)), max.map(BigDecimal(_)))
      case SearchConstraint.NearUser =>
        Right(Json.obj())
    }

  private def softBoostClause(
    spec: BeautySearchSpec,
    ranking: RankingSpec,
    constraint: SearchConstraint,
  ): Either[QueryFailure, Json] =
    constraintClause(spec, constraint).map {
      clause =>
        Json.obj(
          "constant_score" -> Json.obj(
            "filter" -> clause,
            "boost" -> Json.fromDoubleOrNull(SearchSpecSupport.constraintBoostWeight(ranking, constraint)),
          )
        )
    }

  private def termClause(path: Either[QueryFailure, String], value: Json): Either[QueryFailure, Json] =
    path.map(fieldPath => Json.obj("term" -> Json.obj(fieldPath -> value)))

  private def termsClause(path: Either[QueryFailure, String], values: Set[String]): Either[QueryFailure, Json] =
    path.map { fieldPath =>
      if (values.size == 1) {
        Json.obj("term" -> Json.obj(fieldPath -> Json.fromString(values.head)))
      } else {
        Json.obj("terms" -> Json.obj(fieldPath -> Json.arr(values.toList.sorted.map(Json.fromString): _*)))
      }
    }

  private def rangeClause(
    path: Either[QueryFailure, String],
    min: Option[BigDecimal],
    max: Option[BigDecimal],
  ): Either[QueryFailure, Json] =
    path.map { fieldPath =>
      Json.obj(
        "range" -> Json.obj(
          fieldPath -> Json.obj(
            min.map(value => "gte" -> Json.fromBigDecimal(value)).toList ++
              max.map(value => "lte" -> Json.fromBigDecimal(value)).toList: _*
          )
        )
      )
    }

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
