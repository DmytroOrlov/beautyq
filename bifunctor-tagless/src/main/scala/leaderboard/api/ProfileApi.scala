package leaderboard.api

import cats.effect.Async
import izumi.functional.bio.Error2
import leaderboard.http.HttpApiFailure
import leaderboard.http.tapir.{LegacyJsonResponse, ProfileTapirEndpoints, TapirHttpSupport}
import leaderboard.repo.Profiles
import leaderboard.services.Ranks
import logstage.LogIO2
import org.http4s.HttpRoutes

class ProfileApi[F[+_, +_]: Error2](
  profiles: Profiles[F],
  ranks: Ranks[F],
  log: LogIO2[F],
  tapirEndpoints: ProfileTapirEndpoints,
  tapirHttpSupport: TapirHttpSupport[F],
)(implicit
  async: Async[F[Throwable, _]]
) extends HttpApi[F] {
  def http: HttpRoutes[F[Throwable, _]] =
    tapirHttpSupport.toRoutes {
      import tapirEndpoints.*
      List(
        getProfile.serverLogic[F[Throwable, _]](userId => async.map(HttpApiFailure.fromQueryEffect(ranks.getRank(userId)))(_.map(LegacyJsonResponse.optionalAsJson))),
        setProfile.serverLogic[F[Throwable, _]] {
          case (userId, profile) =>
            async.flatMap(log.info(s"Saving $profile"))(_ => HttpApiFailure.fromQueryEffect(profiles.setProfile(userId, profile)))
        },
      )
    }
}
