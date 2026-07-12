package leaderboard.search.beautyq.gen2.materialization

import leaderboard.search.gen2.core.materialization.{CanonicalFingerprint, CanonicalSnapshot, ContentFingerprint}

/** Canonical, order-independent content fingerprint over every persisted source value in a
  * [[BeautyQSearchSnapshot]]: sorts each list/map independently before encoding, so `capturedAt`,
  * repository iteration order, and diagnostics never influence the result. Uses
  * [[CanonicalFingerprint]]'s length-prefixed canonical token writer (never case-class `toString`) so
  * free-form text such as a location address cannot be confused with a field delimiter.
  */
object BeautyQSnapshotFingerprint {
  import BeautyQSnapshotCanonicalRows.given

  val EncodingVersion: String = "beautyq-source-snapshot-v1"
  val SourceNames: Vector[String] = CanonicalSnapshot.sourceNames[BeautyQSearchSnapshot]

  def compute(snapshot: BeautyQSearchSnapshot): ContentFingerprint =
    ContentFingerprint(CanonicalFingerprint.sha256HexTokens(canonicalTokens(snapshot)))

  private def canonicalTokens(snapshot: BeautyQSearchSnapshot): Vector[String] =
    Vector(CanonicalFingerprint.token("encodingVersion", EncodingVersion)) ++
      CanonicalSnapshot.encode(snapshot).tokens

}
