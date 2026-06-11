package leaderboard.search

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.eval.{BeautySearchEvalReport, EngineEvalComparisonMetrics, EngineEvalEngine, EngineEvalQueryReport, EngineEvalResult, EngineExpectedRole}
import leaderboard.search.qdrant.QdrantEmbeddingBenchmarkQueryResult
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

final class EngineEvalSpec extends AnyWordSpec {

  private def variantId(slot: Int): MasterServiceOfferVariantId =
    UUID.fromString(f"00000000-0000-0000-0000-00000000${slot}%04x")

  private val v1: MasterServiceOfferVariantId = variantId(1)
  private val v2: MasterServiceOfferVariantId = variantId(2)
  private val v3: MasterServiceOfferVariantId = variantId(3)
  private val v4: MasterServiceOfferVariantId = variantId(4)
  private val v5: MasterServiceOfferVariantId = variantId(5)

  "EngineEvalComparisonMetrics.from" should {

    "count ES recall as expected ids found in ES result" in {
      val es = EngineEvalResult(EngineEvalEngine.Elasticsearch, "q_001", List(v1, v2, v3))
      val qdrant = EngineEvalResult(EngineEvalEngine.Qdrant, "q_001", List(v1))
      val hybrid = EngineEvalResult(EngineEvalEngine.SimulatedHybrid, "q_001", List(v1, v2, v3))
      val expected: Set[MasterServiceOfferVariantId] = Set(v1, v2, v4)

      val metrics = EngineEvalComparisonMetrics.from(
        expectedRole = EngineExpectedRole.EsShouldHandle,
        expectedVariantIds = expected,
        es = es,
        qdrant = qdrant,
        simulatedHybrid = hybrid,
      )

      assert(metrics.esRecallCount == 2)
    }

    "count Qdrant recall as expected ids found in Qdrant result" in {
      val es = EngineEvalResult(EngineEvalEngine.Elasticsearch, "q_002", List(v1))
      val qdrant = EngineEvalResult(EngineEvalEngine.Qdrant, "q_002", List(v2, v3, v4))
      val hybrid = EngineEvalResult(EngineEvalEngine.SimulatedHybrid, "q_002", List(v1, v2, v3, v4))
      val expected: Set[MasterServiceOfferVariantId] = Set(v2, v3, v5)

      val metrics = EngineEvalComparisonMetrics.from(
        expectedRole = EngineExpectedRole.QdrantMayComplement,
        expectedVariantIds = expected,
        es = es,
        qdrant = qdrant,
        simulatedHybrid = hybrid,
      )

      assert(metrics.qdrantRecallCount == 2)
    }

    "count Qdrant complement as expected ids found by Qdrant but missed by ES" in {
      val es = EngineEvalResult(EngineEvalEngine.Elasticsearch, "q_003", List(v1))
      val qdrant = EngineEvalResult(EngineEvalEngine.Qdrant, "q_003", List(v1, v2, v3, v4))
      val hybrid = EngineEvalResult(EngineEvalEngine.SimulatedHybrid, "q_003", List(v1, v2, v3, v4))
      val expected: Set[MasterServiceOfferVariantId] = Set(v1, v2, v3, v4, v5)

      val metrics = EngineEvalComparisonMetrics.from(
        expectedRole = EngineExpectedRole.HybridMayImprove,
        expectedVariantIds = expected,
        es = es,
        qdrant = qdrant,
        simulatedHybrid = hybrid,
      )

      assert(metrics.qdrantComplementCount == 3)
    }

    "count overlap as ids returned by both ES and Qdrant" in {
      val es = EngineEvalResult(EngineEvalEngine.Elasticsearch, "q_004", List(v1, v2, v3, v4))
      val qdrant = EngineEvalResult(EngineEvalEngine.Qdrant, "q_004", List(v2, v3, v4, v5))
      val hybrid = EngineEvalResult(EngineEvalEngine.SimulatedHybrid, "q_004", List(v1, v2, v3, v4, v5))
      val expected: Set[MasterServiceOfferVariantId] = Set(v1, v2, v3, v4, v5)

      val metrics = EngineEvalComparisonMetrics.from(
        expectedRole = EngineExpectedRole.HybridMayImprove,
        expectedVariantIds = expected,
        es = es,
        qdrant = qdrant,
        simulatedHybrid = hybrid,
      )

      assert(metrics.overlapCount == 3)
    }

    "count simulated hybrid gain as expected ids found by simulated hybrid but missed by ES" in {
      val es = EngineEvalResult(EngineEvalEngine.Elasticsearch, "q_005", List(v1, v2))
      val qdrant = EngineEvalResult(EngineEvalEngine.Qdrant, "q_005", List(v1, v2, v5))
      val hybrid = EngineEvalResult(EngineEvalEngine.SimulatedHybrid, "q_005", List(v1, v2, v3, v4, v5))
      val expected: Set[MasterServiceOfferVariantId] = Set(v1, v2, v3, v4, v5)

      val metrics = EngineEvalComparisonMetrics.from(
        expectedRole = EngineExpectedRole.HybridMayImprove,
        expectedVariantIds = expected,
        es = es,
        qdrant = qdrant,
        simulatedHybrid = hybrid,
      )

      assert(metrics.simulatedHybridGainCount == 3)
    }

    "report Qdrant noise as zero for QdrantMayComplement role" in {
      val es = EngineEvalResult(EngineEvalEngine.Elasticsearch, "q_006", List(v1))
      val qdrant = EngineEvalResult(EngineEvalEngine.Qdrant, "q_006", List(v1, v2, v3, v4, v5))
      val hybrid = EngineEvalResult(EngineEvalEngine.SimulatedHybrid, "q_006", List(v1, v2, v3, v4, v5))
      val expected: Set[MasterServiceOfferVariantId] = Set(v1, v2, v3, v4, v5)

      val metrics = EngineEvalComparisonMetrics.from(
        expectedRole = EngineExpectedRole.QdrantMayComplement,
        expectedVariantIds = expected,
        es = es,
        qdrant = qdrant,
        simulatedHybrid = hybrid,
      )

      assert(metrics.qdrantNoiseCount == 0)
    }

    "report Qdrant noise as all Qdrant returned ids for QdrantShouldStaySilent role" in {
      val es = EngineEvalResult(EngineEvalEngine.Elasticsearch, "q_007", List(v1, v2, v3))
      val qdrant = EngineEvalResult(EngineEvalEngine.Qdrant, "q_007", List(v4, v5))
      val hybrid = EngineEvalResult(EngineEvalEngine.SimulatedHybrid, "q_007", List(v1, v2, v3))
      val expected: Set[MasterServiceOfferVariantId] = Set(v1, v2, v3)

      val metrics = EngineEvalComparisonMetrics.from(
        expectedRole = EngineExpectedRole.QdrantShouldStaySilent,
        expectedVariantIds = expected,
        es = es,
        qdrant = qdrant,
        simulatedHybrid = hybrid,
      )

      assert(metrics.qdrantNoiseCount == 2)
    }

    "not inflate overlap, complement, recall, or hybrid gain metrics when Qdrant returns duplicate ids" in {
      val es = EngineEvalResult(EngineEvalEngine.Elasticsearch, "q_010", List(v1, v1, v2, v3, v3))
      val qdrant = EngineEvalResult(EngineEvalEngine.Qdrant, "q_010", List(v1, v1, v2, v3, v4, v4, v5, v5))
      val hybrid = EngineEvalResult(EngineEvalEngine.SimulatedHybrid, "q_010", List(v1, v2, v2, v3, v4, v5, v5))
      val expected: Set[MasterServiceOfferVariantId] = Set(v1, v2, v3, v4, v5)

      val metrics = EngineEvalComparisonMetrics.from(
        expectedRole = EngineExpectedRole.HybridMayImprove,
        expectedVariantIds = expected,
        es = es,
        qdrant = qdrant,
        simulatedHybrid = hybrid,
      )

      assert(metrics.esRecallCount == 3)
      assert(metrics.qdrantRecallCount == 5)
      assert(metrics.qdrantComplementCount == 2)
      assert(metrics.overlapCount == 3)
      assert(metrics.simulatedHybridGainCount == 2)
    }

    "count distinct Qdrant ids once for QdrantShouldStaySilent even with duplicates" in {
      val es = EngineEvalResult(EngineEvalEngine.Elasticsearch, "q_009", List(v1, v2, v3))
      val qdrant = EngineEvalResult(EngineEvalEngine.Qdrant, "q_009", List(v4, v4, v5))
      val hybrid = EngineEvalResult(EngineEvalEngine.SimulatedHybrid, "q_009", List(v1, v2, v3))
      val expected: Set[MasterServiceOfferVariantId] = Set(v1, v2, v3)

      val metrics = EngineEvalComparisonMetrics.from(
        expectedRole = EngineExpectedRole.QdrantShouldStaySilent,
        expectedVariantIds = expected,
        es = es,
        qdrant = qdrant,
        simulatedHybrid = hybrid,
      )

      assert(metrics.qdrantNoiseCount == 2)
    }

    "produce zero metrics for empty expected ids and empty results" in {
      val es = EngineEvalResult(EngineEvalEngine.Elasticsearch, "q_008", Nil)
      val qdrant = EngineEvalResult(EngineEvalEngine.Qdrant, "q_008", Nil)
      val hybrid = EngineEvalResult(EngineEvalEngine.SimulatedHybrid, "q_008", Nil)
      val expected: Set[MasterServiceOfferVariantId] = Set.empty

      val metrics = EngineEvalComparisonMetrics.from(
        expectedRole = EngineExpectedRole.EsShouldHandle,
        expectedVariantIds = expected,
        es = es,
        qdrant = qdrant,
        simulatedHybrid = hybrid,
      )

      assert(metrics.esRecallCount == 0)
      assert(metrics.qdrantRecallCount == 0)
      assert(metrics.qdrantComplementCount == 0)
      assert(metrics.qdrantNoiseCount == 0)
      assert(metrics.overlapCount == 0)
      assert(metrics.simulatedHybridGainCount == 0)
    }
  }

  "EngineEvalResult.fromElasticsearchEvalReport" should {

    "normalize a BeautySearchEvalReport into an Elasticsearch EngineEvalResult" in {
      val report = BeautySearchEvalReport(
        queryId = "q_es_01",
        query = "manicure near me",
        score = 42,
        failedAssertions = Nil,
        topVariantIds = List(v1, v2, v3),
        topProviderLocationIds = Nil,
        topServiceIds = Nil,
      )

      val result = EngineEvalResult.fromElasticsearchEvalReport(report)

      assert(result.engine == EngineEvalEngine.Elasticsearch)
      assert(result.queryId == "q_es_01")
      assert(result.variantIds == List(v1, v2, v3))
    }
  }

  "EngineEvalResult.fromQdrantBenchmarkResult" should {

    "normalize a QdrantEmbeddingBenchmarkQueryResult into a Qdrant EngineEvalResult" in {
      val benchmarkResult = QdrantEmbeddingBenchmarkQueryResult(
        candidateId = "candidate_1",
        queryId = "q_qdrant_01",
        queryText = "gel nails",
        topVariantIds = List(v4, v5),
        topProviderIds = Nil,
        topServiceIds = Nil,
        scores = List(0.95, 0.88),
      )

      val result = EngineEvalResult.fromQdrantBenchmarkResult(benchmarkResult)

      assert(result.engine == EngineEvalEngine.Qdrant)
      assert(result.queryId == "q_qdrant_01")
      assert(result.variantIds == List(v4, v5))
    }
  }

  "EngineEvalResult normalizers" should {

    "preserve duplicate ids and input order during normalization" in {
      val report = BeautySearchEvalReport(
        queryId = "q_dup_01",
        query = "pedicure",
        score = 0,
        failedAssertions = Nil,
        topVariantIds = List(v1, v1, v2, v3, v3),
        topProviderLocationIds = Nil,
        topServiceIds = Nil,
      )

      val benchmarkResult = QdrantEmbeddingBenchmarkQueryResult(
        candidateId = "candidate_dup",
        queryId = "q_dup_02",
        queryText = "hair color",
        topVariantIds = List(v5, v4, v4, v3),
        topProviderIds = Nil,
        topServiceIds = Nil,
        scores = List(0.9, 0.8, 0.7, 0.6),
      )

      val esResult = EngineEvalResult.fromElasticsearchEvalReport(report)
      val qdrantResult = EngineEvalResult.fromQdrantBenchmarkResult(benchmarkResult)

      assert(esResult.variantIds == List(v1, v1, v2, v3, v3))
      assert(qdrantResult.variantIds == List(v5, v4, v4, v3))
    }
  }

  "EngineEvalResult.simulatedHybridFrom" should {

    "create a SimulatedHybrid result using the ES query id" in {
      val es = EngineEvalResult(EngineEvalEngine.Elasticsearch, "q_hybrid_01", List(v1, v2))
      val qdrant = EngineEvalResult(EngineEvalEngine.Qdrant, "q_hybrid_01", List(v3))

      val hybrid = EngineEvalResult.simulatedHybridFrom(es, qdrant)

      assert(hybrid.engine == EngineEvalEngine.SimulatedHybrid)
      assert(hybrid.queryId == "q_hybrid_01")
      assert(hybrid.variantIds == List(v1, v2, v3))
    }

    "keep ES ids first and append only Qdrant complements in Qdrant order" in {
      val es = EngineEvalResult(EngineEvalEngine.Elasticsearch, "q_hybrid_02", List(v1, v2, v4))
      val qdrant = EngineEvalResult(EngineEvalEngine.Qdrant, "q_hybrid_02", List(v2, v3, v5, v1))

      val hybrid = EngineEvalResult.simulatedHybridFrom(es, qdrant)

      assert(hybrid.variantIds == List(v1, v2, v4, v3, v5))
    }

    "remove duplicate ids while preserving first occurrence order across ES then Qdrant" in {
      val es = EngineEvalResult(EngineEvalEngine.Elasticsearch, "q_hybrid_03", List(v1, v1, v2, v4))
      val qdrant = EngineEvalResult(EngineEvalEngine.Qdrant, "q_hybrid_03", List(v2, v3, v3, v5, v1))

      val hybrid = EngineEvalResult.simulatedHybridFrom(es, qdrant)

      assert(hybrid.variantIds == List(v1, v2, v4, v3, v5))
    }
  }

  "EngineEvalQueryReport.from" should {

    "build a report containing ES, Qdrant, simulated hybrid, and metrics" in {
      val es = EngineEvalResult(EngineEvalEngine.Elasticsearch, "q_report_01", List(v1, v2))
      val qdrant = EngineEvalResult(EngineEvalEngine.Qdrant, "q_report_01", List(v2, v3, v4))
      val expected: Set[MasterServiceOfferVariantId] = Set(v1, v2, v3)

      val report = EngineEvalQueryReport.from(
        expectedRole = EngineExpectedRole.HybridMayImprove,
        expectedVariantIds = expected,
        es = es,
        qdrant = qdrant,
      )

      assert(report.es.variantIds == List(v1, v2))
      assert(report.qdrant.variantIds == List(v2, v3, v4))
      assert(report.simulatedHybrid.variantIds == List(v1, v2, v3, v4))
      assert(report.metrics.esRecallCount == 2)
      assert(report.metrics.qdrantRecallCount == 2)
      assert(report.metrics.qdrantComplementCount == 1)
      assert(report.metrics.simulatedHybridGainCount == 1)
    }

    "use the ES query id and preserve expected role and expected ids" in {
      val es = EngineEvalResult(EngineEvalEngine.Elasticsearch, "q_report_es", List(v1, v2))
      val qdrant = EngineEvalResult(EngineEvalEngine.Qdrant, "q_report_qdrant", List(v2, v3))
      val expected: Set[MasterServiceOfferVariantId] = Set(v1, v2, v3)

      val report = EngineEvalQueryReport.from(
        expectedRole = EngineExpectedRole.HybridMayImprove,
        expectedVariantIds = expected,
        es = es,
        qdrant = qdrant,
      )

      assert(report.queryId == "q_report_es")
      assert(report.expectedRole == EngineExpectedRole.HybridMayImprove)
      assert(report.expectedVariantIds == expected)
    }

    "pass expected role into metrics, including Qdrant silence/noise behavior" in {
      val es = EngineEvalResult(EngineEvalEngine.Elasticsearch, "q_report_noise", List(v1, v2, v3))
      val qdrant = EngineEvalResult(EngineEvalEngine.Qdrant, "q_report_noise", List(v4, v4, v5))
      val expected: Set[MasterServiceOfferVariantId] = Set(v1, v2, v3)

      val report = EngineEvalQueryReport.from(
        expectedRole = EngineExpectedRole.QdrantShouldStaySilent,
        expectedVariantIds = expected,
        es = es,
        qdrant = qdrant,
      )

      assert(report.metrics.qdrantNoiseCount == 2)
    }
  }
}
