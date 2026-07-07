package leaderboard.search

import java.util.UUID

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.hybrid.BeautyQHybridCandidateSource.{Lexical, Semantic}
import leaderboard.search.hybrid.{BeautyQHybridProjectionPolicy, HybridDocumentRetrievalResult}
import leaderboard.search.lexical.LexicalDocumentHit
import leaderboard.search.semantic.SemanticDocumentHit
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQHybridProjectionPolicySpec extends AnyWordSpec {
  "BeautyQ hybrid projection policy" should {
    "preserve lexical order for lexical-only candidates" in {
      val first = variantId(1)
      val second = variantId(2)

      val result = project(
        lexicalHits = List(LexicalDocumentHit(first, 12.0), LexicalDocumentHit(second, 8.0)),
        semanticHits = Nil,
      )

      assert(result.candidates.map(_.variantId) == List(first, second))
      assert(result.candidates.map(_.sources) == List(Set(Lexical), Set(Lexical)))
    }

    "preserve semantic order for semantic-only candidates" in {
      val first = variantId(3)
      val second = variantId(4)

      val result = project(
        lexicalHits = Nil,
        semanticHits = List(SemanticDocumentHit(first, 0.81), SemanticDocumentHit(second, 0.72)),
      )

      assert(result.candidates.map(_.variantId) == List(first, second))
      assert(result.candidates.map(_.sources) == List(Set(Semantic), Set(Semantic)))
    }

    "collapse overlap to one candidate by variant id" in {
      val shared = variantId(5)

      val result = project(
        lexicalHits = List(LexicalDocumentHit(shared, 10.0)),
        semanticHits = List(SemanticDocumentHit(shared, 0.91)),
      )

      assert(result.candidates.map(_.variantId) == List(shared))
      assert(result.candidates.head.sources == Set(Lexical, Semantic))
    }

    "keep lexical-first order when overlap exists" in {
      val lexicalFirst = variantId(6)
      val shared = variantId(7)
      val semanticFirst = variantId(8)

      val result = project(
        lexicalHits = List(LexicalDocumentHit(lexicalFirst, 10.0), LexicalDocumentHit(shared, 9.0)),
        semanticHits = List(SemanticDocumentHit(semanticFirst, 0.95), SemanticDocumentHit(shared, 0.90)),
      )

      assert(result.candidates.map(_.variantId) == List(lexicalFirst, shared, semanticFirst))
    }

    "append semantic-only candidates after lexical candidates" in {
      val lexicalOnly = variantId(9)
      val semanticOnly = variantId(10)

      val result = project(
        lexicalHits = List(LexicalDocumentHit(lexicalOnly, 4.0)),
        semanticHits = List(SemanticDocumentHit(semanticOnly, 0.77)),
      )

      assert(result.candidates.map(_.variantId) == List(lexicalOnly, semanticOnly))
    }

    "preserve lexical and semantic scores separately" in {
      val shared = variantId(11)

      val result = project(
        lexicalHits = List(LexicalDocumentHit(shared, 17.5)),
        semanticHits = List(SemanticDocumentHit(shared, 0.88)),
      )

      assert(result.candidates.head.lexicalScore == Some(17.5))
      assert(result.candidates.head.semanticScore == Some(0.88))
    }

    "not produce a fused score field" in {
      val candidate = project(
        lexicalHits = List(LexicalDocumentHit(variantId(12), 7.0)),
        semanticHits = Nil,
      ).candidates.head

      assert(!candidate.productElementNames.toSet.contains("score"))
    }

    "use the first lexical hit for duplicate lexical ids" in {
      val duplicate = variantId(13)

      val result = project(
        lexicalHits = List(LexicalDocumentHit(duplicate, 11.0), LexicalDocumentHit(duplicate, 3.0)),
        semanticHits = Nil,
      )

      assert(result.candidates.map(_.variantId) == List(duplicate))
      assert(result.candidates.head.lexicalScore == Some(11.0))
    }

    "use the first semantic hit for duplicate semantic ids" in {
      val duplicate = variantId(14)

      val result = project(
        lexicalHits = Nil,
        semanticHits = List(SemanticDocumentHit(duplicate, 0.93), SemanticDocumentHit(duplicate, 0.41)),
      )

      assert(result.candidates.map(_.variantId) == List(duplicate))
      assert(result.candidates.head.semanticScore == Some(0.93))
    }

    "report input and unique candidate category diagnostics" in {
      val lexicalOnly = variantId(15)
      val shared = variantId(16)
      val semanticOnly = variantId(17)

      val result = project(
        lexicalHits = List(
          LexicalDocumentHit(lexicalOnly, 12.0),
          LexicalDocumentHit(shared, 11.0),
          LexicalDocumentHit(shared, 1.0),
        ),
        semanticHits = List(
          SemanticDocumentHit(shared, 0.91),
          SemanticDocumentHit(semanticOnly, 0.81),
          SemanticDocumentHit(semanticOnly, 0.22),
        ),
      )

      assert(result.diagnostics.lexicalInputCount == 3)
      assert(result.diagnostics.semanticInputCount == 3)
      assert(result.diagnostics.overlapCount == 1)
      assert(result.diagnostics.lexicalOnlyCount == 1)
      assert(result.diagnostics.semanticOnlyCount == 1)
    }

    "work from HybridDocumentRetrievalResult of MasterServiceOfferVariantId" in {
      val lexicalId = variantId(18)
      val semanticId = variantId(19)
      val retrieval: HybridDocumentRetrievalResult[MasterServiceOfferVariantId] =
        HybridDocumentRetrievalResult.fromHits(
          lexicalHits = List(LexicalDocumentHit(lexicalId, 5.0)),
          semanticHits = List(SemanticDocumentHit(semanticId, 0.68)),
        )

      val result = BeautyQHybridProjectionPolicy.lexicalFirstSemanticSupplement(retrieval)

      assert(result.candidates.map(_.variantId) == List(lexicalId, semanticId))
    }

    "not require Qdrant, Elasticsearch, or file IO" in {
      val result = project(
        lexicalHits = List(LexicalDocumentHit(variantId(20), 2.0)),
        semanticHits = List(SemanticDocumentHit(variantId(21), 0.51)),
      )

      assert(result.candidates.size == 2)
      assert(result.diagnostics.lexicalInputCount == 1)
      assert(result.diagnostics.semanticInputCount == 1)
    }

    "not reorder semantic-only candidates by higher semantic score" in {
      val first = variantId(22)
      val second = variantId(23)

      val result = project(
        lexicalHits = Nil,
        semanticHits = List(
          SemanticDocumentHit(first, 0.30),
          SemanticDocumentHit(second, 0.95),
        ),
      )

      assert(result.candidates.map(_.variantId) == List(first, second))
    }

    "not move mixed candidate with higher semantic score ahead of lexical-only" in {
      val lexicalOnly = variantId(24)
      val mixed = variantId(25)

      val result = project(
        lexicalHits = List(LexicalDocumentHit(lexicalOnly, 5.0)),
        semanticHits = List(
          SemanticDocumentHit(mixed, 0.99),
          SemanticDocumentHit(lexicalOnly, 0.10),
        ),
      )

      assert(result.candidates.map(_.variantId) == List(lexicalOnly, mixed))
      assert(result.candidates.head.semanticScore == Some(0.10))
      assert(result.candidates.tail.head.semanticScore == Some(0.99))
    }

    "not reorder output candidates by any score magnitude" in {
      val lowLexical = variantId(26)
      val highSemanticOnly = variantId(27)
      val midMixed = variantId(28)

      val result = project(
        lexicalHits = List(
          LexicalDocumentHit(lowLexical, 0.5),
          LexicalDocumentHit(midMixed, 1.0),
        ),
        semanticHits = List(
          SemanticDocumentHit(highSemanticOnly, 0.99),
          SemanticDocumentHit(midMixed, 0.80),
          SemanticDocumentHit(lowLexical, 0.70),
        ),
      )

      assert(result.candidates.map(_.variantId) == List(lowLexical, midMixed, highSemanticOnly))
    }
  }

  private def project(
    lexicalHits: List[LexicalDocumentHit[MasterServiceOfferVariantId]],
    semanticHits: List[SemanticDocumentHit[MasterServiceOfferVariantId]],
  ) =
    BeautyQHybridProjectionPolicy.lexicalFirstSemanticSupplement(
      HybridDocumentRetrievalResult.fromHits(lexicalHits, semanticHits)
    )

  private def variantId(value: Int): MasterServiceOfferVariantId =
    UUID.fromString(f"00000000-0000-0000-0000-$value%012d")
}
