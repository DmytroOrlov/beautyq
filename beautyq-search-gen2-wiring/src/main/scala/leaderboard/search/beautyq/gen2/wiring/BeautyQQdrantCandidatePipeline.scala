package leaderboard.search.beautyq.gen2.wiring

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.beautyq.gen2.contract.*
import leaderboard.search.beautyq.gen2.materialization.MaterializedBeautyQVariantDocuments
import leaderboard.search.gen2.contract.CandidatePlanDecision
import leaderboard.search.gen2.core.hydration.*
import leaderboard.search.gen2.qdrant.*

enum BeautyQCandidateProvenance {
  case SemanticSupplement

  def stableCode: String = this match {
    case BeautyQCandidateProvenance.SemanticSupplement => "semantic-supplement"
  }
}

object BeautyQQdrantHydrationPolicy {
  val policy: CandidateHydrationPolicy[BeautyQCandidateProvenance] =
    CandidateHydrationPolicy(
      missingDocuments = CandidateMissingDocumentPolicy.Fail,
      hardConstraints = CandidateHardConstraintPolicy.RequireAll,
      provenance = BeautyQCandidateProvenance.SemanticSupplement,
    )
}

sealed trait BeautyQQdrantCandidatePipelineError[+EmbeddingError]
object BeautyQQdrantCandidatePipelineError {
  final case class Qdrant[EmbeddingError](error: QdrantCandidatePipelineError[EmbeddingError]) extends BeautyQQdrantCandidatePipelineError[EmbeddingError]
  final case class Hydration(error: CandidateHydrationError[MasterServiceOfferVariantId]) extends BeautyQQdrantCandidatePipelineError[Nothing]
}

/** BeautyQ's final candidate-only composition result. The original evaluation and its exact
  * ineligible reason or hydrated candidate result are bound together by this pipeline. */
type BeautyQQdrantCandidatePipelineResult = BeautyQQdrantCandidatePipeline.Result

object BeautyQQdrantCandidatePipeline {
  import BeautyQQdrantCandidatePipelineError.*

  final class Result private[BeautyQQdrantCandidatePipeline] (
    val evaluation: CompiledCandidateEvaluation,
    val outcome: Either[BeautyQCandidateIneligibility, HydratedCandidateSearchResult[
      VariantSearchDocumentGen2,
      MasterServiceOfferVariantId,
      Double,
      QdrantResourceName,
      QdrantGenerationMetadata,
      QdrantCandidateDiagnostics,
      BeautyQCandidateProvenance,
    ]],
  )

  def execute[EmbeddingError](
    evaluation: CompiledCandidateEvaluation,
    materialized: MaterializedBeautyQVariantDocuments,
    embeddingPort: QdrantQueryEmbeddingPort[EmbeddingError],
    service: QdrantCandidateService,
  ): Either[BeautyQQdrantCandidatePipelineError[EmbeddingError], BeautyQQdrantCandidatePipelineResult] =
    evaluation.decision match {
      case CandidatePlanDecision.Ineligible(reason) =>
        Right(new Result(evaluation, Left(reason)))
      case CandidatePlanDecision.Eligible(plan) =>
        for {
          executed <- QdrantCandidatePipeline.execute(
                        BeautyQQdrantPolicy.policy,
                        plan,
                        embeddingPort,
                        service,
                      ).left.map(Qdrant.apply)
          hydrated <- CandidateHydrator.hydrate(executed, materialized, BeautyQQdrantHydrationPolicy.policy).left.map(Hydration.apply)
        } yield new Result(evaluation, Right(hydrated))
    }
}
