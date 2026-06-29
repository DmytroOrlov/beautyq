package leaderboard.search

import distage.{DIKey, Mode}
import izumi.distage.model.definition.Activation
import leaderboard.config.QdrantPortCfg
import leaderboard.model.QueryFailure
import leaderboard.plugins.BeautySearchQdrantSupplementActivation.{EsOnlyRollback, QdrantSupplementNotReady, QdrantSupplementReady}
import leaderboard.plugins.BeautySearchQdrantSupplementActivationPreflightStatus.{Blocked, ReadyToEnable}
import leaderboard.plugins.{
  BeautySearchQdrantSupplementActivationConfig,
  BeautySearchQdrantSupplementActivationPreflight,
  BeautySearchQdrantSupplementActivationPreflightCommand,
  BeautySearchQdrantSupplementActivationPreflightResult,
}
import leaderboard.search.dsl.{EmbeddingSpec, VectorDistance, VectorSearchSpec}
import leaderboard.search.qdrant.{
  ObservedQdrantVectorConfig,
  QdrantClient,
  QdrantClientCollectionInfoAdapter,
  QdrantCollectionCompatibilityChecker,
  QdrantCollectionCompatibilityExpectation,
  QdrantCollectionIdentity,
  QdrantCollectionInfoClient,
  QdrantJsonInterpreter,
}
import leaderboard.{LeaderboardTest, ProdTest}
import io.circe.Json
import zio.{IO, Runtime, Unsafe, ZIO}

import java.util.UUID

/**
 * QP11: operator-facing real-resource preflight command for the no-worsening Qdrant supplement
 * activation path, proved via `BeautySearchQdrantSupplementActivationPreflightCommand.run(...)`.
 *
 * Proves:
 *   - absent/explicit-ES-only/explicit-not-ready/invalid operator config all report `Blocked` with the
 *     matching QP8 reason label, identically to the pure `BeautySearchQdrantSupplementActivationPreflight`
 *     helper, and never invoke the (real-resource-capable) `QdrantCollectionCompatibilityChecker`;
 *   - the explicit `qdrant-supplement-ready` operator value reports `ReadyToEnable` only when the
 *     collection the checker reads is compatible, and `Blocked` / `READINESS_MISMATCH` -- never
 *     `ReadyToEnable` -- on a mismatch, a missing collection (404), or an unreachable Qdrant;
 *   - against a real local Qdrant (provisioned the same way as the existing
 *     `QdrantCollectionCompatibilityIntegrationSpec`): a freshly created, namespaced, compatible test
 *     collection reports `ReadyToEnable`, and the same collection checked against a mismatched
 *     expectation reports `Blocked` / `READINESS_MISMATCH`;
 *   - this is a probe only: it performs only the checker's read-only collection-info GET, never
 *     creates/deletes/recreates a collection itself (the spec's own test-scoped collection lifecycle is
 *     outside the command), switches a route, or starts indexing.
 *
 * Proof-only: no default route change, no Qdrant-as-default, no auto-activation, no production
 * collection lifecycle, no startup indexing.
 */
final class QP11QdrantSupplementRealResourcePreflightSpec extends LeaderboardTest with ProdTest {
  override def config = super.config.copy(
    activation = super.config.activation ++ Activation(Mode -> Mode.Test),
    memoizationRoots = super.config.memoizationRoots + DIKey[QdrantPortCfg],
  )

  // ============================================================================================
  // Pure operator-state contract: rollback / not-ready / invalid config never touch the checker,
  // and agree with the existing QP8 pure preflight helper for the same operator value.
  // ============================================================================================

  "BeautySearchQdrantSupplementActivationPreflightCommand.run for non-ready operator values" should {
    "report Blocked / ES_ONLY_ROLLBACK_SELECTED for an absent operator value, without invoking the checker" in {
      val result = runCommand(None, new FailIfCalledQdrantCollectionInfoClient)
      assert(result.status == Blocked)
      assert(result.selectedActivation == Some(EsOnlyRollback))
      assert(result.reason == BeautySearchQdrantSupplementActivationPreflight.EsOnlyRollbackSelectedReason)
      assert(result.mismatchDetail.isEmpty)
      assertAgreesWithPurePreflight(None, result)
    }

    "report Blocked / ES_ONLY_ROLLBACK_SELECTED for the explicit es-only-rollback operator value, without invoking the checker" in {
      val operatorValue = Some(BeautySearchQdrantSupplementActivationConfig.EsOnlyRollbackOperatorValue)
      val result         = runCommand(operatorValue, new FailIfCalledQdrantCollectionInfoClient)
      assert(result.status == Blocked)
      assert(result.selectedActivation == Some(EsOnlyRollback))
      assert(result.reason == BeautySearchQdrantSupplementActivationPreflight.EsOnlyRollbackSelectedReason)
      assertAgreesWithPurePreflight(operatorValue, result)
    }

    "report Blocked / QDRANT_SUPPLEMENT_NOT_READY_SELECTED for the explicit not-ready operator value, without invoking the checker" in {
      val operatorValue = Some(BeautySearchQdrantSupplementActivationConfig.QdrantSupplementNotReadyOperatorValue)
      val result         = runCommand(operatorValue, new FailIfCalledQdrantCollectionInfoClient)
      assert(result.status == Blocked)
      assert(result.selectedActivation == Some(QdrantSupplementNotReady))
      assert(result.reason == BeautySearchQdrantSupplementActivationPreflight.QdrantSupplementNotReadySelectedReason)
      assertAgreesWithPurePreflight(operatorValue, result)
    }

    "report Blocked / INVALID_OPERATOR_CONFIG for an unrecognized operator value, never selecting QdrantSupplementReady, without invoking the checker" in {
      val operatorValue = Some("totally-unrecognized")
      val result         = runCommand(operatorValue, new FailIfCalledQdrantCollectionInfoClient)
      assert(result.status == Blocked)
      assert(result.selectedActivation.isEmpty)
      assert(result.reason == BeautySearchQdrantSupplementActivationPreflight.InvalidOperatorConfigReason)
      assert(result.selectedActivation != Some(QdrantSupplementReady))
      assertAgreesWithPurePreflight(operatorValue, result)
    }
  }

  // ============================================================================================
  // Explicit ready, against a checker backed by a deterministic (in-memory, non-network)
  // QdrantCollectionInfoClient: compatible -> ReadyToEnable, mismatched/missing -> Blocked.
  // ============================================================================================

  "BeautySearchQdrantSupplementActivationPreflightCommand.run for the explicit ready operator value" should {
    "report ReadyToEnable / READY_TO_ENABLE when the checker reads a compatible collection" in {
      val checker = new QdrantCollectionCompatibilityChecker(new ConstQdrantCollectionInfoClient(compatibleCollectionInfoJson))
      val result  = runCommand(readyOperatorValue, checker)
      assert(result.status == ReadyToEnable)
      assert(result.selectedActivation == Some(QdrantSupplementReady))
      assert(result.reason == BeautySearchQdrantSupplementActivationPreflight.ReadyToEnableReason)
      assert(result.mismatchDetail.isEmpty)
    }

    "report Blocked / READINESS_MISMATCH, never ReadyToEnable, when the checker reads a dimension-mismatched collection" in {
      val checker = new QdrantCollectionCompatibilityChecker(new ConstQdrantCollectionInfoClient(mismatchedCollectionInfoJson))
      val result  = runCommand(readyOperatorValue, checker)
      assert(result.status == Blocked)
      assert(result.selectedActivation == Some(QdrantSupplementReady))
      assert(result.reason == BeautySearchQdrantSupplementActivationPreflight.ReadinessMismatchReason)
      assert(result.mismatchDetail.exists(_.nonEmpty))
    }

    "report Blocked / READINESS_MISMATCH, never ReadyToEnable, when the checked collection is missing (404)" in {
      val checker = new QdrantCollectionCompatibilityChecker(new NotFoundQdrantCollectionInfoClient)
      val result  = runCommand(readyOperatorValue, checker)
      assert(result.status == Blocked)
      assert(result.selectedActivation == Some(QdrantSupplementReady))
      assert(result.reason == BeautySearchQdrantSupplementActivationPreflight.ReadinessMismatchReason)
    }

    "report Blocked / READINESS_MISMATCH, never ReadyToEnable, when Qdrant is unreachable" in {
      val checker = new QdrantCollectionCompatibilityChecker(new UnreachableQdrantCollectionInfoClient)
      val result  = runCommand(readyOperatorValue, checker)
      assert(result.status == Blocked)
      assert(result.selectedActivation == Some(QdrantSupplementReady))
      assert(result.reason == BeautySearchQdrantSupplementActivationPreflight.ReadinessMismatchReason)
    }
  }

  // ============================================================================================
  // Real-resource probe against the local Qdrant provisioned the same way as the existing
  // QdrantCollectionCompatibilityIntegrationSpec (Docker-managed via the `LeaderboardTest`
  // harness's `QdrantPortCfg` memoization root). A namespaced, freshly created, test-only
  // collection is used and deleted afterwards -- mirroring that existing spec's own lifecycle,
  // never a production collection.
  // ============================================================================================

  "BeautySearchQdrantSupplementActivationPreflightCommand.run against a real local Qdrant" should {
    "report ReadyToEnable for a freshly created compatible collection, and Blocked / READINESS_MISMATCH for a real dimension mismatch on the same collection" in {
      (portCfg: QdrantPortCfg) =>
        val qdrantClient    = new QdrantClient(portCfg.host, portCfg.port)
        val collectionName  = s"qp11_preflight_${UUID.randomUUID().toString.replace('-', '_')}"
        val collectionPath  = s"/collections/$collectionName"
        val realVectorSearchSpec = VectorSearchSpec(
          collectionName = collectionName,
          vectorName = "qp11-preflight-vector",
          topK = 10,
          scoreThreshold = None,
        )
        val realEmbeddingSpec = EmbeddingSpec[Any](
          vectorName = realVectorSearchSpec.vectorName,
          modelName = "qp11-preflight-model",
          dimension = 4,
          distance = VectorDistance.Cosine,
          sourceTextFields = Nil,
        )
        val realExpectation = QdrantCollectionIdentity.compatibilityExpectation(realEmbeddingSpec, realVectorSearchSpec)
        val checker          = new QdrantCollectionCompatibilityChecker(new QdrantClientCollectionInfoAdapter(qdrantClient))

        runIO {
          (for {
            _ <- qdrantClient.createCollection(
              collectionPath,
              QdrantJsonInterpreter.createCollectionJson(realVectorSearchSpec, realEmbeddingSpec),
            )
            compatibleResult <- BeautySearchQdrantSupplementActivationPreflightCommand.run(readyOperatorValue, realExpectation, checker)
            mismatchedResult <- BeautySearchQdrantSupplementActivationPreflightCommand.run(
              readyOperatorValue,
              realExpectation.copy(expectedDimension = realEmbeddingSpec.dimension + 1),
              checker,
            )
            _ <- ZIO.succeed {
              assert(compatibleResult.status == ReadyToEnable, s"expected ReadyToEnable for a real compatible collection, got $compatibleResult")
              assert(compatibleResult.reason == BeautySearchQdrantSupplementActivationPreflight.ReadyToEnableReason)
              assert(mismatchedResult.status == Blocked, s"expected Blocked for a real dimension mismatch, got $mismatchedResult")
              assert(mismatchedResult.reason == BeautySearchQdrantSupplementActivationPreflight.ReadinessMismatchReason)
              assert(mismatchedResult.mismatchDetail.exists(_.nonEmpty))
            }
          } yield ()).ensuring(qdrantClient.deleteCollection(collectionPath).either.unit)
        }
    }
  }

  // ============================================================================================
  // Shared fixtures.
  // ============================================================================================

  private val readyOperatorValue: Option[String] =
    Some(BeautySearchQdrantSupplementActivationConfig.QdrantSupplementReadyOperatorValue)

  private val embeddingSpec: EmbeddingSpec[Any] =
    EmbeddingSpec[Any](
      vectorName = "qp11-embedding-spec-vector-name-is-not-identity-source",
      modelName = "llama-cpp-embedding",
      dimension = 1024,
      distance = VectorDistance.Cosine,
      sourceTextFields = Nil,
    )

  private val vectorSearchSpec: VectorSearchSpec =
    VectorSearchSpec(
      collectionName = "placeholder_collection",
      vectorName = "variant-embedding",
      topK = 20,
      scoreThreshold = Some(0.2),
    )

  private val expectation: QdrantCollectionCompatibilityExpectation =
    QdrantCollectionIdentity.compatibilityExpectation(embeddingSpec, vectorSearchSpec)

  private def compatibleCollectionInfoJson: Json =
    Json.obj(
      "result" -> Json.obj(
        "name" -> Json.fromString(expectation.collectionName),
        "config" -> Json.obj(
          "params" -> Json.obj(
            "vectors" -> Json.obj(
              expectation.vectorName -> Json.obj(
                "size" -> Json.fromInt(expectation.expectedDimension),
                "distance" -> Json.fromString("Cosine"),
              )
            )
          )
        ),
        "metadata" -> Json.obj(
          "embeddingModelName" -> Json.fromString(expectation.embeddingModelName)
        ),
      )
    )

  private def mismatchedCollectionInfoJson: Json =
    Json.obj(
      "result" -> Json.obj(
        "name" -> Json.fromString(expectation.collectionName),
        "config" -> Json.obj(
          "params" -> Json.obj(
            "vectors" -> Json.obj(
              expectation.vectorName -> Json.obj(
                "size" -> Json.fromInt(expectation.expectedDimension + 1),
                "distance" -> Json.fromString("Cosine"),
              )
            )
          )
        ),
      )
    )

  private final class ConstQdrantCollectionInfoClient(json: Json) extends QdrantCollectionInfoClient {
    override def collectionInfo(path: String): IO[QueryFailure, Json] = ZIO.succeed(json)
  }

  private final class NotFoundQdrantCollectionInfoClient extends QdrantCollectionInfoClient {
    override def collectionInfo(path: String): IO[QueryFailure, Json] =
      ZIO.fail(QueryFailure.operation("get-qdrant-collection-info", s"Unexpected status 404: collection not found at $path"))
  }

  private final class UnreachableQdrantCollectionInfoClient extends QdrantCollectionInfoClient {
    override def collectionInfo(path: String): IO[QueryFailure, Json] =
      ZIO.fail(QueryFailure.fromThrowable("execute-qdrant-http", new java.net.ConnectException(s"Connection refused for $path")))
  }

  // Proves the command never attempts the real-resource checker call for non-ready operator
  // selections: invoking this client would die the test.
  private final class FailIfCalledQdrantCollectionInfoClient extends QdrantCollectionInfoClient {
    override def collectionInfo(path: String): IO[QueryFailure, Json] =
      ZIO.dieMessage(s"QP11: the checker must not be invoked for a non-ready operator selection (path=$path)")
  }

  private def runCommand(
    operatorValue: Option[String],
    infoClient: QdrantCollectionInfoClient,
  ): BeautySearchQdrantSupplementActivationPreflightResult =
    runIO(BeautySearchQdrantSupplementActivationPreflightCommand.run(
      operatorValue,
      expectation,
      new QdrantCollectionCompatibilityChecker(infoClient),
    ))

  private def runCommand(
    operatorValue: Option[String],
    checker: QdrantCollectionCompatibilityChecker,
  ): BeautySearchQdrantSupplementActivationPreflightResult =
    runIO(BeautySearchQdrantSupplementActivationPreflightCommand.run(operatorValue, expectation, checker))

  private def assertAgreesWithPurePreflight(
    operatorValue: Option[String],
    commandResult: BeautySearchQdrantSupplementActivationPreflightResult,
  ): Unit = {
    val pureResult = BeautySearchQdrantSupplementActivationPreflight.preflight(
      operatorValue,
      expectation,
      ObservedQdrantVectorConfig(
        collectionName = expectation.collectionName,
        vectorName = expectation.vectorName,
        dimension = expectation.expectedDimension,
        distance = expectation.expectedDistance,
        embeddingModelName = Some(expectation.embeddingModelName),
      ),
    )
    assert(commandResult.status == pureResult.status)
    assert(commandResult.selectedActivation == pureResult.selectedActivation)
    assert(commandResult.reason == pureResult.reason): Unit
  }

  private def runIO[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
