package leaderboard.search.hybrid

import izumi.functional.bio.Error2
import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.{ParsedSearchIntent, UserSearchInput}
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.lexical.LexicalDocumentBackend
import leaderboard.search.semantic.{SemanticDocumentBackend, SemanticDocumentLookup}

final class BeautyQNonProductionHybridExperimentRunner[F[+_, +_]](
  experiment: BeautyQNonProductionHybridResponseExperiment[F],
) {
  def run(
    input: UserSearchInput,
    intent: ParsedSearchIntent,
  ): F[QueryFailure, BeautyQNonProductionHybridResponseExperimentResult] =
    experiment.search(input, intent)
}

object BeautyQNonProductionHybridExperimentRunner {
  def apply[F[+_, +_]: Error2](
    lexicalBackend: LexicalDocumentBackend[F, MasterServiceOfferVariantId],
    semanticBackend: SemanticDocumentBackend[F, MasterServiceOfferVariantId],
    documentLookup: SemanticDocumentLookup[F, MasterServiceOfferVariantId, VariantSearchDocument],
  ): BeautyQNonProductionHybridExperimentRunner[F] =
    new BeautyQNonProductionHybridExperimentRunner[F](
      new BeautyQNonProductionHybridResponseExperiment[F](
        lexicalBackend = lexicalBackend,
        semanticBackend = semanticBackend,
        documentLookup = documentLookup,
      ),
    )
}
