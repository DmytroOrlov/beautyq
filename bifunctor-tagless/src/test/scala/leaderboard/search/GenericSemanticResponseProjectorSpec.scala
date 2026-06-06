package leaderboard.search

import leaderboard.search.semantic.SemanticResponseProjector
import org.scalatest.wordspec.AnyWordSpec

final class GenericSemanticResponseProjectorSpec extends AnyWordSpec {
  "Generic semantic response projector" should {
    "project a non-Beauty assembly into a non-Beauty response" in {
      val projector = new TestSemanticResponseProjector
      val assembly = TestSemanticAssembly(List(
        TestSemanticCandidate("doc-1", "Alpha"),
        TestSemanticCandidate("doc-2", "Beta"),
      ))

      val response = projector.project(assembly)

      assert(response == TestSemanticResponse(List("Alpha", "Beta")))
    }

    "preserve candidate order when implementation does so" in {
      val projector = new TestSemanticResponseProjector
      val assembly = TestSemanticAssembly(List(
        TestSemanticCandidate("doc-3", "Gamma"),
        TestSemanticCandidate("doc-1", "Alpha"),
        TestSemanticCandidate("doc-2", "Beta"),
      ))

      val response = projector.project(assembly)

      assert(response.items == List("Gamma", "Alpha", "Beta"))
    }

    "not require Qdrant or BeautyQ types" in {
      val projector: SemanticResponseProjector[TestSemanticAssembly, TestSemanticResponse] = new TestSemanticResponseProjector

      val response = projector.project(TestSemanticAssembly(List(TestSemanticCandidate("doc-1", "Alpha"))))

      assert(response.items == List("Alpha"))
    }
  }

  private final class TestSemanticResponseProjector extends SemanticResponseProjector[TestSemanticAssembly, TestSemanticResponse] {
    override def project(assembly: TestSemanticAssembly): TestSemanticResponse =
      TestSemanticResponse(assembly.candidates.map(_.title))
  }

  private final case class TestSemanticAssembly(candidates: List[TestSemanticCandidate])
  private final case class TestSemanticCandidate(id: String, title: String)
  private final case class TestSemanticResponse(items: List[String])
}
