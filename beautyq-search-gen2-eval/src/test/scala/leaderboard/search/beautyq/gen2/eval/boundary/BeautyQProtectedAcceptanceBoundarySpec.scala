package leaderboard.search.beautyq.gen2.eval.boundary

import org.scalatest.wordspec.AnyWordSpec

final class BeautyQProtectedAcceptanceBoundarySpec extends AnyWordSpec {
  "BeautyQ protected acceptance" should {
    "keep the green result constructor outside the external package" in {
      assertDoesNotCompile(
        """new leaderboard.search.beautyq.gen2.eval.BeautyQProtectedAcceptanceResult("v", true, "evaluation", "policy", 1, Vector.empty)"""
      )
      assertDoesNotCompile(
        """final class Forged extends leaderboard.search.beautyq.gen2.eval.BeautyQProtectedAcceptanceResult("v", true, "evaluation", "policy", 1, Vector.empty)"""
      )
    }
  }
}
