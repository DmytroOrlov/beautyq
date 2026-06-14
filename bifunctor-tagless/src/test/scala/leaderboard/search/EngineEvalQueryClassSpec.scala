package leaderboard.search

import leaderboard.model.QueryFailure
import leaderboard.search.eval.EngineEvalQueryClass
import org.scalatest.wordspec.AnyWordSpec

final class EngineEvalQueryClassSpec extends AnyWordSpec {

  private val knownQueryTypes: Set[String] = Set(
    "ambiguous",
    "attribute",
    "attribute_heavy",
    "broad",
    "conversational",
    "direct",
    "english",
    "german",
    "hard_negative",
    "home_visit",
    "location",
    "mixed_language",
    "multi_intent",
    "negative_attribute",
    "numeric",
    "price",
    "synonym",
    "technical_token",
    "typo",
  )

  "EngineEvalQueryClass.fromQueryTypes" should {

    "cover every current BeautySearch eval inventory query type" in {
      assert(BeautySearchEvalInventory.evalSuite.queries.size == 63)

      BeautySearchEvalInventory.evalSuite.queries.foreach { query =>
        EngineEvalQueryClass.fromQueryTypes(query.queryTypes) match {
          case Right(_) =>
            (): Unit
          case Left(error) =>
            fail(s"Expected query ${query.id} queryTypes to be covered, got ${error.message}")
        }
      }
    }

    "lock the observed BeautySearch eval inventory query type set" in {
      val observed = BeautySearchEvalInventory.evalSuite.queries.flatMap(_.queryTypes).toSet

      assert(observed == knownQueryTypes)
    }

    "return stable ordered distinct classes and ignore language modifier tags" in {
      val result = EngineEvalQueryClass.fromQueryTypes(
        List(
          "german",
          "broad",
          "english",
          "price",
          "direct",
          "attribute",
          "broad",
        )
      )

      assert(
        result == Right(
          List(
            EngineEvalQueryClass.ExactService,
            EngineEvalQueryClass.StructuredFilter,
            EngineEvalQueryClass.PriceDuration,
            EngineEvalQueryClass.BroadIntent,
          )
        )
      )
    }

    "return no classes for empty query types" in {
      assert(EngineEvalQueryClass.fromQueryTypes(Nil) == Right(Nil))
    }

    "fail unknown query type tags instead of ignoring them" in {
      val result = EngineEvalQueryClass.fromQueryTypes(List("direct", "new_tag"))

      result match {
        case Left(QueryFailure.OperationFailure(operationName, message)) =>
          assert(operationName == "engine-eval-query-class")
          assert(message.contains("new_tag"))
        case other =>
          fail(s"Expected unknown tag failure naming new_tag, got $other")
      }
    }

    "classify q_broad_005 as price-duration plus broad-intent in stable order" in {
      val query = BeautySearchEvalInventory.evalSuite.queries.find(_.id == "q_broad_005") match {
        case Some(value) => value
        case None        => fail("Expected q_broad_005 in eval inventory")
      }

      assert(query.queryTypes == List("price", "broad"))
      assert(
        EngineEvalQueryClass.fromQueryTypes(query.queryTypes) == Right(
          List(
            EngineEvalQueryClass.PriceDuration,
            EngineEvalQueryClass.BroadIntent,
          )
        )
      )
    }
  }
}
