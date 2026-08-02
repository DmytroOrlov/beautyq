package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.beautyq.gen2.materialization.MaterializedBeautyQVariantDocuments
import leaderboard.search.gen2.elasticsearch.lifecycle.LifecycleResolvedElasticsearchGeneration
import leaderboard.search.gen2.qdrant.ActiveQdrantGeneration

sealed trait SupplementStartupPolicy {
  def stableCode: String
}

sealed trait AttemptSupplementStartup extends SupplementStartupPolicy

object SupplementStartupPolicy {
  case object Required extends AttemptSupplementStartup {
    val stableCode: String = "required"
  }
  case object Preferred extends AttemptSupplementStartup {
    val stableCode: String = "preferred"
  }
  case object Disabled extends SupplementStartupPolicy {
    val stableCode: String = "disabled"
  }

  val Default: SupplementStartupPolicy = Required

  val ordered: Vector[SupplementStartupPolicy] =
    Vector(Required, Preferred, Disabled)

  final class SupplementStartupPolicyError(message: String)
    extends RuntimeException(message)

  def fromStableCode(value: String): Either[SupplementStartupPolicyError, SupplementStartupPolicy] =
    value.trim match {
      case "" => Left(new SupplementStartupPolicyError("empty supplement startup policy code"))
      case s if s != value => Left(new SupplementStartupPolicyError("whitespace in supplement startup policy code"))
      case "required" => Right(Required)
      case "preferred" => Right(Preferred)
      case "disabled" => Right(Disabled)
      case other => Left(new SupplementStartupPolicyError(s"unknown supplement startup policy code: $other"))
    }
}

sealed trait BeautyQServingMode {
  def modeCode: String
}

object BeautyQServingMode {
  case object FullSearch extends BeautyQServingMode {
    val modeCode: String = "full_search"
  }
  case object BaselineOnly extends BeautyQServingMode {
    val modeCode: String = "baseline_only"
  }
}

final class StartupServingStatus private (
  val policy: SupplementStartupPolicy,
  val servingMode: BeautyQServingMode,
  val condition: String,
  val reason: Option[StartupServingStatus.Reason],
  val restartRequired: Boolean,
  val sourceContentFingerprint: String,
  val projectedDocumentsFingerprint: String,
  val elasticsearchReference: String,
  val elasticsearchPhysicalTarget: String,
  val qdrantGenerationId: Option[String],
  val qdrantPhysicalCollection: Option[String],
) {
  def supplementReady: Boolean = servingMode == BeautyQServingMode.FullSearch
}

object StartupServingStatus {

  final class Reason private (
    val code: String,
    val message: String,
    val detail: String,
    private[wiring] val typedCause: Option[BeautyQSearchGenerationActivationError],
  )

  object Reason {
    private[StartupServingStatus] def create(
      code: String,
      message: String,
      detail: String,
      typedCause: Option[BeautyQSearchGenerationActivationError],
    ): Reason = new Reason(code, message, detail, typedCause)
  }

  private[leaderboard] def healthy(
    policy: SupplementStartupPolicy,
    materialized: MaterializedBeautyQVariantDocuments,
    elasticsearchGeneration: LifecycleResolvedElasticsearchGeneration,
    qdrantGeneration: ActiveQdrantGeneration,
  ): StartupServingStatus =
    new StartupServingStatus(
      policy = policy,
      servingMode = BeautyQServingMode.FullSearch,
      condition = "healthy",
      reason = None,
      restartRequired = false,
      sourceContentFingerprint = materialized.sourceSnapshot.contentFingerprint.value,
      projectedDocumentsFingerprint = materialized.projectedDocumentsFingerprint.value,
      elasticsearchReference = elasticsearchGeneration.reference.value,
      elasticsearchPhysicalTarget = elasticsearchGeneration.physicalTarget.value,
      qdrantGenerationId = Some(qdrantGeneration.metadata.generationId),
      qdrantPhysicalCollection = Some(qdrantGeneration.physicalCollection.value),
    )

  private[leaderboard] def degraded(
    policy: SupplementStartupPolicy,
    materialized: MaterializedBeautyQVariantDocuments,
    elasticsearchGeneration: LifecycleResolvedElasticsearchGeneration,
    code: String,
    message: String,
    detail: String,
    typedCause: BeautyQSearchGenerationActivationError,
  ): StartupServingStatus =
    new StartupServingStatus(
      policy = policy,
      servingMode = BeautyQServingMode.BaselineOnly,
      condition = "degraded",
      reason = Some(Reason.create(code, message, detail, Some(typedCause))),
      restartRequired = true,
      sourceContentFingerprint = materialized.sourceSnapshot.contentFingerprint.value,
      projectedDocumentsFingerprint = materialized.projectedDocumentsFingerprint.value,
      elasticsearchReference = elasticsearchGeneration.reference.value,
      elasticsearchPhysicalTarget = elasticsearchGeneration.physicalTarget.value,
      qdrantGenerationId = None,
      qdrantPhysicalCollection = None,
    )

  private[leaderboard] def limited(
    policy: SupplementStartupPolicy,
    materialized: MaterializedBeautyQVariantDocuments,
    elasticsearchGeneration: LifecycleResolvedElasticsearchGeneration,
    code: String,
    message: String,
    detail: String,
  ): StartupServingStatus =
    new StartupServingStatus(
      policy = policy,
      servingMode = BeautyQServingMode.BaselineOnly,
      condition = "limited",
      reason = Some(Reason.create(code, message, detail, None)),
      restartRequired = true,
      sourceContentFingerprint = materialized.sourceSnapshot.contentFingerprint.value,
      projectedDocumentsFingerprint = materialized.projectedDocumentsFingerprint.value,
      elasticsearchReference = elasticsearchGeneration.reference.value,
      elasticsearchPhysicalTarget = elasticsearchGeneration.physicalTarget.value,
      qdrantGenerationId = None,
      qdrantPhysicalCollection = None,
    )
}
