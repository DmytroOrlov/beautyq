package leaderboard.search.gen2.core.materialization

import izumi.functional.bio.Error2
import izumi.functional.bio.impl.BioEither
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant

/** Neutral fixture proof: `SampleSnapshot`/`SampleDocument`/`SampleLoadError`/`SampleProjectionError`
  * stand in for any domain's real types - no BeautyQ type appears anywhere in this file.
  */
final class SearchMaterializerSpec extends AnyWordSpec {
  private given Error2[Either] = BioEither

  private final case class SampleSnapshot(values: Vector[Int])
  private final case class SampleDocument(value: Int)
  private final case class SampleLoadError(reason: String)
  private final case class SampleProjectionError(reason: String)

  private final class FixedSnapshotSource(result: Either[SampleLoadError, VersionedSnapshot[SampleSnapshot]])
    extends SearchSnapshotSource[Either, SampleLoadError, SampleSnapshot] {
    def load: Either[SampleLoadError, VersionedSnapshot[SampleSnapshot]] = result
  }

  private val capturedAt = Instant.parse("2026-01-01T00:00:00Z")
  private val sampleFormatVersion = ProjectionFormatVersion("sample-projection-v1")
  private val sampleSnapshot = SampleSnapshot(Vector(1, 2, 3))
  private val sampleVersioned =
    VersionedSnapshot(sampleSnapshot, ContentFingerprint("sample-fingerprint"), capturedAt = capturedAt)

  private def projectSuccessfully(snapshot: SampleSnapshot): Either[SampleProjectionError, Vector[SampleDocument]] =
    Right(snapshot.values.map(SampleDocument.apply))

  private def fingerprintOf(documents: Vector[SampleDocument]): ProjectedDocumentsFingerprint =
    ProjectedDocumentsFingerprint(CanonicalFingerprint.sha256HexTokens(documents.map(_.value.toString)))

  private def materialize(
    source: SearchSnapshotSource[Either, SampleLoadError, SampleSnapshot],
    project: SampleSnapshot => Either[SampleProjectionError, Vector[SampleDocument]] = projectSuccessfully,
    fingerprint: Vector[SampleDocument] => ProjectedDocumentsFingerprint = fingerprintOf,
  ): Either[SearchMaterializationError[SampleLoadError, SampleProjectionError], MaterializedSearchDocuments[SampleSnapshot, SampleDocument]] =
    SearchMaterializer.materialize[Either, SampleLoadError, SampleSnapshot, SampleProjectionError, SampleDocument](
      source,
      project,
      fingerprint,
      sampleFormatVersion,
    )

  "SearchMaterializer.materialize" should {
    "map a source load failure into SearchMaterializationError.Snapshot" in {
      val loadError = SampleLoadError("boom")
      assert(materialize(new FixedSnapshotSource(Left(loadError))) == Left(SearchMaterializationError.Snapshot(loadError)))
    }

    "map a projection failure into SearchMaterializationError.Projection" in {
      val projectionError = SampleProjectionError("invalid")
      val result = materialize(new FixedSnapshotSource(Right(sampleVersioned)), _ => Left(projectionError))
      assert(result == Left(SearchMaterializationError.Projection(projectionError)))
    }

    "preserve the exact loaded VersionedSnapshot on success" in {
      materialize(new FixedSnapshotSource(Right(sampleVersioned))) match {
        case Right(materialized) => assert(materialized.sourceSnapshot == sampleVersioned)
        case Left(error)         => fail(s"expected success, got $error")
      }
    }

    "return the projected documents on success" in {
      materialize(new FixedSnapshotSource(Right(sampleVersioned))) match {
        case Right(materialized) => assert(materialized.documents == Vector(SampleDocument(1), SampleDocument(2), SampleDocument(3)))
        case Left(error)         => fail(s"expected success, got $error")
      }
    }

    "invoke the supplied fingerprint function with the projected documents" in {
      val suppliedFingerprint = ProjectedDocumentsFingerprint("supplied-fingerprint")
      materialize(new FixedSnapshotSource(Right(sampleVersioned)), fingerprint = _ => suppliedFingerprint) match {
        case Right(materialized) => assert(materialized.projectedDocumentsFingerprint == suppliedFingerprint)
        case Left(error)         => fail(s"expected success, got $error")
      }
    }

    "expose the exact supplied projection format version on success" in {
      materialize(new FixedSnapshotSource(Right(sampleVersioned))) match {
        case Right(materialized) => assert(materialized.projectionFormatVersion == sampleFormatVersion)
        case Left(error)         => fail(s"expected success, got $error")
      }
    }

    "never recompute or replace the source content fingerprint" in {
      val deliberatelyWrongFingerprint = ContentFingerprint("not-the-real-fingerprint")
      val versioned = sampleVersioned.copy(contentFingerprint = deliberatelyWrongFingerprint)

      materialize(new FixedSnapshotSource(Right(versioned))) match {
        case Right(materialized) => assert(materialized.sourceSnapshot.contentFingerprint == deliberatelyWrongFingerprint)
        case Left(error)         => fail(s"expected success, got $error")
      }
    }
  }
}
