package leaderboard.search.qdrant

import leaderboard.model.QueryFailure

sealed trait QdrantNonProductionExperimentActivation extends Product with Serializable

object QdrantNonProductionExperimentActivation {
  case object Disabled extends QdrantNonProductionExperimentActivation

  final case class Enabled(
    config: QdrantNonProductionExperimentConfig,
  ) extends QdrantNonProductionExperimentActivation

  val default: QdrantNonProductionExperimentActivation = Disabled
}

final case class QdrantNonProductionExperimentConfig(
  experimentId: String,
  readinessConfig: QdrantCollectionReadinessConfig,
  indexingTrigger: QdrantNonProductionExperimentIndexingTrigger,
  metadataSource: QdrantNonProductionExperimentMetadataSource,
)

object QdrantNonProductionExperimentConfig {
  def create(
    experimentId: String,
    readinessConfig: QdrantCollectionReadinessConfig,
    indexingTrigger: QdrantNonProductionExperimentIndexingTrigger,
    metadataSource: QdrantNonProductionExperimentMetadataSource,
  ): Either[QueryFailure, QdrantNonProductionExperimentConfig] =
    if (experimentId.trim.isEmpty) {
      Left(QueryFailure.domain("Qdrant non-production experimentId must be non-empty"))
    } else {
      Right(QdrantNonProductionExperimentConfig(
        experimentId = experimentId,
        readinessConfig = readinessConfig,
        indexingTrigger = indexingTrigger,
        metadataSource = metadataSource,
      ))
    }
}

sealed trait QdrantNonProductionExperimentIndexingTrigger extends Product with Serializable

object QdrantNonProductionExperimentIndexingTrigger {
  case object ManualTask extends QdrantNonProductionExperimentIndexingTrigger
  case object TestSetup extends QdrantNonProductionExperimentIndexingTrigger

  val all: List[QdrantNonProductionExperimentIndexingTrigger] =
    List(ManualTask, TestSetup)
}

sealed trait QdrantNonProductionExperimentMetadataSource extends Product with Serializable

object QdrantNonProductionExperimentMetadataSource {
  case object ExplicitMetadataOnly extends QdrantNonProductionExperimentMetadataSource

  val all: List[QdrantNonProductionExperimentMetadataSource] =
    List(ExplicitMetadataOnly)
}
