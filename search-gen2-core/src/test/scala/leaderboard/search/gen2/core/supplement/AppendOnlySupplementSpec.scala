package leaderboard.search.gen2.core.supplement

import org.scalatest.wordspec.AnyWordSpec

final case class ArticleCandidate(id: String, score: BigDecimal)

final class AppendOnlySupplementSpec extends AnyWordSpec {

  "AppendOnlySupplementPolicy" should {
    "accept maxAppended = 1" in {
      AppendOnlySupplementPolicy.create(1) match {
        case Right(policy) => assert(policy.maxAppended == 1)
        case Left(_) => fail("expected success for maxAppended=1")
      }
    }

    "accept another positive value" in {
      AppendOnlySupplementPolicy.create(5) match {
        case Right(policy) => assert(policy.maxAppended == 5)
        case Left(_) => fail("expected success for maxAppended=5")
      }
    }

    "reject maxAppended = 0" in {
      AppendOnlySupplementPolicy.create(0) match {
        case Left(AppendOnlySupplementPolicyError.InvalidMaxAppended(0)) =>
        case other => fail(s"expected InvalidMaxAppended(0), got $other")
      }
    }

    "reject negative maxAppended" in {
      AppendOnlySupplementPolicy.create(-3) match {
        case Left(AppendOnlySupplementPolicyError.InvalidMaxAppended(-3)) =>
        case other => fail(s"expected InvalidMaxAppended(-3), got $other")
      }
    }

    "return MembershipInputMismatch on order mismatch" in {
      val policy = AppendOnlySupplementPolicy.create(3).getOrElse(fail("expected valid policy"))
      val candidates = Vector(ArticleCandidate("a", 1.0), ArticleCandidate("b", 2.0))
      val baselinePageIds = Vector.empty[String]
      val membership = BaselineMembershipResult.fromBackend(
        Vector("b", "a"),
        Vector.empty[String],
      )
      policy.select(baselinePageIds, candidates, membership)(_.id) match {
        case Left(AppendOnlySupplementSelectionError.MembershipInputMismatch(candidateIds, requestedIds)) =>
          assert(candidateIds == Vector("a", "b"))
          assert(requestedIds == Vector("b", "a"))
        case other => fail(s"expected MembershipInputMismatch, got $other")
      }
    }

    "return MembershipInputMismatch on value mismatch" in {
      val policy = AppendOnlySupplementPolicy.create(3).getOrElse(fail("expected valid policy"))
      val candidates = Vector(ArticleCandidate("a", 1.0))
      val baselinePageIds = Vector.empty[String]
      val membership = BaselineMembershipResult.fromBackend(
        Vector("b"),
        Vector.empty[String],
      )
      policy.select(baselinePageIds, candidates, membership)(_.id) match {
        case Left(AppendOnlySupplementSelectionError.MembershipInputMismatch(_, _)) =>
        case other => fail(s"expected MembershipInputMismatch, got $other")
      }
    }

    "remove current-page duplicates" in {
      val policy = AppendOnlySupplementPolicy.create(3).getOrElse(fail("expected valid policy"))
      val candidates = Vector(ArticleCandidate("a", 1.0), ArticleCandidate("b", 2.0))
      val baselinePageIds = Vector("a")
      val membership = BaselineMembershipResult.fromBackend(
        Vector("a", "b"),
        Vector.empty[String],
      )
      val result = policy.select(baselinePageIds, candidates, membership)(_.id).getOrElse(fail("expected success"))
      assert(result.currentPageDuplicateIds == Vector("a"))
      assert(result.appended.map(_.id) == Vector("b"))
      assert(result.baselineMemberIds.isEmpty)
    }

    "remove complete-baseline members not on current page" in {
      val policy = AppendOnlySupplementPolicy.create(3).getOrElse(fail("expected valid policy"))
      val candidates = Vector(ArticleCandidate("a", 1.0), ArticleCandidate("b", 2.0))
      val baselinePageIds = Vector("a")
      val membership = BaselineMembershipResult.fromBackend(
        Vector("a", "b"),
        Vector("b"),
      )
      val result = policy.select(baselinePageIds, candidates, membership)(_.id).getOrElse(fail("expected success"))
      assert(result.baselineMemberIds == Vector("b"))
      assert(result.appended.isEmpty)
    }

    "give current-page classification wins when an ID is also a complete-baseline member" in {
      val policy = AppendOnlySupplementPolicy.create(3).getOrElse(fail("expected valid policy"))
      val candidates = Vector(ArticleCandidate("a", 1.0))
      val baselinePageIds = Vector("a")
      val membership = BaselineMembershipResult.fromBackend(
        Vector("a"),
        Vector("a"),
      )
      val result = policy.select(baselinePageIds, candidates, membership)(_.id).getOrElse(fail("expected success"))
      assert(result.currentPageDuplicateIds == Vector("a"))
      assert(result.baselineMemberIds.isEmpty)
      assert(result.appended.isEmpty)
    }

    "preserve original candidate order in appended" in {
      val policy = AppendOnlySupplementPolicy.create(3).getOrElse(fail("expected valid policy"))
      val candidates = Vector(
        ArticleCandidate("x", 3.0),
        ArticleCandidate("y", 2.0),
        ArticleCandidate("z", 1.0),
      )
      val baselinePageIds = Vector.empty[String]
      val membership = BaselineMembershipResult.fromBackend(
        Vector("x", "y", "z"),
        Vector.empty[String],
      )
      val result = policy.select(baselinePageIds, candidates, membership)(_.id).getOrElse(fail("expected success"))
      assert(result.appended.map(_.id) == Vector("x", "y", "z"))
    }

    "preserve original score/data objects unchanged" in {
      val policy = AppendOnlySupplementPolicy.create(3).getOrElse(fail("expected valid policy"))
      val original = ArticleCandidate("a", BigDecimal("3.14"))
      val candidates = Vector(original)
      val baselinePageIds = Vector.empty[String]
      val membership = BaselineMembershipResult.fromBackend(
        Vector("a"),
        Vector.empty[String],
      )
      val result = policy.select(baselinePageIds, candidates, membership)(_.id).getOrElse(fail("expected success"))
      result.appended match {
        case Vector(actual) =>
          assert(actual.score == BigDecimal("3.14"))
          assert(actual eq original)
        case other =>
          fail(s"expected exactly one appended candidate, got $other")
      }
    }

    "append at most maxAppended candidates" in {
      val policy = AppendOnlySupplementPolicy.create(2).getOrElse(fail("expected valid policy"))
      val candidates = Vector(
        ArticleCandidate("a", 1.0),
        ArticleCandidate("b", 2.0),
        ArticleCandidate("c", 3.0),
        ArticleCandidate("d", 4.0),
      )
      val baselinePageIds = Vector.empty[String]
      val membership = BaselineMembershipResult.fromBackend(
        Vector("a", "b", "c", "d"),
        Vector.empty[String],
      )
      val result = policy.select(baselinePageIds, candidates, membership)(_.id).getOrElse(fail("expected success"))
      assert(result.appended.map(_.id) == Vector("a", "b"))
      assert(result.budgetExcludedIds == Vector("c", "d"))
    }

    "put later eligible candidates into budgetExcludedIds in order" in {
      val policy = AppendOnlySupplementPolicy.create(1).getOrElse(fail("expected valid policy"))
      val candidates = Vector(
        ArticleCandidate("a", 1.0),
        ArticleCandidate("b", 2.0),
        ArticleCandidate("c", 3.0),
      )
      val baselinePageIds = Vector.empty[String]
      val membership = BaselineMembershipResult.fromBackend(
        Vector("a", "b", "c"),
        Vector("c"),
      )
      val result = policy.select(baselinePageIds, candidates, membership)(_.id).getOrElse(fail("expected success"))
      assert(result.appended.map(_.id) == Vector("a"))
      assert(result.baselineMemberIds == Vector("c"))
      assert(result.budgetExcludedIds == Vector("b"))
    }

    "not return baseline input in any output" in {
      val policy = AppendOnlySupplementPolicy.create(3).getOrElse(fail("expected valid policy"))
      val candidates = Vector(ArticleCandidate("a", 1.0))
      val baselinePageIds = Vector("a")
      val membership = BaselineMembershipResult.fromBackend(
        Vector("a"),
        Vector("a"),
      )
      val result = policy.select(baselinePageIds, candidates, membership)(_.id).getOrElse(fail("expected success"))
      assert(result.appended.isEmpty)
      assert(result.currentPageDuplicateIds == Vector("a"))
      assert(result.baselineMemberIds.isEmpty)
    }

    "produce empty selection for empty candidates with empty membership" in {
      val policy = AppendOnlySupplementPolicy.create(3).getOrElse(fail("expected valid policy"))
      val candidates = Vector.empty[ArticleCandidate]
      val baselinePageIds = Vector.empty[String]
      val membership = BaselineMembershipResult.fromBackend(Vector.empty[String], Vector.empty[String])
      val result = policy.select(baselinePageIds, candidates, membership)(_.id).getOrElse(fail("expected success"))
      assert(result.appended.isEmpty)
      assert(result.currentPageDuplicateIds.isEmpty)
      assert(result.baselineMemberIds.isEmpty)
      assert(result.budgetExcludedIds.isEmpty)
    }
  }
}
