package leaderboard.search

import leaderboard.search.semantic.{SemanticSupplementPolicy, SemanticSupplementWindow}
import org.scalatest.wordspec.AnyWordSpec

final class SemanticSupplementPolicySpec extends AnyWordSpec {
  "SemanticSupplementPolicy.AppendAll" should {
    "preserve the lexical prefix order and append semantic candidates within cap room" in {
      val lexicalPrefix = List(ToyDocument("lex-1"), ToyDocument("lex-2"))
      val semanticCandidates = List(ToyDocument("sem-1"), ToyDocument("sem-2"), ToyDocument("sem-3"))
      val policy = SemanticSupplementPolicy.AppendAll[ToyDocument]()

      val selected = policy.select(
        lexicalPrefix = lexicalPrefix,
        semanticCandidates = semanticCandidates,
        window = SemanticSupplementWindow(capRoom = 2),
      )

      assert(selected == lexicalPrefix ++ List(ToyDocument("sem-1"), ToyDocument("sem-2")))
    }

    "remove semantic candidates already present in the lexical prefix" in {
      val lexicalPrefix = List(ToyDocument("lex-1"), ToyDocument("lex-2"))
      val semanticCandidates = List(ToyDocument("lex-1"), ToyDocument("sem-1"))
      val policy = SemanticSupplementPolicy.AppendAll[ToyDocument]()

      val selected = policy.select(
        lexicalPrefix = lexicalPrefix,
        semanticCandidates = semanticCandidates,
        window = SemanticSupplementWindow(capRoom = 4),
      )

      assert(selected == lexicalPrefix ++ List(ToyDocument("sem-1")))
    }
  }

  "SemanticSupplementPolicy.PrefixPreservingTop1" should {
    "preserve the lexical prefix order and append only the first eligible candidate" in {
      val lexicalPrefix = List(ToyDocument("lex-1"), ToyDocument("lex-2"))
      val skipped = ToyDocument("sem-1", eligible = false)
      val selectedSemantic = ToyDocument("sem-2", eligible = true)
      val later = ToyDocument("sem-3", eligible = true)
      val policy = SemanticSupplementPolicy.PrefixPreservingTop1[ToyDocument](_.eligible)

      val selected = policy.select(
        lexicalPrefix = lexicalPrefix,
        semanticCandidates = List(skipped, selectedSemantic, later),
        window = SemanticSupplementWindow(capRoom = 4),
      )

      assert(selected == lexicalPrefix ++ List(selectedSemantic))
    }

    "append nothing when cap room is zero" in {
      val lexicalPrefix = List(ToyDocument("lex-1"), ToyDocument("lex-2"))
      val policy = SemanticSupplementPolicy.PrefixPreservingTop1[ToyDocument](_.eligible)

      val selected = policy.select(
        lexicalPrefix = lexicalPrefix,
        semanticCandidates = List(ToyDocument("sem-1", eligible = true)),
        window = SemanticSupplementWindow(capRoom = 0),
      )

      assert(selected == lexicalPrefix)
    }

    "append nothing when no semantic candidate is eligible" in {
      val lexicalPrefix = List(ToyDocument("lex-1"), ToyDocument("lex-2"))
      val policy = SemanticSupplementPolicy.PrefixPreservingTop1[ToyDocument](_.eligible)

      val selected = policy.select(
        lexicalPrefix = lexicalPrefix,
        semanticCandidates = List(ToyDocument("sem-1", eligible = false), ToyDocument("sem-2", eligible = false)),
        window = SemanticSupplementWindow(capRoom = 4),
      )

      assert(selected == lexicalPrefix)
    }
  }

  private final case class ToyDocument(id: String, eligible: Boolean = true)
}
