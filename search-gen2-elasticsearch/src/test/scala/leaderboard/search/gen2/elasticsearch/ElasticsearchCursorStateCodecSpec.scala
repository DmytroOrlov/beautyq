package leaderboard.search.gen2.elasticsearch

import org.scalatest.wordspec.AnyWordSpec

import io.circe.Json

/** Neutral calibration for the versioned Elasticsearch cursor-state codec: deterministic encoding, strict
  * typed decoding, explicit protocol version, no offset/`from` state, and arity validation against the
  * compiled sort vector.
  */
final class ElasticsearchCursorStateCodecSpec extends AnyWordSpec {

  private val generationReference = ElasticsearchGenerationReference("generation-neutral-20260101")

  private val state =
    ElasticsearchCursorState(
      ElasticsearchSearchCompilerVersion.Current,
      generationReference,
      Vector(
        ElasticsearchSearchAfterValue.Text("abc"),
        ElasticsearchSearchAfterValue.Number(Json.fromBigDecimal(BigDecimal("42.5")).asNumber.getOrElse(fail("expected a JSON number"))),
        ElasticsearchSearchAfterValue.Bool(true),
        ElasticsearchSearchAfterValue.Null,
      ),
    )

  "ElasticsearchCursorStateCodec" should {
    "round-trip encode/decode exactly, for the full search_after scalar domain (string, number, boolean, null)" in {
      val encoded = ElasticsearchCursorStateCodec.encode(state)
      ElasticsearchCursorStateCodec.decode(encoded, expectedSearchAfterArity = 4) match {
        case Right(decoded) => assert(decoded == state)
        case Left(error)    => fail(s"expected successful decode, got $error")
      }
    }

    "produce deterministic JSON for repeated encoding of identical state" in {
      assert(ElasticsearchCursorStateCodec.encode(state) == ElasticsearchCursorStateCodec.encode(state))
    }

    "carry no offset/from state in its encoded form" in {
      assert(!ElasticsearchCursorStateCodec.encode(state).contains("\"from\""))
      assert(!ElasticsearchCursorStateCodec.encode(state).contains("offset"))
    }

    "reject malformed JSON" in {
      ElasticsearchCursorStateCodec.decode("not json", expectedSearchAfterArity = 1) match {
        case Left(_: ElasticsearchCursorStateError.MalformedJson) => succeed
        case other                                                => fail(s"expected MalformedJson, got $other")
      }
    }

    "reject an unsupported protocol version" in {
      val badJson =
        Json
          .obj(
            "protocolVersion" -> Json.fromString("unknown-version"),
            "generationReference" -> Json.fromString(generationReference.value),
            "searchAfter"     -> Json.arr(),
          )
          .noSpaces

      ElasticsearchCursorStateCodec.decode(badJson, expectedSearchAfterArity = 0) match {
        case Left(ElasticsearchCursorStateError.UnsupportedProtocolVersion(actual)) => assert(actual == "unknown-version")
        case other                                                                  => fail(s"expected UnsupportedProtocolVersion, got $other")
      }
    }

    "reject an empty generation reference instead of treating it as an executable target" in {
      val badJson = Json.obj(
        "protocolVersion" -> Json.fromString(ElasticsearchSearchCompilerVersion.Current.value),
        "generationReference" -> Json.fromString(""),
        "searchAfter" -> Json.arr(),
      ).noSpaces
      ElasticsearchCursorStateCodec.decode(badJson, expectedSearchAfterArity = 0) match {
        case Left(ElasticsearchCursorStateError.InvalidGenerationReference(value)) => assert(value.isEmpty)
        case other => fail(s"expected InvalidGenerationReference, got $other")
      }
    }

    "reject an array value inside search_after" in {
      val badJson =
        Json
          .obj(
            "protocolVersion" -> Json.fromString(ElasticsearchSearchCompilerVersion.Current.value),
            "generationReference" -> Json.fromString(generationReference.value),
            "searchAfter"     -> Json.arr(Json.arr()),
          )
          .noSpaces

      ElasticsearchCursorStateCodec.decode(badJson, expectedSearchAfterArity = 1) match {
        case Left(ElasticsearchCursorStateError.UnsupportedSearchAfterValueShape(0)) => succeed
        case other                                                                   => fail(s"expected UnsupportedSearchAfterValueShape, got $other")
      }
    }

    "reject an object value inside search_after" in {
      val badJson =
        Json
          .obj(
            "protocolVersion" -> Json.fromString(ElasticsearchSearchCompilerVersion.Current.value),
            "generationReference" -> Json.fromString(generationReference.value),
            "searchAfter"     -> Json.arr(Json.obj()),
          )
          .noSpaces

      ElasticsearchCursorStateCodec.decode(badJson, expectedSearchAfterArity = 1) match {
        case Left(ElasticsearchCursorStateError.UnsupportedSearchAfterValueShape(0)) => succeed
        case other                                                                   => fail(s"expected UnsupportedSearchAfterValueShape, got $other")
      }
    }

    "reject a search_after arity mismatch against the compiled sort vector" in {
      val encoded = ElasticsearchCursorStateCodec.encode(state)
      ElasticsearchCursorStateCodec.decode(encoded, expectedSearchAfterArity = 2) match {
        case Left(ElasticsearchCursorStateError.SearchAfterArityMismatch(expected, actual)) =>
          assert(expected == 2)
          assert(actual == 4)
        case other => fail(s"expected SearchAfterArityMismatch, got $other")
      }
    }
  }
}
