package leaderboard.search

import leaderboard.search.hybrid.HybridDocumentRetrievalResult
import leaderboard.search.lexical.LexicalDocumentHit
import leaderboard.search.semantic.SemanticDocumentHit
import org.scalatest.wordspec.AnyWordSpec

final class GenericHybridDocumentRetrievalSpec extends AnyWordSpec {
  "Generic hybrid document retrieval result" should {
    "preserve lexical hit order" in {
      val result = HybridDocumentRetrievalResult.fromHits(
        lexicalHits = List(
          LexicalDocumentHit("article-3", 3.0),
          LexicalDocumentHit("article-1", 1.0),
          LexicalDocumentHit("article-2", 2.0),
        ),
        semanticHits = Nil,
      )

      assert(result.lexicalHits.map(_.documentId) == List("article-3", "article-1", "article-2"))
    }

    "preserve semantic hit order" in {
      val result = HybridDocumentRetrievalResult.fromHits(
        lexicalHits = Nil,
        semanticHits = List(
          SemanticDocumentHit("manual-2", 0.72),
          SemanticDocumentHit("manual-1", 0.91),
          SemanticDocumentHit("manual-3", 0.51),
        ),
      )

      assert(result.semanticHits.map(_.documentId) == List("manual-2", "manual-1", "manual-3"))
    }

    "keep lexical and semantic scores separate" in {
      val result = HybridDocumentRetrievalResult.fromHits(
        lexicalHits = List(LexicalDocumentHit("shared-doc", 15.5)),
        semanticHits = List(SemanticDocumentHit("shared-doc", 0.88)),
      )

      assert(result.lexicalHits.map(_.score) == List(15.5))
      assert(result.semanticHits.map(_.score) == List(0.88))
    }

    "report channel hit counts in diagnostics" in {
      val result = HybridDocumentRetrievalResult.fromHits(
        lexicalHits = List(LexicalDocumentHit("lexical-1", 9.0), LexicalDocumentHit("lexical-2", 7.0)),
        semanticHits = List(SemanticDocumentHit("semantic-1", 0.81)),
      )

      val diagnostics = HybridDocumentRetrievalResult.diagnostics(result)

      assert(diagnostics.lexicalHitCount == 2)
      assert(diagnostics.semanticHitCount == 1)
      assert(diagnostics.lexicalExecuted)
      assert(diagnostics.semanticExecuted)
    }

    "preserve distinct ids in lexical-first channel order without ranking claims" in {
      val result = HybridDocumentRetrievalResult.fromHits(
        lexicalHits = List(
          LexicalDocumentHit("doc-b", 2.0),
          LexicalDocumentHit("doc-a", 5.0),
          LexicalDocumentHit("doc-b", 1.0),
        ),
        semanticHits = List(
          SemanticDocumentHit("doc-c", 0.91),
          SemanticDocumentHit("doc-a", 0.89),
          SemanticDocumentHit("doc-d", 0.71),
          SemanticDocumentHit("doc-c", 0.42),
        ),
      )

      val ids = HybridDocumentRetrievalResult.distinctDocumentIdsInChannelOrder(result)

      assert(ids == List("doc-b", "doc-a", "doc-c", "doc-d"))
    }

    "not require BeautyQ Elasticsearch or Qdrant types for generic use" in {
      final case class KnowledgeBaseDocumentId(value: String)
      val lexicalId = KnowledgeBaseDocumentId("kb-lexical")
      val semanticId = KnowledgeBaseDocumentId("kb-semantic")

      val result: HybridDocumentRetrievalResult[KnowledgeBaseDocumentId] =
        HybridDocumentRetrievalResult.fromHits(
          lexicalHits = List(LexicalDocumentHit(lexicalId, 4.25, matchedFields = List("title"))),
          semanticHits = List(SemanticDocumentHit(semanticId, 0.63)),
        )

      assert(result.lexicalHits.head.documentId == lexicalId)
      assert(result.semanticHits.head.documentId == semanticId)
    }

    "handle empty retrieval result with zero diagnostics" in {
      val result = HybridDocumentRetrievalResult.fromHits(
        lexicalHits = Nil,
        semanticHits = Nil,
      )

      val diagnostics = HybridDocumentRetrievalResult.diagnostics(result)

      assert(result.lexicalHits == Nil)
      assert(result.semanticHits == Nil)
      assert(diagnostics.lexicalHitCount == 0)
      assert(diagnostics.semanticHitCount == 0)
      assert(diagnostics.lexicalExecuted)
      assert(diagnostics.semanticExecuted)
      assert(HybridDocumentRetrievalResult.distinctDocumentIdsInChannelOrder(result) == Nil)
    }

    "report lexical-only diagnostics when semantic channel is empty" in {
      val result = HybridDocumentRetrievalResult.fromHits(
        lexicalHits = List(LexicalDocumentHit("lex-1", 5.0), LexicalDocumentHit("lex-2", 3.0)),
        semanticHits = Nil,
      )

      val diagnostics = HybridDocumentRetrievalResult.diagnostics(result)

      assert(result.lexicalHits.size == 2)
      assert(result.semanticHits == Nil)
      assert(diagnostics.lexicalHitCount == 2)
      assert(diagnostics.semanticHitCount == 0)
      assert(diagnostics.lexicalExecuted)
      assert(diagnostics.semanticExecuted)
      assert(HybridDocumentRetrievalResult.distinctDocumentIdsInChannelOrder(result) == List("lex-1", "lex-2"))
    }

    "report semantic-only diagnostics when lexical channel is empty" in {
      val result = HybridDocumentRetrievalResult.fromHits(
        lexicalHits = Nil,
        semanticHits = List(SemanticDocumentHit("sem-1", 0.75), SemanticDocumentHit("sem-2", 0.60)),
      )

      val diagnostics = HybridDocumentRetrievalResult.diagnostics(result)

      assert(result.lexicalHits == Nil)
      assert(result.semanticHits.size == 2)
      assert(diagnostics.lexicalHitCount == 0)
      assert(diagnostics.semanticHitCount == 2)
      assert(diagnostics.lexicalExecuted)
      assert(diagnostics.semanticExecuted)
      assert(HybridDocumentRetrievalResult.distinctDocumentIdsInChannelOrder(result) == List("sem-1", "sem-2"))
    }

    "not imply route decisions such as fallback, reranking, or runtime selection" in {
      val result = HybridDocumentRetrievalResult.fromHits(
        lexicalHits = List(LexicalDocumentHit("doc-1", 4.0)),
        semanticHits = List(SemanticDocumentHit("doc-2", 0.55)),
      )

      val diagnostics = HybridDocumentRetrievalResult.diagnostics(result)

      assert(!result.productElementNames.toSet("fallback"))
      assert(!result.productElementNames.toSet("reranked"))
      assert(!result.productElementNames.toSet("routeSelection"))
      assert(!result.productElementNames.toSet("decision"))
      assert(!result.productElementNames.toSet("selectedChannel"))
      assert(!diagnostics.productElementNames.toSet("fallback"))
      assert(!diagnostics.productElementNames.toSet("reranked"))
      assert(!diagnostics.productElementNames.toSet("routeSelection"))
      assert(!diagnostics.productElementNames.toSet("decision"))
      assert(!diagnostics.productElementNames.toSet("selectedChannel"))
    }
  }
}
