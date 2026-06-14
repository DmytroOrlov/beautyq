package leaderboard.search

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.eval.{
  EngineEvalAggregateReport,
  EngineEvalEngine,
  EngineEvalQueryReport,
  EngineEvalReportFormatter,
  EngineEvalResult,
  EngineExpectedRole,
}
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

final class EngineEvalReportFormatterSpec extends AnyWordSpec {

  private def variantId(slot: Int): MasterServiceOfferVariantId =
    UUID.fromString(f"00000000-0000-0000-0000-00000000${slot}%04x")

  private val v1: MasterServiceOfferVariantId = variantId(1)
  private val v2: MasterServiceOfferVariantId = variantId(2)
  private val v3: MasterServiceOfferVariantId = variantId(3)
  private val v4: MasterServiceOfferVariantId = variantId(4)
  private val v5: MasterServiceOfferVariantId = variantId(5)

  "EngineEvalReportFormatter.format" should {

    "format aggregate header and all aggregate counts" in {
      val es1 = EngineEvalResult(EngineEvalEngine.Elasticsearch, "q_fmt_a", List(v1, v2))
      val qdrant1 = EngineEvalResult(EngineEvalEngine.Qdrant, "q_fmt_a", List(v2, v3))
      val report1 = EngineEvalQueryReport.from(
        expectedRole = EngineExpectedRole.HybridMayImprove,
        expectedVariantIds = Set(v1, v2, v3),
        es = es1,
        qdrant = qdrant1,
      )

      val es2 = EngineEvalResult(EngineEvalEngine.Elasticsearch, "q_fmt_b", List(v4))
      val qdrant2 = EngineEvalResult(EngineEvalEngine.Qdrant, "q_fmt_b", List(v5))
      val report2 = EngineEvalQueryReport.from(
        expectedRole = EngineExpectedRole.EsShouldHandle,
        expectedVariantIds = Set(v4, v5),
        es = es2,
        qdrant = qdrant2,
      )

      val report = EngineEvalAggregateReport.from(List(report1, report2))
      val formatted = EngineEvalReportFormatter.format(report)

      assert(formatted.contains("EngineEval aggregate report"))
      assert(formatted.contains("queryCount: 2"))
      assert(formatted.contains(s"expectedVariantCount: ${report.aggregate.expectedVariantCount}"))
      assert(formatted.contains("esRecallCount:"))
      assert(formatted.contains("qdrantRecallCount:"))
      assert(formatted.contains("qdrantComplementCount:"))
      assert(formatted.contains("qdrantNoiseCount:"))
      assert(formatted.contains("overlapCount:"))
      assert(formatted.contains("simulatedHybridGainCount:"))
    }

    "preserve query order and ranked variant id order including duplicates" in {
      val es1 = EngineEvalResult(EngineEvalEngine.Elasticsearch, "q_fmt_1", List(v1, v1, v2))
      val qdrant1 = EngineEvalResult(EngineEvalEngine.Qdrant, "q_fmt_1", List(v3, v4))
      val report1 = EngineEvalQueryReport.from(
        expectedRole = EngineExpectedRole.HybridMayImprove,
        expectedVariantIds = Set(v1, v2, v3, v4),
        es = es1,
        qdrant = qdrant1,
      )

      val es2 = EngineEvalResult(EngineEvalEngine.Elasticsearch, "q_fmt_2", List(v5))
      val qdrant2 = EngineEvalResult(EngineEvalEngine.Qdrant, "q_fmt_2", List(v1))
      val report2 = EngineEvalQueryReport.from(
        expectedRole = EngineExpectedRole.EsShouldHandle,
        expectedVariantIds = Set(v1, v5),
        es = es2,
        qdrant = qdrant2,
      )

      val report = EngineEvalAggregateReport.from(List(report1, report2))
      val formatted = EngineEvalReportFormatter.format(report)

      assert(formatted.indexOf("q_fmt_1") < formatted.indexOf("q_fmt_2"))
      assert(formatted.contains(s"es: ${v1}, ${v1}, ${v2}"))
      assert(formatted.contains("qdrant:"))
      assert(formatted.contains("simulatedHybrid:"))
    }

    "format empty aggregate report without a queries section" in {
      val report = EngineEvalAggregateReport.from(Nil)
      val formatted = EngineEvalReportFormatter.format(report)

      val expected =
        """EngineEval aggregate report
          |queryCount: 0
          |expectedVariantCount: 0
          |esRecallCount: 0
          |qdrantRecallCount: 0
          |qdrantComplementCount: 0
          |qdrantNoiseCount: 0
          |overlapCount: 0
          |simulatedHybridGainCount: 0
          |""".stripMargin

      assert(formatted == expected)
      assert(!formatted.contains("queries:"))
      assert(!formatted.contains("roleAggregates:"))
    }

    "format role aggregates section with stable role order" in {
      val v1 = variantId(1)
      val v2 = variantId(2)
      val v3 = variantId(3)

      val es1 = EngineEvalResult(EngineEvalEngine.Elasticsearch, "q_role_1", List(v1))
      val qdrant1 = EngineEvalResult(EngineEvalEngine.Qdrant, "q_role_1", List(v2))
      val report1 = EngineEvalQueryReport.from(
        expectedRole = EngineExpectedRole.EsShouldHandle,
        expectedVariantIds = Set(v1, v2),
        es = es1,
        qdrant = qdrant1,
      )

      val es2 = EngineEvalResult(EngineEvalEngine.Elasticsearch, "q_role_2", List(v3))
      val qdrant2 = EngineEvalResult(EngineEvalEngine.Qdrant, "q_role_2", List())
      val report2 = EngineEvalQueryReport.from(
        expectedRole = EngineExpectedRole.QdrantShouldStaySilent,
        expectedVariantIds = Set(v3),
        es = es2,
        qdrant = qdrant2,
      )

      val report = EngineEvalAggregateReport.from(List(report1, report2))
      val formatted = EngineEvalReportFormatter.format(report)

      assert(formatted.contains("roleAggregates:"))
      assert(formatted.contains("EsShouldHandle"))
      assert(formatted.contains("QdrantShouldStaySilent"))

      val esIdx = formatted.indexOf("EsShouldHandle")
      val silentIdx = formatted.indexOf("QdrantShouldStaySilent")
      assert(esIdx < silentIdx)
    }

    "format role aggregates in stable role order across multiple roles" in {
      val v1 = variantId(1)
      val v2 = variantId(2)
      val v3 = variantId(3)
      val v4 = variantId(4)

      val tests = List(
        (EngineExpectedRole.HybridMayImprove, Set(v1), List(v1), List()),
        (EngineExpectedRole.QdrantMayComplement, Set(v2), List(), List(v2)),
        (EngineExpectedRole.EsShouldHandle, Set(v3), List(v3), List()),
        (EngineExpectedRole.QdrantShouldStaySilent, Set(v4), List(), List(v4)),
      )

      val queryReports = tests.zipWithIndex.map { case ((role, expectedIds, esIds, qIds), i) =>
        val es = EngineEvalResult(EngineEvalEngine.Elasticsearch, s"q_order_$i", esIds)
        val qdrant = EngineEvalResult(EngineEvalEngine.Qdrant, s"q_order_$i", qIds)
        EngineEvalQueryReport.from(role, expectedIds, es, qdrant)
      }

      val report = EngineEvalAggregateReport.from(queryReports)
      val formatted = EngineEvalReportFormatter.format(report)

      val rolesInOrder = EngineExpectedRole.stableOrder.map(_.toString)
      val indices = rolesInOrder.map(formatted.indexOf)
      assert(indices(0) < indices(1))
      assert(indices(1) < indices(2))
      assert(indices(2) < indices(3))
    }
  }
}
