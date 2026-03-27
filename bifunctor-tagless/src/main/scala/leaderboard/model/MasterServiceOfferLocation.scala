package leaderboard.model

import io.circe.Codec
import io.circe.generic.semiauto

case class MasterServiceOfferLocation(
  id: MasterServiceOfferLocationId,
  masterServiceOfferId: MasterServiceOfferId,
  masterLocationId: MasterLocationId,
)

object MasterServiceOfferLocation {
  implicit val codec: Codec.AsObject[MasterServiceOfferLocation] = semiauto.deriveCodec
}
