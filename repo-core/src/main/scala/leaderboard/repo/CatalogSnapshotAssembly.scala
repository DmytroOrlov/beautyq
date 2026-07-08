package leaderboard.repo

import scala.deriving.Mirror

/** Assembles one deduplicated snapshot field (`List[A]`) from a
  * [[LoadedCatalog]]'s raw lists. [[fromValue]] (aggregate/value types) takes
  * priority over [[AssembleSnapshotFieldLowPriority.fromConventionalId]]
  * (normal entity types) - the same low-priority-trait disambiguation
  * pattern [[TupleSelect]]/[[TupleSelectLowPriority]] already use.
  */
private[repo] trait AssembleSnapshotField[Items <: Tuple, Field] {
  def apply(items: Items): Field
}

/** The only implementation of [[AssembleSnapshotField]]: a raw list selector
  * plus an already-resolved dedup key function. Named (not anonymous) so
  * `fromConventionalId` below - which must be `inline` to let its
  * `CatalogEntity.derived` call see a concrete `A` per summon site, see its
  * own doc - can construct an instance via an ordinary constructor call
  * instead of an inline-duplicated anonymous class/lambda body (both
  * rejected by the compiler for an `inline given`'s own right-hand side).
  */
private[repo] final class AssembleSnapshotFieldImpl[Items <: Tuple, A, K](
  raw: TupleSelect[Items, List[A]],
  keyOf: A => K,
) extends AssembleSnapshotField[Items, List[A]] {
  def apply(items: Items): List[A] = GraphLoading.distinctByKey(raw(items))(keyOf)
}

private[repo] object AssembleSnapshotField extends AssembleSnapshotFieldLowPriority {

  /** `A` has aggregate/value evidence: dedup by the same [[CatalogValue.Aux]]
    * key field (e.g. `serviceId`) the domain already declared for
    * materializing `.value[...]` edges - never a guessed key.
    */
  given fromValue[Items <: Tuple, A, K, Row](using
    raw: TupleSelect[Items, List[A]],
    value: CatalogValue.Aux[A, K, Row],
  ): AssembleSnapshotField[Items, List[A]] =
    new AssembleSnapshotFieldImpl(raw, value.valueSource.keyField.select)
}

/** Lower-priority home for the conventional-id entity fallback, so
  * [[AssembleSnapshotField.fromValue]] wins whenever both could apply to the
  * same `A`.
  */
private[repo] trait AssembleSnapshotFieldLowPriority {

  /** Falls back to conventional-`id` entity evidence for normal (non-value)
    * product types. Deliberately does not request `CatalogEntity.Aux[A, K]`
    * via a `using` clause: `K` would be a free type parameter of *this*
    * given with no other unification source, reproducing the "defaults to
    * `Any` before the given search runs" limitation `RepoGraph.scala`'s
    * `ConventionalIdKey` doc already describes for declaration-time specs.
    * Calling `CatalogEntity.derived[A](RepoEntity.derived[A])` directly
    * instead sidesteps that: `A` is inferred from the concrete `RepoEntity[A]`
    * argument (not solved as a free `using` parameter), so the `transparent
    * inline` macro's precise `Key` member is usable immediately, entirely
    * local to this given's own body - it never needs to be named or exposed
    * to an outer caller, only used structurally to build the key function
    * passed straight into `distinctByKey`.
    */
  inline given fromConventionalId[Items <: Tuple, A](using
    raw: TupleSelect[Items, List[A]],
    mirror: Mirror.ProductOf[A],
  ): AssembleSnapshotField[Items, List[A]] = {
    val entity = CatalogEntity.derived[A](RepoEntity.derived[A])
    new AssembleSnapshotFieldImpl(raw, entity.node.key.select)
  }
}

/** Recursively assembles an entire snapshot field-type tuple - a product
  * mirror's own `MirroredElemTypes`, e.g. `(List[Category], List[Service],
  * ...)` - from a [[LoadedCatalog]], one [[AssembleSnapshotField]] per field,
  * in the snapshot's own declared field order, independent of whatever order
  * [[LoadedCatalog.items]] happens to store its raw lists in.
  */
private[repo] trait AssembleSnapshotFields[Items <: Tuple, Fields <: Tuple] {
  def apply(items: Items): Fields
}
private[repo] object AssembleSnapshotFields {
  given nil[Items <: Tuple]: AssembleSnapshotFields[Items, EmptyTuple] =
    _ => EmptyTuple

  given cons[Items <: Tuple, Field, FieldsTail <: Tuple](using
    head: AssembleSnapshotField[Items, Field],
    tail: AssembleSnapshotFields[Items, FieldsTail],
  ): AssembleSnapshotFields[Items, Field *: FieldsTail] =
    items => head(items) *: tail(items)
}

/** `loaded.toSnapshot[Snapshot]`: builds a product snapshot case class
  * (every field `List[A]`) from a [[LoadedCatalog]], deduplicating each
  * field via [[AssembleSnapshotField]] and constructing `Snapshot` through
  * its own `Mirror.ProductOf` - so snapshot field order always follows
  * `Snapshot`'s own constructor, never [[LoadedCatalog.items]]'s storage
  * order. A standalone extension (not a method on [[LoadedCatalog]] itself,
  * which lives in `CatalogLoadedGraph.scala`), matching how `loadAll`
  * extends `MaterializedDeclaration` there.
  */
extension [Items <: Tuple](loaded: LoadedCatalog[Items]) {
  inline def toSnapshot[Snapshot](using
    mirror: Mirror.ProductOf[Snapshot],
    assemble: AssembleSnapshotFields[Items, mirror.MirroredElemTypes],
  ): Snapshot =
    mirror.fromProduct(assemble(loaded.items))
}
