package leaderboard

import leaderboard.model.AttributeMap.Impl

import java.util.UUID

package object model {
  type UserId                      = UUID
  type ServiceId                   = UUID
  type MasterId                    = UUID
  type MasterLocationId            = UUID
  type MasterServiceOfferId        = UUID
  type MasterServiceOfferVariantId = UUID
  type Score                       = Long
  type AttributeMap[A]             = Impl[A, AttributeDefinition[A]]
}
