package leaderboard.search.elasticsearch

import leaderboard.model.QueryFailure

sealed trait ElasticsearchStartupReadinessTransition {
  def servingDecision: ElasticsearchStartupServingDecision
  def lifecycleMetadata: Option[ElasticsearchSeedLifecycleMetadata]
  def lifecycleStatusResponse: Option[ElasticsearchLifecycleStatusResponse]
}

object ElasticsearchStartupReadinessTransition {
  final case class Prepared(
    state: ElasticsearchProductionReadinessState,
    servingDecision: ElasticsearchStartupServingDecision,
  ) extends ElasticsearchStartupReadinessTransition {
    override def lifecycleMetadata: Option[ElasticsearchSeedLifecycleMetadata] =
      Some(state.lifecycleMetadata)

    override def lifecycleStatusResponse: Option[ElasticsearchLifecycleStatusResponse] =
      Some(ElasticsearchLifecycleStatusResponse.from(state))
  }

  final case class PreparationFailed(
    operationName: String,
    message: String,
    servingDecision: ElasticsearchStartupServingDecision,
  ) extends ElasticsearchStartupReadinessTransition {
    override val lifecycleMetadata: Option[ElasticsearchSeedLifecycleMetadata] = None
    override val lifecycleStatusResponse: Option[ElasticsearchLifecycleStatusResponse] = None
  }

  final case class UnsupportedFailure(failure: QueryFailure)

  def prepared(state: ElasticsearchProductionReadinessState): Prepared =
    Prepared(
      state = state,
      servingDecision = ElasticsearchStartupServingDecision.NotEnforced,
    )

  def preparationFailed(
    failure: QueryFailure,
  ): Either[UnsupportedFailure, PreparationFailed] =
    failure match {
      case QueryFailure.OperationFailure(operationName, message) =>
        Right(
          PreparationFailed(
            operationName = operationName,
            message = message,
            servingDecision = ElasticsearchStartupServingDecision.NotEnforced,
          )
        )
      case unsupported =>
        Left(UnsupportedFailure(unsupported))
    }
}

sealed trait ElasticsearchStartupServingDecision

object ElasticsearchStartupServingDecision {
  case object NotEnforced extends ElasticsearchStartupServingDecision
}
