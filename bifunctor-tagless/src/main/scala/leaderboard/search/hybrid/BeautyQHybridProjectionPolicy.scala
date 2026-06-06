package leaderboard.search.hybrid

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.lexical.LexicalDocumentHit
import leaderboard.search.semantic.SemanticDocumentHit

final case class BeautyQHybridVariantCandidate(
  variantId: MasterServiceOfferVariantId,
  lexicalScore: Option[Double],
  semanticScore: Option[Double],
  sources: Set[BeautyQHybridCandidateSource],
)

sealed trait BeautyQHybridCandidateSource
object BeautyQHybridCandidateSource {
  case object Lexical extends BeautyQHybridCandidateSource
  case object Semantic extends BeautyQHybridCandidateSource
}

final case class BeautyQHybridProjectionPolicyResult(
  candidates: List[BeautyQHybridVariantCandidate],
  diagnostics: BeautyQHybridProjectionDiagnostics,
)

final case class BeautyQHybridProjectionDiagnostics(
  lexicalInputCount: Int,
  semanticInputCount: Int,
  overlapCount: Int,
  lexicalOnlyCount: Int,
  semanticOnlyCount: Int,
)

object BeautyQHybridProjectionPolicy {
  import BeautyQHybridCandidateSource.*

  def lexicalFirstSemanticSupplement(
    retrieval: HybridDocumentRetrievalResult[MasterServiceOfferVariantId],
  ): BeautyQHybridProjectionPolicyResult = {
    val lexicalHits = firstHitsById(retrieval.lexicalHits)
    val semanticHits = firstHitsById(retrieval.semanticHits)
    val semanticById = semanticHits.map(hit => hit.documentId -> hit).toMap
    val lexicalIds = lexicalHits.map(_.documentId).toSet
    val semanticIds = semanticHits.map(_.documentId).toSet

    val lexicalCandidates = lexicalHits.map { hit =>
      semanticById.get(hit.documentId) match {
        case Some(semanticHit) =>
          BeautyQHybridVariantCandidate(
            variantId = hit.documentId,
            lexicalScore = Some(hit.score),
            semanticScore = Some(semanticHit.score),
            sources = Set(Lexical, Semantic),
          )
        case None =>
          BeautyQHybridVariantCandidate(
            variantId = hit.documentId,
            lexicalScore = Some(hit.score),
            semanticScore = None,
            sources = Set(Lexical),
          )
      }
    }

    val semanticOnlyCandidates = semanticHits
      .filterNot(hit => lexicalIds(hit.documentId))
      .map { hit =>
        BeautyQHybridVariantCandidate(
          variantId = hit.documentId,
          lexicalScore = None,
          semanticScore = Some(hit.score),
          sources = Set(Semantic),
        )
      }

    BeautyQHybridProjectionPolicyResult(
      candidates = lexicalCandidates ++ semanticOnlyCandidates,
      diagnostics = BeautyQHybridProjectionDiagnostics(
        lexicalInputCount = retrieval.lexicalHits.size,
        semanticInputCount = retrieval.semanticHits.size,
        overlapCount = (lexicalIds intersect semanticIds).size,
        lexicalOnlyCount = (lexicalIds diff semanticIds).size,
        semanticOnlyCount = (semanticIds diff lexicalIds).size,
      ),
    )
  }

  private def firstHitsById[Id, Hit](hits: List[Hit])(using documentIdOf: DocumentIdOf[Hit, Id]): List[Hit] = {
    val (_, hitsReverse) = hits.foldLeft((Set.empty[Id], List.empty[Hit])) {
      case ((seenIds, selectedHits), hit) if seenIds(documentIdOf.documentId(hit)) =>
        (seenIds, selectedHits)
      case ((seenIds, selectedHits), hit) =>
        (seenIds + documentIdOf.documentId(hit), hit :: selectedHits)
    }

    hitsReverse.reverse
  }

  private trait DocumentIdOf[-Hit, Id] {
    def documentId(hit: Hit): Id
  }

  private given lexicalDocumentIdOf[Id]: DocumentIdOf[LexicalDocumentHit[Id], Id] with {
    override def documentId(hit: LexicalDocumentHit[Id]): Id = hit.documentId
  }

  private given semanticDocumentIdOf[Id]: DocumentIdOf[SemanticDocumentHit[Id], Id] with {
    override def documentId(hit: SemanticDocumentHit[Id]): Id = hit.documentId
  }
}
