package leaderboard.search

import leaderboard.search.beautyq.gen2.materialization.BeautyQSnapshotFingerprint
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQCanonicalSeedEvaluationCatalogSpec extends AnyWordSpec {
  "BeautyQCanonicalSeedEvaluationCatalog" should {
    "load one typed snapshot, fingerprint and surface-specific inventories" in {
      BeautyQCanonicalSeedEvaluationCatalog.load() match {
        case Right(value) =>
          assert(value.variantResultIds.nonEmpty)
          assert(value.providerResultIds.nonEmpty)
          assert(value.serviceIntentResultIds.nonEmpty)
          assert(value.sourceFingerprint == BeautyQSnapshotFingerprint.compute(value.snapshot))
          assert(value.variantResultIds.intersect(value.providerResultIds).isEmpty)
          assert(value.variantResultIds.intersect(value.serviceIntentResultIds).isEmpty)
          assert(value.providerResultIds.intersect(value.serviceIntentResultIds).isEmpty)
        case Left(error) => fail(s"expected canonical seed catalog, got $error")
      }
    }

    "retain the complete seven-field snapshot shape" in {
      BeautyQCanonicalSeedEvaluationCatalog.load() match {
        case Right(value) =>
          assert(value.snapshot.categories.nonEmpty)
          assert(value.snapshot.services.nonEmpty)
          assert(value.snapshot.serviceVariantSchemas.nonEmpty)
          assert(value.snapshot.masters.nonEmpty)
          assert(value.snapshot.masterLocations.nonEmpty)
          assert(value.snapshot.masterServiceOffers.nonEmpty)
          assert(value.snapshot.masterServiceOfferVariants.nonEmpty)
        case Left(error) => fail(s"expected canonical seed catalog, got $error")
      }
    }
  }
}
