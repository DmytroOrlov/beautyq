package leaderboard.search.beautyq.gen2.materialization

import leaderboard.model.*
import leaderboard.model.Category.CategoryId
import leaderboard.search.gen2.contract.NonEmptyErrors

sealed trait BeautyQVariantProjectionError extends Product with Serializable {
  def code: String
  def message: String
  def sortKey: String
}

object BeautyQVariantProjectionError {
  final case class DuplicateEntityId(entityName: String, id: String) extends BeautyQVariantProjectionError {
    def code: String = "duplicate-entity-id"
    def message: String = s"Duplicate $entityName id: $id"
    def sortKey: String = s"$entityName:$id"
  }

  final case class DuplicateCategoryCode(categoryCode: CategoryCode) extends BeautyQVariantProjectionError {
    def code: String = "duplicate-category-code"
    def message: String = s"Duplicate CategoryCode: ${categoryCode.value}"
    def sortKey: String = categoryCode.value
  }

  final case class DuplicateServiceCode(serviceCode: ServiceCode) extends BeautyQVariantProjectionError {
    def code: String = "duplicate-service-code"
    def message: String = s"Duplicate ServiceCode: ${serviceCode.value}"
    def sortKey: String = serviceCode.value
  }

  final case class DuplicateServiceSchema(serviceId: ServiceId) extends BeautyQVariantProjectionError {
    def code: String = "duplicate-service-schema"
    def message: String = s"Duplicate ServiceVariantSchema for service ${serviceId.value}"
    def sortKey: String = serviceId.value.toString
  }

  final case class MissingOffer(variantId: MasterServiceOfferVariantId, offerId: MasterServiceOfferId) extends BeautyQVariantProjectionError {
    def code: String = "missing-offer"
    def message: String = s"Variant ${variantId.value} references missing MasterServiceOffer ${offerId.value}"
    def sortKey: String = variantId.value.toString
  }

  final case class MissingService(variantId: MasterServiceOfferVariantId, serviceId: ServiceId) extends BeautyQVariantProjectionError {
    def code: String = "missing-service"
    def message: String = s"Variant ${variantId.value} references missing Service ${serviceId.value}"
    def sortKey: String = variantId.value.toString
  }

  final case class MissingCategory(variantId: MasterServiceOfferVariantId, categoryId: CategoryId) extends BeautyQVariantProjectionError {
    def code: String = "missing-category"
    def message: String = s"Variant ${variantId.value} references missing Category ${categoryId.value}"
    def sortKey: String = variantId.value.toString
  }

  final case class MissingMaster(variantId: MasterServiceOfferVariantId, masterId: MasterId) extends BeautyQVariantProjectionError {
    def code: String = "missing-master"
    def message: String = s"Variant ${variantId.value} references missing Master ${masterId.value}"
    def sortKey: String = variantId.value.toString
  }

  final case class MissingLocation(variantId: MasterServiceOfferVariantId, locationId: MasterLocationId) extends BeautyQVariantProjectionError {
    def code: String = "missing-location"
    def message: String = s"Variant ${variantId.value} references missing MasterLocation ${locationId.value}"
    def sortKey: String = variantId.value.toString
  }

  final case class MissingServiceSchema(variantId: MasterServiceOfferVariantId, serviceId: ServiceId) extends BeautyQVariantProjectionError {
    def code: String = "missing-service-schema"
    def message: String = s"Variant ${variantId.value}'s service ${serviceId.value} has no ServiceVariantSchema in the snapshot"
    def sortKey: String = variantId.value.toString
  }

  final case class OfferLocationMasterMismatch(
    variantId: MasterServiceOfferVariantId,
    offerMasterId: MasterId,
    locationMasterId: MasterId,
  ) extends BeautyQVariantProjectionError {
    def code: String = "offer-location-master-mismatch"
    def message: String =
      s"Variant ${variantId.value}'s offer master ${offerMasterId.value} does not match location master ${locationMasterId.value}"
    def sortKey: String = variantId.value.toString
  }

  final case class SchemaViolation(
    variantId: MasterServiceOfferVariantId,
    serviceId: ServiceId,
    violation: String,
  ) extends BeautyQVariantProjectionError {
    def code: String = "schema-violation"
    def message: String = s"Variant ${variantId.value} violates schema for service ${serviceId.value}: $violation"
    def sortKey: String = variantId.value.toString
  }
}

/** A non-empty, deterministically ordered accumulation of independent variant projection errors,
  * sorted by `sortKey`, then `code`, then `message` before construction so the same invalid snapshot
  * always reports its errors in the same order.
  */
type BeautyQVariantProjectionErrors = NonEmptyErrors[BeautyQVariantProjectionError]

object BeautyQVariantProjectionErrors {
  def fromVector(errors: Vector[BeautyQVariantProjectionError]): Option[BeautyQVariantProjectionErrors] =
    NonEmptyErrors.fromVector(errors.sortBy(error => (error.sortKey, error.code, error.message)))
}
