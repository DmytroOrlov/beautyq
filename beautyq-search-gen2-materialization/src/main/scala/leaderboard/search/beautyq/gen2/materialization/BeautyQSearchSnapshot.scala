package leaderboard.search.beautyq.gen2.materialization

import leaderboard.model.*

/** The consistent BeautyQ source snapshot: exactly the persisted rows read under one repeatable-read
  * transaction, in [[BeautyQSearchSnapshotSource]]'s own read order. Carries no capture time, source
  * revision, fingerprint, seed metadata, backend state, or diagnostics - those are [[VersionedSnapshot]]
  * metadata or fingerprint outputs, never part of the snapshot value itself.
  */
final case class BeautyQSearchSnapshot(
  categories: Vector[Category],
  services: Vector[Service],
  serviceVariantSchemas: Vector[ServiceVariantSchema],
  masters: Vector[Master],
  masterLocations: Vector[MasterLocation],
  masterServiceOffers: Vector[MasterServiceOffer],
  masterServiceOfferVariants: Vector[MasterServiceOfferVariant],
)
