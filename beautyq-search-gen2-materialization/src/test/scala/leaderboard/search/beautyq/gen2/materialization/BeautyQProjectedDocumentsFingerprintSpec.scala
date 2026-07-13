package leaderboard.search.beautyq.gen2.materialization

import leaderboard.model.*
import leaderboard.search.beautyq.gen2.contract.{BeautyQSearchDeclarations, VariantSearchDocumentGen2}
import leaderboard.search.gen2.contract.GeoPoint
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQProjectedDocumentsFingerprintSpec extends AnyWordSpec {
  import BeautyQGen2MaterializationFixtures.*

  private def fingerprintOf(documents: Vector[VariantSearchDocumentGen2]): String = BeautyQProjectedDocumentsFingerprint.compute(documents).value

  private val document: VariantSearchDocumentGen2 =
    BeautyQVariantProjectionGen2.project(snapshot) match {
      case Right(Vector(single)) => single
      case other                 => throw new IllegalStateException(s"golden fixture must project to exactly one document, got: $other")
    }

  private val document2: VariantSearchDocumentGen2 =
    BeautyQVariantProjectionGen2.project(snapshot2) match {
      case Right(Vector(single)) => single
      case other                 => throw new IllegalStateException(s"golden fixture 2 must project to exactly one document, got: $other")
    }

  "BeautyQProjectedDocumentsFingerprint" should {
    "not let caller document order affect the fingerprint" in {
      assert(fingerprintOf(Vector(document, document2)) == fingerprintOf(Vector(document2, document)))
    }

    "produce equal fingerprints for identical documents" in {
      assert(fingerprintOf(Vector(document)) == fingerprintOf(Vector(document.copy())))
    }

    "change when a nominal id changes" in {
      val changed = document.copy(masterId = MasterId(java.util.UUID.fromString("d0000000-0000-0000-0000-000000000001")))
      assert(fingerprintOf(Vector(document)) != fingerprintOf(Vector(changed)))
    }

    "change when a stable code changes" in {
      val changed = document.copy(serviceCode = ServiceCode.unsafeFromString("changed_service_code"))
      assert(fingerprintOf(Vector(document)) != fingerprintOf(Vector(changed)))
    }

    "change when a presentation name changes" in {
      val changed = document.copy(serviceName = "Renamed Service")
      assert(fingerprintOf(Vector(document)) != fingerprintOf(Vector(changed)))
    }

    "change when a coordinate changes" in {
      val changed = document.copy(location = GeoPoint(document.lat + BigDecimal("1.0"), document.lon), lat = document.lat + BigDecimal("1.0"))
      assert(fingerprintOf(Vector(document)) != fingerprintOf(Vector(changed)))
    }

    "change when a price changes" in {
      val changed = document.copy(priceFrom = document.priceFrom + BigDecimal("5.0"))
      assert(fingerprintOf(Vector(document)) != fingerprintOf(Vector(changed)))
    }

    "change when a duration changes" in {
      val changed = document.copy(durationMin = document.durationMin + 1)
      assert(fingerprintOf(Vector(document)) != fingerprintOf(Vector(changed)))
    }

    "change when an enum attribute changes" in {
      val changed = document.copy(enumAttributes = document.enumAttributes.updated("nail_coating_type", "acrylic"))
      assert(fingerprintOf(Vector(document)) != fingerprintOf(Vector(changed)))
    }

    "change when a boolean attribute changes" in {
      val changed = document.copy(booleanAttributes = document.booleanAttributes.updated("with_removal", false))
      assert(fingerprintOf(Vector(document)) != fingerprintOf(Vector(changed)))
    }

    "change when an integer attribute changes" in {
      val changed = document.copy(intAttributes = document.intAttributes.updated("session_count", 99))
      assert(fingerprintOf(Vector(document)) != fingerprintOf(Vector(changed)))
    }

    "change when a decimal attribute changes" in {
      val changed = document.copy(bigDecimalAttributes = document.bigDecimalAttributes.updated("deposit_amount", BigDecimal("999.0000")))
      assert(fingerprintOf(Vector(document)) != fingerprintOf(Vector(changed)))
    }

    "change when composed text changes" in {
      val changed = document.copy(allText = document.allText + " extra")
      assert(fingerprintOf(Vector(document)) != fingerprintOf(Vector(changed)))
    }

    "change between an absent and a present dynamic field" in {
      val absent = document.copy(intAttributes = Map.empty)
      val present = document.copy(intAttributes = Map("session_count" -> 3))
      assert(fingerprintOf(Vector(absent)) != fingerprintOf(Vector(present)))
    }

    "produce a total order for two documents sharing the same variantId" in {
      val first = document.copy(serviceName = "First Duplicate Identity Service", priceFrom = document.priceFrom + BigDecimal("1.0"))
      val second = document.copy(variantId = first.variantId, serviceName = "Second Duplicate Identity Service", priceFrom = document.priceFrom + BigDecimal("2.0"))

      assert(first.variantId == second.variantId)
      assert(fingerprintOf(Vector(first, second)) == fingerprintOf(Vector(second, first)))
      assert(fingerprintOf(Vector(first, second)) != fingerprintOf(Vector(first)))
      assert(fingerprintOf(Vector(first, second)) != fingerprintOf(Vector(second)))
    }

    "own exactly 44 field handles on the root document" in {
      assert(BeautyQSearchDeclarations.variants.document.allFields.size == 44)
    }

    "include the document identity in the fingerprint" in {
      val changedIdentity = document.copy(variantId = MasterServiceOfferVariantId(java.util.UUID.fromString("d0000000-0000-0000-0000-000000000002")))
      assert(fingerprintOf(Vector(document)) != fingerprintOf(Vector(changedIdentity)))
    }

    // No second, independently maintained field inventory exists: `BeautyQProjectedDocumentsFingerprint.compute`
    // reads its field vector directly from `BeautyQSearchDeclarations.variants.document.allFields` (verified by source
    // inspection and by the Brick 3 rg source scan), so the 44-handle count above is the single source of truth -
    // every field-family test in this suite (ids, codes, names, coordinates, price/duration, all four attribute
    // families, composed text, presence/absence) changes the fingerprint through that same one inventory.
    "produce exactly 64 lowercase hexadecimal characters" in {
      val hex = fingerprintOf(Vector(document))
      assert(hex.length == 64)
      assert(hex.forall(c => c.isDigit || (c >= 'a' && c <= 'f')))
    }

    "produce the accepted golden fixture fingerprint (Commit 3D kernel-extraction compatibility)" in {
      assert(fingerprintOf(Vector(document)) == "badd0b61d5de1cc24deda1a8d9f5d9cdf97d0fcf6d84ec1d2910af38e2296e0a")
    }
  }
}
