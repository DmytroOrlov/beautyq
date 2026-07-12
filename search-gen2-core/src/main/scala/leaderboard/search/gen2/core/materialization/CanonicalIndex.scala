package leaderboard.search.gen2.core.materialization

import scala.annotation.tailrec

/** Domain-neutral deterministic indexing mechanics for invalid as well as valid source collections.
  * A domain supplies its business key and canonical row/key ordering; this kernel owns representative
  * selection and duplicate reporting so those algorithms are not copied into every projection.
  */
object CanonicalIndex {
  def preservingFirst[A, K](rows: Vector[A])(key: A => K)(canonicalRowKey: A => String): Map[K, A] =
    rows.sortBy(canonicalRowKey).foldLeft(Map.empty[K, A]) {
      (acc, row) =>
        val rowKey = key(row)
        if (acc.contains(rowKey)) acc else acc.updated(rowKey, row)
    }

  /** Reports each value whose canonical key occurs more than once exactly once, at its second
    * canonical encounter, independent of input order and repeat count.
    */
  def duplicates[A](values: Vector[A])(canonicalKey: A => String): Vector[A] = {
    @tailrec
    def loop(remaining: Vector[A], seen: Set[String], reported: Set[String], acc: Vector[A]): Vector[A] =
      remaining match {
        case head +: tail =>
          val key = canonicalKey(head)
          if (!seen.contains(key)) loop(tail, seen + key, reported, acc)
          else if (!reported.contains(key)) loop(tail, seen, reported + key, acc :+ head)
          else loop(tail, seen, reported, acc)
        case _ => acc
      }

    loop(values.sortBy(canonicalKey), Set.empty, Set.empty, Vector.empty)
  }
}
