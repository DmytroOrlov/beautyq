package leaderboard.search.beautyq.gen2.wiring

import org.scalatest.wordspec.AnyWordSpec

final class QdrantConstructionBoundarySpec extends AnyWordSpec {
  "Qdrant lifecycle results" should {
    "not expose construction or copy paths outside the qdrant owner" in {
      assertDoesNotCompile("""new leaderboard.search.gen2.qdrant.ActiveQdrantGeneration(null, null, null)""")
      assertDoesNotCompile("""new leaderboard.search.gen2.qdrant.QdrantCandidateSearchResult[Int](Vector.empty, null)""")
      assertDoesNotCompile("""def forged(value: leaderboard.search.gen2.qdrant.QdrantCandidateSearchResult[Int]): Any = value.copy(hits = Vector.empty)""")
    }
  }
}
