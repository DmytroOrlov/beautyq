package leaderboard.search.beautyq.gen2.materialization

import leaderboard.model.*
import leaderboard.search.gen2.core.materialization.{CanonicalFingerprint, CanonicalSourceRows, canonicalRow}

/** BeautyQ's explicit source-field/value policy, shared by source fingerprinting and projection
  * duplicate ordering. The generic kernel owns row framing, token emission and sorting; this object
  * owns only which persisted values participate in each BeautyQ row.
  */
object BeautyQSnapshotCanonicalRows {
  given CanonicalSourceRows[Category] = CanonicalSourceRows("category")(category)
  given CanonicalSourceRows[Service] = CanonicalSourceRows("service")(service)
  given CanonicalSourceRows[ServiceVariantSchema] = CanonicalSourceRows("serviceVariantSchema")(schema)
  given CanonicalSourceRows[Master] = CanonicalSourceRows("master")(master)
  given CanonicalSourceRows[MasterLocation] = CanonicalSourceRows("masterLocation")(location)
  given CanonicalSourceRows[MasterServiceOffer] = CanonicalSourceRows("masterServiceOffer")(offer)
  given CanonicalSourceRows[MasterServiceOfferVariant] = CanonicalSourceRows("masterServiceOfferVariant")(variant)

  def category(category: Category): CanonicalFingerprint.CanonicalRow =
    canonicalRow("category", category)
      .field(_.id)
      .field(_.code)
      .field(_.parentId)
      .field(_.depth)
      .field(_.name)
      .build

  def service(service: Service): CanonicalFingerprint.CanonicalRow =
    canonicalRow("service", service)
      .field(_.id)
      .field(_.code)
      .field(_.categoryId)
      .field(_.name)
      .build

  def schema(schema: ServiceVariantSchema): CanonicalFingerprint.CanonicalRow = {
    val items = schema.items.toVector.sortBy(_.attribute.code)
    canonicalRow("schema", schema)
      .field(_.serviceId)
      .group("item", items) { (group, item) =>
        group
          .value("code", item.attribute.code)
          .field(item)(_.required)
      }
      .build
  }

  def master(master: Master): CanonicalFingerprint.CanonicalRow =
    canonicalRow("master", master)
      .field(_.id)
      .field(_.name)
      .build

  def location(location: MasterLocation): CanonicalFingerprint.CanonicalRow =
    canonicalRow("location", location)
      .field(_.id)
      .field(_.masterId)
      .field(_.name)
      .field(_.address)
      .field(_.lat)
      .field(_.lon)
      .build

  def offer(offer: MasterServiceOffer): CanonicalFingerprint.CanonicalRow =
    canonicalRow("offer", offer)
      .field(_.id)
      .field(_.masterId)
      .field(_.serviceId)
      .build

  def variant(variant: MasterServiceOfferVariant): CanonicalFingerprint.CanonicalRow = {
    val enumAttributes = variant.enumAttributes.iterator.toVector.sortBy { case (definition, _) => definition.code }
    val booleanAttributes = variant.booleanAttributes.iterator.toVector.sortBy { case (definition, _) => definition.code }
    val intAttributes = variant.intAttributes.iterator.toVector.sortBy { case (definition, _) => definition.code }
    val bigDecimalAttributes = variant.bigDecimalAttributes.iterator.toVector.sortBy { case (definition, _) => definition.code }

    canonicalRow("variant", variant)
      .field(_.id)
      .field(_.masterServiceOfferId)
      .field(_.masterLocationId)
      .field(_.priceFrom)
      .field(_.priceTo)
      .field(_.durationMin)
      .group("enumAttributes", enumAttributes) { case (group, (definition, value)) =>
        group.value("code", definition.code).value("value", value.stringCode)
      }
      .group("booleanAttributes", booleanAttributes) { case (group, (definition, value)) =>
        group.value("code", definition.code).value("value", value)
      }
      .group("intAttributes", intAttributes) { case (group, (definition, value)) =>
        group.value("code", definition.code).value("value", value)
      }
      .group("bigDecimalAttributes", bigDecimalAttributes) { case (group, (definition, value)) =>
        group.value("code", definition.code).value("value", value)
      }
      .build
  }
}
