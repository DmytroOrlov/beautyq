package leaderboard.search.gen2.elasticsearch

import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.plan.*
import org.scalatest.wordspec.AnyWordSpec

import io.circe.Json

/** Neutral group-query and response proof using the reusable book fixture, not BeautyQ policy. */
final class ElasticsearchGroupQuerySpec extends AnyWordSpec {
  import ElasticsearchTestFixtures.*

  private val groupId = GroupId("genre-group")
  private val metricId = GroupMetricId("best-score")

  private val group: GroupRequest[BookDocument, String] =
    GroupRequest(
      id = groupId,
      keyField = genre,
      size = GroupSize.from(2).getOrElse(fail("expected a valid group size")),
      representative = RepresentativeRequest.Fields(Vector(title, genre)),
      metrics = Vector(GroupMetricRequest.BestScore(metricId)),
      order = Vector(
        GroupOrder.Metric(metricId, SortDirection.Desc),
        GroupOrder.MatchingDocumentCount(SortDirection.Desc),
        GroupOrder.Key(SortDirection.Asc),
      ),
      precision = GroupPrecisionPolicy.RequireExact,
    )

  private val bound: BoundSearchPlan[BookDocument] = {
    val plan = SearchPlan[BookDocument](
      residualText = Some("scala"),
      appliedFilters = Vector.empty,
      softSignals = Vector.empty,
      sort = Vector.empty,
      page = PageRequest(None, PageSize.from(10).getOrElse(fail("expected a valid page size"))),
      facets = Vector.empty,
      groups = Vector(group),
      diagnostics = PlanDiagnostics.empty,
    )
    SearchCursorEnvelope.bind(plan, CanonicalPlanView[BookDocument](fullPolicy.contractFingerprint)) match {
      case Right(value) => value
      case Left(error)  => fail(s"expected a valid bound plan, got $error")
    }
  }

  private val prepared: CompiledElasticsearchSearchRequest[BookDocument, String] =
    ElasticsearchSearchRequestCompiler.compile(fullPolicy, bound) match {
      case Right(value) => value
      case Left(error)  => fail(s"expected a prepared request, got $error")
    }

  private val compiled: CompiledElasticsearchGroupQuery[BookDocument, String] =
    ElasticsearchGroupQueryCompiler.compile(prepared, group) match {
      case Right(value) => value
      case Left(error)  => fail(s"expected a group query, got $error")
    }

  private def at(body: Json, path: String*): Json =
    path.foldLeft(body.hcursor: io.circe.ACursor) { (cursor, field) =>
      field.toIntOption match {
        case Some(index) => cursor.downN(index)
        case None        => cursor.downField(field)
      }
    }.focus.getOrElse(fail(s"expected JSON at ${path.mkString(".")}"))

  private val oneBucketResponse: Json =
    Json.obj(
      "timed_out" -> Json.fromBoolean(false),
      "_shards" -> Json.obj("total" -> Json.fromInt(1), "successful" -> Json.fromInt(1), "failed" -> Json.fromInt(0)),
      "aggregations" -> Json.obj(
        "gen2_group" -> Json.obj(
          "buckets" -> Json.arr(
            Json.obj(
              "key" -> Json.obj("group_key" -> Json.fromString("Technology")),
              "doc_count" -> Json.fromInt(7),
              "representative" -> Json.obj(
                "hits" -> Json.obj(
                  "hits" -> Json.arr(
                    Json.obj(
                      "_id" -> Json.fromString(bookA.isbn),
                      "_score" -> Json.fromBigDecimal(BigDecimal("4.2")),
                      "_source" -> Json.obj("title" -> Json.fromString(bookA.title), "genre" -> Json.fromString(bookA.genre)),
                    )
                  )
                )
              ),
            )
          )
        )
      ),
    )

  "ElasticsearchGroupQueryCompiler" should {
    "compile a composite query with the declared representative, metric and deterministic source" in {
      val body = compiled.body(None)
      assert(at(body, "size") == Json.fromInt(0))
      assert(at(body, "query") == prepared.executionQuery)
      assert(at(body, "aggs", "gen2_group", "composite", "size") == Json.fromInt(500))
      assert(at(body, "aggs", "gen2_group", "composite", "sources", "0", "group_key", "terms", "field") == Json.fromString("genre"))
      assert(at(body, "aggs", "gen2_group", "aggs", "representative", "top_hits", "size") == Json.fromInt(1))
      assert(at(body, "aggs", "gen2_group", "aggs", "representative", "top_hits", "_source", "includes", "0") == Json.fromString("title"))
      assert(at(body, "aggs", "gen2_group", "aggs", "representative", "top_hits", "_source", "includes", "1") == Json.fromString("genre"))
    }

    "carry the composite after key without changing the declared query" in {
      val after = Json.obj("group_key" -> Json.fromString("Technology"))
      assert(at(compiled.body(Some(after)), "aggs", "gen2_group", "composite", "after") == after)
      assert(at(compiled.body(Some(after)), "query") == prepared.executionQuery)
    }

    "emit a declared geo metric only when the group policy requests it" in {
      val origin = GeoPoint(BigDecimal("52.5"), BigDecimal("13.4"))
      val geoGroup = group.copy(
        metrics = Vector(
          GroupMetricRequest.BestScore(metricId),
          GroupMetricRequest.MinGeoDistance(GroupMetricId("min-distance"), storeLocation, origin),
        ),
        order = Vector(
          GroupOrder.Metric(metricId, SortDirection.Desc),
          GroupOrder.Metric(GroupMetricId("min-distance"), SortDirection.Asc),
          GroupOrder.Key(SortDirection.Asc),
        ),
      )
      val geoCompiled = ElasticsearchGroupQueryCompiler.compile(prepared, geoGroup) match {
        case Right(value) => value
        case Left(error)  => fail(s"expected a geo group query, got $error")
      }
      val body = geoCompiled.body(None)
      assert(at(body, "aggs", "gen2_group", "aggs", "min-distance", "top_hits", "size") == Json.fromInt(1))
      assert(at(body, "aggs", "gen2_group", "aggs", "min-distance", "top_hits", "sort", "0", "_geo_distance", "storeLocation", "lat") == Json.fromBigDecimal(origin.lat))
    }
  }

  "ElasticsearchGroupResponseDecoder" should {
    "decode a typed key, representative fields, exact count and best score" in {
      ElasticsearchGroupResponseDecoder.decode(compiled, 0, oneBucketResponse, None, 0L) match {
        case Right(page) =>
          val bucket = page.buckets.headOption.getOrElse(fail("expected one group bucket"))
          assert(bucket.key(genre) == Right("Technology"))
          assert(bucket.matchingDocumentCount == 7L)
          assert(bucket.representative.requiredValue(title) == Right(bookA.title))
          assert(bucket.representative.requiredValue(genre) == Right(bookA.genre))
          assert(bucket.representative.id == bookA.isbn)
          assert(bucket.bestScore(metricId).contains(BigDecimal("4.2")))
          assert(page.afterKey.isEmpty)
          assert(page.diagnostics.shardsFailed == 0)
        case Left(errors) => fail(s"expected a decoded group page, got $errors")
      }
    }

    "reject a repeated composite after key instead of looping forever" in {
      val response = oneBucketResponse.deepMerge(Json.obj("aggregations" -> Json.obj("gen2_group" -> Json.obj("after_key" -> Json.obj("group_key" -> Json.fromString("Technology"))))))
      ElasticsearchGroupResponseDecoder.decode(compiled, 1, response, Some(Json.obj("group_key" -> Json.fromString("Technology"))), 1L) match {
        case Left(errors) => assert(errors.toVector.exists(_.isInstanceOf[ElasticsearchGroupResponseError.NonAdvancingAfterKey]))
        case Right(value) => fail(s"expected a non-advancing after-key failure, got $value")
      }
    }
  }
}
