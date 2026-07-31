package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.beautyq.gen2.contract.*
import leaderboard.search.beautyq.gen2.materialization.MaterializedBeautyQVariantDocuments
import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.elasticsearch.*
import leaderboard.search.gen2.qdrant.*

sealed trait BeautyQSearchApplicationError
object BeautyQSearchApplicationError {
  final case class Input(error: NonEmptyErrors[BeautySearchRequestError]) extends BeautyQSearchApplicationError
  final case class Intent(error: NonEmptyErrors[BeautyIntentParseError]) extends BeautyQSearchApplicationError
  final case class Plan(error: NonEmptyErrors[BeautyQSearchPlanCompileError]) extends BeautyQSearchApplicationError
  final case class Evaluation(error: CandidateEvaluationError) extends BeautyQSearchApplicationError
  final case class Baseline(error: BeautyQElasticsearchBaselineServiceError) extends BeautyQSearchApplicationError
  final case class Orchestration(error: BeautyQSearchOrchestrationError) extends BeautyQSearchApplicationError
}

private sealed trait SupplementCapability
private object SupplementCapability {
  final class Full(
    val embedding: QdrantQueryEmbeddingPort[BeautyQEmbeddingRequestError],
    val qdrant: QdrantCandidateService,
  ) extends SupplementCapability
  case object Baseline extends SupplementCapability
}

final class BeautyQSearchApplication private (
  materialized: MaterializedBeautyQVariantDocuments,
  elasticsearch: BeautyQElasticsearchBaselineService,
  private val supplementCapability: SupplementCapability,
) {
  def execute(
    request: BeautySearchRequestGen2,
  ): Either[BeautyQSearchApplicationError, BeautyQSearchOrchestrator.Result] =
    for {
      prepared <- prepare(request)
      (evaluation, baseline) = prepared
      result <- supplementCapability match {
        case full: SupplementCapability.Full =>
          BeautyQSearchOrchestrator.execute(
            baseline,
            evaluation,
            materialized,
            full.embedding,
            full.qdrant,
            elasticsearch,
          ).left.map(BeautyQSearchApplicationError.Orchestration.apply)
        case SupplementCapability.Baseline =>
          BeautyQSearchOrchestrator.baselineOnly(baseline, evaluation)
            .left.map(BeautyQSearchApplicationError.Orchestration.apply)
      }
    } yield result

  private def prepare(
    request: BeautySearchRequestGen2,
  ): Either[
    BeautyQSearchApplicationError,
    (CompiledCandidateEvaluation, BoundElasticsearchBaselineResult[VariantSearchDocumentGen2, MasterServiceOfferVariantId]),
  ] =
    for {
      validated <- BeautySearchRequestGen2.validate(request).left.map(BeautyQSearchApplicationError.Input.apply)
      intent <- BeautyQIntentParserGen2.parse(validated, BeautyQSearchDeclarations.variants.intent.vocabulary)
        .left.map(BeautyQSearchApplicationError.Intent.apply)
      compiled <- BeautyQSearchPlanCompiler.compile(validated, intent)
        .left.map(BeautyQSearchApplicationError.Plan.apply)
      evaluation <- BeautyQCandidatePlanCompiler.compile(compiled)
        .left.map(BeautyQSearchApplicationError.Evaluation.apply)
      baseline <- elasticsearch.searchBound(compiled)
        .left.map(BeautyQSearchApplicationError.Baseline.apply)
    } yield (evaluation, baseline)
}

object BeautyQSearchApplication {
  def make(
    materialized: MaterializedBeautyQVariantDocuments,
    elasticsearch: BeautyQElasticsearchBaselineService,
    embedding: QdrantQueryEmbeddingPort[BeautyQEmbeddingRequestError],
    qdrant: QdrantCandidateService,
  ): BeautyQSearchApplication =
    new BeautyQSearchApplication(materialized, elasticsearch, new SupplementCapability.Full(embedding, qdrant))

  def makeBaselineOnly(
    materialized: MaterializedBeautyQVariantDocuments,
    elasticsearch: BeautyQElasticsearchBaselineService,
  ): BeautyQSearchApplication =
    new BeautyQSearchApplication(materialized, elasticsearch, SupplementCapability.Baseline)
}
