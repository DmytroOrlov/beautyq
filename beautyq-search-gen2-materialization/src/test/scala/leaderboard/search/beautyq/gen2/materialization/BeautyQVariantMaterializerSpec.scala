package leaderboard.search.beautyq.gen2.materialization

import izumi.functional.bio.Error2
import izumi.functional.bio.impl.BioEither
import leaderboard.model.QueryFailure
import leaderboard.search.gen2.core.materialization.{ContentFingerprint, SearchSnapshotSource, VersionedSnapshot}
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant

final class BeautyQVariantMaterializerSpec extends AnyWordSpec {
  import BeautyQGen2MaterializationFixtures.*

  private given Error2[Either] = BioEither

  private final class FixedSnapshotSource(result: Either[SnapshotLoadError, VersionedSnapshot[BeautyQSearchSnapshot]])
    extends SearchSnapshotSource[Either, SnapshotLoadError, BeautyQSearchSnapshot] {
    def load: Either[SnapshotLoadError, VersionedSnapshot[BeautyQSearchSnapshot]] = result
  }

  private val capturedAt = Instant.parse("2026-01-01T00:00:00Z")

  private def materializerFor(
    result: Either[SnapshotLoadError, VersionedSnapshot[BeautyQSearchSnapshot]]
  ): BeautyQVariantMaterializer[Either] =
    new BeautyQVariantMaterializer.FromSnapshotSource[Either](new FixedSnapshotSource(result))

  "BeautyQVariantMaterializer" should {
    "turn a snapshot load error into BeautyQMaterializationError.Snapshot" in {
      val loadError = SnapshotLoadError.Repository(QueryFailure.domain("boom"))
      val materializer = materializerFor(Left(loadError))

      assert(materializer.load == Left(BeautyQMaterializationError.Snapshot(loadError)))
    }

    "turn projection errors into BeautyQMaterializationError.Projection" in {
      val invalidSnapshot = snapshot.copy(masterServiceOffers = Vector.empty)
      val versioned = VersionedSnapshot(
        value = invalidSnapshot,
        contentFingerprint = BeautyQSnapshotFingerprint.compute(invalidSnapshot),
        sourceRevision = None,
        capturedAt = capturedAt,
      )
      val materializer = materializerFor(Right(versioned))

      val expectedErrors = BeautyQVariantProjectionGen2.project(invalidSnapshot) match {
        case Left(errors) => errors
        case Right(_)     => fail("fixture snapshot was expected to be invalid")
      }

      assert(materializer.load == Left(BeautyQMaterializationError.Projection(expectedErrors)))
    }

    "reuse the exact loaded VersionedSnapshot on success" in {
      val versioned = VersionedSnapshot(
        value = snapshot,
        contentFingerprint = BeautyQSnapshotFingerprint.compute(snapshot),
        sourceRevision = None,
        capturedAt = capturedAt,
      )
      val materializer = materializerFor(Right(versioned))

      materializer.load match {
        case Right(materialized) => assert(materialized.sourceSnapshot == versioned)
        case Left(error)         => fail(s"expected success, got $error")
      }
    }

    "return the projected documents on success" in {
      val versioned = VersionedSnapshot(snapshot, BeautyQSnapshotFingerprint.compute(snapshot), sourceRevision = None, capturedAt = capturedAt)
      val materializer = materializerFor(Right(versioned))

      val expectedDocuments = BeautyQVariantProjectionGen2.project(snapshot) match {
        case Right(documents) => documents
        case Left(errors)     => fail(s"fixture snapshot was expected to be valid, got $errors")
      }

      materializer.load match {
        case Right(materialized) => assert(materialized.documents == expectedDocuments)
        case Left(error)         => fail(s"expected success, got $error")
      }
    }

    "compute the projected-documents fingerprint on success" in {
      val versioned = VersionedSnapshot(snapshot, BeautyQSnapshotFingerprint.compute(snapshot), sourceRevision = None, capturedAt = capturedAt)
      val materializer = materializerFor(Right(versioned))

      materializer.load match {
        case Right(materialized) =>
          assert(materialized.projectedDocumentsFingerprint == BeautyQProjectedDocumentsFingerprint.compute(materialized.documents))
        case Left(error) => fail(s"expected success, got $error")
      }
    }

    "expose the current projection format version on success" in {
      val versioned = VersionedSnapshot(snapshot, BeautyQSnapshotFingerprint.compute(snapshot), sourceRevision = None, capturedAt = capturedAt)
      val materializer = materializerFor(Right(versioned))

      materializer.load match {
        case Right(materialized) => assert(materialized.projectionFormatVersion.value == "beautyq-variant-projection-v1")
        case Left(error)         => fail(s"expected success, got $error")
      }
    }

    "never recompute or replace the source content fingerprint" in {
      val deliberatelyWrongFingerprint = ContentFingerprint("not-the-real-fingerprint")
      val versioned = VersionedSnapshot(snapshot, deliberatelyWrongFingerprint, sourceRevision = None, capturedAt = capturedAt)
      val materializer = materializerFor(Right(versioned))

      materializer.load match {
        case Right(materialized) => assert(materialized.sourceSnapshot.contentFingerprint == deliberatelyWrongFingerprint)
        case Left(error)         => fail(s"expected success, got $error")
      }
    }
  }
}
