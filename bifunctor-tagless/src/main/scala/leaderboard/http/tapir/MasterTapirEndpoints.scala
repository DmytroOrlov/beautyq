package leaderboard.http.tapir

import io.circe.Json
import leaderboard.http.HttpApiFailure
import leaderboard.model.{Master, MasterId}
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*

trait MasterTapirEndpoints {
  def getMaster: PublicEndpoint[MasterId, HttpApiFailure, Json, Any]
  def getMasters: PublicEndpoint[Unit, HttpApiFailure, List[Master], Any]
  def upsertMaster: PublicEndpoint[Master, HttpApiFailure, Unit, Any]

  final def all: List[AnyEndpoint] = List(
    getMaster,
    getMasters,
    upsertMaster,
  )
}

object MasterTapirEndpoints extends MasterTapirEndpoints {
  private val base = HttpApiFailureTapirSupport.endpointBase.in("master")

  val getMaster = base.get
    .in(path[MasterId]("id"))
    .out(jsonBody[Json])

  val getMasters = base.get
    .out(jsonBody[List[Master]])

  val upsertMaster = base.post
    .in(jsonBody[Master])
    .out(emptyOutput)
}
