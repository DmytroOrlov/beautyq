package leaderboard.search.gen2.qdrant

import io.circe.Json
import leaderboard.search.gen2.contract.*
import org.scalatest.wordspec.AnyWordSpec

final class QdrantCandidateRequestCompilerSpec extends AnyWordSpec {
  private val semanticText = SemanticQueryText.from("alpha beta").getOrElse(fail("expected semantic text"))

  "QdrantCandidateRequestCompiler" should {
    "compile typed hard constraints, vector input and retrieval settings" in {
      val plan = CandidatePlan(
        semanticText,
        Vector(
          PlannedConstraint.Terms(QdrantTestFixtures.active, Set(true)),
          PlannedConstraint.NumberRange(QdrantTestFixtures.count, RangeBounds(Bound.Inclusive(2), Bound.Unbounded)),
          PlannedConstraint.GeoDistanceFilter(QdrantTestFixtures.location, GeoPoint(BigDecimal("52.5"), BigDecimal("13.4")), Distance(BigDecimal("5000"))),
        ),
      )
      QdrantCandidateRequestCompiler.prepare(QdrantTestFixtures.policy, plan) match {
        case Left(error) => fail(s"expected candidate preparation, got $error")
        case Right(prepared) =>
          val embedding = QdrantEmbeddingResult.from(prepared.embeddingInput, Vector(0.1, 0.2, 0.3)).getOrElse(fail("expected valid embedding"))
          QdrantCandidateRequestCompiler.complete(prepared, embedding) match {
            case Left(error) => fail(s"expected request, got $error")
            case Right(request) =>
              assert(request.body.hcursor.downField("using").as[String] == Right("neutral-vector"))
              assert(request.body.hcursor.downField("limit").as[Int] == Right(4))
              assert(request.body.hcursor.downField("query").focus.contains(Json.arr(Json.fromBigDecimal(BigDecimal("0.1")), Json.fromBigDecimal(BigDecimal("0.2")), Json.fromBigDecimal(BigDecimal("0.3")))))
              assert(request.body.hcursor.downField("filter").downField("must").as[Vector[Json]].exists(_.size == 3))
          }
      }
    }

    "reject empty terms instead of emitting an ambiguous backend filter" in {
      val plan = CandidatePlan(semanticText, Vector(PlannedConstraint.Terms(QdrantTestFixtures.active, Set.empty)))
      QdrantCandidateRequestCompiler.prepare(QdrantTestFixtures.policy, plan) match {
        case Left(QdrantCandidateCompileError.EmptyTerms(0, fieldId)) => assert(fieldId == QdrantTestFixtures.active.id)
        case Left(error) => fail(s"expected empty terms error, got $error")
        case Right(value) => fail(s"expected rejection, got $value")
      }
    }
  }
}
