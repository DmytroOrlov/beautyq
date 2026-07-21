package leaderboard.http

import io.circe.Json
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
  }
}
