package leaderboard.search.gen2.core.materialization

import izumi.functional.bio.{Error2, F}

final case class MaterializedSearchDocuments[Snapshot, Document](
  sourceSnapshot: VersionedSnapshot[Snapshot],
  documents: Vector[Document],
  projectedDocumentsFingerprint: ProjectedDocumentsFingerprint,
  projectionFormatVersion: ProjectionFormatVersion,
)

sealed trait SearchMaterializationError[+LoadError, +ProjectionError] extends Product with Serializable

object SearchMaterializationError {
  final case class Snapshot[LoadError](error: LoadError) extends SearchMaterializationError[LoadError, Nothing]

  final case class Projection[ProjectionError](error: ProjectionError) extends SearchMaterializationError[Nothing, ProjectionError]
}

/** Generic load -> project -> fingerprint orchestration: loads one [[VersionedSnapshot]] from `source`,
  * projects its value through the domain-supplied `project`, and computes the projected-documents
  * fingerprint from the projection result via the domain-supplied `fingerprint`. The source content
  * fingerprint carried by the loaded [[VersionedSnapshot]] is reused as-is and never recomputed or
  * replaced.
  */
object SearchMaterializer {
  def materialize[
    F[+_, +_]: Error2,
    LoadError,
    Snapshot,
    ProjectionError,
    Document,
  ](
    source: SearchSnapshotSource[F, LoadError, Snapshot],
    project: Snapshot => Either[ProjectionError, Vector[Document]],
    fingerprint: Vector[Document] => ProjectedDocumentsFingerprint,
    projectionFormatVersion: ProjectionFormatVersion,
  ): F[SearchMaterializationError[LoadError, ProjectionError], MaterializedSearchDocuments[Snapshot, Document]] =
    source.load
      .leftMap(SearchMaterializationError.Snapshot.apply)
      .flatMap {
        versionedSnapshot =>
          project(versionedSnapshot.value) match {
            case Right(documents) =>
              F.pure(
                MaterializedSearchDocuments(
                  sourceSnapshot = versionedSnapshot,
                  documents = documents,
                  projectedDocumentsFingerprint = fingerprint(documents),
                  projectionFormatVersion = projectionFormatVersion,
                )
              )
            case Left(error) =>
              F.fail(SearchMaterializationError.Projection(error))
          }
      }
}
