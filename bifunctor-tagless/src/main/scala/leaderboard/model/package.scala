package leaderboard

import leaderboard.model.AttributeMap.Impl
import io.circe.Codec
import io.circe.generic.semiauto
import leaderboard.model.Category.CategoryId

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

  case class Service(id: ServiceId, categoryId: CategoryId, name: String)

  object Service {
    implicit val codec: Codec.AsObject[Service] = semiauto.deriveCodec
  }

  case class Category(id: CategoryId, parentId: CategoryId, depth: Int, name: String)

  object Category {
    type CategoryId = UUID

    val rootCategoryId: UUID = UUID.fromString("73ba445e-edf0-4ecf-a02b-91d0932e1f10")

    implicit val codec: Codec.AsObject[Category] = semiauto.deriveCodec
  }

  case class Master(
    id: MasterId,
    name: String,
  )

  object Master {
    implicit val codec: Codec.AsObject[Master] = semiauto.deriveCodec
  }

  case class MasterServiceOffer(
    id: MasterServiceOfferId,
    masterId: MasterId,
    serviceId: ServiceId,
  )

  object MasterServiceOffer {
    implicit val codec: Codec.AsObject[MasterServiceOffer] = semiauto.deriveCodec
  }

  case class MasterLocation(
    id: MasterLocationId,
    masterId: MasterId,
    name: String,
    address: String,
    lat: BigDecimal,
    lon: BigDecimal,
  )

  object MasterLocation {
    implicit val codec: Codec.AsObject[MasterLocation] = semiauto.deriveCodec
  }
}
