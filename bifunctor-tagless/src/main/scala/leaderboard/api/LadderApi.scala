package leaderboard.api

import leaderboard.http.tapir.LadderTapirEndpoints.*
import leaderboard.http.tapir.TapirHttpSupport
import leaderboard.repo.Ladder
import org.http4s.HttpRoutes
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.ServerEndpoint

final class LadderApi[F[+_, +_]](
  ladder: Ladder[F],
  tapirHttpSupport: TapirHttpSupport[F],
) extends HttpApi[F] {
  def http: HttpRoutes[F[Throwable, _]] =
    tapirHttpSupport.toRoutes(all)

  private def all: List[ServerEndpoint[Fs2Streams[F[Throwable, _]], F[Throwable, _]]] =
    List(
      getScores.serverLogicSuccess[F[Throwable, _]](_ => ladder.getScores),
      submitScore.serverLogicSuccess[F[Throwable, _]] { case (userId, score) =>
        ladder.submitScore(userId, score)
      },
    )
}
