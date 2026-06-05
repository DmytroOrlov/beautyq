package leaderboard.search.document

import izumi.functional.bio.{Error2, F}
import leaderboard.model.QueryFailure

trait VariantSearchDocumentSnapshotProvider[F[_, _]] {
  def loadSnapshot(): F[QueryFailure, List[VariantSearchDocument]]
}

final class InMemoryVariantSearchDocumentSnapshotProvider[F[+_, +_]: Error2](
  documents: List[VariantSearchDocument]
) extends VariantSearchDocumentSnapshotProvider[F] {
  override def loadSnapshot(): F[QueryFailure, List[VariantSearchDocument]] =
    F.pure(documents)
}
