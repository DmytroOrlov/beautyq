package leaderboard.search.gen2.core.hydration

import leaderboard.search.gen2.core.candidate.{BoundCandidateExecution, CandidateGenerationMetadata}
import leaderboard.search.gen2.core.materialization.MaterializedSearchDocuments

enum CandidateMissingDocumentPolicy {
  case Fail
}

enum CandidateHardConstraintPolicy {
  case RequireAll
}

final case class CandidateHydrationPolicy[Provenance](
  missingDocuments: CandidateMissingDocumentPolicy,
  hardConstraints: CandidateHardConstraintPolicy,
  provenance: Provenance,
)

sealed trait CandidateHydrationError[+Id]
object CandidateHydrationError {
  final case class SourceSnapshotMismatch(expected: String, actual: String) extends CandidateHydrationError[Nothing]
  final case class ProjectedDocumentsMismatch(expected: String, actual: String) extends CandidateHydrationError[Nothing]
  final case class ProjectionFormatMismatch(expected: String, actual: String) extends CandidateHydrationError[Nothing]
  final case class PointCountMismatch(expected: Int, actual: Int) extends CandidateHydrationError[Nothing]
  final case class Lookup[Id](error: OrderedCandidateDocumentLookupError[Id]) extends CandidateHydrationError[Id]
  final case class ConstraintViolations[Id](values: Vector[(Int, Id, Vector[PlannedConstraintViolation])]) extends CandidateHydrationError[Id]
}

final case class HydratedCandidate[Document, Id, Score, Provenance](
  id: Id,
  document: Document,
  score: Score,
  provenance: Provenance,
)

/** Candidate-only hydrated output. It deliberately exposes no total, facet, group or pagination
  * surface; baseline Elasticsearch remains the owner of those public result semantics. */
final class HydratedCandidateSearchResult[
  Document,
  Id,
  Score,
  Target,
  Metadata <: CandidateGenerationMetadata,
  Diagnostics,
  Provenance,
] private[hydration] (
  val candidates: Vector[HydratedCandidate[Document, Id, Score, Provenance]],
  val target: Target,
  val metadata: Metadata,
  val diagnostics: Diagnostics,
)

object CandidateHydrator {
  import CandidateHydrationError.*

  def hydrate[
    Document,
    Id,
    Score,
    Target,
    Metadata <: CandidateGenerationMetadata,
    Diagnostics,
    Provenance,
  ](
    executed: BoundCandidateExecution[Document, Id, Score, Target, Metadata, Diagnostics],
    materialized: MaterializedSearchDocuments[?, Document],
    policy: CandidateHydrationPolicy[Provenance],
  ): Either[CandidateHydrationError[Id], HydratedCandidateSearchResult[Document, Id, Score, Target, Metadata, Diagnostics, Provenance]] = {
    val metadata = executed.metadata
    val snapshotFingerprint = materialized.sourceSnapshot.contentFingerprint.value

    if (metadata.sourceContentFingerprint != snapshotFingerprint) Left(SourceSnapshotMismatch(metadata.sourceContentFingerprint, snapshotFingerprint))
    else if (metadata.projectedDocumentsFingerprint != materialized.projectedDocumentsFingerprint.value) Left(ProjectedDocumentsMismatch(metadata.projectedDocumentsFingerprint, materialized.projectedDocumentsFingerprint.value))
    else if (metadata.projectionFormatVersion != materialized.projectionFormatVersion.value) Left(ProjectionFormatMismatch(metadata.projectionFormatVersion, materialized.projectionFormatVersion.value))
    else if (metadata.pointCount != materialized.documents.size) Left(PointCountMismatch(metadata.pointCount, materialized.documents.size))
    else policy match {
      case CandidateHydrationPolicy(CandidateMissingDocumentPolicy.Fail, CandidateHardConstraintPolicy.RequireAll, provenance) =>
        OrderedCandidateDocumentLookup.lookup(executed.hits.map(_.id), materialized.documents)(executed.declaration.identityOf) match {
          case Left(error) => Left(Lookup(error))
          case Right(documents) =>
            val hydrated = executed.hits.zip(documents).zipWithIndex.map { case ((hit, document), index) =>
              HydratedCandidate(hit.id, document, hit.score, provenance)
            }
            val constraintErrors = hydrated.zipWithIndex.flatMap { case (candidate, index) =>
              PlannedConstraintAssertion.assertAll(candidate.document, executed.candidatePlan.hardConstraints) match {
                case Right(()) => Vector.empty
                case Left(violations) => Vector((index, candidate.id, violations))
              }
            }
            if (constraintErrors.isEmpty) Right(new HydratedCandidateSearchResult(hydrated, executed.target, metadata, executed.diagnostics))
            else Left(ConstraintViolations(constraintErrors))
        }
    }
  }
}
