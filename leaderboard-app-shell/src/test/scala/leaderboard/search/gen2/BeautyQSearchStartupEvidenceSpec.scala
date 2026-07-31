package leaderboard.search.gen2

import leaderboard.search.beautyq.gen2.wiring.{BeautyQServingMode, StartupServingStatus, SupplementStartupPolicy}
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant

final class BeautyQSearchStartupEvidenceSpec extends AnyWordSpec {
  "BeautyQSearchGen2HttpService startup evidence" should {
    "publish captured provenance, measured durations and request-time non-negative ages" in {
      val capturedAt = Instant.parse("2026-07-31T10:00:00Z")
      val activatedAt = Instant.parse("2026-07-31T10:00:05Z")
      val evidence = new BeautyQSearchStartupEvidence(
        capturedAt,
        Some("postgres-snapshot-42"),
        1200000L,
        3400000L,
        activatedAt,
      )
      val status = new StartupServingStatus(
        SupplementStartupPolicy.Required,
        BeautyQServingMode.FullSearch,
        "healthy",
        None,
        restartRequired = false,
        "source-fingerprint",
        "documents-fingerprint",
        "es-reference",
        "es-target",
        Some("qdrant-generation"),
        Some("qdrant-collection"),
      )
      val json = BeautyQSearchGen2HttpService.encodeStatus(
        status,
        Some(evidence),
        Instant.parse("2026-07-31T10:00:10Z"),
      )
      assert(json.hcursor.get[String]("observedAt") == Right("2026-07-31T10:00:10Z"))
      val snapshot = json.hcursor.downField("snapshot")
      assert(snapshot.get[String]("capturedAt") == Right(capturedAt.toString))
      assert(snapshot.get[String]("sourceRevision") == Right("postgres-snapshot-42"))
      assert(snapshot.get[Long]("ageSeconds") == Right(10L))
      val durations = json.hcursor.downField("startupDurations")
      assert(durations.get[Long]("materializationNanos") == Right(1200000L))
      assert(durations.get[Long]("activationNanos") == Right(3400000L))
      val active = json.hcursor.downField("activeGenerations")
      assert(active.get[String]("activatedAt") == Right(activatedAt.toString))
      assert(active.get[Long]("ageSeconds") == Right(5L))

      val beforeCapture = BeautyQSearchGen2HttpService.encodeStatus(
        status,
        Some(evidence),
        Instant.parse("2026-07-31T09:59:59Z"),
      )
      assert(beforeCapture.hcursor.downField("snapshot").get[Long]("ageSeconds") == Right(0L))
      assert(beforeCapture.hcursor.downField("activeGenerations").get[Long]("ageSeconds") == Right(0L))
    }
  }
}
