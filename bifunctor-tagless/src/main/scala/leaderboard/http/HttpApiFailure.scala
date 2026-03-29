package leaderboard.http

import izumi.functional.bio.Error2
import leaderboard.model.QueryFailure

sealed trait HttpApiFailure extends Product with Serializable

object HttpApiFailure {
  case object InternalServerError extends HttpApiFailure

  def fromQueryFailure(error: QueryFailure): HttpApiFailure =
    InternalServerError

  def fromQueryEffect[F[+_, +_]: Error2, A](effect: F[QueryFailure, A]): F[Nothing, Either[HttpApiFailure, A]] =
    effect.attempt.map(_.left.map(fromQueryFailure))

  def fromEither[E, A](value: Either[E, A]): Either[HttpApiFailure, A] =
    value.left.map(_ => InternalServerError)
}
