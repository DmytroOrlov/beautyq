package leaderboard

import java.util.UUID

package object model {
  type UserId = UUID
  type Score  = Long
  type CategoryId = UUID
  val rootCategoryId = UUID.fromString("73ba445e-edf0-4ecf-a02b-91d0932e1f10")
}
