package leaderboard.model

import io.circe.Codec
import io.circe.generic.semiauto

case class MasterServiceOffer(
  id: MasterServiceOfferId,
  masterId: MasterId,
  serviceId: ServiceId,
)

object MasterServiceOffer {
  implicit val codec: Codec.AsObject[MasterServiceOffer] = semiauto.deriveCodec
}
