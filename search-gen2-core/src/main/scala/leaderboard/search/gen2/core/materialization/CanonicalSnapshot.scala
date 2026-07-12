package leaderboard.search.gen2.core.materialization

import scala.deriving.Mirror

/** One domain-owned canonical row policy registered for a source entity type. `sourceName` is the
  * stable catalog-facing entity name; row field selection remains domain policy, while complete
  * snapshot traversal and ordering are derived generically from the snapshot product type.
  */
trait CanonicalSourceRows[A] {
  def sourceName: String
  def row(value: A): CanonicalFingerprint.CanonicalRow
}

object CanonicalSourceRows {
  def apply[A](name: String)(encode: A => CanonicalFingerprint.CanonicalRow): CanonicalSourceRows[A] =
    new CanonicalSourceRows[A] {
      def sourceName: String = name
      def row(value: A): CanonicalFingerprint.CanonicalRow = encode(value)
    }
}

final case class CanonicalSnapshotEncoding(
  sourceNames: Vector[String],
  tokens: Vector[String],
)

private[materialization] trait CanonicalSnapshotTuple[Fields <: Tuple] {
  def sourceNames: Vector[String]
  def encode(fields: Fields): Vector[String]
}

private[materialization] object CanonicalSnapshotTuple {
  given empty: CanonicalSnapshotTuple[EmptyTuple] with {
    def sourceNames: Vector[String] = Vector.empty
    def encode(fields: EmptyTuple): Vector[String] = Vector.empty
  }

  given cons[Entity, Tail <: Tuple](using
    head: CanonicalSourceRows[Entity],
    tail: CanonicalSnapshotTuple[Tail],
  ): CanonicalSnapshotTuple[Vector[Entity] *: Tail] with {
    def sourceNames: Vector[String] = head.sourceName +: tail.sourceNames

    def encode(fields: Vector[Entity] *: Tail): Vector[String] = {
      val values *: remaining = fields
      CanonicalFingerprint.orderedTokens(values.map(head.row)) ++ tail.encode(remaining)
    }
  }

  given single[Entity, Tail <: Tuple](using
    head: CanonicalSourceRows[Entity],
    tail: CanonicalSnapshotTuple[Tail],
  ): CanonicalSnapshotTuple[CanonicalSnapshot.Single[Entity] *: Tail] with {
    def sourceNames: Vector[String] = head.sourceName +: tail.sourceNames

    def encode(fields: CanonicalSnapshot.Single[Entity] *: Tail): Vector[String] = {
      val one *: remaining = fields
      CanonicalFingerprint.orderedTokens(Vector(head.row(one.value))) ++ tail.encode(remaining)
    }
  }
}

object CanonicalSnapshot {
  /** Marks one snapshot product member as carrying exactly one source row, e.g. a singleton
    * configuration/global row alongside sibling `Vector`-of-many members. A distinct wrapper type -
    * rather than reusing bare `Entity` - keeps given resolution for `Vector[Entity] *: Tail` and
    * `Single[Entity] *: Tail` unambiguous even when `Entity` itself happens to be a `Vector[_]`.
    */
  final case class Single[A](value: A)

  inline def sourceNames[Snapshot <: Product](using
    mirror: Mirror.ProductOf[Snapshot],
    encoder: CanonicalSnapshotTuple[mirror.MirroredElemTypes],
  ): Vector[String] = encoder.sourceNames

  inline def encode[Snapshot <: Product](snapshot: Snapshot)(using
    mirror: Mirror.ProductOf[Snapshot],
    encoder: CanonicalSnapshotTuple[mirror.MirroredElemTypes],
  ): CanonicalSnapshotEncoding =
    CanonicalSnapshotEncoding(
      sourceNames = encoder.sourceNames,
      tokens = encoder.encode(Tuple.fromProductTyped(snapshot)),
    )
}
