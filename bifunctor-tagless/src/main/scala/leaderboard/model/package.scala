package leaderboard

import java.util.UUID

package object model {
  type UserId                      = UUID
  type ServiceId                   = UUID
  type MasterId                    = UUID
  type MasterLocationId            = UUID
  type MasterServiceOfferId        = UUID
  type MasterServiceOfferVariantId = UUID
  type Score                       = Long
  type AttributeMap[A]             = leaderboard.model.AttributeMapImpl[A, AttributeDefinition[A]]
}
