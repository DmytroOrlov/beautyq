package leaderboard.search.gen2.contract

trait PublicFacetSpec[Id] {
  def id: Id
}

/** Generic ordered facet inventory. Facet IDs are expected to be unique in the declaration vector;
  * field handles stay in the domain spec, while ordering, IDs and lookup are derived once. */
final class PublicFacetRegistry[Id, Spec <: PublicFacetSpec[Id]] private (entries: Vector[Spec]) {
  val ids: Vector[Id] = entries.map(_.id)
  def contains(id: Id): Boolean = ids.contains(id)
}

object PublicFacetRegistry {
  def apply[Id, Spec <: PublicFacetSpec[Id]](entries: Vector[Spec]): PublicFacetRegistry[Id, Spec] =
    new PublicFacetRegistry(entries)
}
