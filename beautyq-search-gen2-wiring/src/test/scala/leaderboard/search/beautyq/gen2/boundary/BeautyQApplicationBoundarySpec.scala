package leaderboard.search.beautyq.gen2.boundary

import org.scalatest.wordspec.AnyWordSpec

/** Black-box construction proofs for the response projector boundary. */
final class BeautyQApplicationBoundarySpec extends AnyWordSpec {
  "the Gen2 response boundary" should {
    "keep application construction in the wiring owner" in {
      assertDoesNotCompile(
        """def forge(
          |  materialized: leaderboard.search.beautyq.gen2.materialization.MaterializedBeautyQVariantDocuments,
          |  elasticsearch: leaderboard.search.beautyq.gen2.wiring.BeautyQElasticsearchBaselineService,
          |  embedding: leaderboard.search.gen2.qdrant.QdrantQueryEmbeddingPort[leaderboard.search.beautyq.gen2.wiring.BeautyQEmbeddingRequestError],
          |  qdrant: leaderboard.search.gen2.qdrant.QdrantCandidateService,
          |): leaderboard.search.beautyq.gen2.wiring.BeautyQSearchApplication =
          |  new leaderboard.search.beautyq.gen2.wiring.BeautyQSearchApplication(materialized, elasticsearch, embedding, qdrant)""".stripMargin
      )
    }

    "reject direct response construction" in {
      assertDoesNotCompile(
        """def forge(): leaderboard.search.beautyq.gen2.wiring.BeautyQSearchResponseGen2Projector.Response =
          |  new leaderboard.search.beautyq.gen2.wiring.BeautyQSearchResponseGen2Projector.Response(
          |    hits = Vector.empty,
          |    totalHits = 0L,
          |    totalRelation = "eq",
          |    facets = Vector.empty,
          |    groups = Vector.empty,
          |    providerCarousel = Vector.empty,
          |    serviceIntentCarousel = Vector.empty,
          |    appliedFilters = Vector.empty,
          |    suppressedFilters = Vector.empty,
          |    nextCursor = None,
          |    supplementCount = 0,
          |    supplementStatus = leaderboard.search.beautyq.gen2.wiring.BeautyQSupplementStatus.NoAppend,
          |    supplementStatusCode = "no_append",
          |    ineligibilityReason = None,
          |    degradationReason = None,
          |    diagnostics = leaderboard.search.gen2.elasticsearch.ElasticsearchResponseDiagnostics(false, 0, 0, 0),
          |  )""".stripMargin
      )
    }

    "reject response copying and subclassing" in {
      assertDoesNotCompile(
        """def copy(value: leaderboard.search.beautyq.gen2.wiring.BeautyQSearchResponseGen2Projector.Response): Any =
          |  value.copy(hits = value.hits)""".stripMargin
      )
      assertDoesNotCompile(
        """final class Forged extends leaderboard.search.beautyq.gen2.wiring.BeautyQSearchResponseGen2Projector.Response(
          |  hits = Vector.empty,
          |  totalHits = 0L,
          |  totalRelation = "eq",
          |  facets = Vector.empty,
          |  groups = Vector.empty,
          |  providerCarousel = Vector.empty,
          |  serviceIntentCarousel = Vector.empty,
          |  appliedFilters = Vector.empty,
          |  suppressedFilters = Vector.empty,
          |  nextCursor = None,
          |  supplementCount = 0,
          |  supplementStatus = leaderboard.search.beautyq.gen2.wiring.BeautyQSupplementStatus.NoAppend,
          |  supplementStatusCode = "no_append",
          |  ineligibilityReason = None,
          |  degradationReason = None,
          |  diagnostics = leaderboard.search.gen2.elasticsearch.ElasticsearchResponseDiagnostics(false, 0, 0, 0),
          |)""".stripMargin
      )
    }
  }
}
