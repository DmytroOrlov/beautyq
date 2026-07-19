package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.gen2.qdrant.*

/** Thin BeautyQ binding for the generic Qdrant lifecycle and candidate service. */
object BeautyQQdrantRuntime {
  val policy = BeautyQQdrantPolicy.policy
  val resources = BeautyQSearchGen2ResourceNames

  def lifecycle(client: QdrantGen2Client): Either[QdrantGenerationLifecycleConfig.Error, QdrantGenerationLifecycle] =
    QdrantResourceName.from(resources.QdrantCollectionAlias) match {
      case Left(error) => Left(QdrantGenerationLifecycleConfig.Error.InvalidAlias(error))
      case Right(alias) => QdrantGenerationLifecycleConfig.create(alias, resources.QdrantPhysicalCollectionPrefix).map(config => new QdrantGenerationLifecycle(client, config))
    }

  def candidateService(client: QdrantGen2Client): Either[QdrantCandidateServiceConfig.Error, QdrantCandidateService] =
    QdrantResourceName.from(resources.QdrantCollectionAlias) match {
      case Left(error) => Left(QdrantCandidateServiceConfig.Error.InvalidAlias(error))
      case Right(alias) => QdrantCandidateServiceConfig.create(alias, resources.QdrantPhysicalCollectionPrefix).map(config => new QdrantCandidateService(client, config))
    }
}
