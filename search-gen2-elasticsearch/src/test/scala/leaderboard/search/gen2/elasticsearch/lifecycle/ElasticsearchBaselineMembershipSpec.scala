package leaderboard.search.gen2.elasticsearch.lifecycle

import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.plan.*
import leaderboard.search.gen2.elasticsearch.*
import org.scalatest.wordspec.AnyWordSpec

import io.circe.Json

/** Elasticsearch membership compiler/decoder/service proofs using the neutral book fixture and
  * authorization pattern. */
final class ElasticsearchBaselineMembershipSpec extends AnyWordSpec {
  import ElasticsearchTestFixtures.*

  private val bound = {
    val plan = SearchPlan[BookDocument](None, Vector.empty, Vector.empty, Vector.empty, PageRequest(None, PageSize.from(10).getOrElse(fail("expected page size"))), Vector.empty, Vector.empty, PlanDiagnostics.empty)
    SearchCursorEnvelope.bind(plan, CanonicalPlanView[BookDocument](fullPolicy.contractFingerprint)).getOrElse(fail("expected bound plan"))
  }

  private val prepared = ElasticsearchSearchRequestCompiler.compile(fullPolicy, bound).getOrElse(fail("expected prepared request"))

  private val authorized: AuthorizedElasticsearchSearchRequest[BookDocument, String] = {
    ElasticsearchSearchRequestAuthorization.authorize(
      prepared,
      new LifecycleResolvedElasticsearchGeneration(testGenerationReference, testPhysicalTarget, testMetadata),
    ).getOrElse(fail("expected an authorized request"))
  }

  private val candidateIds = Vector("978-0-13-468599-1", "978-0-201-63361-0")

  private val derivedTarget = testPhysicalTarget.value

  private def expectedBody: Json =
    Json.obj(
      "size" -> Json.fromInt(2),
      "_source" -> Json.fromBoolean(false),
      "track_total_hits" -> Json.fromBoolean(false),
      "query" -> Json.obj(
        "bool" -> Json.obj(
          "must" -> Json.arr(prepared.executionQuery),
          "filter" -> Json.arr(
            Json.obj(
              "terms" -> Json.obj(
                isbn.path.value -> Json.arr(Json.fromString("978-0-13-468599-1"), Json.fromString("978-0-201-63361-0")),
              ),
            ),
          ),
        ),
      ),
    )

  "ElasticsearchBaselineMembershipCompiler" should {
    "produce exact request JSON" in {
      val boundResult = new BoundElasticsearchBaselineResult(authorized, new ElasticsearchFullSearchResult(
        ElasticsearchSearchResponseDecoder.decode(authorized, validSearchResponse).getOrElse(fail("expected decoded page")),
        Vector.empty,
      ))
      ElasticsearchBaselineMembershipCompiler.compile(boundResult, candidateIds) match {
        case Right(compiled) =>
          assert(compiled.body == expectedBody)
          assert(compiled.baseline eq boundResult)
        case Left(error) => fail(s"expected compiled request, got $error")
      }
    }

    "include authorized baseline executionQuery unchanged in must" in {
      val boundResult = new BoundElasticsearchBaselineResult(authorized, new ElasticsearchFullSearchResult(
        ElasticsearchSearchResponseDecoder.decode(authorized, validSearchResponse).getOrElse(fail("expected decoded page")),
        Vector.empty,
      ))
      ElasticsearchBaselineMembershipCompiler.compile(boundResult, candidateIds) match {
        case Right(compiled) =>
          val query = compiled.body.hcursor.downField("query").downField("bool").downField("must").focus
          assert(query.contains(Json.arr(authorized.prepared.executionQuery)))
        case Left(error) => fail(s"expected compiled request, got $error")
      }
    }

    "encode identity terms with typed scalar encoding" in {
      val boundResult = new BoundElasticsearchBaselineResult(authorized, new ElasticsearchFullSearchResult(
        ElasticsearchSearchResponseDecoder.decode(authorized, validSearchResponse).getOrElse(fail("expected decoded page")),
        Vector.empty,
      ))
      ElasticsearchBaselineMembershipCompiler.compile(boundResult, candidateIds) match {
        case Right(compiled) =>
          val termsArray = compiled.body.hcursor
            .downField("query")
            .downField("bool")
            .downField("filter")
            .downN(0)
            .downField("terms")
            .downField(isbn.path.value)
            .focus
            .flatMap(_.asArray)
          assert(termsArray.exists(_.toVector == Vector(Json.fromString("978-0-13-468599-1"), Json.fromString("978-0-201-63361-0"))))
        case Left(error) => fail(s"expected compiled request, got $error")
      }
    }

    "preserve candidate ID order in terms array" in {
      val reversed = Vector("978-0-201-63361-0", "978-0-13-468599-1")
      val boundResult = new BoundElasticsearchBaselineResult(authorized, new ElasticsearchFullSearchResult(
        ElasticsearchSearchResponseDecoder.decode(authorized, validSearchResponse).getOrElse(fail("expected decoded page")),
        Vector.empty,
      ))
      ElasticsearchBaselineMembershipCompiler.compile(boundResult, reversed) match {
        case Right(compiled) =>
          val termsArray = compiled.body.hcursor
            .downField("query")
            .downField("bool")
            .downField("filter")
            .downN(0)
            .downField("terms")
            .downField(isbn.path.value)
            .focus
            .flatMap(_.asArray)
          assert(termsArray.exists(_.toVector == Vector(Json.fromString("978-0-201-63361-0"), Json.fromString("978-0-13-468599-1"))))
        case Left(error) => fail(s"expected compiled request, got $error")
      }
    }

    "use lifecycle-authorized physical target" in {
      val boundResult = new BoundElasticsearchBaselineResult(authorized, new ElasticsearchFullSearchResult(
        ElasticsearchSearchResponseDecoder.decode(authorized, validSearchResponse).getOrElse(fail("expected decoded page")),
        Vector.empty,
      ))
      ElasticsearchBaselineMembershipCompiler.compile(boundResult, candidateIds) match {
        case Right(compiled) =>
          assert(compiled.target == testPhysicalTarget)
        case Left(error) => fail(s"expected compiled request, got $error")
      }
    }

    "omit alias, raw target, caller query, facets, groups, cursor, search_after, sort, and aggs" in {
      val boundResult = new BoundElasticsearchBaselineResult(authorized, new ElasticsearchFullSearchResult(
        ElasticsearchSearchResponseDecoder.decode(authorized, validSearchResponse).getOrElse(fail("expected decoded page")),
        Vector.empty,
      ))
      ElasticsearchBaselineMembershipCompiler.compile(boundResult, candidateIds) match {
        case Right(compiled) =>
          val bodyObj = compiled.body.asObject.getOrElse(fail("expected object body"))
          val bodyKeys = bodyObj.keys.toVector
          assert(!bodyKeys.contains("search_after"))
          assert(!bodyKeys.contains("sort"))
          assert(!bodyKeys.contains("aggs"))
          assert(!bodyKeys.contains("agg"))
        case Left(error) => fail(s"expected compiled request, got $error")
      }
    }
  }

  "ElasticsearchBaselineMembershipDecoder" should {
    "derive matchingIds from response hit order, not response hit order" in {
      val response = Json.obj(
        "hits" -> Json.obj(
          "hits" -> Json.arr(
            Json.obj("_id" -> Json.fromString("978-0-201-63361-0")),
            Json.obj("_id" -> Json.fromString("978-0-13-468599-1")),
          ),
        ),
      )
      val boundResult = new BoundElasticsearchBaselineResult(authorized, new ElasticsearchFullSearchResult(
        ElasticsearchSearchResponseDecoder.decode(authorized, validSearchResponse).getOrElse(fail("expected decoded page")),
        Vector.empty,
      ))
      val compiled = ElasticsearchBaselineMembershipCompiler.compile(boundResult, candidateIds).getOrElse(fail("expected compiled"))
      ElasticsearchBaselineMembershipDecoder.decode(compiled, response) match {
        case Right(result) =>
          assert(result.matchingIds == Vector("978-0-13-468599-1", "978-0-201-63361-0"))
        case Left(error) => fail(s"expected matching result, got $error")
      }
    }

    "produce no matching IDs for empty response" in {
      val response = Json.obj(
        "hits" -> Json.obj(
          "hits" -> Json.arr(),
        ),
      )
      val boundResult = new BoundElasticsearchBaselineResult(authorized, new ElasticsearchFullSearchResult(
        ElasticsearchSearchResponseDecoder.decode(authorized, validSearchResponse).getOrElse(fail("expected decoded page")),
        Vector.empty,
      ))
      val compiled = ElasticsearchBaselineMembershipCompiler.compile(boundResult, candidateIds).getOrElse(fail("expected compiled"))
      ElasticsearchBaselineMembershipDecoder.decode(compiled, response) match {
        case Right(result) =>
          assert(result.matchingIds.isEmpty)
        case Left(error) => fail(s"expected empty matching result, got $error")
      }
    }

    "fail on malformed top-level (not an object)" in {
      val response = Json.arr()
      val boundResult = new BoundElasticsearchBaselineResult(authorized, new ElasticsearchFullSearchResult(
        ElasticsearchSearchResponseDecoder.decode(authorized, validSearchResponse).getOrElse(fail("expected decoded page")),
        Vector.empty,
      ))
      val compiled = ElasticsearchBaselineMembershipCompiler.compile(boundResult, candidateIds).getOrElse(fail("expected compiled"))
      ElasticsearchBaselineMembershipDecoder.decode(compiled, response) match {
        case Left(ElasticsearchBaselineMembershipResponseError.Malformed(_)) =>
        case other => fail(s"expected Malformed, got $other")
      }
    }

    "fail on missing hits" in {
      val response = Json.obj()
      val boundResult = new BoundElasticsearchBaselineResult(authorized, new ElasticsearchFullSearchResult(
        ElasticsearchSearchResponseDecoder.decode(authorized, validSearchResponse).getOrElse(fail("expected decoded page")),
        Vector.empty,
      ))
      val compiled = ElasticsearchBaselineMembershipCompiler.compile(boundResult, candidateIds).getOrElse(fail("expected compiled"))
      ElasticsearchBaselineMembershipDecoder.decode(compiled, response) match {
        case Left(ElasticsearchBaselineMembershipResponseError.Malformed(_)) =>
        case other => fail(s"expected Malformed, got $other")
      }
    }

    "fail on non-object hits" in {
      val response = Json.obj("hits" -> Json.arr())
      val boundResult = new BoundElasticsearchBaselineResult(authorized, new ElasticsearchFullSearchResult(
        ElasticsearchSearchResponseDecoder.decode(authorized, validSearchResponse).getOrElse(fail("expected decoded page")),
        Vector.empty,
      ))
      val compiled = ElasticsearchBaselineMembershipCompiler.compile(boundResult, candidateIds).getOrElse(fail("expected compiled"))
      ElasticsearchBaselineMembershipDecoder.decode(compiled, response) match {
        case Left(ElasticsearchBaselineMembershipResponseError.Malformed(_)) =>
        case other => fail(s"expected Malformed, got $other")
      }
    }

    "fail on missing hits.hits" in {
      val response = Json.obj("hits" -> Json.obj())
      val boundResult = new BoundElasticsearchBaselineResult(authorized, new ElasticsearchFullSearchResult(
        ElasticsearchSearchResponseDecoder.decode(authorized, validSearchResponse).getOrElse(fail("expected decoded page")),
        Vector.empty,
      ))
      val compiled = ElasticsearchBaselineMembershipCompiler.compile(boundResult, candidateIds).getOrElse(fail("expected compiled"))
      ElasticsearchBaselineMembershipDecoder.decode(compiled, response) match {
        case Left(ElasticsearchBaselineMembershipResponseError.Malformed(_)) =>
        case other => fail(s"expected Malformed, got $other")
      }
    }

    "fail on non-array hits.hits" in {
      val response = Json.obj("hits" -> Json.obj("hits" -> Json.obj()))
      val boundResult = new BoundElasticsearchBaselineResult(authorized, new ElasticsearchFullSearchResult(
        ElasticsearchSearchResponseDecoder.decode(authorized, validSearchResponse).getOrElse(fail("expected decoded page")),
        Vector.empty,
      ))
      val compiled = ElasticsearchBaselineMembershipCompiler.compile(boundResult, candidateIds).getOrElse(fail("expected compiled"))
      ElasticsearchBaselineMembershipDecoder.decode(compiled, response) match {
        case Left(ElasticsearchBaselineMembershipResponseError.Malformed(_)) =>
        case other => fail(s"expected Malformed, got $other")
      }
    }

    "fail on malformed hit (not an object)" in {
      val response = Json.obj(
        "hits" -> Json.obj(
          "hits" -> Json.arr(Json.fromString("not-an-object")),
        ),
      )
      val boundResult = new BoundElasticsearchBaselineResult(authorized, new ElasticsearchFullSearchResult(
        ElasticsearchSearchResponseDecoder.decode(authorized, validSearchResponse).getOrElse(fail("expected decoded page")),
        Vector.empty,
      ))
      val compiled = ElasticsearchBaselineMembershipCompiler.compile(boundResult, candidateIds).getOrElse(fail("expected compiled"))
      ElasticsearchBaselineMembershipDecoder.decode(compiled, response) match {
        case Left(ElasticsearchBaselineMembershipResponseError.InvalidHit(0, _)) =>
        case other => fail(s"expected InvalidHit, got $other")
      }
    }

    "fail on missing _id" in {
      val response = Json.obj(
        "hits" -> Json.obj(
          "hits" -> Json.arr(Json.obj("_score" -> Json.fromDoubleOrNull(1.0))),
        ),
      )
      val boundResult = new BoundElasticsearchBaselineResult(authorized, new ElasticsearchFullSearchResult(
        ElasticsearchSearchResponseDecoder.decode(authorized, validSearchResponse).getOrElse(fail("expected decoded page")),
        Vector.empty,
      ))
      val compiled = ElasticsearchBaselineMembershipCompiler.compile(boundResult, candidateIds).getOrElse(fail("expected compiled"))
      ElasticsearchBaselineMembershipDecoder.decode(compiled, response) match {
        case Left(ElasticsearchBaselineMembershipResponseError.InvalidHit(0, _)) =>
        case other => fail(s"expected InvalidHit, got $other")
      }
    }

    "fail on non-string _id" in {
      val response = Json.obj(
        "hits" -> Json.obj(
          "hits" -> Json.arr(Json.obj("_id" -> Json.fromInt(123))),
        ),
      )
      val boundResult = new BoundElasticsearchBaselineResult(authorized, new ElasticsearchFullSearchResult(
        ElasticsearchSearchResponseDecoder.decode(authorized, validSearchResponse).getOrElse(fail("expected decoded page")),
        Vector.empty,
      ))
      val compiled = ElasticsearchBaselineMembershipCompiler.compile(boundResult, candidateIds).getOrElse(fail("expected compiled"))
      ElasticsearchBaselineMembershipDecoder.decode(compiled, response) match {
        case Left(ElasticsearchBaselineMembershipResponseError.InvalidHit(0, _)) =>
        case other => fail(s"expected InvalidHit, got $other")
      }
    }

    "fail on excessive hits" in {
      val response = Json.obj(
        "hits" -> Json.obj(
          "hits" -> Json.arr(
            Json.obj("_id" -> Json.fromString("978-0-13-468599-1")),
            Json.obj("_id" -> Json.fromString("978-0-201-63361-0")),
            Json.obj("_id" -> Json.fromString("extra")),
          ),
        ),
      )
      val boundResult = new BoundElasticsearchBaselineResult(authorized, new ElasticsearchFullSearchResult(
        ElasticsearchSearchResponseDecoder.decode(authorized, validSearchResponse).getOrElse(fail("expected decoded page")),
        Vector.empty,
      ))
      val compiled = ElasticsearchBaselineMembershipCompiler.compile(boundResult, candidateIds).getOrElse(fail("expected compiled"))
      ElasticsearchBaselineMembershipDecoder.decode(compiled, response) match {
        case Left(ElasticsearchBaselineMembershipResponseError.ExcessiveHits(2, 3)) =>
        case other => fail(s"expected ExcessiveHits, got $other")
      }
    }

    "report unexpected identity on decode" in {
      val response = Json.obj(
        "hits" -> Json.obj(
          "hits" -> Json.arr(Json.obj("_id" -> Json.fromString("not-a-valid-isbn-format"))),
        ),
      )
      val boundResult = new BoundElasticsearchBaselineResult(authorized, new ElasticsearchFullSearchResult(
        ElasticsearchSearchResponseDecoder.decode(authorized, validSearchResponse).getOrElse(fail("expected decoded page")),
        Vector.empty,
      ))
      val compiled = ElasticsearchBaselineMembershipCompiler.compile(boundResult, candidateIds).getOrElse(fail("expected compiled"))
      ElasticsearchBaselineMembershipDecoder.decode(compiled, response) match {
        case Left(ElasticsearchBaselineMembershipResponseError.UnexpectedIdentity(idx, canon)) =>
          assert(idx == 0)
          assert(canon == "not-a-valid-isbn-format")
        case other => fail(s"expected UnexpectedIdentity, got $other")
      }
    }

    "report duplicate response identity with first/duplicate indexes" in {
      val response = Json.obj(
        "hits" -> Json.obj(
          "hits" -> Json.arr(
            Json.obj("_id" -> Json.fromString("978-0-13-468599-1")),
            Json.obj("_id" -> Json.fromString("978-0-13-468599-1")),
          ),
        ),
      )
      val boundResult = new BoundElasticsearchBaselineResult(authorized, new ElasticsearchFullSearchResult(
        ElasticsearchSearchResponseDecoder.decode(authorized, validSearchResponse).getOrElse(fail("expected decoded page")),
        Vector.empty,
      ))
      val compiled = ElasticsearchBaselineMembershipCompiler.compile(boundResult, candidateIds).getOrElse(fail("expected compiled"))
      ElasticsearchBaselineMembershipDecoder.decode(compiled, response) match {
        case Left(ElasticsearchBaselineMembershipResponseError.DuplicateIdentity(id, firstIndex, duplicateIndex)) =>
          assert(id == "978-0-13-468599-1")
          assert(firstIndex == 0)
          assert(duplicateIndex == 1)
        case other => fail(s"expected DuplicateIdentity, got $other")
      }
    }

    "fail on decoded but unrequested identity" in {
      val response = Json.obj(
        "hits" -> Json.obj(
          "hits" -> Json.arr(Json.obj("_id" -> Json.fromString("unexpected-id"))),
        ),
      )
      val boundResult = new BoundElasticsearchBaselineResult(authorized, new ElasticsearchFullSearchResult(
        ElasticsearchSearchResponseDecoder.decode(authorized, validSearchResponse).getOrElse(fail("expected decoded page")),
        Vector.empty,
      ))
      val compiled = ElasticsearchBaselineMembershipCompiler.compile(boundResult, candidateIds).getOrElse(fail("expected compiled"))
      ElasticsearchBaselineMembershipDecoder.decode(compiled, response) match {
        case Left(ElasticsearchBaselineMembershipResponseError.UnexpectedIdentity(idx, canon)) =>
          assert(idx == 0)
          assert(canon == "unexpected-id")
        case other => fail(s"expected UnexpectedIdentity, got $other")
      }
    }
  }

  "ElasticsearchBaselineService.membership" should {
    "perform no transport call when candidateIds is empty" in {
      val service = new ElasticsearchBaselineService(FailIfCalledLifecycleClient)
      val boundResult = new BoundElasticsearchBaselineResult(authorized, new ElasticsearchFullSearchResult(
        ElasticsearchSearchResponseDecoder.decode(authorized, validSearchResponse).getOrElse(fail("expected decoded page")),
        Vector.empty,
      ))
      service.membership(boundResult, Vector.empty) match {
        case Right(result) =>
          assert(result.requestedIds.isEmpty)
          assert(result.matchingIds.isEmpty)
        case other => fail(s"expected empty result, got $other")
      }
    }

    "use bound physical target and decoder for non-empty execution" in {
      val response = Json.obj(
        "hits" -> Json.obj(
          "hits" -> Json.arr(Json.obj("_id" -> Json.fromString("978-0-13-468599-1"))),
        ),
      )
      val service = new ElasticsearchBaselineService(ScriptedMembershipClient(response))
      val boundResult = new BoundElasticsearchBaselineResult(authorized, new ElasticsearchFullSearchResult(
        ElasticsearchSearchResponseDecoder.decode(authorized, validSearchResponse).getOrElse(fail("expected decoded page")),
        Vector.empty,
      ))
      service.membership(boundResult, candidateIds) match {
        case Right(result) =>
          assert(result.matchingIds == Vector("978-0-13-468599-1"))
        case Left(error) => fail(s"expected matching result, got $error")
      }
    }
  }

  "ElasticsearchBaselineService.search" should {
    "delegate to searchBound without changing observable result" in {
      val response = Json.obj(
        "timed_out" -> Json.fromBoolean(false),
        "_shards" -> Json.obj("total" -> Json.fromInt(1), "successful" -> Json.fromInt(1), "failed" -> Json.fromInt(0)),
        "hits" -> Json.obj(
          "total" -> Json.obj("value" -> Json.fromInt(2), "relation" -> Json.fromString("eq")),
          "hits" -> Json.arr(
            Json.obj(
              "_id" -> Json.fromString("978-0-13-468599-1"),
              "_score" -> Json.fromBigDecimal(BigDecimal("1.0")),
              "_source" -> Json.obj(),
              "sort" -> Json.arr(Json.fromString("978-0-13-468599-1")),
            ),
          ),
        ),
      )
      val service = new ElasticsearchBaselineService(ScriptedSearchClient(response))
      service.search(prepared) match {
        case Right(result) =>
          assert(result.hits.length == 1)
        case Left(error) => fail(s"expected search result, got $error")
      }
    }
  }

  private val validSearchResponse: Json = Json.obj(
    "timed_out" -> Json.fromBoolean(false),
    "_shards" -> Json.obj("total" -> Json.fromInt(1), "successful" -> Json.fromInt(1), "failed" -> Json.fromInt(0)),
    "hits" -> Json.obj(
      "total" -> Json.obj("value" -> Json.fromInt(0), "relation" -> Json.fromString("eq")),
      "hits" -> Json.arr(),
    ),
  )

  private val FailIfCalledLifecycleClient = new ElasticsearchGenerationLifecycle(
    new ElasticsearchGen2JsonClient {
      def postJson(path: String, body: Json) = fail(s"unexpected postJson($path, $body)")
      def putJson(path: String, body: Json) = fail(s"unexpected putJson($path, $body)")
      def post(path: String) = fail(s"unexpected post($path)")
      def postNdjson(path: String, body: String) = fail(s"unexpected postNdjson($path, $body)")
      def getJson(path: String) = fail(s"unexpected getJson($path)")
      def delete(path: String) = fail(s"unexpected delete($path)")
    },
    testLifecycleConfig,
    java.time.Clock.systemUTC(),
  )

  private def ScriptedMembershipClient(response: Json) = new ElasticsearchGenerationLifecycle(
    new ElasticsearchGen2JsonClient {
      def postJson(path: String, body: Json): Either[ElasticsearchGen2TransportError, Json] = {
        assert(path == s"/${derivedTarget}/_search")
        assert(body == expectedBody)
        Right(response)
      }
      def putJson(path: String, body: Json) = fail(s"unexpected putJson($path, $body)")
      def post(path: String) = fail(s"unexpected post($path)")
      def postNdjson(path: String, body: String) = fail(s"unexpected postNdjson($path, $body)")
      def getJson(path: String) = fail(s"unexpected getJson($path)")
      def delete(path: String) = fail(s"unexpected delete($path)")
    },
    testLifecycleConfig,
    java.time.Clock.systemUTC(),
  )

  private def ScriptedSearchClient(response: Json) = new ElasticsearchGenerationLifecycle(
    new ElasticsearchGen2JsonClient {
      def postJson(path: String, body: Json): Either[ElasticsearchGen2TransportError, Json] = {
        (path, body.asObject.flatMap(_("query"))) match {
          case (target @ s"/$derivedTarget/_search", _) =>
            assert(target == s"/$derivedTarget/_search")
            Right(response)
          case (countPath @ s"/$derivedTarget/_count", Some(queryObj)) if queryObj.toString.contains("match_all") =>
            Right(Json.obj("count" -> Json.fromLong(2L), "_shards" -> Json.obj("total" -> Json.fromInt(1), "successful" -> Json.fromInt(1), "failed" -> Json.fromInt(0))))
          case _ => fail(s"unexpected postJson($path, $body)")
        }
      }
      def putJson(path: String, body: Json) = fail(s"unexpected putJson($path, $body)")
      def post(path: String) = fail(s"unexpected post($path)")
      def postNdjson(path: String, body: String) = fail(s"unexpected postNdjson($path, $body)")
      def getJson(path: String): Either[ElasticsearchGen2TransportError, Json] = path match {
        case "/_alias/books" =>
          Right(Json.obj(derivedTarget -> Json.obj("aliases" -> Json.obj("books" -> Json.obj()))))
        case s"/$derivedTarget/_mapping" =>
          Right(Json.obj(derivedTarget -> Json.obj("mappings" -> Json.obj(
            "_meta" -> testMetadataJson,
            "properties" -> Json.obj(),
          ))))
        case other => fail(s"unexpected getJson($other)")
      }
      def delete(path: String) = fail(s"unexpected delete($path)")
    },
    testLifecycleConfig,
    java.time.Clock.systemUTC(),
  )
}
