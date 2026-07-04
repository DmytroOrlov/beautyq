package leaderboard.search.elasticsearch

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

final case class ElasticsearchLifecycleStatusResponse(
  indexName: String,
  source: String,
  documentCount: Int,
  preparationMode: String,
  lifecycleStatus: String,
  servingReadiness: String,
  replacement: String,
  freshness: String,
  refresh: String,
  rollback: String,
  operatorVisibility: String,
  productionLifecycleComplete: Boolean,
)

object ElasticsearchLifecycleStatusResponse {
  implicit val encoder: Encoder.AsObject[ElasticsearchLifecycleStatusResponse] = deriveEncoder
  implicit val decoder: Decoder[ElasticsearchLifecycleStatusResponse] = deriveDecoder

  def from(state: ElasticsearchProductionReadinessState): ElasticsearchLifecycleStatusResponse =
    ElasticsearchLifecycleStatusResponse(
      indexName = state.lifecycleMetadata.indexName,
      source = state.lifecycleMetadata.source,
      documentCount = state.lifecycleMetadata.documentCount,
      preparationMode = preparationModeValue(state.lifecycleMetadata.preparationMode),
      lifecycleStatus = lifecycleStatusValue(state.lifecycleMetadata.lifecycleStatus),
      servingReadiness = servingReadinessValue(state.servingReadiness),
      replacement = replacementValue(state.replacement),
      freshness = freshnessValue(state.freshness),
      refresh = refreshValue(state.refresh),
      rollback = rollbackValue(state.rollback),
      operatorVisibility = operatorVisibilityValue(state.operatorVisibility),
      productionLifecycleComplete = false,
    )

  private def preparationModeValue(value: ElasticsearchSeedPreparationMode): String =
    value match {
      case ElasticsearchSeedPreparationMode.EagerSeedIndexPreparation => "eager_seed_index_preparation"
    }

  private def lifecycleStatusValue(value: ElasticsearchSeedLifecycleStatus): String =
    value match {
      case ElasticsearchSeedLifecycleStatus.SeedOnlyNotProductionLifecycle => "seed_only_not_production_lifecycle"
    }

  private def servingReadinessValue(value: ElasticsearchServingReadiness): String =
    value match {
      case ElasticsearchServingReadiness.NotEnforced => "not_enforced"
    }

  private def replacementValue(value: ElasticsearchReplacementReadiness): String =
    value match {
      case ElasticsearchReplacementReadiness.NotConfigured => "not_configured"
    }

  private def freshnessValue(value: ElasticsearchFreshnessReadiness): String =
    value match {
      case ElasticsearchFreshnessReadiness.NotTracked => "not_tracked"
    }

  private def refreshValue(value: ElasticsearchRefreshReadiness): String =
    value match {
      case ElasticsearchRefreshReadiness.EagerSeedPreparationOnly => "eager_seed_preparation_only"
    }

  private def rollbackValue(value: ElasticsearchRollbackReadiness): String =
    value match {
      case ElasticsearchRollbackReadiness.NotConfigured => "not_configured"
    }

  private def operatorVisibilityValue(value: ElasticsearchOperatorVisibility): String =
    value match {
      case ElasticsearchOperatorVisibility.NotExposed => "not_exposed"
    }
}
