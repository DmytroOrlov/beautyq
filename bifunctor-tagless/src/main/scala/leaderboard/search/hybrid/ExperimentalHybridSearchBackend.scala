package leaderboard.search.hybrid

import izumi.functional.bio.{Error2, F}
import leaderboard.model.QueryFailure
import leaderboard.search.*
import leaderboard.search.dsl.{BeautyQSearchPresentation, BeautySearchSpec}
import leaderboard.search.qdrant.{QdrantCandidateAssembler, QdrantCandidateResponseProjector}
import leaderboard.search.routing.SearchBackendRoute
import leaderboard.search.semantic.{SemanticCandidateBackend, VariantSearchDocumentLookup}

final class ExperimentalHybridSearchBackend[F[+_, +_]: Error2](
  spec: BeautySearchSpec,
  lexicalBackend: BeautySearchBackend[F],
  routeDecision: (UserSearchInput, ParsedSearchIntent) => SearchBackendRoute,
  semanticBackend: SemanticCandidateBackend[F],
  documentLookup: VariantSearchDocumentLookup[F],
  supplementPolicy: QdrantVariantSupplementPolicy = QdrantVariantSupplementPolicy.AppendAll,
) extends BeautySearchBackend[F] {

  override def search(input: UserSearchInput, intent: ParsedSearchIntent): F[QueryFailure, BeautySearchResponse] =
    routeDecision(input, intent) match {
      case SearchBackendRoute.ElasticsearchOnly =>
        lexicalBackend.search(input, intent)

      case SearchBackendRoute.QdrantCandidateRoute =>
        F.flatMap(semanticBackend.candidates(input, intent)) { hits =>
          F.map(documentLookup.lookup(hits.map(_.variantId))) { documentsById =>
            val documents = hits.flatMap(hit => documentsById.get(hit.variantId))
            val assembly = QdrantCandidateAssembler.assemble(hits, documents)
            QdrantCandidateResponseProjector.project(spec, input, assembly)
          }
        }

      case SearchBackendRoute.ElasticsearchThenQdrantFallback =>
        // Runtime fallback is intentionally not implemented in this experimental skeleton.
        lexicalBackend.search(input, intent)

      case SearchBackendRoute.ElasticsearchWithQdrantVariantSupplement =>
        F.flatMap(lexicalBackend.search(input, intent)) { esResponse =>
          F.flatMap(semanticBackend.candidates(input, intent)) { hits =>
            F.map(documentLookup.lookup(hits.map(_.variantId))) { documentsById =>
              val esBaselineResponse = esResponse.copy(
                variantCarousel = esResponse.variantCarousel.map(_.copy(resultOrigin = VariantResultOrigin.EsBaseline)),
                executionMode = BeautySearchExecutionMode.EsPlusQdrantSupplement,
              )
              val esVariantIds = esResponse.variantCarousel.map(_.variantId).toSet
              val variantLimit = BeautyQSearchPresentation.variantLimit(spec.carouselSpec).fold(error => throw new IllegalStateException(error.message), identity)
              val variantCap = math.min(input.limit, variantLimit)
              val capRoom = math.max(0, variantCap - esResponse.variantCarousel.size)
              val selectedHits = supplementPolicy.select(intent, esVariantIds, hits, documentsById, capRoom)
              val provenancePolicy = qdrantSupplementPolicyName(supplementPolicy)
              if (selectedHits.isEmpty) {
                esBaselineResponse.copy(qdrantSupplement = QdrantSupplementSummary.usedNoAppend(provenancePolicy))
              }
              else {
                val selectedDocuments = selectedHits.flatMap(hit => documentsById.get(hit.variantId))
                val assembly = QdrantCandidateAssembler.assemble(selectedHits, selectedDocuments)
                val supplement = QdrantCandidateResponseProjector.project(spec, input, assembly).variantCarousel.map(
                  _.copy(resultOrigin = VariantResultOrigin.QdrantSupplement)
                )
                val variantCarousel = (esBaselineResponse.variantCarousel ++ supplement).take(variantCap)
                val appendedVariantIds = supplement.map(_.variantId)
                val qdrantSupplement =
                  if (appendedVariantIds.isEmpty) QdrantSupplementSummary.usedNoAppend(provenancePolicy)
                  else QdrantSupplementSummary.usedWithAppend(provenancePolicy, appendedVariantIds)
                esBaselineResponse.copy(
                  variantCarousel = variantCarousel,
                  qdrantSupplement = qdrantSupplement,
                )
              }
            }
          }
        }
    }

  private def qdrantSupplementPolicyName(policy: QdrantVariantSupplementPolicy): QdrantSupplementPolicyName =
    policy match {
      case QdrantVariantSupplementPolicy.ExplicitConstraintsFilterPlusTop1 =>
        QdrantSupplementPolicyName.ExplicitConstraintsFilterPlusTop1
      case QdrantVariantSupplementPolicy.AppendAll =>
        QdrantSupplementPolicyName.None
    }
}
