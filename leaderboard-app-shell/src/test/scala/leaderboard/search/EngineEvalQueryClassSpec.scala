package leaderboard.search

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.model.QueryFailure
import leaderboard.search.eval.{EngineEvalEngine, EngineEvalQueryClass, EngineEvalQueryClassAggregateMetrics, EngineEvalQueryClassBreakdown, EngineEvalQueryReport, EngineEvalResult, EngineExpectedRole}
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

final class EngineEvalQueryClassSpec extends AnyWordSpec {

  private def variantId(slot: Int): MasterServiceOfferVariantId =
    UUID.fromString(f"00000000-0000-0000-0000-00000000${slot}%04x")

  private val v1: MasterServiceOfferVariantId = variantId(1)
  private val v2: MasterServiceOfferVariantId = variantId(2)
  private val v3: MasterServiceOfferVariantId = variantId(3)
  private val v4: MasterServiceOfferVariantId = variantId(4)
  private val v5: MasterServiceOfferVariantId = variantId(5)

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

  private def report(
    queryId: String,
    expectedRole: EngineExpectedRole = EngineExpectedRole.HybridMayImprove,
    expectedVariantIds: Set[MasterServiceOfferVariantId] = Set(v1, v2),
    esVariantIds: List[MasterServiceOfferVariantId] = List(v1),
    qdrantVariantIds: List[MasterServiceOfferVariantId] = List(v2, v3),
  ): EngineEvalQueryReport =
    EngineEvalQueryReport.from(
      expectedRole = expectedRole,
      expectedVariantIds = expectedVariantIds,
      es = EngineEvalResult(EngineEvalEngine.Elasticsearch, queryId, esVariantIds),
      qdrant = EngineEvalResult(EngineEvalEngine.Qdrant, queryId, qdrantVariantIds),
    )

  "EngineEvalQueryClass.fromQueryTypes" should {

    "cover every current BeautySearch eval inventory query type" in {
      assert(BeautySearchEvalInventory.evalSuite.queries.size == 89)

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

    "classify q_price_003 = under 50 as a pure price-duration class without broad/semantic class" in {
      val query = BeautySearchEvalInventory.evalSuite.queries.find(_.id == "q_price_003") match {
        case Some(value) => value
        case None        => fail("Expected q_price_003 in eval inventory")
      }

      assert(query.query == "under 50")
      assert(query.queryTypes == List("price", "english"))
      assert(
        EngineEvalQueryClass.fromQueryTypes(query.queryTypes) == Right(
          List(EngineEvalQueryClass.PriceDuration)
        )
      )
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

  "EngineEvalQueryClassBreakdown.from" should {

    "aggregate a query into multiple class buckets" in {
      val queryReport = report("q_multi")

      val result = EngineEvalQueryClassBreakdown.from(
        queryReports = List(queryReport),
        classesByQueryId = Map(
          "q_multi" -> List(
            EngineEvalQueryClass.ExactService,
            EngineEvalQueryClass.BroadIntent,
          )
        ),
      )

      val expectedMetrics = EngineEvalQueryClassAggregateMetrics(
        queryClass = EngineEvalQueryClass.ExactService,
        queryCount = 1,
        expectedVariantCount = 2,
        esRecallCount = 1,
        qdrantRecallCount = 1,
        qdrantComplementCount = 1,
        qdrantNoiseCount = 0,
        overlapCount = 0,
        simulatedHybridGainCount = 1,
      )

      assert(
        result == Right(
          EngineEvalQueryClassBreakdown(
            List(
              expectedMetrics,
              expectedMetrics.copy(queryClass = EngineEvalQueryClass.BroadIntent),
            )
          )
        )
      )
    }

    "deduplicate repeated classes for the same query" in {
      val queryReport = report("q_dedup")

      val result = EngineEvalQueryClassBreakdown.from(
        queryReports = List(queryReport),
        classesByQueryId = Map(
          "q_dedup" -> List(
            EngineEvalQueryClass.Category,
            EngineEvalQueryClass.Category,
          )
        ),
      )

      assert(
        result == Right(
          EngineEvalQueryClassBreakdown(
            List(
              EngineEvalQueryClassAggregateMetrics(
                queryClass = EngineEvalQueryClass.Category,
                queryCount = 1,
                expectedVariantCount = 2,
                esRecallCount = 1,
                qdrantRecallCount = 1,
                qdrantComplementCount = 1,
                qdrantNoiseCount = 0,
                overlapCount = 0,
                simulatedHybridGainCount = 1,
              )
            )
          )
        )
      )
    }

    "return class buckets in EngineEvalQueryClass stable order" in {
      val mixedReport = report(
        queryId = "q_mixed",
        expectedVariantIds = Set(v1, v2, v3),
        esVariantIds = List(v1, v2),
        qdrantVariantIds = List(v3, v4),
      )
      val categoryReport = report(
        queryId = "q_category",
        expectedVariantIds = Set(v4, v5),
        esVariantIds = List(v4),
        qdrantVariantIds = List(v5),
      )

      val result = EngineEvalQueryClassBreakdown.from(
        queryReports = List(mixedReport, categoryReport),
        classesByQueryId = Map(
          "q_mixed"    -> List(EngineEvalQueryClass.Mixed),
          "q_category" -> List(EngineEvalQueryClass.Category),
        ),
      )

      result match {
        case Right(breakdown) =>
          assert(breakdown.byClass.map(_.queryClass) == List(EngineEvalQueryClass.Category, EngineEvalQueryClass.Mixed))
        case other =>
          fail(s"Expected stable ordered class breakdown, got $other")
      }
    }

    "fail when a query report is missing from the sidecar and name the query id" in {
      val result = EngineEvalQueryClassBreakdown.from(
        queryReports = List(report("q_missing_sidecar")),
        classesByQueryId = Map.empty,
      )

      result match {
        case Left(QueryFailure.OperationFailure(operationName, message)) =>
          assert(operationName == "engine-eval-query-class-breakdown")
          assert(message.contains("q_missing_sidecar"))
        case other =>
          fail(s"Expected missing sidecar failure naming q_missing_sidecar, got $other")
      }
    }

    "allow an empty class list and contribute the query to no bucket" in {
      val result = EngineEvalQueryClassBreakdown.from(
        queryReports = List(report("q_empty_classes")),
        classesByQueryId = Map("q_empty_classes" -> Nil),
      )

      assert(result == Right(EngineEvalQueryClassBreakdown(Nil)))
    }
  }
}
