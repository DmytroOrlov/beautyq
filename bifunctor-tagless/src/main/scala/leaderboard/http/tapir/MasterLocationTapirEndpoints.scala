package leaderboard.http.tapir

import io.circe.Json
import leaderboard.model.{MasterId, MasterLocation, MasterLocationId}
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*

trait MasterLocationTapirEndpoints {
  def getMasterLocation: PublicEndpoint[MasterLocationId, Unit, Json, Any]
  def upsertMasterLocation: PublicEndpoint[MasterLocation, Unit, Unit, Any]
  def getMasterLocationsByMaster: PublicEndpoint[MasterId, Unit, List[MasterLocation], Any]

  final def all: List[AnyEndpoint] = List(
    getMasterLocation,
    upsertMasterLocation,
    getMasterLocationsByMaster,
  )
}

object MasterLocationTapirEndpoints extends MasterLocationTapirEndpoints {
  private val base = endpoint.in("master-location")

  val getMasterLocation = base.get
    .in(path[MasterLocationId]("id"))
    .out(jsonBody[Json])

  val upsertMasterLocation = base.post
    .in(jsonBody[MasterLocation])
    .out(emptyOutput)

  val getMasterLocationsByMaster = base.get
    .in("master")
    .in(path[MasterId]("masterId"))
    .out(jsonBody[List[MasterLocation]])
}
