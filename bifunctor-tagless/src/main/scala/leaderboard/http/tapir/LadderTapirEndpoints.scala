package leaderboard.http.tapir

import leaderboard.model.{Score, UserId}
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*

trait LadderTapirEndpoints {
  def getScores: PublicEndpoint[Unit, Unit, List[(UserId, Score)], Any]
  def submitScore: PublicEndpoint[(UserId, Score), Unit, Unit, Any]

  final def all: List[AnyEndpoint] = List(
    getScores,
    submitScore,
  )
}

object LadderTapirEndpoints extends LadderTapirEndpoints {
  private val base = endpoint.in("ladder")

  val getScores = base.get
    .out(jsonBody[List[(UserId, Score)]])

  val submitScore = base.post
    .in(path[UserId]("userId"))
    .in(path[Score]("score"))
    .out(emptyOutput)
}
