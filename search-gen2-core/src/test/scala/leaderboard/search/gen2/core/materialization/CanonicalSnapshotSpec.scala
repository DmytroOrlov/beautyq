package leaderboard.search.gen2.core.materialization

import org.scalatest.wordspec.AnyWordSpec

final class CanonicalSnapshotSpec extends AnyWordSpec {
  private final case class First(value: String)
  private final case class Second(value: Int)
  private final case class SampleSnapshot(first: Vector[First], second: Vector[Second])

  private given CanonicalSourceRows[First] =
    CanonicalSourceRows("first")(value => CanonicalFingerprint.row("first.value" -> value.value))

  private given CanonicalSourceRows[Second] =
    CanonicalSourceRows("second")(value => CanonicalFingerprint.row("second.value" -> value.value.toString))

  "CanonicalSnapshot" should {
    "derive source inventory and traversal order from the snapshot product" in {
      val snapshot = SampleSnapshot(
        first = Vector(First("b"), First("a")),
        second = Vector(Second(2), Second(1)),
      )

      val encoded = CanonicalSnapshot.encode(snapshot)
      assert(encoded.sourceNames == Vector("first", "second"))
      assert(encoded.tokens == Vector(
        CanonicalFingerprint.token("first.value", "a"),
        CanonicalFingerprint.token("first.value", "b"),
        CanonicalFingerprint.token("second.value", "1"),
        CanonicalFingerprint.token("second.value", "2"),
      ))
    }

    "expose the same source names without requiring a snapshot value" in {
      assert(CanonicalSnapshot.sourceNames[SampleSnapshot] == Vector("first", "second"))
    }
  }

  // A snapshot is not always a product of Vector-of-many members: a singleton configuration/global
  // row alongside sibling collections is a real shape (see docs/gen2/SEARCH_GEN2_FRAMEWORK_SCOPE.md),
  // not only the all-Vector shape BeautyQ happens to use.
  "CanonicalSnapshot with a Single-cardinality member" should {
    final case class Config(schemaVersion: Int)
    final case class MixedSnapshot(first: Vector[First], config: CanonicalSnapshot.Single[Config])

    given CanonicalSourceRows[Config] =
      CanonicalSourceRows("config")(value => CanonicalFingerprint.row("config.schemaVersion" -> value.schemaVersion.toString))

    "contribute exactly one row for a Single member, ordered by product position" in {
      val snapshot = MixedSnapshot(first = Vector(First("b"), First("a")), config = CanonicalSnapshot.Single(Config(3)))

      val encoded = CanonicalSnapshot.encode(snapshot)
      assert(encoded.sourceNames == Vector("first", "config"))
      assert(encoded.tokens == Vector(
        CanonicalFingerprint.token("first.value", "a"),
        CanonicalFingerprint.token("first.value", "b"),
        CanonicalFingerprint.token("config.schemaVersion", "3"),
      ))
    }

    "expose the same source names without requiring a snapshot value" in {
      assert(CanonicalSnapshot.sourceNames[MixedSnapshot] == Vector("first", "config"))
    }
  }
}
