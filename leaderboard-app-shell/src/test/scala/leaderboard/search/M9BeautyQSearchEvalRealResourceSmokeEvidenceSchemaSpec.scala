package leaderboard.search

import leaderboard.search.eval.{
  CandidateSource,
  M9BeautyQSearchEvalCombinedSmokeComparisonPlan,
  M9BeautyQSearchEvalEsOnlySmokeExecutionPlan,
  M9BeautyQSearchEvalQdrantOnlySmokeExecutionPlan,
  M9BeautyQSearchEvalRealCallCheckpoint,
  M9BeautyQSearchEvalRealCallCheckpointDecision,
  M9BeautyQSearchEvalRealCallCheckpointInput,
  M9BeautyQSearchEvalRealResourcePrerequisiteReason,
  M9BeautyQSearchEvalRealResourcePrerequisiteStatus,
  M9BeautyQSearchEvalRealResourcePrerequisitesAudit,
  M9BeautyQSearchEvalRealResourceComparisonSmokeEvidence,
  M9BeautyQSearchEvalRealResourceEsOnlySmokeEvidence,
  M9BeautyQSearchEvalRealResourceQdrantOnlySmokeEvidence,
  M9BeautyQSearchEvalRealResourceSmokeEvidenceKind,
  M9BeautyQSearchEvalRealResourceSmokeEvidenceSchema,
  M9BeautyQSearchEvalRealResourceSmokeEvidenceState,
  M9OfflineEvalBackendExecutionMode,
  ServingMode,
}
import org.scalatest.wordspec.AnyWordSpec

final class M9BeautyQSearchEvalRealResourceSmokeEvidenceSchemaSpec extends AnyWordSpec {

  private val defaultArtifact = M9BeautyQSearchEvalRealResourceSmokeEvidenceSchema.DefaultArtifact

  private val comparisonReadyArtifact =
    M9BeautyQSearchEvalRealResourceSmokeEvidenceSchema.fromAudit(
      M9BeautyQSearchEvalRealResourcePrerequisitesAudit.auditForEsQdrantComparison
    )

  private val esOnlyReadyArtifact =
    M9BeautyQSearchEvalRealResourceSmokeEvidenceSchema.fromAudit(
      M9BeautyQSearchEvalRealResourcePrerequisitesAudit.auditForEsOnlyConfig
    )

  private val notReadyArtifact =
    M9BeautyQSearchEvalRealResourceSmokeEvidenceSchema.fromAudit(
      M9BeautyQSearchEvalRealResourcePrerequisitesAudit.audit(
        M9BeautyQSearchEvalRealCallCheckpoint.decide(
          M9BeautyQSearchEvalRealCallCheckpointInput.Default.copy(
            scorecard = M9BeautyQSearchEvalRealCallCheckpointInput.Default.scorecard.copy(
              verdict = "dataset_static_rows_not_ready"
            ),
            esResourceConfigPresent = true,
            qdrantResourceConfigPresent = true,
            operatorApprovalGranted = true,
          )
        )
      )
    )

  "M9BeautyQSearchEvalRealResourceSmokeEvidenceSchema" should {

    "give ES-only and Qdrant-only evidence distinct shapes" in {
      val es: M9BeautyQSearchEvalRealResourceEsOnlySmokeEvidence = comparisonReadyArtifact.esOnly
      val qdrant: M9BeautyQSearchEvalRealResourceQdrantOnlySmokeEvidence = comparisonReadyArtifact.qdrantOnly

      assert(es.kind == M9BeautyQSearchEvalRealResourceSmokeEvidenceKind.EsOnlySmoke)
      assert(qdrant.kind == M9BeautyQSearchEvalRealResourceSmokeEvidenceKind.QdrantOnlySmoke)
      assert(es.kind != qdrant.kind)
      assert(es.artifactKind != qdrant.artifactKind)

      assert(es.candidateSource == CandidateSource.Es)
      assert(es.servingMode == ServingMode.EsOnly)
      assert(es.backendModes == List(M9OfflineEvalBackendExecutionMode.EsOnlyOffline))

      assert(qdrant.candidateSource == CandidateSource.Qdrant)
      assert(qdrant.servingMode == ServingMode.QdrantOnly)
      assert(qdrant.backendModes == List(M9OfflineEvalBackendExecutionMode.QdrantOnlyOffline))

      assert(es.requiredCheckpointDecision == M9BeautyQSearchEvalEsOnlySmokeExecutionPlan.RequiredCheckpointDecision)
      assert(qdrant.requiredCheckpointDecision == M9BeautyQSearchEvalQdrantOnlySmokeExecutionPlan.RequiredCheckpointDecision)
    }

    "include comparison dimensions in the combined comparison evidence" in {
      val comparison: M9BeautyQSearchEvalRealResourceComparisonSmokeEvidence =
        comparisonReadyArtifact.esQdrantComparison

      assert(comparison.kind == M9BeautyQSearchEvalRealResourceSmokeEvidenceKind.EsQdrantComparison)
      assert(comparison.esCandidateSource == CandidateSource.Es)
      assert(comparison.qdrantCandidateSource == CandidateSource.Qdrant)
      assert(comparison.esExecutionMode == M9OfflineEvalBackendExecutionMode.EsOnlyOffline)
      assert(comparison.qdrantExecutionMode == M9OfflineEvalBackendExecutionMode.QdrantOnlyOffline)
      assert(comparison.requiredCheckpointDecision == M9BeautyQSearchEvalCombinedSmokeComparisonPlan.RequiredCheckpointDecision)

      // The comparison shape carries every comparison dimension, all pending (no observed backend data).
      val shape = comparison.comparisonShape
      assert(shape.esCandidateIds.isEmpty)
      assert(shape.qdrantCandidateIds.isEmpty)
      assert(shape.overlapCandidateIds.isEmpty)
      assert(shape.missCandidateIds.isEmpty)
      assert(shape.unexpectedCandidateIds.isEmpty)
      assert(shape.missingLookupRate.isEmpty)
      assert(shape.comparisonNotes.isEmpty)

      // ES-only and Qdrant-only evidence carry no comparison shape (only the combined evidence does).
      assert(comparisonReadyArtifact.evidences.collect {
        case c: M9BeautyQSearchEvalRealResourceComparisonSmokeEvidence => c
      }.size == 1)
    }

    "render blocked/skip evidence for blocked prerequisites, never success evidence" in {
      // Default audit: no resource config => all targets blocked.
      assert(defaultArtifact.checkpointDecision == M9BeautyQSearchEvalRealCallCheckpointDecision.NotEligibleNoExplicitResourceConfig)
      assert(defaultArtifact.evidences.forall(_.blockedOrSkipped))
      assert(defaultArtifact.evidences.forall(_.state == M9BeautyQSearchEvalRealResourceSmokeEvidenceState.PrerequisitesBlockedSkip))
      assert(defaultArtifact.evidences.forall(_.prerequisitesStatus == M9BeautyQSearchEvalRealResourcePrerequisiteStatus.PrerequisitesBlocked))
      assert(defaultArtifact.evidences.forall(!_.prerequisitesComplete))
      assert(defaultArtifact.esOnly.skipOrBlockReasons.contains(M9BeautyQSearchEvalRealResourcePrerequisiteReason.EsResourceConfigMissing))
      assert(defaultArtifact.qdrantOnly.skipOrBlockReasons.contains(M9BeautyQSearchEvalRealResourcePrerequisiteReason.QdrantResourceConfigMissing))

      // ES-config-present audit: ES-only complete (pending execution), the others blocked.
      assert(esOnlyReadyArtifact.esOnly.state == M9BeautyQSearchEvalRealResourceSmokeEvidenceState.PrerequisitesCompletePendingExecution)
      assert(esOnlyReadyArtifact.esOnly.prerequisitesComplete)
      assert(esOnlyReadyArtifact.esOnly.skipOrBlockReasons.isEmpty)
      assert(esOnlyReadyArtifact.qdrantOnly.blockedOrSkipped)
      assert(esOnlyReadyArtifact.esQdrantComparison.blockedOrSkipped)

      // Static scorecard not ready => every target blocked regardless of resource config.
      assert(!notReadyArtifact.staticScorecardReady)
      assert(notReadyArtifact.evidences.forall(_.blockedOrSkipped))
      assert(notReadyArtifact.evidences.forall(_.skipOrBlockReasons.contains(
        M9BeautyQSearchEvalRealResourcePrerequisiteReason.StaticScorecardOrCheckpointNotReady
      )))

      // No evidence state exists for success: even complete prerequisites only reach pending execution.
      val allStates = M9BeautyQSearchEvalRealResourceSmokeEvidenceState.values.toList
      assert(!allStates.exists(_.render.contains("success")))
    }

    "carry required fields on every evidence shape" in {
      val artifacts = List(defaultArtifact, esOnlyReadyArtifact, comparisonReadyArtifact, notReadyArtifact)

      assert(artifacts.forall(_.evidences.forall(_.selectedQueryAnchors.nonEmpty)))
      assert(artifacts.forall(_.evidences.forall(_.expectedEvidenceFilesOrSections.nonEmpty)))
      assert(artifacts.forall(_.evidences.forall(_.validationSummary.nonEmpty)))
      assert(artifacts.forall(_.evidences.forall(_.backendModes.nonEmpty)))
      assert(artifacts.forall(_.evidences.forall(_.standingBoundaries.contains(
        M9BeautyQSearchEvalRealResourcePrerequisiteReason.RealBackendGateDisabledByDefault
      ))))
      assert(artifacts.forall(_.evidences.forall(_.standingBoundaries.contains(
        M9BeautyQSearchEvalRealResourcePrerequisiteReason.ProductionActivationBoundaryNotApproved
      ))))

      // Expected evidence files include the accepted plan markdown artifacts.
      assert(comparisonReadyArtifact.esOnly.expectedEvidenceFilesOrSections.contains(
        M9BeautyQSearchEvalEsOnlySmokeExecutionPlan.MarkdownFilename
      ))
      assert(comparisonReadyArtifact.qdrantOnly.expectedEvidenceFilesOrSections.contains(
        M9BeautyQSearchEvalQdrantOnlySmokeExecutionPlan.MarkdownFilename
      ))
      assert(comparisonReadyArtifact.esQdrantComparison.expectedEvidenceFilesOrSections.contains(
        M9BeautyQSearchEvalCombinedSmokeComparisonPlan.MarkdownFilename
      ))

      // Selected query anchors are sourced from the accepted plan contracts.
      assert(comparisonReadyArtifact.esOnly.selectedQueryAnchors.map(_.queryId) ==
        M9BeautyQSearchEvalEsOnlySmokeExecutionPlan.SelectedQueries.map(_.queryId))
      assert(comparisonReadyArtifact.esQdrantComparison.selectedQueryAnchors.map(_.queryId) ==
        M9BeautyQSearchEvalCombinedSmokeComparisonPlan.SelectedQueries.map(_.queryId))
    }

    "keep production/Qdrant activation false and real backend/client/route flags false everywhere" in {
      val artifacts = List(defaultArtifact, esOnlyReadyArtifact, comparisonReadyArtifact, notReadyArtifact)

      artifacts.foreach { artifact =>
        assert(artifact.savedEvidenceSchemaOnly)
        assert(!artifact.realBackendCallExecutionPartOfThisSchemaTask)
        assert(!artifact.realBackendCallImplemented)
        assert(!artifact.realBackendCallRequired)
        assert(!artifact.esClientCreated)
        assert(!artifact.qdrantClientCreated)
        assert(!artifact.productionBeautySearchCalled)
        assert(!artifact.routePluginDiHttpInvolved)
        assert(!artifact.productionActivationApproved)
        assert(!artifact.qdrantProductionActivationApproved)
        assert(artifact.defaultBeautySearchEsBacked)
        assert(!artifact.qualityGreenClaimed)
        assert(!artifact.productionReadinessClaimed)
        assert(!artifact.routeActivationClaimed)
        assert(!artifact.servingApprovalClaimed)
        assert(!artifact.hybridFallbackFusionRerankingTelemetryRouteSwitchImplemented)

        assert(artifact.evidences.forall(_.savedEvidenceSchemaOnly))
        assert(artifact.evidences.forall(!_.realBackendCallExecutionPartOfThisSchemaTask))
        assert(artifact.evidences.forall(!_.realBackendCallImplemented))
        assert(artifact.evidences.forall(!_.realBackendCallRequired))
        assert(artifact.evidences.forall(!_.esClientCreated))
        assert(artifact.evidences.forall(!_.qdrantClientCreated))
        assert(artifact.evidences.forall(!_.productionBeautySearchCalled))
        assert(artifact.evidences.forall(!_.routePluginDiHttpInvolved))
        assert(artifact.evidences.forall(!_.productionActivationApproved))
        assert(artifact.evidences.forall(!_.qdrantProductionActivationApproved))
        assert(artifact.evidences.forall(_.defaultBeautySearchEsBacked))
        assert(artifact.evidences.forall(!_.qualityGreenClaimed))
        assert(artifact.evidences.forall(!_.productionReadinessClaimed))
        assert(artifact.evidences.forall(!_.routeActivationClaimed))
        assert(artifact.evidences.forall(!_.servingApprovalClaimed))
        assert(artifact.evidences.forall(!_.hybridFallbackFusionRerankingTelemetryRouteSwitchImplemented))
      }
    }

    "never expose forbidden production/hybrid/quality vocabulary in schema tokens" in {
      val forbidden =
        List("hybrid", "fallback", "fusion", "rerank", "route_switch", "production_telemetry", "quality_green", "readiness")
      val tokens =
        M9BeautyQSearchEvalRealResourceSmokeEvidenceKind.values.toList.map(_.render) ++
          M9BeautyQSearchEvalRealResourceSmokeEvidenceState.values.toList.map(_.render) ++
          M9BeautyQSearchEvalRealResourceSmokeEvidenceSchema.ExpectedEvidenceSections ++
          M9BeautyQSearchEvalRealResourceSmokeEvidenceSchema.DefaultArtifact.evidences.map(_.artifactKind)

      assert(tokens.forall(token => forbidden.forall(claim => !token.contains(claim))))
    }
  }
}
