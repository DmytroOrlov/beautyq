package leaderboard

import java.util.UUID

package object model {
  type UserId           = UUID
  type ServiceId        = UUID
  type MasterId         = UUID
  type MasterLocationId = UUID
  type Score            = Long
}
