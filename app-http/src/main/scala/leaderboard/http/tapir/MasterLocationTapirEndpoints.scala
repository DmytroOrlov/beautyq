package leaderboard.http.tapir

import leaderboard.http.HttpApiFailure
import leaderboard.http.tapir.BeautyQIdTapirSupport.given
import leaderboard.model.{MasterId, MasterLocation, MasterLocationId}
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*

trait MasterLocationTapirEndpoints {
  def getMasterLocation: PublicEndpoint[MasterLocationId, HttpApiFailure, MasterLocation, Any]
  def upsertMasterLocation: PublicEndpoint[MasterLocation, HttpApiFailure, Unit, Any]
  def getMasterLocationsByMaster: PublicEndpoint[MasterId, HttpApiFailure, List[MasterLocation], Any]

  final def all: List[AnyEndpoint] = List(
    getMasterLocation,
    upsertMasterLocation,
    getMasterLocationsByMaster,
  )
}

object MasterLocationTapirEndpoints extends MasterLocationTapirEndpoints {
  private val base = sttp.tapir.endpoint.in("master-location")

  val getMasterLocation = base.get
    .errorOut(HttpApiFailureTapirSupport.singleEntityGetErrorOutput)
    .in(path[MasterLocationId]("id"))
    .out(jsonBody[MasterLocation])

  val upsertMasterLocation = HttpApiFailureTapirSupport.endpointBase.in("master-location").post
    .in(jsonBody[MasterLocation])
    .out(emptyOutput)

  val getMasterLocationsByMaster = HttpApiFailureTapirSupport.endpointBase.in("master-location").get
    .in("master")
    .in(path[MasterId]("masterId"))
    .out(jsonBody[List[MasterLocation]])
}
