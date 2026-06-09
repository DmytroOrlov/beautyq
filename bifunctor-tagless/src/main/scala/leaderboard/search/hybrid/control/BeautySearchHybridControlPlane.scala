package leaderboard.search.hybrid.control

import izumi.functional.bio.{Error2, F}
import leaderboard.model.QueryFailure
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

sealed trait BeautySearchHybridServingDecision
object BeautySearchHybridServingDecision {
  case object UseSeedCatalogOnly extends BeautySearchHybridServingDecision
  case object RunHybridShadow extends BeautySearchHybridServingDecision
  final case class ServeHybrid(
    readiness: BeautySearchHybridReadinessStatus.Ready,
  ) extends BeautySearchHybridServingDecision

  def decide(
    policy: BeautySearchHybridServingPolicy,
    readiness: BeautySearchHybridReadinessStatus,
  ): BeautySearchHybridServingDecision =
    policy.mode match {
      case BeautySearchHybridRuntimeMode.SeedCatalogOnly =>
        UseSeedCatalogOnly

      case BeautySearchHybridRuntimeMode.HybridShadow =>
        RunHybridShadow

      case BeautySearchHybridRuntimeMode.HybridServe =>
        readiness match {
          case ready: BeautySearchHybridReadinessStatus.Ready =>
            ServeHybrid(ready)

          case _: BeautySearchHybridReadinessStatus.NotReady =>
            UseSeedCatalogOnly
        }
    }
}

trait BeautySearchHybridReadiness[F[+_, +_]] {
  def status(): F[QueryFailure, BeautySearchHybridReadinessStatus]
}

trait BeautySearchHybridDiagnosticsSink[F[+_, +_]] {
  def report(event: BeautySearchHybridDiagnosticsEvent): F[Nothing, Unit]
}

sealed trait BeautySearchHybridDiagnosticsEvent

object BeautySearchHybridDiagnosticsEvent {
  final case class DecisionEvaluated(
    policy: BeautySearchHybridServingPolicy,
    readiness: BeautySearchHybridReadinessStatus,
    decision: BeautySearchHybridServingDecision,
  ) extends BeautySearchHybridDiagnosticsEvent
}

final class BeautySearchHybridDecisionEvaluator[F[+_, +_]: Error2](
  policy: BeautySearchHybridServingPolicy,
  readiness: BeautySearchHybridReadiness[F],
  diagnosticsSink: BeautySearchHybridDiagnosticsSink[F],
) {

  def evaluate(): F[QueryFailure, BeautySearchHybridServingDecision] = {
    F.flatMap(readiness.status()) { status =>
      val decision = BeautySearchHybridServingDecision.decide(policy, status)
      F.map(diagnosticsSink.report(
        BeautySearchHybridDiagnosticsEvent.DecisionEvaluated(
          policy = policy,
          readiness = status,
          decision = decision,
        )
      ))(_ => decision)
    }
  }
}
