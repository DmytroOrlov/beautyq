package leaderboard.search.hybrid

import izumi.functional.bio.{Error2, F}
import leaderboard.model.QueryFailure
import leaderboard.search.*
import leaderboard.search.dsl.BeautySearchSpec
import leaderboard.search.qdrant.{QdrantCandidateAssembler, QdrantCandidateResponseProjector}
import leaderboard.search.routing.SearchBackendRoute
import leaderboard.search.semantic.{SemanticCandidateBackend, VariantSearchDocumentLookup}

final class ExperimentalHybridSearchBackend[F[+_, +_]: Error2](
  spec: BeautySearchSpec,
  lexicalBackend: BeautySearchBackend[F],
  routeDecision: (UserSearchInput, ParsedSearchIntent) => SearchBackendRoute,
  semanticBackend: SemanticCandidateBackend[F],
  documentLookup: VariantSearchDocumentLookup[F],
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
              val documents = hits.flatMap(hit => documentsById.get(hit.variantId))
              val assembly = QdrantCandidateAssembler.assemble(hits, documents)
              val qdrantResponse = QdrantCandidateResponseProjector.project(spec, input, assembly)
              val esVariantIds = esResponse.variantCarousel.map(_.variantId).toSet
              val supplement = qdrantResponse.variantCarousel.filterNot(variant => esVariantIds.contains(variant.variantId))
              if (supplement.isEmpty) esResponse
              else {
                val variantCap = math.min(input.limit, spec.carouselSpec.variantSize)
                esResponse.copy(
                  variantCarousel = (esResponse.variantCarousel ++ supplement).take(variantCap),
                )
              }
            }
          }
        }
    }
}
