package leaderboard.search.elasticsearch

import io.circe.{Encoder, Json}
import io.circe.syntax._

sealed trait ElasticsearchStartupReadinessStatusResponse {
  def transitionStatus: String
  def servingDecision: String
  def productionLifecycleComplete: Boolean
}

object ElasticsearchStartupReadinessStatusResponse {

  final case class Prepared(
    lifecycleStatus: ElasticsearchLifecycleStatusResponse,
  ) extends ElasticsearchStartupReadinessStatusResponse {
    override val transitionStatus: String = "prepared"
    override val servingDecision: String = "not_enforced"
    override val productionLifecycleComplete: Boolean = false
  }

  final case class PreparationFailed(
    operationName: String,
    message: String,
  ) extends ElasticsearchStartupReadinessStatusResponse {
    override val transitionStatus: String = "preparation_failed"
    override val servingDecision: String = "not_enforced"
    override val productionLifecycleComplete: Boolean = false
  }

  def from(transition: ElasticsearchStartupReadinessTransition): ElasticsearchStartupReadinessStatusResponse =
    transition match {
      case ElasticsearchStartupReadinessTransition.Prepared(state, _) =>
        Prepared(
          lifecycleStatus = ElasticsearchLifecycleStatusResponse.from(state),
        )
      case ElasticsearchStartupReadinessTransition.PreparationFailed(operationName, message, _) =>
        PreparationFailed(
          operationName = operationName,
          message = message,
        )
    }

  implicit val encoder: Encoder[ElasticsearchStartupReadinessStatusResponse] =
    Encoder.instance {
      case Prepared(lifecycleStatus) =>
        Json.obj(
          "transitionStatus" -> "prepared".asJson,
          "servingDecision" -> "not_enforced".asJson,
          "lifecycleStatus" -> lifecycleStatus.asJson,
          "productionLifecycleComplete" -> false.asJson,
        )
      case PreparationFailed(operationName, message) =>
        Json.obj(
          "transitionStatus" -> "preparation_failed".asJson,
          "servingDecision" -> "not_enforced".asJson,
          "operationName" -> operationName.asJson,
          "message" -> message.asJson,
          "productionLifecycleComplete" -> false.asJson,
        )
    }
}
