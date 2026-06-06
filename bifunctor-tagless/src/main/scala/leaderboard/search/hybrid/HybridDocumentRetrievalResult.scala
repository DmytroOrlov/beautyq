package leaderboard.search.hybrid

import leaderboard.search.lexical.LexicalDocumentHit
import leaderboard.search.semantic.SemanticDocumentHit

final case class HybridDocumentRetrievalResult[Id](
  lexicalHits: List[LexicalDocumentHit[Id]],
  semanticHits: List[SemanticDocumentHit[Id]],
)

final case class HybridDocumentRetrievalDiagnostics(
  lexicalHitCount: Int,
  semanticHitCount: Int,
  lexicalExecuted: Boolean,
  semanticExecuted: Boolean,
)

object HybridDocumentRetrievalResult {
  def fromHits[Id](
    lexicalHits: List[LexicalDocumentHit[Id]],
    semanticHits: List[SemanticDocumentHit[Id]],
  ): HybridDocumentRetrievalResult[Id] =
    HybridDocumentRetrievalResult(lexicalHits, semanticHits)

  def diagnostics[Id](result: HybridDocumentRetrievalResult[Id]): HybridDocumentRetrievalDiagnostics =
    HybridDocumentRetrievalDiagnostics(
      lexicalHitCount = result.lexicalHits.size,
      semanticHitCount = result.semanticHits.size,
      lexicalExecuted = true,
      semanticExecuted = true,
    )

  def distinctDocumentIdsInChannelOrder[Id](result: HybridDocumentRetrievalResult[Id]): List[Id] = {
    val (seenLexicalIds, lexicalIdsReverse) =
      distinctIds(result.lexicalHits.map(_.documentId), Set.empty[Id], List.empty[Id])
    val (_, semanticIdsReverse) =
      distinctIds(result.semanticHits.map(_.documentId), seenLexicalIds, lexicalIdsReverse)

    semanticIdsReverse.reverse
  }

  private def distinctIds[Id](ids: List[Id], seen: Set[Id], idsReverse: List[Id]): (Set[Id], List[Id]) =
    ids.foldLeft((seen, idsReverse)) {
      case ((knownIds, orderedIds), id) if knownIds(id) =>
        (knownIds, orderedIds)
      case ((knownIds, orderedIds), id) =>
        (knownIds + id, id :: orderedIds)
    }
}
