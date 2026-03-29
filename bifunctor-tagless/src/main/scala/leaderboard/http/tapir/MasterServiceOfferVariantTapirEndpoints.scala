package leaderboard.http.tapir

import io.circe.Json
import leaderboard.model.{MasterLocationId, MasterServiceOfferId, MasterServiceOfferVariantId}
import sttp.tapir.*
import sttp.tapir.json.circe.*

trait MasterServiceOfferVariantTapirEndpoints {
  def getMasterServiceOfferVariant: PublicEndpoint[MasterServiceOfferVariantId, Unit, Json, Any]
  def upsertMasterServiceOfferVariant: PublicEndpoint[Json, Unit, Unit, Any]
  def getMasterServiceOfferVariantsByOffer: PublicEndpoint[MasterServiceOfferId, Unit, Json, Any]
  def getMasterServiceOfferVariantsByLocation: PublicEndpoint[MasterLocationId, Unit, Json, Any]

  final def all: List[AnyEndpoint] = List(
    getMasterServiceOfferVariant,
    upsertMasterServiceOfferVariant,
    getMasterServiceOfferVariantsByOffer,
    getMasterServiceOfferVariantsByLocation,
  )
}

object MasterServiceOfferVariantTapirEndpoints extends MasterServiceOfferVariantTapirEndpoints {
  private val base = endpoint.in("master-service-offer-variant")

  val getMasterServiceOfferVariant = base.get
    .in(path[MasterServiceOfferVariantId]("id"))
    .out(jsonBody[Json])

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
