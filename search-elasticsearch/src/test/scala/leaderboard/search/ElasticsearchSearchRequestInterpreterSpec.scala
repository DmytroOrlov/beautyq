package leaderboard.search

import io.circe.Json
import leaderboard.search.dsl.SearchConstraint
import leaderboard.search.elasticsearch.{ElasticsearchSearchInput, ElasticsearchSearchRequestInterpreter}
import org.scalatest.wordspec.AnyWordSpec

final class ElasticsearchSearchRequestInterpreterSpec extends AnyWordSpec {
  "ElasticsearchSearchRequestInterpreter.request" should {
    "build text query from generic searchable fields" in {
      val request = requestOrFail(ElasticsearchSearchInput("fresh style", Nil, Nil, None, None, limit = 3))
      val multiMatch = request.hcursor.downField("query").downField("bool").downField("must").downN(0).downField("multi_match")

      assert(multiMatch.get[String]("query") == Right("fresh style"))
      assert(multiMatch.get[List[String]]("fields") == Right(List("title^3.0", "body^1.5")))
      assert(multiMatch.get[String]("operator") == Right("or"))
      assert(request.hcursor.get[Int]("size") == Right(5))
      assert(request.hcursor.get[Boolean]("track_total_hits") == Right(true))
    }

    "resolve explicit constraints through the generic query schema" in {
      val request = requestOrFail(
        ElasticsearchSearchInput(
          remainingText = "",
          explicitConstraints = List(
            SearchConstraint.ServiceAny(Set("Haircut")),
            SearchConstraint.EnumAttr("color", Set("red", "blue")),
            SearchConstraint.BoolAttr("available", true),
          ),
          softBoosts = Nil,
          userLat = None,
          userLon = None,
          limit = 3,
        )
      )
      val filterJson = filterClauses(request).map(_.noSpaces).mkString("\n")

      assert(filterJson.contains("serviceName"))
      assert(filterJson.contains("Haircut"))
      assert(filterJson.contains("attrs.color"))
      assert(filterJson.contains("blue"))
      assert(filterJson.contains("red"))
      assert(filterJson.contains("available"))
      assert(filterJson.contains("true"))
    }

    "build range, term, terms, and near-user empty-clause behavior" in {
      val request = requestOrFail(
        ElasticsearchSearchInput(
          remainingText = "",
          explicitConstraints = List(
            SearchConstraint.PriceRange(Some(BigDecimal(10)), Some(BigDecimal(30))),
            SearchConstraint.DurationRange(None, Some(60)),
            SearchConstraint.IntRange("level", Some(1), Some(3)),
            SearchConstraint.DecimalRange("rating", Some(BigDecimal("4.0")), None),
            SearchConstraint.NearUser,
          ),
          softBoosts = Nil,
          userLat = None,
          userLon = None,
          limit = 3,
        )
      )
      val filters = filterClauses(request)
      val filterJson = filters.map(_.noSpaces).mkString("\n")

      assert(filters.size == 4)
      assert(filterJson.contains("priceFrom"))
      assert(filterJson.contains("gte"))
      assert(filterJson.contains("lte"))
      assert(filterJson.contains("durationMin"))
      assert(filterJson.contains("counts.level"))
      assert(filterJson.contains("metrics.rating"))
      assert(!filterJson.contains("location"))
    }

    "build facets and carousel aggregations from the runtime spec" in {
      val request = requestOrFail(ElasticsearchSearchInput("", Nil, Nil, None, None, limit = 3))
      val aggs = request.hcursor.downField("aggs")

      assert(aggs.downField("agg_serviceName").downField("terms").get[String]("field") == Right("serviceName"))
      assert(aggs.downField("agg_serviceName").downField("terms").get[Int]("size") == Right(7))
      assert(aggs.downField("agg_priceFrom").downField("range").get[String]("field") == Right("priceFrom"))
      assert(aggs.downField("agg_providerId").downField("terms").get[String]("field") == Right("providerId"))
      assert(aggs.downField("agg_serviceId").downField("terms").get[String]("field") == Right("serviceId"))
    }

    "apply geo function scoring from generic input coordinates and query schema location field" in {
      val request = requestOrFail(
        ElasticsearchSearchInput(
          remainingText = "fresh",
          explicitConstraints = Nil,
          softBoosts = List(SearchConstraint.ServiceAny(Set("Haircut"))),
          userLat = Some(BigDecimal("52.52")),
          userLon = Some(BigDecimal("13.40")),
          limit = 9,
        )
      )
      val functionScore = request.hcursor.downField("query").downField("function_score")
      val origin = functionScore.downField("functions").downN(0).downField("gauss").downField("location").downField("origin")
      val boolQuery = functionScore.downField("query").downField("bool")

      assert(request.hcursor.get[Int]("size") == Right(9))
      assert(origin.get[BigDecimal]("lat") == Right(BigDecimal("52.52")))
      assert(origin.get[BigDecimal]("lon") == Right(BigDecimal("13.40")))
      assert(functionScore.downField("functions").downN(0).downField("gauss").downField("location").get[String]("scale") == Right("3km"))
      assert(functionScore.downField("functions").downN(0).downField("gauss").downField("location").get[String]("offset") == Right("1km"))
      assert(functionScore.downField("functions").downN(0).downField("gauss").downField("location").get[Double]("decay") == Right(0.25d))
      assert(functionScore.downField("functions").downN(0).get[Double]("weight") == Right(1.75d))
      assert(boolQuery.downField("should").focus.nonEmpty)
      assert(boolQuery.get[Int]("minimum_should_match") == Right(0))
    }
  }

  private def requestOrFail(input: ElasticsearchSearchInput): Json =
    ElasticsearchSearchRequestInterpreter.request(ToyElasticsearchSearchSpec.runtimeSpec, input) match {
      case Right(value) => value
      case Left(error)  => fail(error.message)
    }

  private def filterClauses(request: Json): List[Json] =
    request.hcursor.downField("query").downField("bool").downField("filter").values match {
      case Some(values) => values.toList
      case None         => fail("expected bool filter clauses")
    }
}
