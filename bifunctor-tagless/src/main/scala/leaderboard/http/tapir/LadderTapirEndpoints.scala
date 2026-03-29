package leaderboard.http.tapir

import leaderboard.model.{Score, UserId}
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*

object LadderTapirEndpoints {
  private val base = endpoint.in("ladder")

  val getScores = base.get
    .out(jsonBody[List[(UserId, Score)]])

  val submitScore = base.post
    .in(path[UserId]("userId"))
    .in(path[Score]("score"))
    .out(emptyOutput)
}
