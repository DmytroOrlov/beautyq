package leaderboard.search.qdrant

sealed trait QdrantProductionCandidateReadinessStatus extends Product with Serializable
object QdrantProductionCandidateReadinessStatus {
  case object Ready extends QdrantProductionCandidateReadinessStatus
  final case class NotReady(reasons: List[String]) extends QdrantProductionCandidateReadinessStatus
  case object NotEvaluated extends QdrantProductionCandidateReadinessStatus
  case object NotConfigured extends QdrantProductionCandidateReadinessStatus
  case object NotApproved extends QdrantProductionCandidateReadinessStatus
  case object Unknown extends QdrantProductionCandidateReadinessStatus

  def isReady(status: QdrantProductionCandidateReadinessStatus): Boolean =
    status == Ready
}

final case class QdrantProductionCandidateReadinessState(
  qdrantActive: Boolean,
  collectionIdentity: QdrantProductionCandidateReadinessStatus,
  contractParity: QdrantProductionCandidateReadinessStatus,
  indexing: QdrantProductionCandidateReadinessStatus,
  search: QdrantProductionCandidateReadinessStatus,
  qualityEval: QdrantProductionCandidateReadinessStatus,
  observability: QdrantProductionCandidateReadinessStatus,
  rollbackDisable: QdrantProductionCandidateReadinessStatus,
  activationPolicy: QdrantProductionCandidateReadinessStatus,
)

final case class QdrantProductionCandidateReadinessReport(
  state: QdrantProductionCandidateReadinessState,
  productionCandidateReady: Boolean,
)

object QdrantProductionCandidateReadiness {
  import QdrantProductionCandidateReadinessStatus.*

  val conservativeDefault: QdrantProductionCandidateReadinessState =
    QdrantProductionCandidateReadinessState(
      qdrantActive = true,
      collectionIdentity = Unknown,
      contractParity = Unknown,
      indexing = Unknown,
      search = Unknown,
      qualityEval = NotEvaluated,
      observability = NotConfigured,
      rollbackDisable = NotConfigured,
      activationPolicy = NotApproved,
    )

  def evaluate(state: QdrantProductionCandidateReadinessState): QdrantProductionCandidateReadinessReport =
    QdrantProductionCandidateReadinessReport(
      state = state,
      productionCandidateReady = state.qdrantActive && requiredStatuses(state).forall(isReady),
    )

  def withCollectionCompatibility(
    state: QdrantProductionCandidateReadinessState,
    compatibility: Either[List[QdrantCollectionCompatibilityMismatch], Unit],
  ): QdrantProductionCandidateReadinessState =
    state.copy(
      collectionIdentity = compatibility match {
        case Right(()) =>
          Ready
        case Left(mismatches) =>
          NotReady(mismatches.map(renderCompatibilityMismatch))
      }
    )

  def withQualityReport(
    state: QdrantProductionCandidateReadinessState,
    report: Option[QdrantProductionCandidateQualityReport],
  ): QdrantProductionCandidateReadinessState =
    state.copy(qualityEval = QdrantProductionCandidateQualityGate.readinessStatus(report))

  def withActivationPolicy(
    state: QdrantProductionCandidateReadinessState,
    policy: Option[QdrantProductionCandidateActivationPolicy],
  ): QdrantProductionCandidateReadinessState =
    state.copy(activationPolicy = QdrantProductionCandidateActivationPolicy.readinessStatus(policy))

  private def requiredStatuses(
    state: QdrantProductionCandidateReadinessState
  ): List[QdrantProductionCandidateReadinessStatus] =
    List(
      state.collectionIdentity,
      state.contractParity,
      state.indexing,
      state.search,
      state.qualityEval,
      state.observability,
      state.rollbackDisable,
      state.activationPolicy,
    )

  private def renderCompatibilityMismatch(mismatch: QdrantCollectionCompatibilityMismatch): String =
    mismatch match {
      case QdrantCollectionCompatibilityMismatch.CollectionNameMismatch(expected, observed) =>
        s"CollectionNameMismatch(expected=$expected, observed=$observed)"
      case QdrantCollectionCompatibilityMismatch.VectorNameMismatch(expected, observed) =>
        s"VectorNameMismatch(expected=$expected, observed=$observed)"
      case QdrantCollectionCompatibilityMismatch.DimensionMismatch(expected, observed) =>
        s"DimensionMismatch(expected=$expected, observed=$observed)"
      case QdrantCollectionCompatibilityMismatch.DistanceMismatch(expected, observed) =>
        s"DistanceMismatch(expected=$expected, observed=$observed)"
      case QdrantCollectionCompatibilityMismatch.EmbeddingModelMismatch(expected, observed) =>
        s"EmbeddingModelMismatch(expected=$expected, observed=$observed)"
    }
}
