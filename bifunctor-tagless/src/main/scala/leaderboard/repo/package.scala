package leaderboard

import leaderboard.model.Category.rootCategoryId

package object repo {
  val rootCategoryIdSqlLiteral = s"'$rootCategoryId'::uuid"
}
