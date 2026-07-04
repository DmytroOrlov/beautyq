package leaderboard.search.elasticsearch

final case class ElasticsearchProductionReadinessState(
  lifecycleMetadata: ElasticsearchSeedLifecycleMetadata,
  servingReadiness: ElasticsearchServingReadiness,
  replacement: ElasticsearchReplacementReadiness,
  freshness: ElasticsearchFreshnessReadiness,
  refresh: ElasticsearchRefreshReadiness,
  rollback: ElasticsearchRollbackReadiness,
  operatorVisibility: ElasticsearchOperatorVisibility,
)

object ElasticsearchProductionReadinessState {
  def seedOnly(
    lifecycleMetadata: ElasticsearchSeedLifecycleMetadata,
  ): ElasticsearchProductionReadinessState =
    ElasticsearchProductionReadinessState(
      lifecycleMetadata = lifecycleMetadata,
      servingReadiness = ElasticsearchServingReadiness.NotEnforced,
      replacement = ElasticsearchReplacementReadiness.NotConfigured,
      freshness = ElasticsearchFreshnessReadiness.NotTracked,
      refresh = ElasticsearchRefreshReadiness.EagerSeedPreparationOnly,
      rollback = ElasticsearchRollbackReadiness.NotConfigured,
      operatorVisibility = ElasticsearchOperatorVisibility.NotExposed,
    )
}

sealed trait ElasticsearchServingReadiness

object ElasticsearchServingReadiness {
  case object NotEnforced extends ElasticsearchServingReadiness
}

sealed trait ElasticsearchReplacementReadiness

object ElasticsearchReplacementReadiness {
  case object NotConfigured extends ElasticsearchReplacementReadiness
}

sealed trait ElasticsearchFreshnessReadiness

object ElasticsearchFreshnessReadiness {
  case object NotTracked extends ElasticsearchFreshnessReadiness
}

sealed trait ElasticsearchRefreshReadiness

object ElasticsearchRefreshReadiness {
  case object EagerSeedPreparationOnly extends ElasticsearchRefreshReadiness
}

sealed trait ElasticsearchRollbackReadiness

object ElasticsearchRollbackReadiness {
  case object NotConfigured extends ElasticsearchRollbackReadiness
}

sealed trait ElasticsearchOperatorVisibility

object ElasticsearchOperatorVisibility {
  case object NotExposed extends ElasticsearchOperatorVisibility
}
