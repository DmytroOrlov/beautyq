package leaderboard.http

import io.circe.Json
import leaderboard.search.beautyq.gen2.wiring.*
import leaderboard.search.beautyq.gen2.wiring.testkit.BeautyQOrchestrationTestKit
import org.scalatest.wordspec.AnyWordSpec

final class BeautySearchGen2JsonSpec extends AnyWordSpec {
  "BeautySearchGen2Json" should {
    "decode the native request shape without using the V1 contract" in {
      val request = BeautySearchGen2Json.decodeRequest(Json.obj(
        "query" -> Json.fromString("massage"),
        "filters" -> Json.arr(Json.obj(
          "field" -> Json.fromString("service"),
          "operator" -> Json.fromString("equal"),
          "value" -> Json.fromString("svc-1"),
        )),
        "requestedFacets" -> Json.arr(Json.fromString("service")),
        "sort" -> Json.arr(Json.obj("name" -> Json.fromString("price"), "direction" -> Json.fromString("asc"))),
        "page" -> Json.obj("cursor" -> Json.Null, "size" -> Json.fromInt(20)),
        "userLocation" -> Json.Null,
      ))

      request match {
        case Right(value) =>
          assert(value.query.contains("massage"))
          assert(value.filters.size == 1)
          assert(value.requestedFacets.map(_.value) == Vector("service"))
          assert(value.page.size.value == 20)
        case Left(error) => fail(s"expected native Gen2 request, got $error")
      }
    }

    "reject an invalid page size before contract validation" in {
      val result = BeautySearchGen2Json.decodeRequest(Json.obj(
        "query" -> Json.Null,
        "filters" -> Json.arr(),
        "requestedFacets" -> Json.arr(),
        "sort" -> Json.arr(),
        "page" -> Json.obj("cursor" -> Json.Null, "size" -> Json.fromInt(0)),
        "userLocation" -> Json.Null,
      ))

      result match {
        case Left(error) => assert(error.contains("InvalidPageSize"))
        case Right(value) => fail(s"expected invalid page size, got $value")
      }
    }

    "reject a mixed-type filter array instead of dropping invalid values" in {
      val result = BeautySearchGen2Json.decodeRequest(Json.obj(
        "query" -> Json.Null,
        "filters" -> Json.arr(Json.obj(
          "field" -> Json.fromString("service"),
          "operator" -> Json.fromString("in"),
          "value" -> Json.arr(Json.fromString("svc-1"), Json.fromInt(2)),
        )),
        "requestedFacets" -> Json.arr(),
        "sort" -> Json.arr(),
        "page" -> Json.obj("cursor" -> Json.Null, "size" -> Json.fromInt(20)),
        "userLocation" -> Json.Null,
      ))

      result match {
        case Left(error) => assert(error.contains("array value must be a string"))
        case Right(value) => fail(s"expected mixed array rejection, got $value")
      }
    }

    "encode the projector-owned response with totalRelation, applied filter constraint+provenance, and suppressed filters" in {
      val context = BeautyQOrchestrationTestKit.eligible()
      val application = BeautyQSearchApplication.make(
        BeautyQOrchestrationTestKit.materialized,
        context.baselineService,
        context.embedding,
        context.qdrant,
      )
      val request = BeautyQOrchestrationTestKit.eligible().request
      val orchestratorResult = application.execute(request) match {
        case Right(value) => value
        case Left(error)  => fail(s"expected application result, got $error")
      }
      val projected = BeautyQSearchResponseGen2Projector.project(orchestratorResult) match {
        case Right(value) => value
        case Left(error)  => fail(s"expected projection, got $error")
      }

      val encoded = BeautySearchGen2Json.encodeResponse(projected)
      val cursor = encoded.hcursor

      cursor.downField("totalRelation").as[String] match {
        case Right(value) => assert(value == orchestratorResult.baselineResult.totalRelation)
        case Left(error)  => fail(s"expected encoded totalRelation, got $error")
      }

      cursor.downField("appliedFilters").focus match {
        case Some(array) =>
          val entries = array.asArray.getOrElse(fail("appliedFilters must be an array"))
          assert(entries.nonEmpty)
          entries.zip(projected.appliedFilters).foreach { case (json, filter) =>
            assert(json.hcursor.get[String]("fieldId").getOrElse(fail("fieldId")) == filter.fieldId)
            assert(json.hcursor.get[String]("constraint").getOrElse(fail("constraint")) == filter.constraint)
            assert(json.hcursor.get[String]("provenance").getOrElse(fail("provenance")) == filter.provenance)
          }
        case None => fail("expected appliedFilters array in encoded response")
      }

      cursor.downField("suppressedFilters").focus match {
        case Some(array) => assert(array.isArray)
        case None        => fail("expected suppressedFilters array in encoded response")
      }
    }

    "encode a non-empty suppressedFilters list with constraint, provenance, and reason" in {
      val context = BeautyQOrchestrationTestKit.eligible()
      val application = BeautyQSearchApplication.make(
        BeautyQOrchestrationTestKit.materialized,
        context.baselineService,
        context.embedding,
        context.qdrant,
      )
      val request = BeautyQOrchestrationTestKit.eligible().request
      val orchestratorResult = application.execute(request) match {
        case Right(value) => value
        case Left(error)  => fail(s"expected application result, got $error")
      }
      val projected = BeautyQSearchResponseGen2Projector.project(orchestratorResult) match {
        case Right(value) => value
        case Left(error)  => fail(s"expected projection, got $error")
      }
      val expectedSuppressed = projected.suppressedFilters

      val encoded = BeautySearchGen2Json.encodeResponse(projected)
      val suppressed = encoded.hcursor.downField("suppressedFilters").focus.flatMap(_.asArray).getOrElse(fail("suppressedFilters must be an array"))

      assert(suppressed.size == expectedSuppressed.size)
      suppressed.zip(expectedSuppressed).foreach { case (json, filter) =>
        assert(json.hcursor.get[String]("fieldId").getOrElse(fail("fieldId")) == filter.fieldId)
        assert(json.hcursor.get[String]("constraint").getOrElse(fail("constraint")) == filter.constraint)
        assert(json.hcursor.get[String]("provenance").getOrElse(fail("provenance")) == filter.provenance)
        assert(json.hcursor.get[String]("reason").getOrElse(fail("reason")) == filter.reason)
      }
    }
  }
}
