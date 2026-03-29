package leaderboard.http.tapir

import io.circe.Json
import leaderboard.model.{UserId, UserProfile}
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*

object ProfileTapirEndpoints {
  private val base = endpoint.in("profile")

  val getProfile = base.get
    .in(path[UserId]("id"))
    .out(jsonBody[Json])

  val setProfile = base.post
    .in(path[UserId]("id"))
    .in(jsonBody[UserProfile])
    .out(emptyOutput)
}
