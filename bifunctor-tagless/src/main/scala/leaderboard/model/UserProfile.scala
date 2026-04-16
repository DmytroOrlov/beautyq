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

case class RankedProfile(
  name: String,
  description: String,
  rank: Int,
  score: Score,
)

object RankedProfile {
  implicit val codec: Codec.AsObject[RankedProfile] = semiauto.deriveCodec
}
