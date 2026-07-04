package leaderboard.search

import distage.{DIKey, Mode}
import izumi.distage.model.definition.Activation
import leaderboard.{LeaderboardTest, ProdTest}
import leaderboard.config.QdrantPortCfg
import leaderboard.model.{MasterId, MasterLocationId, MasterServiceOfferId, MasterServiceOfferVariantId, QueryFailure, ServiceId}
import leaderboard.model.Category.CategoryId
import leaderboard.search.document.{InMemoryVariantSearchDocumentSnapshotProvider, VariantSearchDocument}
import leaderboard.search.dsl.{EmbeddingSpec, SearchGeoPoint, VectorDistance, VectorSearchSpec}
import leaderboard.search.embedding.LlamaCppEmbeddingClient
import leaderboard.search.eval.*
import leaderboard.search.qdrant.{QdrantClient, QdrantCollectionReadinessConfig, QdrantCollectionReadinessInput, QdrantEmbeddingBenchmarkDefaultCompositionFactory, QdrantJsonInterpreter}
import zio.{IO, Runtime, Unsafe}

import java.util.UUID

/**
 * M18B: proves the Qdrant leg of [[M18DualEngineOfflineEvalRunner]] connects to the existing
 * Qdrant-native semantic retrieval seam ([[leaderboard.search.qdrant.QdrantSemanticCandidateBackend]])
 * against a real, repo-local Qdrant instance (the same docker-managed Qdrant used by the embedding
 * benchmark integration specs) and a real embedding endpoint, emitting real candidate rows when both
 * resources are reachable.
 *
 * When the embedding endpoint or Qdrant itself is unreachable, the spec records the exact missing
 * prerequisite(s) on the honest resource-gated outcome instead of faking candidates. The ES leg is not
 * exercised by this spec; it is left explicitly blocked.
 */
final class M18DualEngineOfflineEvalQdrantRealLegSpec extends LeaderboardTest with ProdTest {
  override def config = super.config.copy(
    activation = super.config.activation ++ Activation(Mode -> Mode.Test),
    memoizationRoots = super.config.memoizationRoots + DIKey[QdrantPortCfg],
  )

  private val variantId: MasterServiceOfferVariantId = UUID.randomUUID()
  private val otherVariantId: MasterServiceOfferVariantId = UUID.randomUUID()

  "M18 dual-engine offline eval Qdrant leg against real Qdrant" should {
    "execute the Qdrant leg via QdrantSemanticCandidateBackend's documentHits path and emit non-empty " +
      "real candidate rows when the embedding endpoint and Qdrant are reachable, otherwise stay honestly resource-gated" in {
        (portCfg: QdrantPortCfg) =>
          val qdrantClient = new QdrantClient(portCfg.host, portCfg.port)
          val embeddingConfig = LlamaCppEmbeddingTestConfig.default
          val embeddingClient = LlamaCppEmbeddingTestConfig.client(embeddingConfig)

          val (embeddingProbe, qdrantProbe) = unsafeRun(
            for {
              embeddingResult <- embeddingClient.embed("m18 qdrant real leg probe").either
              qdrantResult <- qdrantClient.collectionInfo("/collections").either
            } yield (embeddingResult, qdrantResult)
          )

          val embeddingConfigured = embeddingProbe.exists(_.nonEmpty)
          val qdrantConfigured = qdrantProbe.isRight

          (embeddingProbe, qdrantConfigured) match {
            case (Right(vector), true) if vector.nonEmpty =>
              val result = unsafeRun(runConnectedLeg(qdrantClient, embeddingClient, vector.length))

              assert(result.qdrantExecuted, s"expected Qdrant leg to execute, got ${result.qdrant}")
              assert(result.qdrantCandidateRows.nonEmpty, "expected non-empty real Qdrant candidate rows")
              assert(result.qdrantCandidateRows.forall(_.backend == M18OfflineEvalBackend.Qdrant))
              assert(result.qdrantCandidateRows.exists(_.candidateId == variantId.toString))
              assert(!result.esExecuted, "ES leg is not exercised by this Qdrant-focused real-leg spec")
              assert(result.separationViolations.isEmpty)
            case _ =>
              val prerequisites = M18QdrantLegPrerequisites(
                realBackendOfflineEvalEnabled = true,
                embeddingClientConfigured = embeddingConfigured,
                qdrantClientConfigured = qdrantConfigured,
                collectionReadinessConfigured = true,
              )
              val result = unsafeRun(runGatedLeg(prerequisites))

              assert(!result.qdrantExecuted, "Qdrant leg must not execute: required real resource(s) unavailable")
              assert(result.qdrantCandidateRows.isEmpty, "no fake Qdrant rows may be emitted")
              result.qdrant match {
                case skipped: M18BackendLegOutcome.Skipped =>
                  assert(skipped.skipKind == M18LegSkipKind.ResourceGated)
                  assert(skipped.missingPrerequisites.nonEmpty)
                  assert(embeddingConfigured || skipped.missingPrerequisites.exists(_.contains("embedding client")))
                  assert(qdrantConfigured || skipped.missingPrerequisites.exists(_.contains("Qdrant search client")))
                case other =>
                  fail(s"expected resource-gated Qdrant leg, got $other")
              }
              assert(result.separationViolations.isEmpty)
          }
      }
  }

  private def runConnectedLeg(
    qdrantClient: QdrantClient,
    embeddingClient: LlamaCppEmbeddingClient,
    vectorDimension: Int,
  ): IO[QueryFailure, M18DualEngineOfflineEvalResult] = {
    val embeddingSpec = EmbeddingSpec[VariantSearchDocument](
      vectorName = "llama-cpp-embedding",
      modelName = "m18b-qdrant-real-leg",
      dimension = vectorDimension,
      distance = VectorDistance.Cosine,
      sourceTextFields = List(leaderboard.search.document.BeautyQVariantSearchDocumentContract.Fields.serviceText, leaderboard.search.document.BeautyQVariantSearchDocumentContract.Fields.attributeText, leaderboard.search.document.BeautyQVariantSearchDocumentContract.Fields.allText, leaderboard.search.document.BeautyQVariantSearchDocumentContract.Fields.categoryName),
    )
    val readinessConfig = QdrantCollectionReadinessConfig.derive(
      QdrantCollectionReadinessInput(
        domainName = "beautyq",
        searchSpecVersion = "v1",
        purpose = s"m18b-qdrant-real-leg-${UUID.randomUUID().toString.replace('-', '_')}",
        embeddingSpec = embeddingSpec,
        vectorSearchSpec = VectorSearchSpec(
          collectionName = "placeholder",
          vectorName = "llama-cpp-embedding",
          topK = 10,
          scoreThreshold = None,
        ),
      )
    )
    val collectionPath = s"/collections/${readinessConfig.collectionName}"
    val snapshotProvider = new InMemoryVariantSearchDocumentSnapshotProvider[IO](
      List(
        syntheticDocument(variantId, "balayage haircut", "service_name"),
        syntheticDocument(otherVariantId, "manicure gel polish", "other"),
      )
    )
    val compositionFactory = new QdrantEmbeddingBenchmarkDefaultCompositionFactory(qdrantClient)

    (
      for {
        composition <- compositionFactory.build(readinessConfig, embeddingClient, snapshotProvider, embeddingSpec)
        createJson = QdrantJsonInterpreter.createCollectionJson(readinessConfig.vectorSearchSpec, embeddingSpec)
        _ <- qdrantClient.createCollection(collectionPath, createJson)
        _ <- composition.indexSnapshot()
        qdrantLeg = M18QdrantLegInput.fromPrerequisites[IO, MasterServiceOfferVariantId](
          M18QdrantLegPrerequisites(
            realBackendOfflineEvalEnabled = true,
            embeddingClientConfigured = true,
            qdrantClientConfigured = true,
            collectionReadinessConfigured = true,
          )
        ) {
          M18QdrantLegInput.Connected(composition.semanticBackend, lookup = None)
        }
        result <- runner.run(
          dataset = dataset,
          esLeg = M18EsLegInput.Blocked("ES leg not exercised by this Qdrant-focused real-leg spec"),
          qdrantLeg = qdrantLeg,
        )
      } yield result
    ).ensuring(qdrantClient.deleteCollection(collectionPath).either.unit)
  }

  private def runGatedLeg(prerequisites: M18QdrantLegPrerequisites): IO[Nothing, M18DualEngineOfflineEvalResult] = {
    val qdrantLeg = M18QdrantLegInput.fromPrerequisites[IO, MasterServiceOfferVariantId](prerequisites) {
      fail("must not connect Qdrant: this branch proves the resource-gated path")
    }
    runner.run(
      dataset = dataset,
      esLeg = M18EsLegInput.Blocked("ES leg not exercised by this Qdrant-focused real-leg spec"),
      qdrantLeg = qdrantLeg,
    )
  }

  private val runner: M18DualEngineOfflineEvalRunner[IO, MasterServiceOfferVariantId] =
    new M18DualEngineOfflineEvalRunner[IO, MasterServiceOfferVariantId](
      inputBuilder = M18EvalQueryInputBuilder.default,
      renderId = _.toString,
      clock = None,
    )

  private val dataset: M9OfflineEvalDataset =
    M9OfflineEvalDataset(
      evalDatasetId = EvalDatasetId("m18b-qdrant-real-leg-dataset"),
      catalogSnapshotId = CatalogSnapshotId("m18b-qdrant-real-leg-snapshot"),
      queries = List(
        M9OfflineEvalDatasetQuery(
          queryId = "q1",
          rawQueryText = "balayage haircut",
          normalizedQueryText = Some("balayage haircut"),
          queryClass = QueryClass.ExactProductNameBrand,
          filters = Nil,
          categories = Nil,
          expectedResults = List(M9OfflineEvalExpectedResult(variantId.toString, None)),
          expectedNotes = Nil,
          negativeOutOfCatalog = false,
        )
      ),
    )

  private def syntheticDocument(id: MasterServiceOfferVariantId, serviceName: String, tag: String): VariantSearchDocument = {
    val masterServiceOfferId: MasterServiceOfferId = UUID.randomUUID()
    val masterLocationId: MasterLocationId = UUID.randomUUID()
    val masterId: MasterId = UUID.randomUUID()
    val serviceId: ServiceId = UUID.randomUUID()
    val categoryId: CategoryId = UUID.randomUUID()
    VariantSearchDocument(
      variantId = id,
      masterServiceOfferId = masterServiceOfferId,
      masterLocationId = masterLocationId,
      masterId = masterId,
      serviceId = serviceId,
      categoryId = categoryId,
      serviceName = serviceName,
      categoryName = "hair",
      masterName = "m18b-master",
      locationName = "m18b-location",
      address = "m18b-address",
      location = SearchGeoPoint(BigDecimal("53.57532"), BigDecimal("10.07672")),
      lat = BigDecimal("53.57532"),
      lon = BigDecimal("10.07672"),
      priceFrom = BigDecimal("30.0000"),
      priceTo = BigDecimal("45.0000"),
      durationMin = 60,
      enumAttributes = Map.empty,
      booleanAttributes = Map.empty,
      intAttributes = Map.empty,
      bigDecimalAttributes = Map.empty,
      allText = s"$serviceName $tag",
      serviceText = serviceName,
      attributeText = tag,
      providerText = "m18b-master m18b-location",
      locationText = "m18b-location m18b-address hair",
    )
  }

  private def unsafeRun[E, A](effect: IO[E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
