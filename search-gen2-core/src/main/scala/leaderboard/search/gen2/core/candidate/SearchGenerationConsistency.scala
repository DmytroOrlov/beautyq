package leaderboard.search.gen2.core.candidate

sealed trait SearchGenerationConsistencyError

object SearchGenerationConsistencyError {
  final case class SourceContentMismatch(expected: String, actual: String) extends SearchGenerationConsistencyError
  final case class ProjectedDocumentsMismatch(expected: String, actual: String) extends SearchGenerationConsistencyError
  final case class ProjectionFormatMismatch(expected: String, actual: String) extends SearchGenerationConsistencyError
  final case class DocumentCountMismatch(expected: Long, actual: Long) extends SearchGenerationConsistencyError
}

object SearchGenerationConsistency {

  def verify(
    expected: SearchGenerationEvidence,
    actual: SearchGenerationEvidence,
  ): Either[SearchGenerationConsistencyError, Unit] =
    if (expected.sourceContentFingerprint != actual.sourceContentFingerprint)
      Left(SearchGenerationConsistencyError.SourceContentMismatch(expected.sourceContentFingerprint, actual.sourceContentFingerprint))
    else if (expected.projectedDocumentsFingerprint != actual.projectedDocumentsFingerprint)
      Left(SearchGenerationConsistencyError.ProjectedDocumentsMismatch(expected.projectedDocumentsFingerprint, actual.projectedDocumentsFingerprint))
    else if (expected.projectionFormatVersion != actual.projectionFormatVersion)
      Left(SearchGenerationConsistencyError.ProjectionFormatMismatch(expected.projectionFormatVersion, actual.projectionFormatVersion))
    else if (expected.documentCount != actual.documentCount)
      Left(SearchGenerationConsistencyError.DocumentCountMismatch(expected.documentCount, actual.documentCount))
    else
      Right(())
}
