package leaderboard.search.gen2.core.hydration

/** Errors proving that candidate identities cannot be mapped to one unambiguous document view. */
sealed trait OrderedCandidateDocumentLookupError[+Id]

object OrderedCandidateDocumentLookupError {
  final case class DuplicateDocumentIdentity[Id](id: Id, firstIndex: Int, duplicateIndex: Int)
      extends OrderedCandidateDocumentLookupError[Id]

  final case class MissingDocument[Id](candidateIndex: Int, id: Id)
      extends OrderedCandidateDocumentLookupError[Id]
}

/** One deterministic identity index for one materialized document vector. Candidate order is never
  * rebuilt or deduplicated here: the lookup returns documents in exactly the supplied hit order. */
object OrderedCandidateDocumentLookup {
  import OrderedCandidateDocumentLookupError.*

  def lookup[Document, Id](
    candidateIds: Vector[Id],
    documents: Vector[Document],
  )(identityOf: Document => Id): Either[OrderedCandidateDocumentLookupError[Id], Vector[Document]] = {
    val indexed = documents.zipWithIndex.foldLeft[Either[OrderedCandidateDocumentLookupError[Id], Map[Id, (Int, Document)]]](Right(Map.empty)) {
      case (acc, (document, index)) =>
        acc.flatMap { values =>
          val id = identityOf(document)
          values.get(id) match {
            case Some((firstIndex, _)) => Left(DuplicateDocumentIdentity(id, firstIndex, index))
            case None                  => Right(values.updated(id, index -> document))
          }
        }
    }

    indexed.flatMap { values =>
      candidateIds.zipWithIndex.foldLeft[Either[OrderedCandidateDocumentLookupError[Id], Vector[Document]]](Right(Vector.empty)) {
        case (acc, (id, candidateIndex)) =>
          acc.flatMap { found =>
            values.get(id) match {
              case Some((_, document)) => Right(found :+ document)
              case None                => Left(MissingDocument(candidateIndex, id))
            }
          }
      }
    }
  }
}
