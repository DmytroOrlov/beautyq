package leaderboard.http.tapir

import io.circe.Json
import leaderboard.http.HttpApiFailure
import leaderboard.model.{MasterLocationId, MasterServiceOfferId, MasterServiceOfferVariant, MasterServiceOfferVariantId}
import sttp.tapir.*
import sttp.tapir.json.circe.*

trait MasterServiceOfferVariantTapirEndpoints {
  def getMasterServiceOfferVariant: PublicEndpoint[MasterServiceOfferVariantId, HttpApiFailure, MasterServiceOfferVariant, Any]
  def upsertMasterServiceOfferVariant: PublicEndpoint[Json, HttpApiFailure, Unit, Any]
  def getMasterServiceOfferVariantsByOffer: PublicEndpoint[MasterServiceOfferId, HttpApiFailure, Json, Any]
  def getMasterServiceOfferVariantsByLocation: PublicEndpoint[MasterLocationId, HttpApiFailure, Json, Any]

  final def all: List[AnyEndpoint] = List(
    getMasterServiceOfferVariant,
    upsertMasterServiceOfferVariant,
    getMasterServiceOfferVariantsByOffer,
    getMasterServiceOfferVariantsByLocation,
  )
}

object MasterServiceOfferVariantTapirEndpoints extends MasterServiceOfferVariantTapirEndpoints {
  given Schema[MasterServiceOfferVariant] =
    Schema.any[MasterServiceOfferVariant]

  private val base = HttpApiFailureTapirSupport.endpointBase.in("master-service-offer-variant")
  private val getBase = sttp.tapir.endpoint.in("master-service-offer-variant")

  val getMasterServiceOfferVariant = getBase.get
    .errorOut(HttpApiFailureTapirSupport.singleEntityGetErrorOutput)
    .in(path[MasterServiceOfferVariantId]("id"))
    .out(jsonBody[MasterServiceOfferVariant])

  val upsertMasterServiceOfferVariant = base.post
    .in(jsonBody[Json])
    .out(emptyOutput)

  val getMasterServiceOfferVariantsByOffer = base.get
    .in("offer")
    .in(path[MasterServiceOfferId]("masterServiceOfferId"))
    .out(jsonBody[Json])

  val getMasterServiceOfferVariantsByLocation = base.get
    .in("location")
    .in(path[MasterLocationId]("masterLocationId"))
    .out(jsonBody[Json])
}
