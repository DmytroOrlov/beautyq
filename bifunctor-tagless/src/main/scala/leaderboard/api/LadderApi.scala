package leaderboard.api

import izumi.functional.bio.Error2
import leaderboard.http.HttpApiFailure
import leaderboard.http.tapir.{LadderTapirEndpoints, TapirHttpSupport}
import leaderboard.repo.Ladder
import org.http4s.HttpRoutes

class LadderApi[F[+_, +_]: Error2](
  ladder: Ladder[F],
  tapirEndpoints: LadderTapirEndpoints,
  tapirHttpSupport: TapirHttpSupport[F],
) extends HttpApi[F] {
  def http: HttpRoutes[F[Throwable, _]] =
    tapirHttpSupport.toRoutes {
      import tapirEndpoints.*
      List(
        getScores.serverLogic[F[Throwable, _]](_ => HttpApiFailure.fromQueryEffect(ladder.getScores)),
        submitScore.serverLogic[F[Throwable, _]] {
          case (userId, score) =>
            HttpApiFailure.fromQueryEffect(ladder.submitScore(userId, score))
        },
      )
    }
}
