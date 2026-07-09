package leaderboard.search

import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.semantic.{SemanticCandidateHit, SemanticDocumentBackend, SemanticDocumentHit}
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

final class GenericSemanticDocumentBackendSpec extends AnyWordSpec {
  "Generic semantic document backend" should {
    "return non-Beauty semantic document hits" in {
      val backend: SemanticDocumentBackend[EitherQueryFailure, String] =
        new TestSemanticDocumentBackend(List(SemanticDocumentHit("article-1", 0.91)))

      val result = backend.documentHits(input, intent)

      assert(result == Right(List(SemanticDocumentHit("article-1", 0.91))))
    }

    "preserve hit id and score values" in {
      val hits = List(
        SemanticDocumentHit("article-2", 0.77),
        SemanticDocumentHit("article-3", 0.42),
      )
      val backend: SemanticDocumentBackend[EitherQueryFailure, String] =
        new TestSemanticDocumentBackend(hits)

      val result = backend.documentHits(input, intent)

      assert(result.map(_.map(_.documentId)) == Right(List("article-2", "article-3")))
      assert(result.map(_.map(_.score)) == Right(List(0.77, 0.42)))
    }

    "not require Qdrant or BeautyQ types for generic use" in {
      val backend: SemanticDocumentBackend[EitherQueryFailure, String] =
        new TestSemanticDocumentBackend(List(SemanticDocumentHit("plain-doc", 0.5)))

      assert(backend.documentHits(input, intent).exists(_.headOption.exists(_.documentId == "plain-doc")))
    }

    "convert BeautyQ semantic candidate hits to generic document hits" in {
      val variantId = MasterServiceOfferVariantId(UUID.fromString("00000000-0000-0000-0000-000000000123"))
      val candidateHit = SemanticCandidateHit(variantId, 0.88)

      val documentHit = SemanticCandidateHit.toDocumentHit(candidateHit)

      assert(documentHit == SemanticDocumentHit(variantId, 0.88))
    }
  }

  private type EitherQueryFailure[+E, +A] = Either[E, A]

  private final class TestSemanticDocumentBackend(hits: List[SemanticDocumentHit[String]])
    extends SemanticDocumentBackend[EitherQueryFailure, String] {
    override def documentHits(input: UserSearchInput, intent: ParsedSearchIntent): Either[QueryFailure, List[SemanticDocumentHit[String]]] =
      Right(hits)
  }

  private val input = UserSearchInput(query = "plain semantic query", userLat = None, userLon = None, limit = 10)
  private val intent = ParsedSearchIntent(
    originalQuery = input.query,
    normalizedTokens = List("plain", "semantic", "query"),
    explicitConstraints = Nil,
    softBoosts = Nil,
    remainingText = "plain semantic query",
  )
}
