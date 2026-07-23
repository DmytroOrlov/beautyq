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
          |  metadata: leaderboard.search.gen2.elasticsearch.ElasticsearchGenerationMetadata,
          |): leaderboard.search.gen2.elasticsearch.lifecycle.LifecycleResolvedElasticsearchGeneration =
          |  new leaderboard.search.gen2.elasticsearch.lifecycle.LifecycleResolvedElasticsearchGeneration(reference, target, metadata)""".stripMargin
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
          |    hits = Vector.empty,
          |    totalHits = 0L,
          |    totalRelation = "eq",
          |    facets = Vector.empty,
          |    diagnostics = leaderboard.search.gen2.elasticsearch.ElasticsearchResponseDiagnostics(false, 1, 1, 0),
          |    nextCursor = None,
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
          |](hits = Vector.empty, totalHits = 0L, totalRelation = "eq", facets = Vector.empty, diagnostics = leaderboard.search.gen2.elasticsearch.ElasticsearchResponseDiagnostics(false, 1, 1, 0), nextCursor = None)""".stripMargin
      )
      assertDoesNotCompile(
        """def forgeFullResult(): leaderboard.search.gen2.elasticsearch.ElasticsearchFullSearchResult[
          |  leaderboard.search.beautyq.gen2.contract.VariantSearchDocumentGen2,
          |  leaderboard.model.MasterServiceOfferVariantId
          |] = new leaderboard.search.gen2.elasticsearch.ElasticsearchFullSearchResult(null, Vector.empty)""".stripMargin
      )
      assertDoesNotCompile(
        """final class ForgedFullResult extends leaderboard.search.gen2.elasticsearch.ElasticsearchFullSearchResult[
          |  leaderboard.search.beautyq.gen2.contract.VariantSearchDocumentGen2,
          |  leaderboard.model.MasterServiceOfferVariantId
          |](null, Vector.empty)""".stripMargin
      )
      assertDoesNotCompile(
        """def forgeFullResultCopy(
          |  value: leaderboard.search.gen2.elasticsearch.ElasticsearchFullSearchResult[
          |    leaderboard.search.beautyq.gen2.contract.VariantSearchDocumentGen2,
          |    leaderboard.model.MasterServiceOfferVariantId
          |  ]
          |): Any = value.copy(page = value.page)""".stripMargin
      )
    }
  }

  "the bound baseline result boundary" should {
    "reject construction of BoundElasticsearchBaselineResult outside elasticsearch package" in {
      assertDoesNotCompile(
        """def forgeBoundResult(
          |  auth: leaderboard.search.gen2.elasticsearch.lifecycle.AuthorizedElasticsearchSearchRequest[leaderboard.search.beautyq.gen2.contract.VariantSearchDocumentGen2, leaderboard.model.MasterServiceOfferVariantId],
          |  result: leaderboard.search.gen2.elasticsearch.ElasticsearchFullSearchResult[leaderboard.search.beautyq.gen2.contract.VariantSearchDocumentGen2, leaderboard.model.MasterServiceOfferVariantId],
          |): leaderboard.search.gen2.elasticsearch.BoundElasticsearchBaselineResult[leaderboard.search.beautyq.gen2.contract.VariantSearchDocumentGen2, leaderboard.model.MasterServiceOfferVariantId] =
          |  new leaderboard.search.gen2.elasticsearch.BoundElasticsearchBaselineResult(auth, result)""".stripMargin
      )
    }
    "reject copy on BoundElasticsearchBaselineResult" in {
      assertDoesNotCompile(
        """def forgeBoundResultCopy(
          |  value: leaderboard.search.gen2.elasticsearch.BoundElasticsearchBaselineResult[leaderboard.search.beautyq.gen2.contract.VariantSearchDocumentGen2, leaderboard.model.MasterServiceOfferVariantId],
          |): Any = value.copy(authorizedRequest = value.authorizedRequest)""".stripMargin
      )
    }
    "reject subclassing BoundElasticsearchBaselineResult" in {
      assertDoesNotCompile(
        """final class ForgedBoundResult[Document, Id] extends leaderboard.search.gen2.elasticsearch.BoundElasticsearchBaselineResult[Document, Id](
          |  null: leaderboard.search.gen2.elasticsearch.lifecycle.AuthorizedElasticsearchSearchRequest[Document, Id],
          |  null: leaderboard.search.gen2.elasticsearch.ElasticsearchFullSearchResult[Document, Id]
          |)""".stripMargin
      )
    }
  }

  "the membership compilation boundary" should {
    "reject construction of CompiledElasticsearchBaselineMembershipRequest outside compiler package" in {
      assertDoesNotCompile(
        """def forgeCompiledRequest(
          |  auth: leaderboard.search.gen2.elasticsearch.lifecycle.AuthorizedElasticsearchSearchRequest[leaderboard.search.beautyq.gen2.contract.VariantSearchDocumentGen2, leaderboard.model.MasterServiceOfferVariantId],
          |  ids: Vector[leaderboard.model.MasterServiceOfferVariantId],
          |  body: io.circe.Json,
          |): leaderboard.search.gen2.elasticsearch.CompiledElasticsearchBaselineMembershipRequest[leaderboard.search.beautyq.gen2.contract.VariantSearchDocumentGen2, leaderboard.model.MasterServiceOfferVariantId] =
          |  new leaderboard.search.gen2.elasticsearch.CompiledElasticsearchBaselineMembershipRequest(auth, ids, body)""".stripMargin
      )
    }
  }

  "the membership evidence boundary" should {
    "reject calling BaselineMembershipResult.fromBackend from BeautyQ package" in {
      assertDoesNotCompile(
        """def forgeMembership(
          |  requested: Vector[leaderboard.model.MasterServiceOfferVariantId],
          |  matching: Vector[leaderboard.model.MasterServiceOfferVariantId],
          |): leaderboard.search.gen2.core.supplement.BaselineMembershipResult[leaderboard.model.MasterServiceOfferVariantId] =
          |  leaderboard.search.gen2.core.supplement.BaselineMembershipResult.fromBackend(requested, matching)""".stripMargin
      )
    }
    "reject direct construction of BaselineMembershipResult" in {
      assertDoesNotCompile(
        """def forgeMembershipDirect(
          |  requested: Vector[leaderboard.model.MasterServiceOfferVariantId],
          |  matching: Vector[leaderboard.model.MasterServiceOfferVariantId],
          |): leaderboard.search.gen2.core.supplement.BaselineMembershipResult[leaderboard.model.MasterServiceOfferVariantId] =
          |  new leaderboard.search.gen2.core.supplement.BaselineMembershipResult(requested, matching)""".stripMargin
      )
    }
  }
}
