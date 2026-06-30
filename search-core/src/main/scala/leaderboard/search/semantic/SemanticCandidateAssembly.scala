package leaderboard.search.semantic

import leaderboard.model.QueryFailure

final case class SemanticDocumentHit[Id](
  documentId: Id,
  score: Double,
)

trait SemanticDocumentLookup[F[_, _], Id, Doc] {
  def lookup(ids: List[Id]): F[QueryFailure, Map[Id, Doc]]
}

trait SemanticCandidateAssembler[Id, Doc, Candidate] {
  def assemble(hits: List[SemanticDocumentHit[Id]], documents: Map[Id, Doc]): List[Candidate]
}

trait SemanticResponseProjector[Assembly, Response] {
  def project(assembly: Assembly): Response
}

object SemanticCandidateAssembler {
  def assembleUnique[Id, Doc, Candidate](
    hits: List[SemanticDocumentHit[Id]],
    documents: Map[Id, Doc],
  )(project: (SemanticDocumentHit[Id], Doc) => Candidate): List[Candidate] = {
    val (_, candidatesReverse) = hits.foldLeft((Set.empty[Id], List.empty[Candidate])) {
      case ((seen, candidates), hit) if seen(hit.documentId) =>
        (seen, candidates)
      case ((seen, candidates), hit) =>
        documents.get(hit.documentId) match {
          case Some(document) => (seen + hit.documentId, project(hit, document) :: candidates)
          case None => (seen + hit.documentId, candidates)
        }
    }

    candidatesReverse.reverse
  }
}
