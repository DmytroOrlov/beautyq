package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.beautyq.gen2.contract.*
import leaderboard.search.beautyq.gen2.materialization.MaterializedBeautyQVariantDocuments
import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.elasticsearch.*
import leaderboard.search.gen2.qdrant.*

/** Errors at the one public Gen2 application boundary. The wrapped values are
  * the errors actually produced by the canonical declaration/parser/compiler
  * path; this owner does not reinterpret them as another policy. */
sealed trait BeautyQSearchApplicationError
object BeautyQSearchApplicationError {
  final case class Input(error: NonEmptyErrors[BeautySearchRequestError]) extends BeautyQSearchApplicationError
  final case class Intent(error: NonEmptyErrors[BeautyIntentParseError]) extends BeautyQSearchApplicationError
  final case class Plan(error: NonEmptyErrors[BeautyQSearchPlanCompileError]) extends BeautyQSearchApplicationError
  final case class Evaluation(error: CandidateEvaluationError) extends BeautyQSearchApplicationError
  final case class Baseline(error: BeautyQElasticsearchBaselineServiceError) extends BeautyQSearchApplicationError
  final case class Orchestration(error: BeautyQSearchOrchestrationError) extends BeautyQSearchApplicationError
}

/** The single executable BeautyQ Gen2 request owner. It receives immutable
  * materialized documents and already-composed backend services; it never
  * chooses a physical resource or rebuilds generic backend mechanics. */
final class BeautyQSearchApplication private (
  materialized: MaterializedBeautyQVariantDocuments,
  elasticsearch: BeautyQElasticsearchBaselineService,
  embedding: QdrantQueryEmbeddingPort[BeautyQEmbeddingRequestError],
  qdrant: QdrantCandidateService,
) {
  def execute(
    request: BeautySearchRequestGen2,
  ): Either[BeautyQSearchApplicationError, BeautyQSearchOrchestrator.Result] =
    for {
      prepared <- prepare(request)
      (evaluation, baseline) = prepared
      result <- BeautyQSearchOrchestrator.execute(
        baseline,
        evaluation,
        materialized,
        embedding,
        qdrant,
        elasticsearch,
      ).left.map(BeautyQSearchApplicationError.Orchestration.apply)
    } yield result

  def executeBaselineOnly(
    request: BeautySearchRequestGen2,
  ): Either[BeautyQSearchApplicationError, BeautyQSearchOrchestrator.Result] =
    for {
      prepared <- prepare(request)
      (evaluation, baseline) = prepared
      result <- BeautyQSearchOrchestrator.baselineOnly(baseline, evaluation)
        .left.map(BeautyQSearchApplicationError.Orchestration.apply)
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
    new BeautyQSearchApplication(materialized, elasticsearch, embedding, qdrant)
}
