package leaderboard.search.beautyq.gen2.wiring

import org.scalatest.wordspec.AnyWordSpec

/** Compile-negative boundary proofs: BeautyQ wiring can carry an authorized request, but cannot invent
  * lifecycle generations or construct/copy/subclass the lifecycle-owned executable result. */
final class BeautyQElasticsearchBaselineBoundarySpec extends AnyWordSpec {
  "the lifecycle authorization boundary" should {
    "reject construction and mutation outside the lifecycle owner" in {
      assertDoesNotCompile(
        """def forgeGeneration(
          |  reference: leaderboard.search.gen2.elasticsearch.ElasticsearchGenerationReference,
          |  target: leaderboard.search.gen2.elasticsearch.ElasticsearchSearchTarget,
          |  identity: leaderboard.search.gen2.elasticsearch.ElasticsearchGenerationIdentity,
          |): leaderboard.search.gen2.elasticsearch.lifecycle.LifecycleResolvedElasticsearchGeneration =
          |  new leaderboard.search.gen2.elasticsearch.lifecycle.LifecycleResolvedElasticsearchGeneration(reference, target, identity)""".stripMargin
      )
      assertDoesNotCompile(
        """def forgeAuthorized(
          |  prepared: leaderboard.search.gen2.elasticsearch.PreparedElasticsearchSearchRequest[leaderboard.search.beautyq.gen2.contract.VariantSearchDocumentGen2, leaderboard.model.MasterServiceOfferVariantId],
          |  generation: leaderboard.search.gen2.elasticsearch.lifecycle.LifecycleResolvedElasticsearchGeneration,
          |): leaderboard.search.gen2.elasticsearch.lifecycle.AuthorizedElasticsearchSearchRequest[leaderboard.search.beautyq.gen2.contract.VariantSearchDocumentGen2, leaderboard.model.MasterServiceOfferVariantId] =
          |  new leaderboard.search.gen2.elasticsearch.lifecycle.ElasticsearchSearchRequestAuthorization.AuthorizedElasticsearchSearchRequest(prepared, generation)""".stripMargin
      )
      assertDoesNotCompile(
        """final class ForgedAuthorized(
          |  prepared: leaderboard.search.gen2.elasticsearch.PreparedElasticsearchSearchRequest[leaderboard.search.beautyq.gen2.contract.VariantSearchDocumentGen2, leaderboard.model.MasterServiceOfferVariantId],
          |  generation: leaderboard.search.gen2.elasticsearch.lifecycle.LifecycleResolvedElasticsearchGeneration,
          |) extends leaderboard.search.gen2.elasticsearch.lifecycle.ElasticsearchSearchRequestAuthorization.AuthorizedElasticsearchSearchRequest[leaderboard.search.beautyq.gen2.contract.VariantSearchDocumentGen2, leaderboard.model.MasterServiceOfferVariantId](prepared, generation)""".stripMargin
      )
      assertDoesNotCompile(
        """def forgeCopy(
          |  value: leaderboard.search.gen2.elasticsearch.lifecycle.AuthorizedElasticsearchSearchRequest[leaderboard.search.beautyq.gen2.contract.VariantSearchDocumentGen2, leaderboard.model.MasterServiceOfferVariantId],
          |): Any = value.copy(prepared = value.prepared)""".stripMargin
      )
      assertDoesNotCompile(
        """def forgePage(): leaderboard.search.gen2.elasticsearch.BaselineSearchPage[leaderboard.search.beautyq.gen2.contract.VariantSearchDocumentGen2, leaderboard.model.MasterServiceOfferVariantId] =
          |  new leaderboard.search.gen2.elasticsearch.ElasticsearchSearchResponseDecoder.BaselineSearchPage(
          |    Vector.empty,
          |    0L,
          |    Vector.empty,
          |    leaderboard.search.gen2.elasticsearch.ElasticsearchResponseDiagnostics(false, 1, 1, 0),
          |    None,
          |  )""".stripMargin
      )
      assertDoesNotCompile(
        """def forgePageCopy(
          |  value: leaderboard.search.gen2.elasticsearch.BaselineSearchPage[leaderboard.search.beautyq.gen2.contract.VariantSearchDocumentGen2, leaderboard.model.MasterServiceOfferVariantId],
          |): Any = value.copy(hits = value.hits)""".stripMargin
      )
      assertDoesNotCompile(
        """final class ForgedPage extends leaderboard.search.gen2.elasticsearch.ElasticsearchSearchResponseDecoder.BaselineSearchPage[
          |  leaderboard.search.beautyq.gen2.contract.VariantSearchDocumentGen2,
          |  leaderboard.model.MasterServiceOfferVariantId
          |](Vector.empty, 0L, Vector.empty, leaderboard.search.gen2.elasticsearch.ElasticsearchResponseDiagnostics(false, 1, 1, 0), None)""".stripMargin
      )
    }
  }
}
