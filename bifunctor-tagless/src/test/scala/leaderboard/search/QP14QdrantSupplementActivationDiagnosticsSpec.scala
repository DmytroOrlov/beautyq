package leaderboard.search

import leaderboard.plugins.{
  BeautySearchQdrantSupplementActivationConfig,
  BeautySearchQdrantSupplementActivationDiagnostics,
  BeautySearchQdrantSupplementActivationPreflight,
}
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.dsl.{EmbeddingSpec, VectorDistance, VectorSearchSpec}
import leaderboard.search.qdrant.{
  ObservedQdrantVectorConfig,
  QdrantCollectionCompatibilityExpectation,
  QdrantCollectionReadinessConfig,
  QdrantCollectionReadinessInput,
}
import org.scalatest.wordspec.AnyWordSpec

/**
 * QP14: operator-visible diagnostics for the no-worsening Qdrant supplement activation path. Pure
 * `Contractual + Blackbox + Atomic` proof over `BeautySearchQdrantSupplementActivationDiagnostics`,
 * built from the existing pure QP8 preflight result (no real ES/Qdrant/Llama, no DI graph, no route).
 *
 * No source-confirmed runtime logging seam exists in this path, so the diagnostics helper is a pure
 * formatter: this spec only asserts the stable summary it produces. It touches no public HTTP route,
 * route JSON, or API codec -- it consumes the preflight result value object and emits stable
 * key/value lines.
 *
 * Proves:
 *   - every operator value (absent / es-only-rollback / not-ready / ready / invalid) reports the
 *     expected activation mode, parse outcome, and decision summary;
 *   - invalid config reports fail-closed `REJECTED` / `InvalidConfig` and never reports
 *     `QdrantSupplementReady` as selected;
 *   - every QP8 preflight reason is restated verbatim in `preflight.reason`;
 *   - every readiness mismatch axis is summarized as a stable machine-readable type token, with no
 *     expected/observed payload values exposed;
 *   - the output is a stable key/value line list (no route JSON / API field names).
 */
final class QP14QdrantSupplementActivationDiagnosticsSpec extends AnyWordSpec {

  import BeautySearchQdrantSupplementActivationDiagnostics as Diag

  // ============================================================================================
  // Activation diagnostic: selected mode + parse outcome per operator value.
  // ============================================================================================

  "BeautySearchQdrantSupplementActivationDiagnostics activation diagnostic" should {
    "report EsOnlyRollback / ACCEPTED for an absent operator value, rendering the absent label" in {
      val diagnostics = diagnosticsFor(None, compatibleObserved)
      assert(diagnostics.activationMode == Diag.EsOnlyRollbackMode)
      assert(diagnostics.activationParse == Diag.ParseAccepted)
      assert(diagnostics.operatorValue == Diag.AbsentOperatorValueLabel)
      assert(diagnostics.decisionSummary == Diag.DecisionDefaultEsOnly)
    }

    "report EsOnlyRollback / ACCEPTED for the explicit es-only-rollback operator value" in {
      val diagnostics = diagnosticsFor(esOnlyRollbackValue, compatibleObserved)
      assert(diagnostics.activationMode == Diag.EsOnlyRollbackMode)
      assert(diagnostics.activationParse == Diag.ParseAccepted)
      assert(diagnostics.operatorValue == BeautySearchQdrantSupplementActivationConfig.EsOnlyRollbackOperatorValue)
      assert(diagnostics.decisionSummary == Diag.DecisionDefaultEsOnly)
    }

    "report QdrantSupplementNotReady / ACCEPTED for the explicit not-ready operator value" in {
      val diagnostics = diagnosticsFor(notReadyValue, compatibleObserved)
      assert(diagnostics.activationMode == Diag.QdrantSupplementNotReadyMode)
      assert(diagnostics.activationParse == Diag.ParseAccepted)
      assert(diagnostics.decisionSummary == Diag.DecisionBlockedNotReady)
    }

    "report QdrantSupplementReady / ACCEPTED for the explicit ready operator value with compatible readiness" in {
      val diagnostics = diagnosticsFor(readyValue, compatibleObserved)
      assert(diagnostics.activationMode == Diag.QdrantSupplementReadyMode)
      assert(diagnostics.activationParse == Diag.ParseAccepted)
      assert(diagnostics.decisionSummary == Diag.DecisionReadyToEnable)
    }

    "report fail-closed InvalidConfig / REJECTED for an invalid operator value, never reporting ready as selected" in {
      val diagnostics = diagnosticsFor(Some("totally-unrecognized"), compatibleObserved)
      assert(diagnostics.activationMode == Diag.InvalidConfigMode)
      assert(diagnostics.activationParse == Diag.ParseRejected)
      assert(diagnostics.activationMode != Diag.QdrantSupplementReadyMode)
      assert(diagnostics.decisionSummary == Diag.DecisionBlockedInvalidConfig)
    }
  }

  // ============================================================================================
  // Preflight diagnostic: status + reason restated verbatim for every QP8 reason.
  // ============================================================================================

  "BeautySearchQdrantSupplementActivationDiagnostics preflight diagnostic" should {
    "restate READY_TO_ENABLE status and reason for compatible ready" in {
      val diagnostics = diagnosticsFor(readyValue, compatibleObserved)
      assert(diagnostics.preflightStatus == "READY_TO_ENABLE")
      assert(diagnostics.preflightReason == BeautySearchQdrantSupplementActivationPreflight.ReadyToEnableReason)
    }

    "restate BLOCKED / ES_ONLY_ROLLBACK_SELECTED for absent config" in {
      val diagnostics = diagnosticsFor(None, compatibleObserved)
      assert(diagnostics.preflightStatus == "BLOCKED")
      assert(diagnostics.preflightReason == BeautySearchQdrantSupplementActivationPreflight.EsOnlyRollbackSelectedReason)
    }

    "restate BLOCKED / QDRANT_SUPPLEMENT_NOT_READY_SELECTED for the not-ready value" in {
      val diagnostics = diagnosticsFor(notReadyValue, compatibleObserved)
      assert(diagnostics.preflightStatus == "BLOCKED")
      assert(diagnostics.preflightReason == BeautySearchQdrantSupplementActivationPreflight.QdrantSupplementNotReadySelectedReason)
    }

    "restate BLOCKED / INVALID_OPERATOR_CONFIG for an invalid value" in {
      val diagnostics = diagnosticsFor(Some("totally-unrecognized"), compatibleObserved)
      assert(diagnostics.preflightStatus == "BLOCKED")
      assert(diagnostics.preflightReason == BeautySearchQdrantSupplementActivationPreflight.InvalidOperatorConfigReason)
    }

    "restate BLOCKED / READINESS_MISMATCH for a ready value with incompatible readiness" in {
      val diagnostics = diagnosticsFor(readyValue, compatibleObserved.copy(dimension = 768))
      assert(diagnostics.preflightStatus == "BLOCKED")
      assert(diagnostics.preflightReason == BeautySearchQdrantSupplementActivationPreflight.ReadinessMismatchReason)
      assert(diagnostics.decisionSummary == Diag.DecisionBlockedReadinessMismatch)
    }
  }

  // ============================================================================================
  // Readiness mismatch summary: stable type token per existing mismatch axis; no payload values.
  // ============================================================================================

  "BeautySearchQdrantSupplementActivationDiagnostics mismatch summary" should {
    "report COLLECTION_NAME for a collection name mismatch" in {
      val diagnostics = diagnosticsFor(readyValue, compatibleObserved.copy(collectionName = "other_collection"))
      assert(diagnostics.mismatches == List(Diag.CollectionNameMismatchToken))
      assertNoPayloadLeak(diagnostics, "other_collection")
    }

    "report VECTOR_NAME for a vector name mismatch" in {
      val diagnostics = diagnosticsFor(readyValue, compatibleObserved.copy(vectorName = "other-vector"))
      assert(diagnostics.mismatches == List(Diag.VectorNameMismatchToken))
      assertNoPayloadLeak(diagnostics, "other-vector")
    }

    "report DIMENSION for a dimension mismatch" in {
      val diagnostics = diagnosticsFor(readyValue, compatibleObserved.copy(dimension = 768))
      assert(diagnostics.mismatches == List(Diag.DimensionMismatchToken))
      assertNoPayloadLeak(diagnostics, "768")
    }

    "report DISTANCE for a distance mismatch" in {
      val diagnostics = diagnosticsFor(readyValue, compatibleObserved.copy(distance = VectorDistance.Euclidean))
      assert(diagnostics.mismatches == List(Diag.DistanceMismatchToken))
    }

    "report EMBEDDING_MODEL for an embedding model mismatch" in {
      val diagnostics = diagnosticsFor(readyValue, compatibleObserved.copy(embeddingModelName = Some("other-model")))
      assert(diagnostics.mismatches == List(Diag.EmbeddingModelMismatchToken))
      assertNoPayloadLeak(diagnostics, "other-model")
    }

    "report no mismatches as an empty list rendered as the stable 'none' token" in {
      val diagnostics = diagnosticsFor(readyValue, compatibleObserved)
      assert(diagnostics.mismatches.isEmpty)
      assert(lineValue(diagnostics, Diag.PreflightMismatchesKey) == Diag.NoMismatches)
    }
  }

  // ============================================================================================
  // Output shape: stable key/value line list (no route JSON / API field names).
  // ============================================================================================

  "BeautySearchQdrantSupplementActivationDiagnostics output shape" should {
    "expose exactly the documented stable keys in order" in {
      val diagnostics = diagnosticsFor(readyValue, compatibleObserved)
      assert(diagnostics.lines.map(_._1) == List(
        Diag.ActivationModeKey,
        Diag.ActivationOperatorKey,
        Diag.ActivationParseKey,
        Diag.PreflightStatusKey,
        Diag.PreflightReasonKey,
        Diag.PreflightMismatchesKey,
        Diag.DecisionSummaryKey,
      ))
    }

    "render key=value lines matching the case-class fields" in {
      val diagnostics = diagnosticsFor(notReadyValue, compatibleObserved)
      val rendered    = diagnostics.renderLines
      assert(rendered.contains(s"${Diag.ActivationModeKey}=${Diag.QdrantSupplementNotReadyMode}"))
      assert(rendered.contains(s"${Diag.DecisionSummaryKey}=${Diag.DecisionBlockedNotReady}"))
    }
  }

  // ============================================================================================
  // Fixtures: reuse the QP8 readiness derivation so the diagnostics consume the real preflight result.
  // ============================================================================================

  private val esOnlyRollbackValue: Option[String] =
    Some(BeautySearchQdrantSupplementActivationConfig.EsOnlyRollbackOperatorValue)
  private val notReadyValue: Option[String] =
    Some(BeautySearchQdrantSupplementActivationConfig.QdrantSupplementNotReadyOperatorValue)
  private val readyValue: Option[String] =
    Some(BeautySearchQdrantSupplementActivationConfig.QdrantSupplementReadyOperatorValue)

  private def diagnosticsFor(
    operatorValue: Option[String],
    observed: ObservedQdrantVectorConfig,
  ): BeautySearchQdrantSupplementActivationDiagnostics = {
    val result = BeautySearchQdrantSupplementActivationPreflight.preflight(operatorValue, expectation, observed)
    BeautySearchQdrantSupplementActivationDiagnostics.from(operatorValue, result)
  }

  private def assertNoPayloadLeak(
    diagnostics: BeautySearchQdrantSupplementActivationDiagnostics,
    payloadValue: String,
  ): Unit = {
    assert(!diagnostics.mismatches.exists(_.contains(payloadValue)), s"mismatch tokens must not leak payload value '$payloadValue'")
    val mismatchLine = lineValue(diagnostics, Diag.PreflightMismatchesKey)
    assert(!mismatchLine.contains(payloadValue), s"mismatch line must not leak payload value '$payloadValue'"): Unit
  }

  private def lineValue(
    diagnostics: BeautySearchQdrantSupplementActivationDiagnostics,
    key: String,
  ): String =
    diagnostics.lines.collectFirst { case (k, value) if k == key => value }
      .getOrElse(fail(s"diagnostics lines must contain key '$key', got ${diagnostics.lines.map(_._1)}"))

  private val embeddingSpec: EmbeddingSpec[VariantSearchDocument] =
    EmbeddingSpec[VariantSearchDocument](
      vectorName = "qp14-embedding-spec-vector-name-is-not-identity-source",
      modelName = "llama-cpp-embedding",
      dimension = 1024,
      distance = VectorDistance.Cosine,
      sourceTextFieldPaths = List("serviceText", "allText"),
    )

  private val vectorSearchSpec: VectorSearchSpec =
    VectorSearchSpec(
      collectionName = "placeholder_collection",
      vectorName = "variant-embedding",
      topK = 20,
      scoreThreshold = Some(0.2),
    )

  private val readinessConfig: QdrantCollectionReadinessConfig =
    QdrantCollectionReadinessConfig.derive(
      QdrantCollectionReadinessInput(
        domainName = "beauty_variant",
        searchSpecVersion = "v1",
        purpose = "qp14-activation-diagnostics",
        embeddingSpec = embeddingSpec,
        vectorSearchSpec = vectorSearchSpec,
      )
    )

  private val expectation: QdrantCollectionCompatibilityExpectation =
    readinessConfig.compatibilityExpectation

  private val compatibleObserved: ObservedQdrantVectorConfig =
    ObservedQdrantVectorConfig(
      collectionName = expectation.collectionName,
      vectorName = expectation.vectorName,
      dimension = expectation.expectedDimension,
      distance = expectation.expectedDistance,
      embeddingModelName = Some(expectation.embeddingModelName),
    )
}
