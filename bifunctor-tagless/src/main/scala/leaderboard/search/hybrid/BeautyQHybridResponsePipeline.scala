package leaderboard.search.hybrid

import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.BeautySearchResponse
import leaderboard.search.document.VariantSearchDocument

final case class BeautyQHybridResponsePipelineDiagnostics(
  policy: BeautyQHybridProjectionDiagnostics,
  variantProjection: BeautyQHybridVariantProjectionDiagnostics,
  providerServiceProjection: BeautyQHybridProviderServiceProjectionDiagnostics,
  responseAdapter: BeautyQHybridResponseAdapterDiagnostics,
)

final case class BeautyQHybridResponsePipelineResult(
  response: BeautySearchResponse,
  diagnostics: BeautyQHybridResponsePipelineDiagnostics,
)

object BeautyQHybridResponsePipeline {
  def projectResponse(
    retrieval: HybridDocumentRetrievalResult[MasterServiceOfferVariantId],
    documentsByVariantId: Map[MasterServiceOfferVariantId, VariantSearchDocument],
    limits: BeautyQHybridResponseCarouselLimits,
    displayScorePolicy: BeautyQHybridDisplayScorePolicy =
      BeautyQHybridDisplayScorePolicy.LexicalThenSemantic,
  ): Either[QueryFailure, BeautyQHybridResponsePipelineResult] = {
    val policyResult = BeautyQHybridProjectionPolicy.lexicalFirstSemanticSupplement(retrieval)

    BeautyQHybridVariantProjection.project(policyResult, documentsByVariantId).map { variantProjection =>
      val providerServiceProjection =
        BeautyQHybridProviderServiceProjection.project(variantProjection, displayScorePolicy)
      val responseAdapter =
        BeautyQHybridResponseAdapter.responseWithProviderServiceCarousels(
          variantProjection = variantProjection,
          providerServiceProjection = providerServiceProjection,
          limits = limits,
          displayScorePolicy = displayScorePolicy,
        )

      BeautyQHybridResponsePipelineResult(
        response = responseAdapter.response,
        diagnostics = BeautyQHybridResponsePipelineDiagnostics(
          policy = policyResult.diagnostics,
          variantProjection = variantProjection.diagnostics,
          providerServiceProjection = providerServiceProjection.diagnostics,
          responseAdapter = responseAdapter.diagnostics,
        ),
      )
    }
  }
}
