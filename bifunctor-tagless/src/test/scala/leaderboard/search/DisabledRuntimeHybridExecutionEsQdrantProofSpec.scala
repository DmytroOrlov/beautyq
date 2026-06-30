package leaderboard.search

import distage.{DIKey, Mode}
import izumi.distage.model.definition.Activation
import leaderboard.{LeaderboardTest, ProdTest}
import leaderboard.config.{ElasticsearchPortCfg, QdrantPortCfg}
import leaderboard.model.{MasterId, MasterLocationId, MasterServiceOfferId, MasterServiceOfferVariantId, QueryFailure, ServiceId}
import leaderboard.model.Category.CategoryId
import leaderboard.search.document.{InMemoryVariantSearchDocumentSnapshotProvider, VariantSearchDocument}
import leaderboard.search.dsl.{BeautySearchSpecV1, EmbeddingSpec, SearchGeoPoint, VectorDistance, VectorSearchSpec}
import leaderboard.search.elasticsearch.BeautyQElasticsearchInterpreterAdapter
import leaderboard.search.embedding.LlamaCppEmbeddingClient
import leaderboard.search.eval.*
import leaderboard.search.eval.M19IBeautyQComponentCombinationPolicyScaffold.ComponentCombinationPolicy
import leaderboard.search.eval.M20BControlledHybridServingOperationalControl.M20BOperationalControl
import leaderboard.search.eval.M20ControlledHybridServingSkeleton.M20HybridServingControl
import leaderboard.search.lexical.{LexicalDocumentBackend, LexicalDocumentHit}
import leaderboard.search.qdrant.{QdrantClient, QdrantCollectionReadinessConfig, QdrantCollectionReadinessInput, QdrantEmbeddingBenchmarkDefaultCompositionFactory, QdrantJsonInterpreter}
import zio.{IO, Runtime, Unsafe, ZIO}

import java.util.UUID

/**
 * T: disabled-runtime ES + Qdrant execution proof for the SAME BeautyQ query.
 *
 * AP1 is already cleared for the source-confirmed default ES route graph (see
 * [[BeautySearchRealEsRouteRegressionSpec]], proof scope `real_es_default_route_graph_non_empty`).
 * That proof does NOT prove runtime hybrid execution and does NOT prove Qdrant contribution. T adds
 * the smallest focused proof that real ES and real Qdrant can both execute at runtime for the same
 * BeautyQ query, strictly behind the disabled/internal [[M18DualEngineOfflineEvalRunner]] surface,
 * while leaving the default `/beauty-search` route entirely untouched.
 *
 * Scope `real_es_and_real_qdrant_execute_for_same_query`:
 *   - the ES leg executes against a real, repo-local Elasticsearch using the existing real-ES pattern
 *     ([[M18DualEngineOfflineEvalEsRealLegSpec]]);
 *   - the Qdrant leg executes against a real, repo-local Qdrant + real embedding endpoint using the
 *     existing real-Qdrant pattern ([[M18DualEngineOfflineEvalQdrantRealLegSpec]]);
 *   - both legs are fed the SAME dataset query (one query, one input/intent built once per leg by the
 *     shared [[M18EvalQueryInputBuilder.default]]);
 *   - ES and Qdrant candidate outputs are kept structurally separate (no fused accessor exists);
 *   - coarse per-leg latency evidence is captured separately for ES and Qdrant via the runner clock.
 *
 * Honesty: when the embedding endpoint or Qdrant is unavailable, the Qdrant leg is recorded as an
 * honest resource-gate (exact missing prerequisites, zero candidates, no fake rows) and the test is
 * CANCELLED — T does NOT clear the runtime ES+Qdrant proof in that case. No Qdrant candidates are
 * ever faked.
 *
 * Boundaries (asserted as data via the disabled M20B control surface): this proof does not call or
 * change the default `/beauty-search` route, assembles no final hybrid response, approves no route
 * switch, and introduces no fallback, score fusion, reranking, shadow/mirror traffic, or automatic
 * Qdrant supplement. Qdrant is evidence-producing only; it is not the default backend.
 */
final class DisabledRuntimeHybridExecutionEsQdrantProofSpec extends LeaderboardTest with ProdTest {
  override def config = super.config.copy(
    activation = super.config.activation ++ Activation(Mode -> Mode.Test),
    memoizationRoots = super.config.memoizationRoots + DIKey[ElasticsearchPortCfg] + DIKey[QdrantPortCfg],
  )

  private val spec = BeautySearchSpecV1.spec

  private val variantId: MasterServiceOfferVariantId      = UUID.randomUUID()
  private val otherVariantId: MasterServiceOfferVariantId = UUID.randomUUID()

  // The single shared query/input source. Both the ES leg and the Qdrant leg run over this exact
  // dataset query; the runner builds one UserSearchInput/ParsedSearchIntent per leg from it, so both
  // legs retrieve for the SAME query.
  private val sharedQueryId   = "q_shared_es_qdrant"
  private val sharedQueryText = "balayage haircut"

  private val dataset: M9OfflineEvalDataset =
    M9OfflineEvalDataset(
      evalDatasetId = EvalDatasetId("t-disabled-runtime-hybrid-dataset"),
      catalogSnapshotId = CatalogSnapshotId("t-disabled-runtime-hybrid-snapshot"),
      queries = List(
        M9OfflineEvalDatasetQuery(
          queryId = sharedQueryId,
          rawQueryText = sharedQueryText,
          normalizedQueryText = Some(sharedQueryText),
          queryClass = QueryClass.ExactProductNameBrand,
          filters = Nil,
          categories = Nil,
          expectedResults = List(M9OfflineEvalExpectedResult(variantId.toString, None)),
          expectedNotes = Nil,
          negativeOutOfCatalog = false,
        )
      ),
    )

  // A real monotonic clock so per-leg latency is measured (not faked) for each executed leg.
  private val legClock: M18OfflineEvalLegClock[IO] = new M18OfflineEvalLegClock[IO] {
    override def monotonicNanos: IO[Nothing, Long] = ZIO.succeed(System.nanoTime())
  }

  private val runner: M18DualEngineOfflineEvalRunner[IO, MasterServiceOfferVariantId] =
    new M18DualEngineOfflineEvalRunner[IO, MasterServiceOfferVariantId](
      inputBuilder = M18EvalQueryInputBuilder.default,
      renderId = _.toString,
      clock = Some(legClock),
    )

  "Disabled-runtime ES + Qdrant execution for the same BeautyQ query (scope real_es_and_real_qdrant_execute_for_same_query)" should {
    "execute real ES and real Qdrant for the SAME query behind the disabled controlled surface (keeping legs separate, with per-leg latency), or honestly resource-gate Qdrant — never faking candidates and never touching the default route" in {
      (esPortCfg: ElasticsearchPortCfg, qdrantPortCfg: QdrantPortCfg) =>
        // ---- The controlled/hybrid path is disabled/internal and is NOT the default route. ----
        // This is the M20B operational control over the disabled M20A skeleton: every dangerous flag
        // is fixed off, serving/activation is unapproved, and the default ES route is unchanged. The
        // M19I policy is consumed as evidence only; its honest content is proven by its own spec, so
        // here it is supplied with the offline/eval honesty flags it guarantees.
        val policy = ComponentCombinationPolicy(
          rows = Nil,
          offlineEvalOnly = true,
          notServingPolicy = true,
          doesNotApproveHybrid = true,
          qdrantDoesNotOwnFacets = true,
          qdrantDoesNotOwnInferredFilters = true,
        )
        val operationalControl =
          M20BOperationalControl.disabledByDefault(M20HybridServingControl.disabledByDefault(policy))
        val status = operationalControl.operatorStatus

        assert(operationalControl.servingControl.disabled, "controlled hybrid surface must be disabled by default")
        assert(operationalControl.effectiveServingDisabled, "effective serving must be disabled")
        assert(!operationalControl.servingApproved, "no serving approval may exist")
        assert(!operationalControl.qdrantProductionActivationApproved, "no Qdrant production activation approval may exist")
        assert(operationalControl.executesNoHybridServing, "no hybrid serving behaviour may be enabled")
        assert(operationalControl.consumesPolicyAsEvidenceOnly, "policy must be consumed as evidence only")
        assert(status.defaultBeautySearchRouteUnchanged, "default /beauty-search route must remain unchanged")
        assert(!status.fallbackEnabled, "no fallback may be introduced")
        assert(!status.scoreFusionEnabled, "no score fusion may be introduced")
        assert(!status.rerankingEnabled, "no reranking may be introduced")
        assert(!status.automaticQdrantSupplementEnabled, "no automatic Qdrant supplement may be introduced")
        assert(!status.routeSwitchEnabled, "no route switch may be introduced")

        // ---- Probe the real Qdrant + embedding resources honestly. ----
        val esClient        = new ElasticsearchTestClient(esPortCfg.host, esPortCfg.port)
        val qdrantClient    = new QdrantClient(qdrantPortCfg.host, qdrantPortCfg.port)
        val embeddingConfig = LlamaCppEmbeddingTestConfig.default
        val embeddingClient = LlamaCppEmbeddingTestConfig.client(embeddingConfig)

        val (embeddingProbe, qdrantProbe) = unsafeRun(
          for {
            embeddingResult <- embeddingClient.embed("t disabled-runtime es+qdrant probe").either
            qdrantResult    <- qdrantClient.collectionInfo("/collections").either
          } yield (embeddingResult, qdrantResult)
        )
        val embeddingConfigured = embeddingProbe.exists(_.nonEmpty)
        val qdrantConfigured    = qdrantProbe.isRight

        val indexName = s"${spec.variantDocument.indexName}_t_${UUID.randomUUID().toString.replace('-', '_')}"
        val testSpec  = spec.copy(variantDocument = spec.variantDocument.copy(indexName = indexName))

        (embeddingProbe, qdrantConfigured) match {
          case (Right(vector), true) if vector.nonEmpty =>
            // ---- Both real resources reachable: execute ES + Qdrant for the SAME query. ----
            val result = unsafeRun(runBothLegs(esClient, testSpec, qdrantClient, embeddingClient, vector.length))

            // ES leg: succeeded, candidate ids present, latency evidence present, attributed to ES.
            assert(result.esExecuted, s"expected ES leg to execute, got ${result.es}")
            assert(result.esCandidateRows.nonEmpty, "expected non-empty real ES candidate ids")
            assert(result.esCandidateRows.forall(_.backend == M18OfflineEvalBackend.Es))
            assert(result.esCandidateRows.exists(_.candidateId == variantId.toString), "ES must retrieve the seeded variant id")
            assertLatencyPresent(result.es, "ES")

            // Qdrant leg: succeeded, candidate ids present, latency evidence present, attributed to Qdrant.
            assert(result.qdrantExecuted, s"expected Qdrant leg to execute, got ${result.qdrant}")
            assert(result.qdrantCandidateRows.nonEmpty, "expected non-empty real Qdrant candidate ids")
            assert(result.qdrantCandidateRows.forall(_.backend == M18OfflineEvalBackend.Qdrant))
            assert(result.qdrantCandidateRows.exists(_.candidateId == variantId.toString), "Qdrant must retrieve the seeded variant id")
            assertLatencyPresent(result.qdrant, "Qdrant")

            // Same query for both legs; outputs remain structurally separate (no fusion/rerank/fallback).
            assert(result.esCandidateRows.forall(_.queryId == sharedQueryId), "ES rows must be for the shared query")
            assert(result.qdrantCandidateRows.forall(_.queryId == sharedQueryId), "Qdrant rows must be for the shared query")
            assert(result.separationViolations.isEmpty, "ES and Qdrant outputs must stay separate")
          // T CLEARED: real ES and real Qdrant both executed for the same query, behind the disabled
          // controlled surface, with separate outputs and separate per-leg latency. This does NOT
          // assemble a final hybrid response and does NOT approve a default route switch.

          case _ =>
            // ---- Qdrant resources unavailable: execute ES, honestly resource-gate Qdrant. ----
            val prerequisites = M18QdrantLegPrerequisites(
              realBackendOfflineEvalEnabled = true,
              embeddingClientConfigured = embeddingConfigured,
              qdrantClientConfigured = qdrantConfigured,
              collectionReadinessConfigured = true,
            )
            val result = unsafeRun(runEsLegWithGatedQdrant(esClient, testSpec, prerequisites))

            // ES still really executes for the query, with latency evidence.
            assert(result.esExecuted, s"expected ES leg to execute, got ${result.es}")
            assert(result.esCandidateRows.nonEmpty, "expected non-empty real ES candidate ids")
            assert(result.esCandidateRows.forall(_.backend == M18OfflineEvalBackend.Es))
            assert(result.esCandidateRows.exists(_.candidateId == variantId.toString), "ES must retrieve the seeded variant id")
            assertLatencyPresent(result.es, "ES")

            // Qdrant is honestly resource-gated: no execution, no fake candidates, exact missing prereqs.
            assert(!result.qdrantExecuted, "Qdrant leg must not execute when its real resource(s) are unavailable")
            assert(result.qdrantCandidateRows.isEmpty, "no fake Qdrant rows may be emitted")
            assert(result.separationViolations.isEmpty, "ES and Qdrant outputs must stay separate")
            val gateReason = result.qdrant match {
              case skipped: M18BackendLegOutcome.Skipped =>
                assert(skipped.skipKind == M18LegSkipKind.ResourceGated)
                assert(skipped.missingPrerequisites.nonEmpty)
                assert(embeddingConfigured || skipped.missingPrerequisites.exists(_.contains("embedding client")))
                assert(qdrantConfigured || skipped.missingPrerequisites.exists(_.contains("Qdrant search client")))
                skipped.reason
              case other =>
                fail(s"expected resource-gated Qdrant leg, got $other")
            }
            // T did NOT clear the runtime ES+Qdrant proof: ES executed but Qdrant was resource-gated.
            cancel(
              s"T did not clear runtime ES+Qdrant proof: real ES executed for the shared query but the " +
                s"Qdrant leg was honestly resource-gated (no candidates faked). $gateReason"
            )
        }
    }
  }

  /** Build the real-ES lexical backend over the prepared index, using the existing real-ES pattern. */
  private def esBackendFor(
    testSpec: leaderboard.search.dsl.BeautySearchSpec,
    client: ElasticsearchTestClient,
  ): LexicalDocumentBackend[IO, MasterServiceOfferVariantId] =
    new LexicalDocumentBackend[IO, MasterServiceOfferVariantId] {
      override def documentHits(
        input: UserSearchInput,
        intent: ParsedSearchIntent,
      ): IO[QueryFailure, List[LexicalDocumentHit[MasterServiceOfferVariantId]]] =
        for {
          requestJson <- ZIO.fromEither(BeautyQElasticsearchInterpreterAdapter.request(testSpec, input, intent))
          rawResponse <- client.postJson(s"/${testSpec.variantDocument.indexName}/_search", requestJson)
          hits        <- ZIO.fromEither(BeautyQElasticsearchInterpreterAdapter.documentHits(rawResponse))
        } yield hits
    }

  /** Prepare the real ES index and seed the shared documents. */
  private def prepareEsIndex(
    testSpec: leaderboard.search.dsl.BeautySearchSpec,
    client: ElasticsearchTestClient,
  ): IO[QueryFailure, Unit] =
    for {
      _ <- client.putJson(s"/${testSpec.variantDocument.indexName}", BeautyQElasticsearchInterpreterAdapter.mapping(testSpec))
      _ <- client.postNdjson(
             s"/${testSpec.variantDocument.indexName}/_bulk",
             BeautyQElasticsearchInterpreterAdapter.bulkPayload(testSpec, sharedDocuments),
           )
      _ <- client.post(s"/${testSpec.variantDocument.indexName}/_refresh")
    } yield ()

  /** Run BOTH real legs (real ES + real Qdrant) over the same dataset query in one runner pass. */
  private def runBothLegs(
    esClient: ElasticsearchTestClient,
    testSpec: leaderboard.search.dsl.BeautySearchSpec,
    qdrantClient: QdrantClient,
    embeddingClient: LlamaCppEmbeddingClient,
    vectorDimension: Int,
  ): IO[QueryFailure, M18DualEngineOfflineEvalResult] = {
    val embeddingSpec = EmbeddingSpec[VariantSearchDocument](
      vectorName = "llama-cpp-embedding",
      modelName = "t-disabled-runtime-hybrid",
      dimension = vectorDimension,
      distance = VectorDistance.Cosine,
      sourceTextFields = List(leaderboard.search.document.BeautyQVariantSearchDocumentSchema.Fields.serviceText, leaderboard.search.document.BeautyQVariantSearchDocumentSchema.Fields.attributeText, leaderboard.search.document.BeautyQVariantSearchDocumentSchema.Fields.allText, leaderboard.search.document.BeautyQVariantSearchDocumentSchema.Fields.categoryName),
    )
    val readinessConfig = QdrantCollectionReadinessConfig.derive(
      QdrantCollectionReadinessInput(
        domainName = "beautyq",
        searchSpecVersion = "v1",
        purpose = s"t-disabled-runtime-hybrid-${UUID.randomUUID().toString.replace('-', '_')}",
        embeddingSpec = embeddingSpec,
        vectorSearchSpec = VectorSearchSpec(
          collectionName = "placeholder",
          vectorName = "llama-cpp-embedding",
          topK = 10,
          scoreThreshold = None,
        ),
      )
    )
    val collectionPath   = s"/collections/${readinessConfig.collectionName}"
    val snapshotProvider = new InMemoryVariantSearchDocumentSnapshotProvider[IO](sharedDocuments)
    val compositionFactory = new QdrantEmbeddingBenchmarkDefaultCompositionFactory(qdrantClient)

    (
      for {
        _           <- prepareEsIndex(testSpec, esClient)
        composition <- compositionFactory.build(readinessConfig, embeddingClient, snapshotProvider, embeddingSpec)
        createJson   = QdrantJsonInterpreter.createCollectionJson(readinessConfig.vectorSearchSpec, embeddingSpec)
        _           <- qdrantClient.createCollection(collectionPath, createJson)
        _           <- composition.indexSnapshot()
        result <- runner.run(
                    dataset = dataset,
                    esLeg = M18EsLegInput.Connected(esBackendFor(testSpec, esClient), lookup = None),
                    qdrantLeg = M18QdrantLegInput.Connected(composition.semanticBackend, lookup = None),
                  )
      } yield result
    ).ensuring(qdrantClient.deleteCollection(collectionPath).either.unit)
      .ensuring(esClient.deleteIndex(testSpec.variantDocument.indexName).either.unit)
  }

  /** Run the real ES leg with the Qdrant leg honestly resource-gated (no Qdrant connection). */
  private def runEsLegWithGatedQdrant(
    esClient: ElasticsearchTestClient,
    testSpec: leaderboard.search.dsl.BeautySearchSpec,
    prerequisites: M18QdrantLegPrerequisites,
  ): IO[QueryFailure, M18DualEngineOfflineEvalResult] = {
    val qdrantLeg = M18QdrantLegInput.fromPrerequisites[IO, MasterServiceOfferVariantId](prerequisites) {
      fail("must not connect Qdrant: this branch proves the honest resource-gated path")
    }
    (
      for {
        _ <- prepareEsIndex(testSpec, esClient)
        result <- runner.run(
                    dataset = dataset,
                    esLeg = M18EsLegInput.Connected(esBackendFor(testSpec, esClient), lookup = None),
                    qdrantLeg = qdrantLeg,
                  )
      } yield result
    ).ensuring(esClient.deleteIndex(testSpec.variantDocument.indexName).either.unit)
  }

  /** Assert coarse latency evidence is present for an executed leg, without unsafe extraction. */
  private def assertLatencyPresent(outcome: M18BackendLegOutcome, legName: String): Unit =
    outcome match {
      case executed: M18BackendLegOutcome.Executed =>
        assert(executed.measuredLatencies.nonEmpty, s"$legName leg must carry coarse per-leg latency evidence")
        assert(executed.measuredLatencies.forall(_ >= 0L), s"$legName latency evidence must be non-negative")
        ()
      case other =>
        fail(s"expected executed $legName leg with latency evidence, got $other")
    }

  private val sharedDocuments: List[VariantSearchDocument] =
    List(
      syntheticDocument(variantId, sharedQueryText, "service_name"),
      syntheticDocument(otherVariantId, "manicure gel polish", "other"),
    )

  private def syntheticDocument(id: MasterServiceOfferVariantId, serviceName: String, tag: String): VariantSearchDocument = {
    val masterServiceOfferId: MasterServiceOfferId = UUID.randomUUID()
    val masterLocationId: MasterLocationId         = UUID.randomUUID()
    val masterId: MasterId                         = UUID.randomUUID()
    val serviceId: ServiceId                       = UUID.randomUUID()
    val categoryId: CategoryId                     = UUID.randomUUID()
    VariantSearchDocument(
      variantId = id,
      masterServiceOfferId = masterServiceOfferId,
      masterLocationId = masterLocationId,
      masterId = masterId,
      serviceId = serviceId,
      categoryId = categoryId,
      serviceName = serviceName,
      categoryName = "hair",
      masterName = "t-master",
      locationName = "t-location",
      address = "t-address",
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
      providerText = "t-master t-location",
      locationText = "t-location t-address hair",
    )
  }

  private def unsafeRun[E, A](effect: IO[E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
