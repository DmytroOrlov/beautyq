package leaderboard.model

import io.circe.Codec
import io.circe.generic.semiauto
import leaderboard.model.Category.CategoryId

import java.util.UUID

case class Category(id: CategoryId, parentId: CategoryId, depth: Int, name: String)

object Category {
  type CategoryId = UUID

  val rootCategoryId: UUID = UUID.fromString("73ba445e-edf0-4ecf-a02b-91d0932e1f10")

  implicit val codec: Codec.AsObject[Category] = semiauto.deriveCodec
}
