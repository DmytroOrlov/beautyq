package leaderboard.http.tapir

import io.circe.Json
import leaderboard.model.{Master, MasterId}
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*

trait MasterTapirEndpoints {
  def getMaster: PublicEndpoint[MasterId, Unit, Json, Any]
  def getMasters: PublicEndpoint[Unit, Unit, List[Master], Any]
  def upsertMaster: PublicEndpoint[Master, Unit, Unit, Any]

  final def all: List[AnyEndpoint] = List(
    getMaster,
    getMasters,
    upsertMaster,
  )
}

object MasterTapirEndpoints extends MasterTapirEndpoints {
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
