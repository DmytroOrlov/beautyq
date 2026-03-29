package leaderboard.http.tapir

import io.circe.Json
import leaderboard.model.{Master, MasterId}
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*

object MasterTapirEndpoints {
  private val base = endpoint.in("master")

  val getMaster = base.get
    .in(path[MasterId]("id"))
    .out(jsonBody[Json])

  val getMasters = base.get
    .out(jsonBody[List[Master]])

  val upsertMaster = base.post
    .in(jsonBody[Master])
    .out(emptyOutput)
}
