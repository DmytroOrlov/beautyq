package leaderboard.search

import leaderboard.search.beautyq.gen2.materialization.{BeautyQSearchSnapshot, BeautyQSnapshotFingerprint}
import leaderboard.search.gen2.core.materialization.ContentFingerprint
import leaderboard.search.gen2.eval.EvaluationResultId
import leaderboard.seed.BeautyQSeedLoader

/** One test-scope owner for the canonical BeautyQ seed snapshot and evaluation identities. */
object BeautyQCanonicalSeedEvaluationCatalog {
  final class Loaded private[search] (
    val snapshot: BeautyQSearchSnapshot,
    val sourceFingerprint: ContentFingerprint,
    val variantResultIds: Set[EvaluationResultId],
    val providerResultIds: Set[EvaluationResultId],
    val serviceIntentResultIds: Set[EvaluationResultId],
  )

  def load(): Either[String, Loaded] =
    new BeautyQSeedLoader.ResourceLoader().load().left.map(_ => "canonical_seed_unavailable").flatMap { seed =>
      val snapshot = BeautyQSearchSnapshot(
        categories = seed.categories.toVector,
        services = seed.services.toVector,
        serviceVariantSchemas = seed.serviceVariantSchemas.toVector,
        masters = seed.masters.toVector,
        masterLocations = seed.masterLocations.toVector,
        masterServiceOffers = seed.masterServiceOffers.toVector,
        masterServiceOfferVariants = seed.masterServiceOfferVariants.toVector,
      )
      val fingerprint = BeautyQSnapshotFingerprint.compute(snapshot)
      for {
        variants <- resultIds(seed.masterServiceOfferVariants.map(_.id.value.toString).toVector, "variant")
        providers <- resultIds(seed.masterLocations.map(_.id.value.toString).toVector, "provider")
        services <- resultIds(seed.services.map(_.id.value.toString).toVector, "service-intent")
      } yield new Loaded(snapshot, fingerprint, variants, providers, services)
    }

  private def resultIds(rawIds: Vector[String], surface: String): Either[String, Set[EvaluationResultId]] =
    rawIds.foldLeft[Either[String, Set[EvaluationResultId]]](Right(Set.empty)) { (acc, raw) =>
      acc.flatMap { current =>
        EvaluationResultId.from(raw).left.map(_ => s"canonical_${surface}_identity_invalid").map(id => current + id)
      }
    }
}
