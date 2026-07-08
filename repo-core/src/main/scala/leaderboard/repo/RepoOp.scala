package leaderboard.repo

import leaderboard.model.QueryFailure

/** Typed repo operation descriptions.
  *
  * These wrappers describe how a repository loads entities without the graph
  * layer knowing concrete repo method names. They are generic in the effect
  * `F[_, _]`, the key type `K` and the entity type `A`; the error type is
  * always [[leaderboard.model.QueryFailure]].
  */
object RepoOp {

  /** Loads an optional entity by key. */
  final case class OptionalByKey[F[_, _], K, A](run: K => F[QueryFailure, Option[A]])

  object OptionalByKey {

    /** Derives the wrapper from the unique method on `Repo` shaped
      * `K => F[QueryFailure, Option[A]]`. Aborts at compile time if zero or
      * more than one method matches; see [[RepoOpDerivation]].
      */
    transparent inline def derived[F[_, _], Repo, K, A](repo: Repo): OptionalByKey[F, K, A] =
      ${ RepoOpDerivation.optionalByKeyImpl[F, Repo, K, A]('repo) }
  }

  /** Loads a required/value entity by key. */
  final case class ValueByKey[F[_, _], K, A](run: K => F[QueryFailure, A])

  object ValueByKey {

    /** Derives the wrapper from the unique method on `Repo` shaped
      * `K => F[QueryFailure, A]`. Aborts at compile time if zero or more than
      * one method matches; see [[RepoOpDerivation]].
      */
    transparent inline def derived[F[_, _], Repo, K, A](repo: Repo): ValueByKey[F, K, A] =
      ${ RepoOpDerivation.valueByKeyImpl[F, Repo, K, A]('repo) }
  }

  /** Loads many entities by key. */
  final case class ManyByKey[F[_, _], K, A](run: K => F[QueryFailure, List[A]])

  object ManyByKey {

    /** Derives the wrapper from the unique method on `Repo` shaped
      * `K => F[QueryFailure, List[A]]`. Aborts at compile time if zero or
      * more than one method matches; see [[RepoOpDerivation]].
      */
    transparent inline def derived[F[_, _], Repo, K, A](repo: Repo): ManyByKey[F, K, A] =
      ${ RepoOpDerivation.manyByKeyImpl[F, Repo, K, A]('repo) }
  }

  /** Loads all entities of a source. */
  final case class AllValues[F[_, _], A](run: () => F[QueryFailure, List[A]])

  object AllValues {

    /** Derives the wrapper from the unique no-argument method on `Repo`
      * shaped `F[QueryFailure, List[A]]`. Aborts at compile time if zero or
      * more than one method matches; see [[RepoOpDerivation]].
      */
    transparent inline def derived[F[_, _], Repo, A](repo: Repo): AllValues[F, A] =
      ${ RepoOpDerivation.allValuesImpl[F, Repo, A]('repo) }
  }
}
