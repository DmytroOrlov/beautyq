package leaderboard.search.gen2.elasticsearch.lifecycle

import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.materialization.{ContentFingerprint, ProjectedDocumentsFingerprint, ProjectionFormatVersion}
import leaderboard.search.gen2.core.plan.*
import leaderboard.search.gen2.elasticsearch.*
import org.scalatest.wordspec.AnyWordSpec

/** Contract proofs for the untrusted-cursor/reference to lifecycle-authorized-target boundary. */
final class ElasticsearchSearchRequestAuthorizationSpec extends AnyWordSpec {
  import ElasticsearchTestFixtures.*

  private def plan(cursor: Option[SearchCursor] = None): SearchPlan[BookDocument] =
    SearchPlan(None, Vector.empty, Vector.empty, Vector.empty, PageRequest(cursor, PageSize.from(2).getOrElse(fail("expected a valid page size"))), Vector.empty, Vector.empty, PlanDiagnostics.empty)

  private def bound(plan: SearchPlan[BookDocument]): BoundSearchPlan[BookDocument] =
    SearchCursorEnvelope.bind(plan, CanonicalPlanView[BookDocument](fullPolicy.contractFingerprint)).getOrElse(fail("expected a bound plan"))

  private def resolved(reference: String, target: String, fingerprint: ContractFingerprint): LifecycleResolvedElasticsearchGeneration =
    new LifecycleResolvedElasticsearchGeneration(
      ElasticsearchGenerationReference(reference),
      ElasticsearchSearchTarget(target),
      ElasticsearchGenerationIdentity(
        ContentFingerprint("source"),
        ProjectedDocumentsFingerprint("projected"),
        fingerprint,
        ProjectionFormatVersion("book-projection-v1"),
        fullPolicy.index.compilerVersion,
        fullPolicy.index.indexFormatVersion,
      ),
    )

  "ElasticsearchSearchRequestAuthorization" should {
    "bind an active prepared request only to the lifecycle-selected target" in {
      val boundPlan = bound(plan())
      val prepared  = ElasticsearchSearchRequestCompiler.compile(fullPolicy, boundPlan).getOrElse(fail("expected a prepared request"))

      ElasticsearchSearchRequestAuthorization.authorize(prepared, resolved("trusted-generation", "books-trusted", boundPlan.identity.contractFingerprint)) match {
        case Right(authorized) =>
          assert(authorized.target == ElasticsearchSearchTarget("books-trusted"))
          assert(authorized.generationReference == ElasticsearchGenerationReference("trusted-generation"))
        case Left(error) => fail(s"expected lifecycle authorization, got $error")
      }
    }

    "keep a cursor-carried generation reference untrusted and reject a reference not resolved by lifecycle" in {
      val firstBound = bound(plan())
      val state = ElasticsearchCursorState(
        ElasticsearchSearchCompilerVersion.Current,
        ElasticsearchGenerationReference("untrusted-cursor-reference"),
        Vector(ElasticsearchSearchAfterValue.Text("isbn-1")),
      )
      val issued = SearchCursorEnvelope.issue(firstBound, ElasticsearchCursorStateCodec.encode(state))
      val secondBound = bound(plan(Some(SearchCursor.fromTransport(issued.opaqueValue))))
      val prepared = ElasticsearchSearchRequestCompiler.compile(fullPolicy, secondBound).getOrElse(fail("expected a prepared second-page request"))

      assert(prepared.generationRequirement == ElasticsearchGenerationRequirement.Pinned(ElasticsearchGenerationReference("untrusted-cursor-reference")))
      ElasticsearchSearchRequestAuthorization.authorize(prepared, resolved("trusted-generation", "books-trusted", secondBound.identity.contractFingerprint)) match {
        case Left(ElasticsearchSearchRequestAuthorizationError.GenerationReferenceMismatch(expected, actual)) =>
          assert(expected == ElasticsearchGenerationReference("untrusted-cursor-reference"))
          assert(actual == ElasticsearchGenerationReference("trusted-generation"))
        case other => fail(s"expected GenerationReferenceMismatch, got $other")
      }
    }

    "accept a pinned reference only when lifecycle resolves that exact reference" in {
      val firstBound = bound(plan())
      val state = ElasticsearchCursorState(ElasticsearchSearchCompilerVersion.Current, ElasticsearchGenerationReference("generation-a"), Vector(ElasticsearchSearchAfterValue.Text("isbn-1")))
      val issued = SearchCursorEnvelope.issue(firstBound, ElasticsearchCursorStateCodec.encode(state))
      val secondBound = bound(plan(Some(SearchCursor.fromTransport(issued.opaqueValue))))
      val prepared = ElasticsearchSearchRequestCompiler.compile(fullPolicy, secondBound).getOrElse(fail("expected a prepared second-page request"))

      ElasticsearchSearchRequestAuthorization.authorize(prepared, resolved("generation-a", "books-generation-a", secondBound.identity.contractFingerprint)) match {
        case Right(authorized) => assert(authorized.target == ElasticsearchSearchTarget("books-generation-a"))
        case Left(error)       => fail(s"expected authorized pinned request, got $error")
      }
    }
  }
}
