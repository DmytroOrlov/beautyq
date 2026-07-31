package leaderboard.search.gen2

import leaderboard.search.beautyq.gen2.contract.BeautySearchRequestGen2
import leaderboard.search.beautyq.gen2.wiring.*
import zio.{IO, ZIO}

sealed trait BeautyQSearchGen2RuntimeError
object BeautyQSearchGen2RuntimeError {
  final case class Application(error: BeautyQSearchApplicationError) extends BeautyQSearchGen2RuntimeError
  final case class Projection(error: BeautyQSearchResponseProjectionError) extends BeautyQSearchGen2RuntimeError
  final case class BlockingFailure(error: Throwable) extends BeautyQSearchGen2RuntimeError
}

final class BeautyQSearchGen2Runtime private (
  application: BeautyQSearchApplication,
  val startupStatus: StartupServingStatus,
  val startupEvidence: Option[BeautyQSearchStartupEvidence],
) {
  def execute(request: BeautySearchRequestGen2): IO[BeautyQSearchGen2RuntimeError, BeautyQSearchResponseGen2] =
    ZIO.attemptBlocking(application.execute(request))
      .mapError(BeautyQSearchGen2RuntimeError.BlockingFailure.apply)
      .flatMap(toRuntimeResult)

  private def toRuntimeResult(
    result: Either[BeautyQSearchApplicationError, BeautyQSearchOrchestrator.Result],
  ): IO[BeautyQSearchGen2RuntimeError, BeautyQSearchResponseGen2] =
    result match {
      case Left(error) => ZIO.fail(BeautyQSearchGen2RuntimeError.Application(error))
      case Right(value) =>
        ZIO.fromEither(BeautyQSearchResponseGen2Projector.project(value, startupStatus))
          .mapError(BeautyQSearchGen2RuntimeError.Projection.apply)
    }
}

object BeautyQSearchGen2Runtime {
  def make(
    application: BeautyQSearchApplication,
    startupStatus: StartupServingStatus,
    startupEvidence: Option[BeautyQSearchStartupEvidence] = None,
  ): BeautyQSearchGen2Runtime =
    new BeautyQSearchGen2Runtime(application, startupStatus, startupEvidence)
}
