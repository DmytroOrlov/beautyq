package leaderboard.http.tapir

import leaderboard.http.HttpApiFailure
import leaderboard.model.{Master, MasterId}
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*

trait MasterTapirEndpoints {
  def getMaster: PublicEndpoint[MasterId, HttpApiFailure, Master, Any]
  def getMasters: PublicEndpoint[Unit, HttpApiFailure, List[Master], Any]
  def upsertMaster: PublicEndpoint[Master, HttpApiFailure, Unit, Any]

  final def all: List[AnyEndpoint] = List(
    getMaster,
    getMasters,
    upsertMaster,
  )
}

object MasterTapirEndpoints extends MasterTapirEndpoints {
  private val base = sttp.tapir.endpoint.in("master")

  val getMaster = base.get
    .errorOut(HttpApiFailureTapirSupport.singleEntityGetErrorOutput)
    .in(path[MasterId]("id"))
    .out(jsonBody[Master])

  val getMasters = HttpApiFailureTapirSupport.endpointBase.in("master").get
    .out(jsonBody[List[Master]])

  val upsertMaster = HttpApiFailureTapirSupport.endpointBase.in("master").post
    .in(jsonBody[Master])
    .out(emptyOutput)
}
