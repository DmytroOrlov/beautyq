package leaderboard.repo

import izumi.functional.bio.{Error2, F}
import leaderboard.model.QueryFailure
import leaderboard.repo.RepoOp.{AllValues, ManyByKey, OptionalByKey, ValueByKey}

/** A typed node in the domain graph: an entity together with the
  * selector-derived field for its own key. Relation builders hang off the node
  * so a graph reads in a model-first form (`category.selfTree(...)`,
  * `master.hasMany(...)`).
  */
final case class EntityNode[A, K](entity: RepoEntity[A], key: RepoField[A, K]) {

  /** A recursive self tree (e.g. categories under a parent). `parent` is the
    * selector of the edge from a node to its parent key; `children` loads direct
    * children by parent key.
    */
  inline def selfTree[F[_, _]](inline parent: A => K, children: ManyByKey[F, K, A]): Relation.SelfTree[F, A, K] =
    Relation.SelfTree(this, entity.field(parent), children)

  /** A has-many relation to a child source joined on the child's `by` field. */
  inline def hasMany[F[_, _], C, CK](child: EntityNode[C, CK])(inline by: C => K, load: ManyByKey[F, K, C]): Relation.HasMany[F, A, K, C, CK] =
    Relation.HasMany(this, child, child.entity.field(by), load)

  /** A has-one/value relation to a value source joined on the value's `by` field. */
  inline def hasValue[F[_, _], V, VK, Row](value: RepoValueSource[V, VK, Row])(inline by: V => K, load: ValueByKey[F, K, V]): Relation.HasValue[F, A, K, V, VK, Row] =
    Relation.HasValue(this, value, RepoField.derived(by), load)

  /** All roots/instances of this entity. */
  def all[F[_, _]](load: AllValues[F, A]): Relation.All[F, A, K] =
    Relation.All(this, load)
}

object Relation {

  /** A recursive self tree. Retains the node and the parent-edge field. */
  final case class SelfTree[F[_, _], A, K](
    node: EntityNode[A, K],
    parent: RepoField[A, K],
    children: ManyByKey[F, K, A],
  )

  /** A has-many relation. Retains both parent and child nodes plus the child
    * foreign-key field used for the join.
    */
  final case class HasMany[F[_, _], P, K, C, CK](
    parent: EntityNode[P, K],
    child: EntityNode[C, CK],
    foreignKey: RepoField[C, K],
    load: ManyByKey[F, K, C],
  )

  /** A has-one/value relation. Retains the parent node, the value source, and
    * the value key field used for the join.
    */
  final case class HasValue[F[_, _], P, K, V, VK, Row](
    parent: EntityNode[P, K],
    value: RepoValueSource[V, VK, Row],
    valueKey: RepoField[V, K],
    load: ValueByKey[F, K, V],
  )

  /** An all-roots relation. Retains the source node. */
  final case class All[F[_, _], A, K](
    node: EntityNode[A, K],
    load: AllValues[F, A],
  )
}

/** Generic interpreter for the domain graph relations.
  *
  * The interpreter is intentionally free of any BeautyQ-specific knowledge: it
  * only understands [[Relation]] primitives and repo operations, executing keys
  * through [[RepoField.select]].
  */
object GraphLoading {

  /** Preorder traversal of a self tree starting from `rootKey`. The synthetic
    * root itself is never part of the result; only its descendants are loaded.
    * Sibling order follows repo-returned order; each node is emitted before its
    * descendants.
    */
  def selfTreeFrom[F[+_, +_]: Error2, A, K](relation: Relation.SelfTree[F, A, K], rootKey: K): F[QueryFailure, List[A]] = {
    def loop(parentKey: K): F[QueryFailure, List[A]] =
      relation.children.run(parentKey).flatMap {
        directChildren =>
          directChildren.foldRight(F.pure(List.empty[A]): F[QueryFailure, List[A]]) {
            (child, acc) =>
              for {
                tail        <- acc
                descendants <- loop(relation.node.key.select(child))
              } yield child :: (descendants ++ tail)
          }
      }

    loop(rootKey)
  }

  /** Load all children of all parents, preserving parent order then child order. */
  def manyFor[F[+_, +_]: Error2, P, K, C, CK](relation: Relation.HasMany[F, P, K, C, CK], parents: List[P]): F[QueryFailure, List[C]] =
    parents.foldRight(F.pure(List.empty[C]): F[QueryFailure, List[C]]) {
      (parent, acc) =>
        for {
          tail   <- acc
          loaded <- relation.load.run(relation.parent.key.select(parent))
        } yield loaded ++ tail
    }

  /** Load the value of each parent, preserving parent order. */
  def valueFor[F[+_, +_]: Error2, P, K, V, VK, Row](relation: Relation.HasValue[F, P, K, V, VK, Row], parents: List[P]): F[QueryFailure, List[V]] =
    parents.foldRight(F.pure(List.empty[V]): F[QueryFailure, List[V]]) {
      (parent, acc) =>
        for {
          tail   <- acc
          loaded <- relation.load.run(relation.parent.key.select(parent))
        } yield loaded :: tail
    }

  /** Load all instances of an all-roots relation. */
  def allOf[F[_, _], A, K](relation: Relation.All[F, A, K]): F[QueryFailure, List[A]] =
    relation.load.run()

  /** Distinct-by-key, preserving the first occurrence and the output order. */
  def distinctByKey[A, K](items: List[A])(key: A => K): List[A] =
    items
      .foldLeft((Set.empty[K], List.empty[A])) {
        case ((seen, acc), item) =>
          val itemKey = key(item)
          if (seen.contains(itemKey)) {
            (seen, acc)
          } else {
            (seen + itemKey, item :: acc)
          }
      }
      ._2
      .reverse

  /** Seed-scoped required optional load: each seed item must resolve, otherwise
    * the load fails with the canonical missing-entity message. Order follows the
    * seed item order.
    */
  def seedRequired[F[+_, +_]: Error2, A, K, B](
    items: List[A],
    entityName: String,
    key: A => K,
    load: OptionalByKey[F, K, B],
  ): F[QueryFailure, List[B]] =
    items.foldRight(F.pure(List.empty[B]): F[QueryFailure, List[B]]) {
      (item, acc) =>
        for {
          tail <- acc
          loaded <- load.run(key(item)).flatMap {
            case Some(value) =>
              F.pure(value)
            case None =>
              F.fail(QueryFailure.domain(s"Seed-scoped search snapshot is missing $entityName for seed item $item"))
          }
        } yield loaded :: tail
    }

  /** Seed-scoped value loads by key, preserving key order. */
  def seedValues[F[+_, +_]: Error2, K, B](keys: List[K], load: ValueByKey[F, K, B]): F[QueryFailure, List[B]] =
    keys.foldRight(F.pure(List.empty[B]): F[QueryFailure, List[B]]) {
      (key, acc) =>
        for {
          tail   <- acc
          loaded <- load.run(key)
        } yield loaded :: tail
    }
}
