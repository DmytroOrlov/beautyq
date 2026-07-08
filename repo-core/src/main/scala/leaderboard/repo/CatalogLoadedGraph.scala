package leaderboard.repo

import izumi.functional.bio.{Error2, F}
import leaderboard.model.QueryFailure

import scala.compiletime.summonInline

/** A domain-agnostic bundle of raw loaded relation outputs, one `List[A]` per
  * distinct entity/value type loaded by [[LoadCatalogRelations]]. Loaded lists
  * are exactly as [[GraphLoading]] produced them - never deduplicated here;
  * a domain caller that wants deduplication (e.g. by conventional `id`) still
  * calls [[GraphLoading.distinctByKey]] itself, same as before this existed.
  */
final case class LoadedCatalog[Items <: Tuple](items: Items) {

  /** Selects the one loaded list of exact type `A` out of [[items]], via
    * [[TupleSelect]] - no manual reverse-tuple destructuring. `A` is almost
    * always given explicitly, e.g. `loaded.values[List[Category]]`.
    */
  inline def values[A]: A =
    summonInline[TupleSelect[Items, A]](items)
}

/** Loads a single materialized relation factory `H` (one of the four shapes
  * [[MaterializeAll]] can ever produce - `R => Relation.SelfTree/All/HasMany
  * /HasValue[...]`), given whatever [[LoadCatalogRelations]] has already
  * loaded for earlier-declared relations (`Prior`) - a has-many/has-value
  * edge selects its already-loaded parent list out of `Prior` via
  * [[TupleSelect]]; a self-tree/all root ignores `Prior` entirely, since
  * roots have no parent to already have loaded. Domain-agnostic: reused for
  * every declared relation kind, regardless of domain.
  */
private[repo] trait LoadOneRelation[F[_, _], R, H, Prior <: Tuple] {
  type Out
  def apply(repositories: R, factory: H, prior: Prior): F[QueryFailure, List[Out]]
}
private[repo] object LoadOneRelation {
  type Aux[F[_, _], R, H, Prior <: Tuple, A] = LoadOneRelation[F, R, H, Prior] { type Out = A }

  given selfTree[F[+_, +_]: Error2, R, A, K, Prior <: Tuple]: Aux[F, R, R => Relation.SelfTree[F, A, K], Prior, A] =
    new LoadOneRelation[F, R, R => Relation.SelfTree[F, A, K], Prior] {
      type Out = A
      def apply(repositories: R, factory: R => Relation.SelfTree[F, A, K], prior: Prior): F[QueryFailure, List[A]] =
        GraphLoading.selfTreeFrom(factory(repositories))
    }

  given all[F[+_, +_], R, A, K, Prior <: Tuple]: Aux[F, R, R => Relation.All[F, A, K], Prior, A] =
    new LoadOneRelation[F, R, R => Relation.All[F, A, K], Prior] {
      type Out = A
      def apply(repositories: R, factory: R => Relation.All[F, A, K], prior: Prior): F[QueryFailure, List[A]] =
        GraphLoading.allOf(factory(repositories))
    }

  given hasMany[F[+_, +_]: Error2, R, P, K, C, CK, Prior <: Tuple](using
    parents: TupleSelect[Prior, List[P]],
  ): Aux[F, R, R => Relation.HasMany[F, P, K, C, CK], Prior, C] =
    new LoadOneRelation[F, R, R => Relation.HasMany[F, P, K, C, CK], Prior] {
      type Out = C
      def apply(repositories: R, factory: R => Relation.HasMany[F, P, K, C, CK], prior: Prior): F[QueryFailure, List[C]] =
        GraphLoading.manyFor(factory(repositories), parents(prior))
    }

  given hasValue[F[+_, +_]: Error2, R, P, K, V, VK, Row, Prior <: Tuple](using
    parents: TupleSelect[Prior, List[P]],
  ): Aux[F, R, R => Relation.HasValue[F, P, K, V, VK, Row], Prior, V] =
    new LoadOneRelation[F, R, R => Relation.HasValue[F, P, K, V, VK, Row], Prior] {
      type Out = V
      def apply(repositories: R, factory: R => Relation.HasValue[F, P, K, V, VK, Row], prior: Prior): F[QueryFailure, List[V]] =
        GraphLoading.valueFor(factory(repositories), parents(prior))
    }
}

/** Loads an entire materialized relation tuple `Rels` (as produced by
  * [[MaterializeAll]]) into a same-length [[LoadedCatalog]] tuple of raw
  * loaded lists, one per relation. Domain-agnostic: reused for any relation
  * tuple, of any length or shape, as long as [[LoadOneRelation]] evidence
  * exists for each element.
  *
  * `Rels` is stored in reverse declaration order: [[CatalogBranch]] builds
  * its spec tuple (and [[MaterializeAll]] mirrors that same shape into the
  * relation tuple) by prepending each newly-declared spec, so the tuple's
  * head is always the *latest*-declared relation and its last element is
  * always the *first*-declared one. Loading must run parents before
  * children, so [[cons]] recurses into the tuple's tail first: the tail
  * (everything declared earlier than the head) is fully loaded, accumulating
  * one loaded list per relation into `Prior`, before the head (the
  * latest-declared relation, which may depend on a parent list the tail just
  * produced) is loaded from that `Prior`. Unwinding this tail-first recursion
  * therefore runs the actual loads in forward declaration order, even though
  * the tuple itself is stored backwards.
  */
private[repo] trait LoadCatalogRelations[F[_, _], R, Rels <: Tuple] {
  type Loaded <: Tuple
  def apply(repositories: R, relations: Rels): F[QueryFailure, Loaded]
}
private[repo] object LoadCatalogRelations {
  type Aux[F[_, _], R, Rels <: Tuple, L <: Tuple] = LoadCatalogRelations[F, R, Rels] { type Loaded = L }

  given nil[F[+_, +_]: Error2, R]: Aux[F, R, EmptyTuple, EmptyTuple] =
    new LoadCatalogRelations[F, R, EmptyTuple] {
      type Loaded = EmptyTuple
      def apply(repositories: R, relations: EmptyTuple): F[QueryFailure, EmptyTuple] =
        F.pure(EmptyTuple)
    }

  given cons[F[+_, +_]: Error2, R, H, T <: Tuple, TLoaded <: Tuple, Out](using
    tail: Aux[F, R, T, TLoaded],
    one: LoadOneRelation.Aux[F, R, H, TLoaded, Out],
  ): Aux[F, R, H *: T, List[Out] *: TLoaded] =
    new LoadCatalogRelations[F, R, H *: T] {
      type Loaded = List[Out] *: TLoaded
      def apply(repositories: R, relations: H *: T): F[QueryFailure, List[Out] *: TLoaded] = {
        val h *: t = relations
        for {
          tLoaded <- tail(repositories, t)
          hLoaded <- one(repositories, h, tLoaded)
        } yield hLoaded *: tLoaded
      }
    }
}

/** `declaration.loadAll(repositories)`: a convenience wrapper around
  * [[LoadCatalogRelations]] for a [[MaterializedDeclaration]]. A standalone
  * extension (not a method declared directly in [[MaterializedDeclaration]]'s
  * own body) so it can introduce its own covariant `F[+_, +_]` - the same
  * shape [[GraphLoading]]'s own functions already require for `Error2[F]` -
  * rather than reusing [[MaterializedDeclaration]]'s own invariant `F[_, _]`,
  * which cannot satisfy `Error2[F]` from inside that class's body.
  */
extension [F[+_, +_]: Error2, R, Rels <: Tuple](declaration: MaterializedDeclaration[F, R, Rels]) {
  def loadAll(repositories: R)(using loader: LoadCatalogRelations[F, R, Rels]): F[QueryFailure, LoadedCatalog[loader.Loaded]] =
    loader(repositories, declaration.relations).map(LoadedCatalog(_))
}
