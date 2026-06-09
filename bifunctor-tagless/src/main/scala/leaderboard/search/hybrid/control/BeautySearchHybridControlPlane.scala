package leaderboard.search.hybrid.control

import leaderboard.search.dsl.VectorDistance

final case class BeautySearchHybridSnapshotIdentity(
  sourceId: String,
  snapshotVersion: String,
  documentCount: Int,
)

final case class BeautySearchHybridCollectionIdentity(
  collectionName: String,
  vectorName: String,
  embeddingModelName: String,
  embeddingDimension: Int,
  distance: VectorDistance,
)

final case class BeautySearchHybridFreshnessPolicy(
  maxStalenessSeconds: Long,
)

sealed trait BeautySearchHybridRuntimeMode
object BeautySearchHybridRuntimeMode {
  case object SeedCatalogOnly extends BeautySearchHybridRuntimeMode
  case object HybridShadow extends BeautySearchHybridRuntimeMode
  case object HybridServe extends BeautySearchHybridRuntimeMode

  val default: BeautySearchHybridRuntimeMode = SeedCatalogOnly
}

sealed trait BeautySearchHybridReadinessStatus
object BeautySearchHybridReadinessStatus {
  final case class Ready(
    snapshot: BeautySearchHybridSnapshotIdentity,
    collection: BeautySearchHybridCollectionIdentity,
    indexedDocumentCount: Int,
  ) extends BeautySearchHybridReadinessStatus

  final case class NotReady(reason: String) extends BeautySearchHybridReadinessStatus

  def isReady(status: BeautySearchHybridReadinessStatus): Boolean =
    status match {
      case _: Ready => true
      case _: NotReady => false
    }
}

final case class BeautySearchHybridServingPolicy(
  mode: BeautySearchHybridRuntimeMode,
  requireReadyForServing: Boolean,
)

object BeautySearchHybridServingPolicy {
  val default: BeautySearchHybridServingPolicy =
    BeautySearchHybridServingPolicy(
      mode = BeautySearchHybridRuntimeMode.SeedCatalogOnly,
      requireReadyForServing = true,
    )
}
