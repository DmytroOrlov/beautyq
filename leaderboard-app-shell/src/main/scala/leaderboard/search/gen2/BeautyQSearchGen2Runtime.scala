package leaderboard.search.gen2

import leaderboard.search.beautyq.gen2.contract.BeautySearchRequestGen2
import leaderboard.search.beautyq.gen2.wiring.*
import zio.{IO, ZIO}

sealed trait BeautyQSearchGen2RuntimeError
object BeautyQSearchGen2RuntimeError {
  final case class NotReady(dependencies: Vector[BeautyQSearchDependency]) extends BeautyQSearchGen2RuntimeError
  final case class Application(error: BeautyQSearchApplicationError) extends BeautyQSearchGen2RuntimeError
  final case class Projection(error: BeautyQSearchResponseProjectionError) extends BeautyQSearchGen2RuntimeError
  final case class BlockingFailure(error: Throwable) extends BeautyQSearchGen2RuntimeError
}

/** Effect adapter only. All business and backend mechanics remain in the
  * synchronous wiring-owned application and projector. */
final class BeautyQSearchGen2Runtime private (
  application: BeautyQSearchApplication,
  readiness: BeautyQSupplementReadinessPolicy.Result,
) {
  def execute(request: BeautySearchRequestGen2): IO[BeautyQSearchGen2RuntimeError, BeautyQSearchResponseGen2] =
    readiness match {
      case unavailable: BeautyQSupplementReadinessPolicy.NotServing =>
        ZIO.fail(BeautyQSearchGen2RuntimeError.NotReady(unavailable.unavailableRequired))
      case serving: BeautyQSupplementReadinessPolicy.Serving =>
        serving.mode match {
          case BeautyQServingMode.BaselineOnly =>
            ZIO.attemptBlocking(application.executeBaselineOnly(request))
              .mapError(BeautyQSearchGen2RuntimeError.BlockingFailure.apply)
              .flatMap(toRuntimeResult)
          case BeautyQServingMode.FullSearch =>
            ZIO.attemptBlocking(application.execute(request))
              .mapError(BeautyQSearchGen2RuntimeError.BlockingFailure.apply)
              .flatMap(toRuntimeResult)
        }
    }

  private def toRuntimeResult(
    result: Either[BeautyQSearchApplicationError, BeautyQSearchOrchestrator.Result],
  ): IO[BeautyQSearchGen2RuntimeError, BeautyQSearchResponseGen2] =
    result match {
      case Left(error) => ZIO.fail(BeautyQSearchGen2RuntimeError.Application(error))
      case Right(value) =>
        ZIO.fromEither(BeautyQSearchResponseGen2Projector.project(value))
          .mapError(BeautyQSearchGen2RuntimeError.Projection.apply)
    }
}

object BeautyQSearchGen2Runtime {
  def make(
    application: BeautyQSearchApplication,
    readiness: BeautyQSupplementReadinessPolicy.Result,
  ): BeautyQSearchGen2Runtime =
    new BeautyQSearchGen2Runtime(application, readiness)
}
