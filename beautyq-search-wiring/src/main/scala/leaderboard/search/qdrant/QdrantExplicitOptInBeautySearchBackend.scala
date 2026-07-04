package leaderboard.search.qdrant

import leaderboard.model.QueryFailure
import leaderboard.search.semantic.SemanticCandidateBackend
import leaderboard.search.{BeautySearchBackend, BeautySearchResponse, ParsedSearchIntent, UserSearchInput}
import leaderboard.search.document.BeautySearchReadyCatalogDocuments
import leaderboard.search.dsl.BeautySearchSpec
import zio.IO

final class QdrantExplicitOptInBeautySearchBackend(
  spec: BeautySearchSpec,
  readyCatalog: BeautySearchReadyCatalogDocuments,
  semanticBackend: SemanticCandidateBackend[IO],
  val routePrerequisites: QdrantExplicitOptInRoutePrerequisites,
) extends BeautySearchBackend[IO] {

  override def search(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, BeautySearchResponse] =
    semanticBackend.candidates(input, intent).map { hits =>
      val assembly = QdrantCandidateAssembler.assemble(hits, readyCatalog.documents)
      QdrantCandidateResponseProjector.project(spec, input, assembly)
    }
}
