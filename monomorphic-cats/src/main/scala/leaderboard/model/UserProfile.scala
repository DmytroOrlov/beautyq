package leaderboard.model

import io.circe.Codec
import io.circe.generic.semiauto

case class UserProfile(
  name: String,
  description: String,
)

object UserProfile {
  implicit val codec: Codec.AsObject[UserProfile] = semiauto.deriveCodec
}
