package leaderboard.runtime

import izumi.functional.bio.Error2
import leaderboard.model.QueryFailure

object QueryFailureToThrowable {
  def apply(error: QueryFailure): Throwable =
    error match {
      case QueryFailure.QueryExecutionFailure(queryName, message, underlying) =>
        new RuntimeException(
          s"""Query "$queryName" failed with $message""",
          underlying,
        )
      case QueryFailure.OperationFailure(operationName, message) =>
        new RuntimeException(
          s"""Operation "$operationName" failed with $message"""
        )
      case QueryFailure.DomainFailure(message) =>
        new RuntimeException(message)
    }

  def lift[F[+_, +_]: Error2, A](effect: F[QueryFailure, A]): F[Throwable, A] =
    effect.leftMap(apply)
}
