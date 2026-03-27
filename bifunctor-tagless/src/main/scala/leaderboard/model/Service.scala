package leaderboard.model

import io.circe.Codec
import io.circe.generic.semiauto
import leaderboard.model.Category.CategoryId

case class Service(id: ServiceId, categoryId: CategoryId, name: String)

object Service {
  implicit val codec: Codec.AsObject[Service] = semiauto.deriveCodec
}
