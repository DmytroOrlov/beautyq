package leaderboard.api

import leaderboard.http.tapir.{LadderTapirEndpoints, TapirHttpSupport}
import leaderboard.repo.Ladder
import org.http4s.HttpRoutes

class LadderApi[F[+_, +_]](
  ladder: Ladder[F],
  tapirEndpoints: LadderTapirEndpoints,
  tapirHttpSupport: TapirHttpSupport[F],
) extends HttpApi[F] {
  def http: HttpRoutes[F[Throwable, _]] =
    tapirHttpSupport.toRoutes {
      import tapirEndpoints.*
      List(
        getScores.serverLogicSuccess[F[Throwable, _]](_ => ladder.getScores),
        submitScore.serverLogicSuccess[F[Throwable, _]] {
          case (userId, score) =>
            ladder.submitScore(userId, score)
        },
      )
    }
}
