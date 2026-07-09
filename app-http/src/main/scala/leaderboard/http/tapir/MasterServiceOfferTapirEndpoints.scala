package leaderboard.http.tapir

import leaderboard.http.HttpApiFailure
import leaderboard.http.tapir.UuidBackedIdTapirSupport.given
import leaderboard.model.{MasterId, MasterServiceOffer, MasterServiceOfferId, ServiceId}
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*

trait MasterServiceOfferTapirEndpoints {
  def getMasterServiceOffer: PublicEndpoint[MasterServiceOfferId, HttpApiFailure, MasterServiceOffer, Any]
  def upsertMasterServiceOffer: PublicEndpoint[MasterServiceOffer, HttpApiFailure, Unit, Any]
  def getMasterServiceOffersByMaster: PublicEndpoint[MasterId, HttpApiFailure, List[MasterServiceOffer], Any]
  def getMasterServiceOffersByService: PublicEndpoint[ServiceId, HttpApiFailure, List[MasterServiceOffer], Any]

  final def all: List[AnyEndpoint] = List(
    getMasterServiceOffer,
    upsertMasterServiceOffer,
    getMasterServiceOffersByMaster,
    getMasterServiceOffersByService,
  )
}

object MasterServiceOfferTapirEndpoints extends MasterServiceOfferTapirEndpoints {
  private val base = HttpApiFailureTapirSupport.endpointBase.in("master-service-offer")
  private val getBase = sttp.tapir.endpoint.in("master-service-offer")

  val getMasterServiceOffer = getBase.get
    .errorOut(HttpApiFailureTapirSupport.singleEntityGetErrorOutput)
    .in(path[MasterServiceOfferId]("id"))
    .out(jsonBody[MasterServiceOffer])

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
