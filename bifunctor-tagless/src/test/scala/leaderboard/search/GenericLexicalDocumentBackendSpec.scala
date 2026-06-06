package leaderboard.search

import leaderboard.model.QueryFailure
import leaderboard.search.lexical.{LexicalDocumentBackend, LexicalDocumentHit}
import org.scalatest.wordspec.AnyWordSpec

final class GenericLexicalDocumentBackendSpec extends AnyWordSpec {
  "Generic lexical document backend" should {
    "return non-Beauty lexical document hits" in {
      val backend: LexicalDocumentBackend[EitherQueryFailure, String] =
        new TestLexicalDocumentBackend(List(LexicalDocumentHit("article-1", 12.5)))

      val result = backend.documentHits(input, intent)

      assert(result == Right(List(LexicalDocumentHit("article-1", 12.5))))
    }

    "preserve hit id, score, and matched fields" in {
      val hits = List(
        LexicalDocumentHit("article-2", 7.25, matchedFields = List("title", "body")),
        LexicalDocumentHit("article-3", 3.5, matchedFields = Nil),
      )
      val backend: LexicalDocumentBackend[EitherQueryFailure, String] =
        new TestLexicalDocumentBackend(hits)

      val result = backend.documentHits(input, intent)

      assert(result.map(_.map(_.documentId)) == Right(List("article-2", "article-3")))
      assert(result.map(_.map(_.score)) == Right(List(7.25, 3.5)))
      assert(result.map(_.map(_.matchedFields)) == Right(List(List("title", "body"), Nil)))
    }

    "not require Elasticsearch or BeautyQ response types for generic use" in {
      val backend: LexicalDocumentBackend[EitherQueryFailure, String] =
        new TestLexicalDocumentBackend(List(LexicalDocumentHit("plain-doc", 1.0, matchedFields = List("summary"))))

      val result = backend.documentHits(input, intent)

      assert(result.exists(_.headOption.exists(_.documentId == "plain-doc")))
      assert(result.exists(_.headOption.exists(_.matchedFields == List("summary"))))
    }
  }

  private type EitherQueryFailure[+E, +A] = Either[E, A]

  private final class TestLexicalDocumentBackend(hits: List[LexicalDocumentHit[String]])
    extends LexicalDocumentBackend[EitherQueryFailure, String] {
    override def documentHits(input: UserSearchInput, intent: ParsedSearchIntent): Either[QueryFailure, List[LexicalDocumentHit[String]]] =
      Right(hits)
  }

  private val input = UserSearchInput(query = "plain lexical query", userLat = None, userLon = None, limit = 10)
  private val intent = ParsedSearchIntent(
    originalQuery = input.query,
    normalizedTokens = List("plain", "lexical", "query"),
    explicitConstraints = Nil,
    softBoosts = Nil,
    remainingText = "plain lexical query",
  )
}
