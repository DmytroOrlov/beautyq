package leaderboard.api

import cats.effect.Async
import izumi.functional.bio.Error2
import leaderboard.http.HttpApiFailure
import leaderboard.http.tapir.LadderTapirEndpoints
import leaderboard.repo.Ladder
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.Http4sServerInterpreter

class LadderApi[F[+_, +_]: Error2](
  ladder: Ladder[F],
  tapirEndpoints: LadderTapirEndpoints,
)(implicit
  async: Async[F[Throwable, _]]
) extends HttpApi[F] {
  def http: HttpRoutes[F[Throwable, _]] =
    Http4sServerInterpreter[F[Throwable, _]]().toRoutes {
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
