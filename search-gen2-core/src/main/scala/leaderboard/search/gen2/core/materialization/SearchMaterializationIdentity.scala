package leaderboard.search.gen2.core.materialization

import java.time.Instant

final case class ContentFingerprint(value: String)

final case class ProjectedDocumentsFingerprint(value: String)
final case class ProjectionFormatVersion(value: String)

final case class VersionedSnapshot[A](
  value: A,
  contentFingerprint: ContentFingerprint,
  capturedAt: Instant,
)

/** One consistent source snapshot boundary: [[load]] must observe every read as of one consistent
  * point in time, never as a sequence of independently observable reads. `LoadError` stays a type
  * parameter so this generic kernel names no domain-specific error type; a domain source adapter owns
  * both the concrete error type and the mechanism that makes its reads consistent.
  */
trait SearchSnapshotSource[F[_, _], LoadError, Snapshot] {
  def load: F[LoadError, VersionedSnapshot[Snapshot]]
}
