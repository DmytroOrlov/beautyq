package leaderboard.plugins

import leaderboard.search.qdrant.{ObservedQdrantVectorConfig, QdrantCollectionCompatibilityExpectation}
import leaderboard.search.dsl.VectorDistance
import org.scalatest.wordspec.AnyWordSpec

final class BeautySearchQdrantSupplementActivationPolicySpec extends AnyWordSpec {

  "BeautySearchQdrantSupplementActivationConfig.fromOperatorValue" should {
    "default to EsOnlyRollback when the operator value is absent" in {
      assert(BeautySearchQdrantSupplementActivationConfig.fromOperatorValue(None) == Right(BeautySearchQdrantSupplementActivation.EsOnlyRollback))
    }

    "accept the qdrant-supplement-ready operator value" in {
      assert(
        BeautySearchQdrantSupplementActivationConfig.fromOperatorValue(
          Some(BeautySearchQdrantSupplementActivationConfig.QdrantSupplementReadyOperatorValue)
        ) == Right(BeautySearchQdrantSupplementActivation.QdrantSupplementReady)
      )
    }

    "fail closed on an unrecognized operator value" in {
      BeautySearchQdrantSupplementActivationConfig.fromOperatorValue(Some("not-a-real-value")) match {
        case Left(failure) =>
          assert(failure.message.contains("Unrecognized"))
        case Right(activation) =>
          fail(s"Expected a rejected parse, got $activation")
      }
    }
  }

  "BeautySearchQdrantSupplementActivationPreflight.preflight" should {
    val expectation = QdrantCollectionCompatibilityExpectation(
      collectionName = "beautyq_v1",
      vectorName = "llama-cpp-embedding",
      expectedDimension = 1024,
      expectedDistance = VectorDistance.Cosine,
      embeddingModelName = "local-llama-cpp-embedding",
    )

    "report ReadyToEnable when the observed config matches the expectation" in {
      val observed = ObservedQdrantVectorConfig(
        collectionName = expectation.collectionName,
        vectorName = expectation.vectorName,
        dimension = expectation.expectedDimension,
        distance = expectation.expectedDistance,
        embeddingModelName = Some(expectation.embeddingModelName),
      )

      val result = BeautySearchQdrantSupplementActivationPreflight.preflight(
        operatorValue = Some(BeautySearchQdrantSupplementActivationConfig.QdrantSupplementReadyOperatorValue),
        expectedReadiness = expectation,
        observedReadiness = observed,
      )

      assert(result.status == BeautySearchQdrantSupplementActivationPreflightStatus.ReadyToEnable)
      assert(result.selectedActivation.contains(BeautySearchQdrantSupplementActivation.QdrantSupplementReady))
    }

    "report Blocked when the observed dimension mismatches the expectation" in {
      val observed = ObservedQdrantVectorConfig(
        collectionName = expectation.collectionName,
        vectorName = expectation.vectorName,
        dimension = expectation.expectedDimension + 1,
        distance = expectation.expectedDistance,
        embeddingModelName = Some(expectation.embeddingModelName),
      )

      val result = BeautySearchQdrantSupplementActivationPreflight.preflight(
        operatorValue = Some(BeautySearchQdrantSupplementActivationConfig.QdrantSupplementReadyOperatorValue),
        expectedReadiness = expectation,
        observedReadiness = observed,
      )

      assert(result.status == BeautySearchQdrantSupplementActivationPreflightStatus.Blocked)
      assert(result.reason == BeautySearchQdrantSupplementActivationPreflight.ReadinessMismatchReason)
    }
  }

  "BeautySearchQdrantSupplementActivationDiagnostics.from" should {
    "render the ready-to-enable decision summary line" in {
      val result = BeautySearchQdrantSupplementActivationPreflightResult(
        status = BeautySearchQdrantSupplementActivationPreflightStatus.ReadyToEnable,
        selectedActivation = Some(BeautySearchQdrantSupplementActivation.QdrantSupplementReady),
        reason = BeautySearchQdrantSupplementActivationPreflight.ReadyToEnableReason,
        mismatchDetail = None,
      )

      val diagnostics = BeautySearchQdrantSupplementActivationDiagnostics.from(
        operatorValue = Some(BeautySearchQdrantSupplementActivationConfig.QdrantSupplementReadyOperatorValue),
        result = result,
      )

      assert(diagnostics.renderLines.contains(s"${BeautySearchQdrantSupplementActivationDiagnostics.DecisionSummaryKey}=${BeautySearchQdrantSupplementActivationDiagnostics.DecisionReadyToEnable}"))
    }
  }
}
