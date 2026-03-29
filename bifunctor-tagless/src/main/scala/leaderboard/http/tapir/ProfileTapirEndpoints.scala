package leaderboard.http.tapir

import io.circe.Json
import leaderboard.model.{UserId, UserProfile}
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*

trait ProfileTapirEndpoints {
  def getProfile: PublicEndpoint[UserId, Unit, Json, Any]
  def setProfile: PublicEndpoint[(UserId, UserProfile), Unit, Unit, Any]

  final def all: List[AnyEndpoint] = List(
    getProfile,
    setProfile,
  )
}

object ProfileTapirEndpoints extends ProfileTapirEndpoints {
  private val base = endpoint.in("profile")

  val getProfile = base.get
    .in(path[UserId]("id"))
    .out(jsonBody[Json])

  val setProfile = base.post
    .in(path[UserId]("id"))
    .in(jsonBody[UserProfile])
    .out(emptyOutput)
}
