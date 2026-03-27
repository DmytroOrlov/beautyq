package leaderboard.model

import io.circe.Codec
import io.circe.generic.semiauto

case class Master(
  id: MasterId,
  name: String,
)

object Master {
  implicit val codec: Codec.AsObject[Master] = semiauto.deriveCodec
}
