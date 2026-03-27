package leaderboard.model

import io.circe.Codec
import io.circe.generic.semiauto

case class MasterLocation(
  id: MasterLocationId,
  masterId: MasterId,
  name: String,
  address: String,
  lat: BigDecimal,
  lon: BigDecimal,
)

object MasterLocation {
  implicit val codec: Codec.AsObject[MasterLocation] = semiauto.deriveCodec
}
