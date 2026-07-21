package leaderboard.search.beautyq.gen2.wiring

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.beautyq.gen2.contract.{BeautyQCandidateIneligibility, VariantSearchDocumentGen2}
import leaderboard.search.beautyq.gen2.materialization.MaterializedBeautyQVariantDocuments
import leaderboard.search.beautyq.gen2.wiring.BeautyQSupplementPolicy.*
import leaderboard.search.gen2.core.candidate.{SearchGenerationConsistency, SearchGenerationConsistencyError}
import leaderboard.search.gen2.core.hydration.HydratedCandidateSearchResult
import leaderboard.search.gen2.core.supplement.{AppendOnlySupplementSelection, BaselineMembershipResult}
import leaderboard.search.gen2.elasticsearch.*
import leaderboard.search.gen2.qdrant.*

/** Status vocabulary: one typed owner, no repeated raw strings. */
enum BeautyQSupplementStatus(val stableCode: String) {
  case Ineligible extends BeautyQSupplementStatus("ineligible")
  case NoAppend extends BeautyQSupplementStatus("no_append")
  case Supplemented extends BeautyQSupplementStatus("supplemented")
  case SupplementFailed extends BeautyQSupplementStatus("supplement_failed")
}

/** Orchestrator-owned error algebras. Hard pipeline/hydration failures preserve the exact original typed cause. */
sealed trait BeautyQSearchOrchestrationError
object BeautyQSearchOrchestrationError {
  final case class BoundPlanMismatch(expected: String, actual: String) extends BeautyQSearchOrchestrationError
  final case class GenerationMismatch(error: SearchGenerationConsistencyError) extends BeautyQSearchOrchestrationError
  final case class Membership(error: ElasticsearchBaselineMembershipError) extends BeautyQSearchOrchestrationError
  final case class BaselineService(error: BeautyQElasticsearchBaselineServiceError) extends BeautyQSearchOrchestrationError
  final case class AppendPolicy(error: leaderboard.search.gen2.core.supplement.AppendOnlySupplementSelectionError[MasterServiceOfferVariantId]) extends BeautyQSearchOrchestrationError
  final case class CandidatePipeline(cause: BeautyQQdrantCandidatePipelineError[BeautyQEmbeddingRequestError]) extends BeautyQSearchOrchestrationError
}

object BeautyQSearchOrchestrator {

  /** Orchestrator-owned outcomes. All implementations are final with private
    * constructors to BeautyQSearchOrchestrator. No case classes, no public apply/factory, no copy, no subclassing. */
  sealed trait BeautyQSupplementOutcome

  final class Ineligible private[BeautyQSearchOrchestrator] (
    val reason: BeautyQCandidateIneligibility,
  ) extends BeautyQSupplementOutcome

  final class BaselineOnly private[BeautyQSearchOrchestrator] () extends BeautyQSupplementOutcome

  final class Evaluated private[BeautyQSearchOrchestrator] (
    val hydrated: HydratedCandidateSearchResult[
      VariantSearchDocumentGen2,
      MasterServiceOfferVariantId,
      Double,
      QdrantResourceName,
      QdrantGenerationMetadata,
      QdrantCandidateDiagnostics,
      BeautyQCandidateProvenance,
    ],
    val membership: BaselineMembershipResult[MasterServiceOfferVariantId],
    val selection: AppendOnlySupplementSelection[
      leaderboard.search.gen2.core.hydration.HydratedCandidate[VariantSearchDocumentGen2, MasterServiceOfferVariantId, Double, BeautyQCandidateProvenance],
      MasterServiceOfferVariantId,
    ],
  ) extends BeautyQSupplementOutcome

  final class Failed private[BeautyQSearchOrchestrator] (
    val failure: BeautyQSupplementPolicy.Degradable,
  ) extends BeautyQSupplementOutcome

  /** Owner-private final result. Baseline and evaluation stored exactly once.
    * Produced only by BeautyQSearchOrchestrator.execute. */
  final class Result private[BeautyQSearchOrchestrator] (
    private val baselineRef: BoundElasticsearchBaselineResult[VariantSearchDocumentGen2, MasterServiceOfferVariantId],
    private val evaluationRef: CompiledCandidateEvaluation,
    private val outcomeRef: BeautyQSupplementOutcome,
  ) {
    def baseline: BoundElasticsearchBaselineResult[VariantSearchDocumentGen2, MasterServiceOfferVariantId] = baselineRef
    def evaluation: CompiledCandidateEvaluation = evaluationRef
    def outcome: BeautyQSupplementOutcome = outcomeRef

    def baselineResult: ElasticsearchFullSearchResult[VariantSearchDocumentGen2, MasterServiceOfferVariantId] =
      baselineRef.result

    def appendedCandidates: Vector[leaderboard.search.gen2.core.hydration.HydratedCandidate[VariantSearchDocumentGen2, MasterServiceOfferVariantId, Double, BeautyQCandidateProvenance]] =
      outcomeRef match {
        case evaluated: Evaluated => evaluated.selection.appended
        case _ => Vector.empty
      }

    def supplementCount: Int = appendedCandidates.size

    def status: BeautyQSupplementStatus = outcomeRef match {
      case _: Ineligible => BeautyQSupplementStatus.Ineligible
      case _: BaselineOnly => BeautyQSupplementStatus.NoAppend
      case evaluated: Evaluated =>
        if (evaluated.selection.appended.isEmpty) BeautyQSupplementStatus.NoAppend
        else BeautyQSupplementStatus.Supplemented
      case _: Failed => BeautyQSupplementStatus.SupplementFailed
    }

    def statusCode: String = status.stableCode

    def ineligibilityReason: Option[BeautyQCandidateIneligibility] = outcomeRef match {
      case v: Ineligible => Some(v.reason)
      case _ => None
    }

    def degradationReason: Option[BeautyQDegradationReason] = outcomeRef match {
      case v: Failed => Some(v.failure.reason)
      case _ => None
    }

    def degradationCause: Option[BeautyQQdrantCandidatePipelineError[BeautyQEmbeddingRequestError]] = outcomeRef match {
      case v: Failed => Some(v.failure.cause)
      case _ => None
    }
  }

  /** Readiness-owned baseline-only execution. It keeps the same validated
    * baseline/evaluation aggregate while deliberately skipping candidate
    * mechanics; the result remains compiler-owned and read-only. */
  def baselineOnly(
    baseline: BoundElasticsearchBaselineResult[VariantSearchDocumentGen2, MasterServiceOfferVariantId],
    evaluation: CompiledCandidateEvaluation,
  ): Either[BeautyQSearchOrchestrationError, Result] =
    if (baseline.boundPlan ne evaluation.compiled.boundPlan)
      Left(BeautyQSearchOrchestrationError.BoundPlanMismatch(
        baseline.boundPlan.identityHash.value,
        evaluation.compiled.boundPlan.identityHash.value,
      ))
    else Right(new Result(baseline, evaluation, new BaselineOnly()))

  def execute(
    baseline: BoundElasticsearchBaselineResult[VariantSearchDocumentGen2, MasterServiceOfferVariantId],
    evaluation: CompiledCandidateEvaluation,
    materialized: MaterializedBeautyQVariantDocuments,
    embeddingPort: QdrantQueryEmbeddingPort[BeautyQEmbeddingRequestError],
    qdrantService: QdrantCandidateService,
    elasticsearchBaselineService: BeautyQElasticsearchBaselineService,
  ): Either[BeautyQSearchOrchestrationError, BeautyQSearchOrchestrator.Result] = {
    // 1. Verify exact bound-plan reference
    if (baseline.boundPlan ne evaluation.compiled.boundPlan) {
      Left(BeautyQSearchOrchestrationError.BoundPlanMismatch(
        baseline.boundPlan.identityHash.value,
        evaluation.compiled.boundPlan.identityHash.value,
      ))
    } else {
      // 2. Execute candidate pipeline with exact type argument (no asInstanceOf needed)
      BeautyQQdrantCandidatePipeline.execute[BeautyQEmbeddingRequestError](
        evaluation,
        materialized,
        embeddingPort,
        qdrantService,
      ) match {
        case Left(err) =>
          // 3. Classify pipeline error — classifier is the single executable owner
          val disposition = classifyPipelineError(err)
          disposition match {
            case degradable: BeautyQSupplementPolicy.Degradable =>
              // Degradable: return unchanged baseline with Failed outcome
              Right(buildFailedResult(baseline, evaluation, degradable))
            case hard: BeautyQSupplementPolicy.Hard =>
              // Hard: consume classifier-owned cause, never select another
              Left(BeautyQSearchOrchestrationError.CandidatePipeline(hard.cause))
          }
        case Right(result) =>
          result.outcome match {
            case Left(reason) =>
              // 4. Ineligibility — no membership lookup
              Right(buildIneligibleResult(baseline, evaluation, reason))
            case Right(hydrated) =>
              // 5. Hydrated candidates
              for {
                _ <- verifyGenerationConsistency(baseline, hydrated)
                candidateIds = hydrated.candidates.map(_.id)
                membership <- elasticsearchBaselineService.membership(baseline, candidateIds)
                  .left.map {
                    case BeautyQElasticsearchBaselineServiceError.Membership(error) =>
                      BeautyQSearchOrchestrationError.Membership(error)
                    case other =>
                      BeautyQSearchOrchestrationError.BaselineService(other)
                  }
                selection <- BeautyQSupplementPolicy.appendOnly.select(
                  baseline.result.hits.map(_.id),
                  hydrated.candidates,
                  membership,
                )(_.id) match {
                  case Left(selErr) =>
                    Left(BeautyQSearchOrchestrationError.AppendPolicy(selErr))
                  case Right(sel) =>
                    Right(sel)
                }
              } yield buildEvaluatedResult(
                baseline,
                evaluation,
                hydrated,
                selection,
                membership,
              )
          }
      }
    }
  }

  private def verifyGenerationConsistency(
    baseline: BoundElasticsearchBaselineResult[?, ?],
    hydrated: HydratedCandidateSearchResult[?, ?, ?, ?, ?, ?, ?],
  ): Either[BeautyQSearchOrchestrationError.GenerationMismatch, Unit] =
    SearchGenerationConsistency.verify(
      baseline.generationMetadata,
      hydrated.metadata,
    ) match {
      case Left(err) => Left(BeautyQSearchOrchestrationError.GenerationMismatch(err))
      case Right(()) => Right(())
    }

  private def buildIneligibleResult(
    baseline: BoundElasticsearchBaselineResult[VariantSearchDocumentGen2, MasterServiceOfferVariantId],
    evaluation: CompiledCandidateEvaluation,
    reason: BeautyQCandidateIneligibility,
  ): Result =
    new Result(
      baseline,
      evaluation,
      new Ineligible(reason),
    )

  private def buildEvaluatedResult(
    baseline: BoundElasticsearchBaselineResult[VariantSearchDocumentGen2, MasterServiceOfferVariantId],
    evaluation: CompiledCandidateEvaluation,
    hydrated: HydratedCandidateSearchResult[
      VariantSearchDocumentGen2,
      MasterServiceOfferVariantId,
      Double,
      QdrantResourceName,
      QdrantGenerationMetadata,
      QdrantCandidateDiagnostics,
      BeautyQCandidateProvenance,
    ],
    selection: AppendOnlySupplementSelection[
      leaderboard.search.gen2.core.hydration.HydratedCandidate[VariantSearchDocumentGen2, MasterServiceOfferVariantId, Double, BeautyQCandidateProvenance],
      MasterServiceOfferVariantId,
    ],
    membership: BaselineMembershipResult[MasterServiceOfferVariantId],
  ): Result =
    new Result(
      baseline,
      evaluation,
      new Evaluated(hydrated, membership, selection),
    )

  private def buildFailedResult(
    baseline: BoundElasticsearchBaselineResult[VariantSearchDocumentGen2, MasterServiceOfferVariantId],
    evaluation: CompiledCandidateEvaluation,
    failure: BeautyQSupplementPolicy.Degradable,
  ): Result =
    new Result(
      baseline,
      evaluation,
      new Failed(failure),
    )
}
