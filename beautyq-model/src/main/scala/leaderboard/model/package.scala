package leaderboard

import leaderboard.model.AttributeMap.Impl
import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto
import leaderboard.model.Category.CategoryId

import java.util.UUID

package object model {
  type UserId           = UUID
  type Score             = Long
  type AttributeMap[A]   = Impl[A, AttributeDefinition[A]]

  // `UuidBackedId[A]` itself lives in `leaderboard-core`
  // (`leaderboard.model.UuidBackedId`) - dependency-free and shared across
  // domains, so a future domain's model module can depend on it directly
  // without depending on BeautyQ's own model internals. It is used here,
  // unqualified, via the same `leaderboard.model` package (no import
  // needed): `beautyq-model` depends on `leaderboard-core`.

  // Captured once, at the top of the package object, and referenced by name
  // (never re-summoned) from `uuidBackedIdCodec`: every opaque id declared
  // below is transparently `=:= UUID` from *inside* this same file, so a
  // `given Codec[A]` generically derived from `UuidBackedId[A]` evidence
  // would itself be a candidate `UuidBackedId[UUID]` for all six ids at
  // once here (ambiguous) - unlike Doobie/Tapir/Scalacheck, which derive
  // their adapters from files outside this transparent scope, where the ids
  // are not interchangeable. A plain helper function (called once per id,
  // not searched for implicitly) sidesteps that opaque-scope ambiguity. Kept
  // here (not in leaderboard-core) because it is Circe/model-local - Circe
  // is a `beautyq-model` dependency, not a `leaderboard-core` one - and
  // `private` because every call site is this same package object file.
  private val uuidDecoder: Decoder[UUID] = Decoder[UUID]
  private val uuidEncoder: Encoder[UUID] = Encoder[UUID]

  private def uuidBackedIdCodec[A](id: UuidBackedId[A]): Codec[A] =
    Codec.from(
      uuidDecoder.map(id.apply),
      uuidEncoder.contramap(id.unwrap),
    )

  opaque type ServiceId = UUID

  object ServiceId extends UuidBackedId[ServiceId] {
    def apply(value: UUID): ServiceId = value
    def unwrap(id: ServiceId): UUID = id
    implicit val codec: Codec[ServiceId] = uuidBackedIdCodec(ServiceId)
    given UuidBackedId[ServiceId] = this
  }

  opaque type MasterId = UUID

  object MasterId extends UuidBackedId[MasterId] {
    def apply(value: UUID): MasterId = value
    def unwrap(id: MasterId): UUID = id
    implicit val codec: Codec[MasterId] = uuidBackedIdCodec(MasterId)
    given UuidBackedId[MasterId] = this
  }

  opaque type MasterLocationId = UUID

  object MasterLocationId extends UuidBackedId[MasterLocationId] {
    def apply(value: UUID): MasterLocationId = value
    def unwrap(id: MasterLocationId): UUID = id
    implicit val codec: Codec[MasterLocationId] = uuidBackedIdCodec(MasterLocationId)
    given UuidBackedId[MasterLocationId] = this
  }

  opaque type MasterServiceOfferId = UUID

  object MasterServiceOfferId extends UuidBackedId[MasterServiceOfferId] {
    def apply(value: UUID): MasterServiceOfferId = value
    def unwrap(id: MasterServiceOfferId): UUID = id
    implicit val codec: Codec[MasterServiceOfferId] = uuidBackedIdCodec(MasterServiceOfferId)
    given UuidBackedId[MasterServiceOfferId] = this
  }

  opaque type MasterServiceOfferVariantId = UUID

  object MasterServiceOfferVariantId extends UuidBackedId[MasterServiceOfferVariantId] {
    def apply(value: UUID): MasterServiceOfferVariantId = value
    def unwrap(id: MasterServiceOfferVariantId): UUID = id
    implicit val codec: Codec[MasterServiceOfferVariantId] = uuidBackedIdCodec(MasterServiceOfferVariantId)
    given UuidBackedId[MasterServiceOfferVariantId] = this
  }

  case class Service(id: ServiceId, categoryId: CategoryId, name: String)

  object Service {
    implicit val codec: Codec.AsObject[Service] = semiauto.deriveCodec
  }

  case class Category(id: CategoryId, parentId: CategoryId, depth: Int, name: String)

  object Category {
    opaque type CategoryId = UUID

    object CategoryId extends UuidBackedId[CategoryId] {
      def apply(value: UUID): CategoryId = value
      def unwrap(id: CategoryId): UUID = id
      implicit val codec: Codec[CategoryId] = uuidBackedIdCodec(CategoryId)
      given UuidBackedId[CategoryId] = this
    }

    val rootCategoryId: CategoryId = CategoryId(UUID.fromString("73ba445e-edf0-4ecf-a02b-91d0932e1f10"))

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
