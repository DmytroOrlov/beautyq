package leaderboard.repo

import izumi.functional.bio.{Error2, F}
import leaderboard.model.QueryFailure
import leaderboard.repo.RepoOp.{AllValues, ManyByKey, OptionalByKey, ValueByKey}

import scala.compiletime.{constValue, summonInline}
import scala.deriving.Mirror

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
  * The interpreter is intentionally free of any concrete-domain knowledge: it
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

// ============================================================================
// Reusable catalog declaration DSL.
//
// A small, domain-agnostic DSL for declaring a "catalog" graph - named
// branches of roots and edges - as one readable, *pure* chain. `catalog(name)`
// takes no `F` and no repositories type: it only records typed specs (parent
// type, child/value type, resolved join field) and inspectable [[CatalogStep]]
// metadata. No repo instances, loader functions, or runtime dependencies ever
// appear in the declaration chain.
//
// A concrete domain separately supplies "Evidence" - given
// instances of [[CatalogEntity]]/[[CatalogValue]] (what a declared type *is*)
// and [[CatalogRootTree]]/[[CatalogRootAll]]/[[CatalogMany]]/[[CatalogValueEdge]]
// (how to *load* it for a concrete `F`/repositories type `R`) - and then calls
// `declaration.materialize[F, R](build)` to turn the pure declaration plus
// that evidence into typed `R => Relation` factories, which `build`
// destructures into the domain's own named fields. Nothing in this file
// requires a concrete domain type.
// ============================================================================

/** Whether a declared root is loaded as a self-tree or as a flat "all" collection. */
enum RootLoading {
  case Tree
  case All
}

/** Whether a declared edge is a has-many child collection or a has-one aggregate value. */
enum EdgeKind {
  case Many
  case Value
}

/** One inspectable step of a catalog declaration: a root or an edge,
  * carrying the business names and join key label a coordinator would want
  * to read, independent of the underlying [[Relation]] machinery. Purely
  * descriptive - never participates in typed relation construction.
  */
enum CatalogStep {
  case Root(entity: String, loading: RootLoading, key: Option[String])
  case Edge(parent: String, child: String, kind: EdgeKind, key: String)

  def summary: String = this match {
    case Root(entity, RootLoading.Tree, Some(key)) => s"root:$entity:tree:$key"
    case Root(entity, RootLoading.Tree, None)      => s"root:$entity:tree"
    case Root(entity, RootLoading.All, _)          => s"root:$entity:all"
    case Edge(parent, child, EdgeKind.Many, key)   => s"many:$parent->$child:$key"
    case Edge(parent, child, EdgeKind.Value, key)  => s"value:$parent->$child:$key"
  }
}

private[repo] def decapitalize(name: String): String =
  if (name.isEmpty) name else name.head.toLower.toString + name.tail

// --- Pure specs: recorded by the declaration chain, no F/R/loaders. ---

/** A declared self-tree root: entity type `A`, its own key type `K`, and the
  * resolved parent-edge field. Pure - no loader, no repositories.
  */
final case class RootTreeSpec[A, K](parentField: RepoField[A, K])

/** A declared flat "all" root for entity type `A`. Pure marker - no loader,
  * no repositories.
  */
final case class RootAllSpec[A]()

/** A declared has-many edge: parent type `P`, child type `C`, and the
  * resolved foreign-key field (typed by the parent's own key `K`). Pure - no
  * loader, no repositories.
  */
final case class ManyEdgeSpec[P, C, K](foreignKey: RepoField[C, K])

/** A declared has-one aggregate value edge: parent type `P`, value type `V`,
  * and the resolved value-key field (typed by the parent's own key `K`).
  * Pure - no loader, no repositories.
  */
final case class ValueEdgeSpec[P, V, K](valueKey: RepoField[V, K])

/** Starts a named, pure catalog declaration - no `F`, no repositories type. */
final case class CatalogStart(name: String) {
  inline def branch[A](using mirror: Mirror.ProductOf[A]): CatalogBranch[EmptyTuple, A] =
    new CatalogBranch(name, Vector.empty, EmptyTuple, decapitalize(constValue[mirror.MirroredLabel]))
}

def catalog(name: String): CatalogStart = CatalogStart(name)

/** A pure catalog declaration currently focused on branch (parent) type `A`,
  * having already declared `Specs` (in reverse order). Every method here is
  * domain-agnostic and reusable: it resolves a `RepoField` immediately (via
  * the same inline selector-label derivation the rest of the repo layer
  * uses) and records a pure spec plus an inspectable [[CatalogStep]] - never
  * an `F`-dependent value.
  */
final class CatalogBranch[Specs <: Tuple, A](
  val name: String,
  val steps: Vector[CatalogStep],
  val specs: Specs,
  val entityName: String,
) {

  /** Declares `A`'s root as a self-tree, joined through `parent`. */
  inline def rootTree[K](inline parent: A => K): CatalogBranch[RootTreeSpec[A, K] *: Specs, A] = {
    val field = RepoField.derived(parent)
    val step  = CatalogStep.Root(entityName, RootLoading.Tree, Some(field.label))
    new CatalogBranch(name, steps :+ step, RootTreeSpec[A, K](field) *: specs, entityName)
  }

  /** Declares `A`'s root as a flat "all" collection. */
  def rootAll: CatalogBranch[RootAllSpec[A] *: Specs, A] = {
    val step = CatalogStep.Root(entityName, RootLoading.All, None)
    new CatalogBranch(name, steps :+ step, RootAllSpec[A]() *: specs, entityName)
  }

  /** Declares a has-many child edge from the current branch to `C`, joined
    * through the selector supplied to the returned applier.
    */
  inline def child[C](using mirror: Mirror.ProductOf[C]): CatalogChildStart[Specs, A, C] =
    new CatalogChildStart(this, decapitalize(constValue[mirror.MirroredLabel]))

  /** Declares a has-one aggregate value edge from the current branch to `V`,
    * joined through the selector supplied to the returned applier.
    */
  inline def value[V](using mirror: Mirror.ProductOf[V]): CatalogValueStart[Specs, A, V] =
    new CatalogValueStart(this, decapitalize(constValue[mirror.MirroredLabel]))

  /** Switches the current branch to type `B`, keeping every spec declared so far. */
  inline def branch[B](using mirror: Mirror.ProductOf[B]): CatalogBranch[Specs, B] =
    new CatalogBranch(name, steps, specs, decapitalize(constValue[mirror.MirroredLabel]))

  /** Starts materializing this declaration for a concrete effect type `F` and
    * repositories type `R`, given the necessary [[CatalogEntity]]/[[CatalogValue]]/
    * [[CatalogRootTree]]/[[CatalogRootAll]]/[[CatalogMany]]/[[CatalogValueEdge]]
    * evidence in scope.
    */
  def materialize[F[_, _], R]: CatalogMaterializeStep[Specs, F, R] = new CatalogMaterializeStep(this)
}

/** `.apply(by)` for a has-many child edge declaration; `K` (the parent's own
  * key type) is inferred from `by`, not supplied explicitly.
  */
final class CatalogChildStart[Specs <: Tuple, A, C](branch: CatalogBranch[Specs, A], childName: String) {
  inline def apply[K](inline by: C => K): CatalogBranch[ManyEdgeSpec[A, C, K] *: Specs, A] = {
    val field = RepoField.derived(by)
    val step  = CatalogStep.Edge(branch.entityName, childName, EdgeKind.Many, field.label)
    new CatalogBranch(branch.name, branch.steps :+ step, ManyEdgeSpec[A, C, K](field) *: branch.specs, branch.entityName)
  }
}

/** `.apply(by)` for a has-one aggregate value edge declaration; `K` (the
  * parent's own key type) is inferred from `by`, not supplied explicitly.
  */
final class CatalogValueStart[Specs <: Tuple, A, V](branch: CatalogBranch[Specs, A], valueName: String) {
  inline def apply[K](inline by: V => K): CatalogBranch[ValueEdgeSpec[A, V, K] *: Specs, A] = {
    val field = RepoField.derived(by)
    val step  = CatalogStep.Edge(branch.entityName, valueName, EdgeKind.Value, field.label)
    new CatalogBranch(branch.name, branch.steps :+ step, ValueEdgeSpec[A, V, K](field) *: branch.specs, branch.entityName)
  }
}

// --- Evidence typeclasses: what a declared type *is*, and how to *load* it. ---

/** Evidence that `A` is a normal, id-keyed catalog entity: its [[EntityNode]]. */
trait CatalogEntity[A] {
  type Key
  def node: EntityNode[A, Key]
}
object CatalogEntity {
  type Aux[A, K] = CatalogEntity[A] { type Key = K }

  /** Explicit entity evidence from an already-built [[EntityNode]] (e.g. one
    * derived the ordinary way via `SomeRepo.entity.node(_.id)`).
    */
  def from[A, K](node0: EntityNode[A, K]): Aux[A, K] =
    new CatalogEntity[A] {
      type Key = K
      def node: EntityNode[A, K] = node0
    }

  /** Derives entity evidence for `A` from its conventional `id` field,
    * inferring the key type instead of requiring a hand-written `_.id`
    * selector. Fails to compile if `A` has no `id` field. `transparent` so
    * the inferred `Key` member is visible at the call site (e.g. so the
    * result can satisfy a `given CatalogEntity.Aux[A, K]`), not erased to
    * the widened `CatalogEntity[A]` declared here.
    */
  transparent inline def derived[A](entity: RepoEntity[A]): CatalogEntity[A] =
    ${ CatalogEntityDerivation.derivedImpl[A]('entity) }
}

/** Evidence that `A` is a value/aggregate catalog source (not a normal
  * id-keyed entity): its [[RepoValueSource]]. Kept as a distinct typeclass
  * from [[CatalogEntity]] so a `.value[...]` edge can only ever resolve
  * against value evidence, never against a normal entity - the value/entity
  * distinction is enforced at compile time.
  */
trait CatalogValue[A] {
  type Key
  type Row
  def valueSource: RepoValueSource[A, Key, Row]
}
object CatalogValue {
  type Aux[A, K, R] = CatalogValue[A] { type Key = K; type Row = R }

  def from[A, K, R](source: RepoValueSource[A, K, R]): Aux[A, K, R] =
    new CatalogValue[A] {
      type Key = K
      type Row = R
      def valueSource: RepoValueSource[A, K, R] = source
    }
}

/** Evidence for how to load a self-tree root's children, for effect type `F`
  * and repositories type `R`.
  */
trait CatalogRootTree[F[_, _], R, A] {
  type Key
  def load(repositories: R): ManyByKey[F, Key, A]
}
object CatalogRootTree {
  type Aux[F[_, _], R, A, K] = CatalogRootTree[F, R, A] { type Key = K }

  def fromRepo[F[_, _], R, Repo, A, K](select: R => Repo)(loader: Repo => ManyByKey[F, K, A]): Aux[F, R, A, K] =
    new CatalogRootTree[F, R, A] {
      type Key = K
      def load(repositories: R): ManyByKey[F, K, A] = loader(select(repositories))
    }
}

/** Evidence for how to load a flat "all" root, for effect type `F` and
  * repositories type `R`.
  */
trait CatalogRootAll[F[_, _], R, A] {
  def load(repositories: R): AllValues[F, A]
}
object CatalogRootAll {
  def fromRepo[F[_, _], R, Repo, A](select: R => Repo)(loader: Repo => AllValues[F, A]): CatalogRootAll[F, R, A] =
    (repositories: R) => loader(select(repositories))
}

/** Evidence for how to load a has-many edge's children, for effect type `F`
  * and repositories type `R`.
  */
trait CatalogMany[F[_, _], R, P, C] {
  type Key
  def load(repositories: R): ManyByKey[F, Key, C]
}
object CatalogMany {
  type Aux[F[_, _], R, P, C, K] = CatalogMany[F, R, P, C] { type Key = K }

  def fromRepo[F[_, _], R, Repo, P, C, K](select: R => Repo)(loader: Repo => ManyByKey[F, K, C]): Aux[F, R, P, C, K] =
    new CatalogMany[F, R, P, C] {
      type Key = K
      def load(repositories: R): ManyByKey[F, K, C] = loader(select(repositories))
    }
}

/** Evidence for how to load a has-one aggregate value edge's value, for
  * effect type `F` and repositories type `R`.
  */
trait CatalogValueEdge[F[_, _], R, P, V] {
  type Key
  def load(repositories: R): ValueByKey[F, Key, V]
}
object CatalogValueEdge {
  type Aux[F[_, _], R, P, V, K] = CatalogValueEdge[F, R, P, V] { type Key = K }

  def fromRepo[F[_, _], R, Repo, P, V, K](select: R => Repo)(loader: Repo => ValueByKey[F, K, V]): Aux[F, R, P, V, K] =
    new CatalogValueEdge[F, R, P, V] {
      type Key = K
      def load(repositories: R): ValueByKey[F, K, V] = loader(select(repositories))
    }
}

// --- Materialization: pure spec + evidence -> typed `R => Relation` factory. ---

/** Materializes a single pure spec into a typed `R => Relation` factory,
  * given the entity/value/loader evidence it needs. Domain-agnostic: reused
  * for every declared root/edge kind, regardless of domain.
  */
private[repo] trait MaterializeOne[Spec, F[_, _], R, Rel] {
  def apply(spec: Spec): R => Rel
}
private[repo] object MaterializeOne {
  given rootTree[F[_, _], R, A, K](using
    entity: CatalogEntity.Aux[A, K],
    loader: CatalogRootTree.Aux[F, R, A, K],
  ): MaterializeOne[RootTreeSpec[A, K], F, R, Relation.SelfTree[F, A, K]] =
    spec => repositories => Relation.SelfTree(entity.node, spec.parentField, loader.load(repositories))

  given rootAll[F[_, _], R, A, K](using
    entity: CatalogEntity.Aux[A, K],
    loader: CatalogRootAll[F, R, A],
  ): MaterializeOne[RootAllSpec[A], F, R, Relation.All[F, A, K]] =
    _ => repositories => Relation.All(entity.node, loader.load(repositories))

  given manyEdge[F[_, _], R, P, C, K, CK](using
    parentEntity: CatalogEntity.Aux[P, K],
    childEntity: CatalogEntity.Aux[C, CK],
    loader: CatalogMany.Aux[F, R, P, C, K],
  ): MaterializeOne[ManyEdgeSpec[P, C, K], F, R, Relation.HasMany[F, P, K, C, CK]] =
    spec => repositories => Relation.HasMany(parentEntity.node, childEntity.node, spec.foreignKey, loader.load(repositories))

  given valueEdge[F[_, _], R, P, V, K, VK, Row](using
    parentEntity: CatalogEntity.Aux[P, K],
    value: CatalogValue.Aux[V, VK, Row],
    loader: CatalogValueEdge.Aux[F, R, P, V, K],
  ): MaterializeOne[ValueEdgeSpec[P, V, K], F, R, Relation.HasValue[F, P, K, V, VK, Row]] =
    spec => repositories => Relation.HasValue(parentEntity.node, value.valueSource, spec.valueKey, loader.load(repositories))
}

/** Recursively materializes an entire spec tuple into a same-shaped tuple of
  * typed `R => Relation` factories. Domain-agnostic: works for any spec
  * tuple, of any length or shape, as long as [[MaterializeOne]] evidence
  * exists for each element.
  */
private[repo] trait MaterializeAll[Specs <: Tuple, F[_, _], R, Out <: Tuple] {
  def apply(specs: Specs): Out
}
private[repo] object MaterializeAll {
  given nil[F[_, _], R]: MaterializeAll[EmptyTuple, F, R, EmptyTuple] =
    _ => EmptyTuple

  given cons[H, T <: Tuple, F[_, _], R, RelH, TOut <: Tuple](using
    one: MaterializeOne[H, F, R, RelH],
    tail: MaterializeAll[T, F, R, TOut],
  ): MaterializeAll[H *: T, F, R, (R => RelH) *: TOut] = {
    case h *: t => one(h) *: tail(t)
  }
}

// --- Typed tuple selection: pick the element of an exact type out of a
// precisely-typed tuple, without knowing its position. Domain-agnostic. ---

/** Evidence that a value of exact type `A` can be found inside typed tuple
  * `T`. Reusable well beyond catalog declarations - it knows nothing about
  * [[Relation]] or repositories.
  *
  * If `T` contains more than one element of the exact same type `A`,
  * [[TupleSelect.found]] takes priority over [[TupleSelectLowPriority.recurse]]
  * for the head position, so the *first* (head-most) matching element wins;
  * it does not disambiguate between two *later* occurrences of the same
  * type. A tuple that legitimately needs two distinct relations of the exact
  * same type (e.g. two different `HasMany[F, Parent, ParentId, Child,
  * ChildId]` edges) is not selectable by type alone and would need
  * explicit labels - not solved here, and not needed by the current
  * declaration (every declared relation factory type is unique).
  */
trait TupleSelect[T <: Tuple, A] {
  def apply(tuple: T): A
}

/** Lower-priority home for the recursive case, so [[TupleSelect.found]] (an
  * exact head match) is preferred by implicit search over recursing past a
  * non-matching head.
  */
trait TupleSelectLowPriority {
  given recurse[H, T <: Tuple, A](using rest: TupleSelect[T, A]): TupleSelect[H *: T, A] with {
    def apply(tuple: H *: T): A = rest(tuple.tail)
  }
}

object TupleSelect extends TupleSelectLowPriority {
  given found[A, T <: Tuple]: TupleSelect[A *: T, A] with {
    def apply(tuple: A *: T): A = tuple.head
  }
}

/** The materialized declaration: the same `name`/`steps` as the pure
  * declaration, plus the precisely-typed tuple of `R => Relation` factories
  * `MaterializeAll` produced from it.
  */
final case class MaterializedDeclaration[F[_, _], R, Rels <: Tuple](
  name: String,
  steps: Vector[CatalogStep],
  relations: Rels,
) {

  /** Selects the one relation factory of exact type `A` out of [[relations]],
    * via [[TupleSelect]] - no manual reverse-tuple destructuring, no
    * repeated tuple type alias. `A` is almost always given explicitly (e.g.
    * `declaration.relation[Env[F] => Relation.HasMany[F, Parent,
    * ParentId, Child, ChildId]]`), since expected-type propagation
    * into a named case-class constructor argument does not, in practice,
    * flow through this call. Resolution is deferred to each inline-expansion
    * site via `summonInline` (rather than a plain `using` parameter) because
    * `Rels` is only concrete at the call site of an `inline` caller such as
    * `Graph.fromDeclaration` - a plain `using` parameter would instead be
    * resolved once, generically, at `fromDeclaration`'s own definition,
    * where `Rels` is still an abstract type parameter.
    */
  inline def relation[A]: A =
    summonInline[TupleSelect[Rels, A]](relations)

  /** Alias for [[relation]] for call sites that read more clearly with the
    * type spelled out explicitly, e.g. `declaration.relationAs[Env[F]
    * => Relation.SelfTree[F, Parent, ParentId]]`.
    */
  inline def relationAs[A]: A =
    summonInline[TupleSelect[Rels, A]](relations)
}

/** `declaration.materialize[F, R](build)`: given evidence, folds the pure
  * spec tuple into a [[MaterializedDeclaration]] and hands it to `build`.
  */
final class CatalogMaterializeStep[Specs <: Tuple, F[_, _], R](branch: CatalogBranch[Specs, ?]) {
  def apply[Out <: Tuple, Out2](using m: MaterializeAll[Specs, F, R, Out])(build: MaterializedDeclaration[F, R, Out] => Out2): Out2 =
    build(MaterializedDeclaration(branch.name, branch.steps, m(branch.specs)))
}
