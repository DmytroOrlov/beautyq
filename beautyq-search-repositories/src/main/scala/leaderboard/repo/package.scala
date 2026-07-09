package leaderboard

import doobie.Meta
import doobie.postgres.implicits.*
import leaderboard.model.Category.rootCategoryId
import leaderboard.model.UuidBackedId

import java.util.UUID

package object repo {
  val rootCategoryIdSqlLiteral = s"'${rootCategoryId.value}'::uuid"

  given [A](using id: UuidBackedId[A]): Meta[A] =
    Meta[UUID].timap(id.apply)(id.unwrap)
}
