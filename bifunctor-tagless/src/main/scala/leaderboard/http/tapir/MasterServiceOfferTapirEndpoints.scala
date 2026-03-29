package leaderboard.http.tapir

import io.circe.Json
import leaderboard.model.{MasterId, MasterServiceOffer, MasterServiceOfferId, ServiceId}
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*

trait MasterServiceOfferTapirEndpoints {
  def getMasterServiceOffer: PublicEndpoint[MasterServiceOfferId, Unit, Json, Any]
  def upsertMasterServiceOffer: PublicEndpoint[MasterServiceOffer, Unit, Unit, Any]
  def getMasterServiceOffersByMaster: PublicEndpoint[MasterId, Unit, List[MasterServiceOffer], Any]
  def getMasterServiceOffersByService: PublicEndpoint[ServiceId, Unit, List[MasterServiceOffer], Any]

  final def all: List[AnyEndpoint] = List(
    getMasterServiceOffer,
    upsertMasterServiceOffer,
    getMasterServiceOffersByMaster,
    getMasterServiceOffersByService,
  )
}

object MasterServiceOfferTapirEndpoints extends MasterServiceOfferTapirEndpoints {
  private val base = endpoint.in("master-service-offer")

  val getMasterServiceOffer = base.get
    .in(path[MasterServiceOfferId]("id"))
    .out(jsonBody[Json])

  val upsertMasterServiceOffer = base.post
    .in(jsonBody[MasterServiceOffer])
    .out(emptyOutput)

  val getMasterServiceOffersByMaster = base.get
    .in("master")
    .in(path[MasterId]("masterId"))
    .out(jsonBody[List[MasterServiceOffer]])

  val getMasterServiceOffersByService = base.get
    .in("service")
    .in(path[ServiceId]("serviceId"))
    .out(jsonBody[List[MasterServiceOffer]])
}
