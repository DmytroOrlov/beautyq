package leaderboard.search.gen2.contract

import org.scalatest.wordspec.AnyWordSpec

/** Neutral, document-independent proofs for Brick 4B's plan-value types: stable IDs need no behavior
  * proof (plain value wrappers with no invariant), so this spec covers the two families that do carry
  * behavior - positive sizes and opaque cursor carriage.
  */
final class PlanValueSpec extends AnyWordSpec {

  "PageSize.from" should {
    "accept a positive value" in {
      PageSize.from(20) match {
        case Right(size) => assert(size.value == 20)
        case Left(error) => fail(s"expected acceptance, got: $error")
      }
    }

    "reject zero" in {
      assert(PageSize.from(0) == Left(PlanValueError.InvalidPageSize(0)))
    }

    "reject a negative value" in {
      assert(PageSize.from(-1) == Left(PlanValueError.InvalidPageSize(-1)))
    }
  }

  "FacetSize.from" should {
    "accept a positive value" in {
      FacetSize.from(10) match {
        case Right(size) => assert(size.value == 10)
        case Left(error) => fail(s"expected acceptance, got: $error")
      }
    }

    "reject zero and negative values" in {
      assert(FacetSize.from(0) == Left(PlanValueError.InvalidFacetSize(0)))
      assert(FacetSize.from(-5) == Left(PlanValueError.InvalidFacetSize(-5)))
    }
  }

  "GroupSize.from" should {
    "accept a positive value" in {
      GroupSize.from(5) match {
        case Right(size) => assert(size.value == 5)
        case Left(error) => fail(s"expected acceptance, got: $error")
      }
    }

    "reject zero and negative values" in {
      assert(GroupSize.from(0) == Left(PlanValueError.InvalidGroupSize(0)))
      assert(GroupSize.from(-3) == Left(PlanValueError.InvalidGroupSize(-3)))
    }
  }

  "SearchCursor construction" should {
    // A negative-compile test confined to this file cannot itself simulate code physically outside the
    // gen2 package tree: Scala resolves `private[gen2]` structurally from the file's own package, and
    // this spec's package (leaderboard.search.gen2.contract) is already inside that boundary. What this
    // file can prove, and what actually matters for "unrelated public code cannot manufacture a trusted
    // cursor", is that SearchCursor exposes no public apply/constructor at all: the only way to produce
    // one is the restricted fromOpaque, which compiles here only because this file itself sits inside
    // the gen2 boundary that fromOpaque is scoped to reach.
    "expose no public apply constructor" in {
      assertDoesNotCompile("""SearchCursor("token")""")
    }

    "remain constructible through fromOpaque from within the Gen2 package boundary" in {
      assertCompiles("""SearchCursor.fromOpaque("token")""")
    }
  }

  "PageRequest.withoutCursor" should {
    "preserve size and clear only the cursor" in {
      val size    = PageSize.from(20).getOrElse(fail("expected a valid PageSize"))
      val cursor  = SearchCursor.fromOpaque("opaque-token")
      val request = PageRequest(Some(cursor), size)

      val result = request.withoutCursor

      assert(result.cursor.isEmpty)
      assert(result.size == size)
    }
  }
}
