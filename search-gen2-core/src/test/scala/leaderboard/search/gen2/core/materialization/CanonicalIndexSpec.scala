package leaderboard.search.gen2.core.materialization

import org.scalatest.wordspec.AnyWordSpec

final class CanonicalIndexSpec extends AnyWordSpec {
  private final case class Row(id: String, value: String)

  "CanonicalIndex.preservingFirst" should {
    "select the canonical first representative independently of caller order" in {
      val later = Row("same", "z")
      val first = Row("same", "a")
      val other = Row("other", "b")

      val forward = CanonicalIndex.preservingFirst(Vector(later, other, first))(_.id)(_.value)
      val reverse = CanonicalIndex.preservingFirst(Vector(first, other, later))(_.id)(_.value)

      assert(forward == Map("same" -> first, "other" -> other))
      assert(reverse == forward)
    }
  }

  "CanonicalIndex.duplicates" should {
    "report each duplicate once in canonical-key order independently of caller order" in {
      val values  = Vector("b", "a", "b", "a", "b")
      val forward = CanonicalIndex.duplicates(values)(identity)
      val reverse = CanonicalIndex.duplicates(values.reverse)(identity)

      assert(forward == Vector("a", "b"))
      assert(reverse == forward)
    }
  }
}
