package leaderboard.api

import cats.effect.Async
import io.circe.Json
import io.circe.syntax.*
import leaderboard.http.tapir.ProfileTapirEndpoints.{getProfile, setProfile}
import leaderboard.http.tapir.TapirHttpSupport
import leaderboard.repo.Profiles
import leaderboard.services.Ranks
import logstage.LogIO2
import org.http4s.HttpRoutes
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.ServerEndpoint

final class ProfileApi[F[+_, +_]](
  profiles: Profiles[F],
  ranks: Ranks[F],
  log: LogIO2[F],
  tapirHttpSupport: TapirHttpSupport[F],
)(implicit
  async: Async[F[Throwable, _]],
) extends HttpApi[F] {
  override def http: HttpRoutes[F[Throwable, _]] =
    tapirHttpSupport.toRoutes(all)

  private def all: List[ServerEndpoint[Fs2Streams[F[Throwable, _]], F[Throwable, _]]] =
    List(
      getProfile.serverLogicSuccess[F[Throwable, _]](userId =>
        async.map(ranks.getRank(userId))(_.fold[Json](Json.Null)(_.asJson))
      ),
      setProfile.serverLogicSuccess[F[Throwable, _]] { case (userId, profile) =>
        async.flatMap(log.info(s"Saving $profile"))(_ => profiles.setProfile(userId, profile))
      },
    )
}
