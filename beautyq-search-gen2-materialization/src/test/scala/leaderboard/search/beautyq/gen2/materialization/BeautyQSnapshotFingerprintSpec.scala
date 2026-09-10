package leaderboard.search.beautyq.gen2.materialization

import leaderboard.model.*
import leaderboard.search.gen2.core.materialization.VersionedSnapshot
import org.scalatest.wordspec.AnyWordSpec
import leaderboard.search.beautyq.gen2.contract.BeautyQSearchDeclarations

import java.time.Instant

final class BeautyQSnapshotFingerprintSpec extends AnyWordSpec {
  import BeautyQGen2MaterializationFixtures.*

  private def fingerprintOf(snapshot: BeautyQSearchSnapshot): String = BeautyQSnapshotFingerprint.compute(snapshot).value

  private def remake(base: MasterServiceOfferVariant)(
    priceFrom: BigDecimal                          = base.priceFrom,
    priceTo: BigDecimal                            = base.priceTo,
    durationMin: Int                                = base.durationMin,
    attributes: MasterServiceOfferVariantAttributes = base.attributes,
  ): MasterServiceOfferVariant =
    MasterServiceOfferVariant
      .make(base.id, base.masterServiceOfferId, base.masterLocationId, priceFrom, priceTo, durationMin, attributes)
      .getOrElse(throw new IllegalStateException("test variant must be valid"))

  "BeautyQSnapshotFingerprint" should {
    "derive the exact catalog entity inventory from the snapshot product" in {
      assert(
        BeautyQSnapshotFingerprint.SourceNames ==
          BeautyQSearchDeclarations.catalog.topology.steps.map(_.introducedEntity)
      )
    }

    "produce equal fingerprints for equal snapshot values" in {
      assert(fingerprintOf(snapshot) == fingerprintOf(snapshot.copy()))
    }

    "be unaffected by reversing every source vector" in {
      val reversed = combinedSnapshot.copy(
        categories = combinedSnapshot.categories.reverse,
        services = combinedSnapshot.services.reverse,
        serviceVariantSchemas = combinedSnapshot.serviceVariantSchemas.reverse,
        masters = combinedSnapshot.masters.reverse,
        masterLocations = combinedSnapshot.masterLocations.reverse,
        masterServiceOffers = combinedSnapshot.masterServiceOffers.reverse,
        masterServiceOfferVariants = combinedSnapshot.masterServiceOfferVariants.reverse,
      )

      assert(fingerprintOf(combinedSnapshot) == fingerprintOf(reversed))
    }

    "be unaffected by schema-item order" in {
      val forwardSchema = ServiceVariantSchema.fromItems(
        serviceId,
        Vector(
          ServiceVariantSchemaItem(AttributeDefinition.SessionCount, required = false),
          ServiceVariantSchemaItem(AttributeDefinition.DepositAmount, required = true),
        ),
      )
      val reversedSchema = ServiceVariantSchema.fromItems(
        serviceId,
        Vector(
          ServiceVariantSchemaItem(AttributeDefinition.DepositAmount, required = true),
          ServiceVariantSchemaItem(AttributeDefinition.SessionCount, required = false),
        ),
      )

      assert(fingerprintOf(snapshot.copy(serviceVariantSchemas = Vector(forwardSchema))) == fingerprintOf(snapshot.copy(serviceVariantSchemas = Vector(reversedSchema))))
    }

    "be unaffected by attribute-map iteration order" in {
      val forwardAttributes = variantAttributes.copy(
        intValues = AttributeMap.empty
          .updated(AttributeDefinition.SessionCount, 3)
          .updated(AttributeDefinition.IncludedCorrectionsCount, 2)
      )
      val reversedAttributes = variantAttributes.copy(
        intValues = AttributeMap.empty
          .updated(AttributeDefinition.IncludedCorrectionsCount, 2)
          .updated(AttributeDefinition.SessionCount, 3)
      )

      val forwardVariant = remake(variant)(attributes = forwardAttributes)
      val reversedVariant = remake(variant)(attributes = reversedAttributes)

      assert(
        fingerprintOf(snapshot.copy(masterServiceOfferVariants = Vector(forwardVariant))) ==
          fingerprintOf(snapshot.copy(masterServiceOfferVariants = Vector(reversedVariant)))
      )
    }

    "not be affected by different VersionedSnapshot.capturedAt values" in {
      val contentFingerprint = BeautyQSnapshotFingerprint.compute(snapshot)
      val versionedEarly = VersionedSnapshot(snapshot, contentFingerprint, capturedAt = Instant.parse("2020-01-01T00:00:00Z"))
      val versionedLate = VersionedSnapshot(snapshot, contentFingerprint, capturedAt = Instant.parse("2030-06-15T12:30:00Z"))

      assert(versionedEarly.contentFingerprint == versionedLate.contentFingerprint)
      assert(versionedEarly.contentFingerprint == BeautyQSnapshotFingerprint.compute(snapshot))
    }

    "change when a category name changes" in {
      val changed = snapshot.copy(categories = Vector(category.copy(name = "Renamed Category")))
      assert(fingerprintOf(snapshot) != fingerprintOf(changed))
    }

    "change when a service code changes" in {
      val changed = snapshot.copy(services = Vector(service.copy(code = ServiceCode.unsafeFromString("changed_service_code"))))
      assert(fingerprintOf(snapshot) != fingerprintOf(changed))
    }

    "change when a location coordinate changes" in {
      val changed = snapshot.copy(masterLocations = Vector(location.copy(lat = location.lat + BigDecimal("1.0"))))
      assert(fingerprintOf(snapshot) != fingerprintOf(changed))
    }

    "change when a schema's required flag changes" in {
      val requiredSchema = ServiceVariantSchema.fromItems(serviceId, Vector(ServiceVariantSchemaItem(AttributeDefinition.SessionCount, required = true)))
      val optionalSchema = ServiceVariantSchema.fromItems(serviceId, Vector(ServiceVariantSchemaItem(AttributeDefinition.SessionCount, required = false)))

      assert(fingerprintOf(snapshot.copy(serviceVariantSchemas = Vector(requiredSchema))) != fingerprintOf(snapshot.copy(serviceVariantSchemas = Vector(optionalSchema))))
    }

    "change when a variant price changes" in {
      val changed = snapshot.copy(masterServiceOfferVariants = Vector(remake(variant)(priceFrom = variant.priceFrom + BigDecimal("5.0"))))
      assert(fingerprintOf(snapshot) != fingerprintOf(changed))
    }

    "change when a variant duration changes" in {
      val changed = snapshot.copy(masterServiceOfferVariants = Vector(remake(variant)(durationMin = variant.durationMin + 1)))
      assert(fingerprintOf(snapshot) != fingerprintOf(changed))
    }

    "change when the enum attribute family changes" in {
      val changedAttributes = variantAttributes.copy(enumValues = AttributeMap.empty.updated(AttributeDefinition.NailCoatingTypeAttribute, NailCoatingType.Acrylic))
      val changed = snapshot.copy(masterServiceOfferVariants = Vector(remake(variant)(attributes = changedAttributes)))
      assert(fingerprintOf(snapshot) != fingerprintOf(changed))
    }

    "change when the boolean attribute family changes" in {
      val changedAttributes = variantAttributes.copy(booleanValues = AttributeMap.empty.updated(AttributeDefinition.WithRemoval, false))
      val changed = snapshot.copy(masterServiceOfferVariants = Vector(remake(variant)(attributes = changedAttributes)))
      assert(fingerprintOf(snapshot) != fingerprintOf(changed))
    }

    "change when the int attribute family changes" in {
      val changedAttributes = variantAttributes.copy(intValues = AttributeMap.empty.updated(AttributeDefinition.SessionCount, 99))
      val changed = snapshot.copy(masterServiceOfferVariants = Vector(remake(variant)(attributes = changedAttributes)))
      assert(fingerprintOf(snapshot) != fingerprintOf(changed))
    }

    "change when the bigDecimal attribute family changes" in {
      val changedAttributes = variantAttributes.copy(bigDecimalValues = AttributeMap.empty.updated(AttributeDefinition.DepositAmount, BigDecimal("999.0000")))
      val changed = snapshot.copy(masterServiceOfferVariants = Vector(remake(variant)(attributes = changedAttributes)))
      assert(fingerprintOf(snapshot) != fingerprintOf(changed))
    }

    "produce exactly 64 lowercase hexadecimal characters" in {
      val hex = fingerprintOf(snapshot)
      assert(hex.length == 64)
      assert(hex.forall(c => c.isDigit || (c >= 'a' && c <= 'f')))
    }

    "produce the accepted golden fixture fingerprint (Commit 3D kernel-extraction compatibility)" in {
      assert(fingerprintOf(snapshot) == "83b54a1bea5b162a7fce40405f4cb81f8a2f0705b40b52265b456a677b429280")
    }

    "produce the same fingerprint for a reordered invalid snapshot with duplicate category ids, when the row multiset is unchanged" in {
      val duplicateA = category.copy(name = "Duplicate Row A")
      val duplicateB = category.copy(name = "Duplicate Row B")

      val forward = snapshot.copy(categories = Vector(duplicateA, duplicateB))
      val backward = snapshot.copy(categories = Vector(duplicateB, duplicateA))

      assert(fingerprintOf(forward) == fingerprintOf(backward))
    }
  }
}
