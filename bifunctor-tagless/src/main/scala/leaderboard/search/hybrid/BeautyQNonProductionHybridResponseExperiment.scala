package leaderboard.search.hybrid

import izumi.functional.bio.{Error2, F}
import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.lexical.LexicalDocumentBackend
import leaderboard.search.semantic.{SemanticDocumentBackend, SemanticDocumentLookup}
import leaderboard.search.{BeautySearchResponse, ParsedSearchIntent, UserSearchInput}

final case class BeautyQNonProductionHybridResponseExperimentDiagnostics(
  lexicalHitCount: Int,
  semanticHitCount: Int,
  distinctVariantIdCount: Int,
  pipeline: BeautyQHybridResponsePipelineDiagnostics,
)

final case class BeautyQNonProductionHybridResponseExperimentResult(
  response: BeautySearchResponse,
  diagnostics: BeautyQNonProductionHybridResponseExperimentDiagnostics,
)

final class BeautyQNonProductionHybridResponseExperiment[F[+_, +_]: Error2](
  lexicalBackend: LexicalDocumentBackend[F, MasterServiceOfferVariantId],
  semanticBackend: SemanticDocumentBackend[F, MasterServiceOfferVariantId],
  documentLookup: SemanticDocumentLookup[F, MasterServiceOfferVariantId, VariantSearchDocument],
) {
  def search(
    input: UserSearchInput,
    intent: ParsedSearchIntent,
  ): F[QueryFailure, BeautyQNonProductionHybridResponseExperimentResult] =
    F.flatMap(lexicalBackend.documentHits(input, intent)) { lexicalHits =>
      F.flatMap(semanticBackend.documentHits(input, intent)) { semanticHits =>
        val retrieval = HybridDocumentRetrievalResult.fromHits(lexicalHits, semanticHits)
        val distinctVariantIds = HybridDocumentRetrievalResult.distinctDocumentIdsInChannelOrder(retrieval)

        F.flatMap(documentLookup.lookup(distinctVariantIds)) { documentsByVariantId =>
          BeautyQHybridResponsePipeline.projectResponse(retrieval, documentsByVariantId) match {
            case Right(pipelineResult) =>
              F.pure(
                BeautyQNonProductionHybridResponseExperimentResult(
                  response = pipelineResult.response,
                  diagnostics = BeautyQNonProductionHybridResponseExperimentDiagnostics(
                    lexicalHitCount = lexicalHits.size,
                    semanticHitCount = semanticHits.size,
                    distinctVariantIdCount = distinctVariantIds.size,
                    pipeline = pipelineResult.diagnostics,
                  ),
                )
              )
            case Left(error) =>
              F.fail(error)
          }
        }
      }
    }
}
