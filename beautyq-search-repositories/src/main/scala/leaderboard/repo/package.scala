package leaderboard

import doobie.Meta
import doobie.postgres.implicits.*
import leaderboard.model.Category.rootCategoryId
import leaderboard.model.{CanonicalStringValue, UuidBackedId}

import java.util.UUID

package object repo {
  val rootCategoryIdSqlLiteral = s"'${rootCategoryId.value}'::uuid"

  given [A](using id: UuidBackedId[A]): Meta[A] =
    Meta[UUID].timap(id.apply)(id.unwrap)

  given [A](using value: CanonicalStringValue[A]): Meta[A] =
    Meta[String].tiemap(value.decodeCanonical)(value.encodeCanonical)
}
