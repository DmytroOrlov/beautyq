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

  /** Loads a required/value entity by key. */
  final case class ValueByKey[F[_, _], K, A](run: K => F[QueryFailure, A])

  /** Loads many entities by key. */
  final case class ManyByKey[F[_, _], K, A](run: K => F[QueryFailure, List[A]])

  /** Loads all entities of a source. */
  final case class AllValues[F[_, _], A](run: () => F[QueryFailure, List[A]])
}
