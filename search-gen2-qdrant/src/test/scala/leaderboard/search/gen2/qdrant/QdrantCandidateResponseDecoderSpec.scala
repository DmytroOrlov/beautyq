package leaderboard.search.gen2.qdrant

import io.circe.Json
import leaderboard.search.gen2.contract.*
import org.scalatest.wordspec.AnyWordSpec

final class QdrantCandidateResponseDecoderSpec extends AnyWordSpec {
  private val semanticText = SemanticQueryText.from("alpha").getOrElse(fail("expected semantic text"))
  private val prepared = QdrantCandidateRequestCompiler.prepare(QdrantTestFixtures.policy, CandidatePlan(semanticText, Vector.empty)).getOrElse(fail("expected prepared query"))
  private val embedding = QdrantEmbeddingResult.from(prepared.embeddingInput, Vector(0.1, 0.2, 0.3)).getOrElse(fail("expected embedding"))
  private val request = QdrantCandidateRequestCompiler.complete(prepared, embedding).getOrElse(fail("expected compiled request"))

  "QdrantCandidateResponseDecoder" should {
    "decode typed ids, preserve order and report duplicate diagnostics" in {
      val id = "00000000-0000-0000-0000-000000000001"
      val raw = Json.obj(
        "status" -> Json.fromString("ok"),
        "time" -> Json.fromBigDecimal(BigDecimal("0.012")),
        "result" -> Json.obj("points" -> Json.arr(
          Json.obj("id" -> Json.fromString(id), "score" -> Json.fromBigDecimal(BigDecimal("0.9"))),
          Json.obj("id" -> Json.fromString(id), "score" -> Json.fromBigDecimal(BigDecimal("0.8"))),
        )),
      )
      QdrantCandidateResponseDecoder.decode(QdrantTestFixtures.id, request, raw) match {
        case Left(error) => fail(s"expected response, got $error")
        case Right(result) =>
          assert(result.hits.size == 1)
          assert(result.hits.headOption.map(_.score).contains(0.9))
          assert(result.diagnostics == QdrantCandidateDiagnostics(Some(BigDecimal("0.012")), 2, 1, 1))
      }
    }

    "reject a response larger than the compiled retrieval limit" in {
      val points = Vector.tabulate(5) { index => Json.obj("id" -> Json.fromString(f"00000000-0000-0000-0000-00000000000${index + 1}%d"), "score" -> Json.fromBigDecimal(BigDecimal("0.5"))) }
      val raw = Json.obj("status" -> Json.fromString("ok"), "result" -> Json.obj("points" -> Json.fromValues(points)))
      QdrantCandidateResponseDecoder.decode(QdrantTestFixtures.id, request, raw) match {
        case Left(QdrantCandidateResponseError.ExcessiveCount(4, 5)) => succeed
        case Left(error) => fail(s"expected excessive count, got $error")
        case Right(value) => fail(s"expected rejection, got $value")
      }
    }

    "reject a non-object point" in {
      val raw = Json.obj("status" -> Json.fromString("ok"), "result" -> Json.obj("points" -> Json.arr(Json.fromString("bad"))))
      QdrantCandidateResponseDecoder.decode(QdrantTestFixtures.id, request, raw) match {
        case Left(QdrantCandidateResponseError.InvalidPoint(0, _)) => succeed
        case Left(error) => fail(s"expected invalid point, got $error")
        case Right(value) => fail(s"expected rejection, got $value")
      }
    }
  }
}
