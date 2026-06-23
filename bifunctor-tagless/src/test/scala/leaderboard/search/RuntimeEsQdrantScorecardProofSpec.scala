package leaderboard.search

import distage.{DIKey, Mode}
import izumi.distage.model.definition.Activation
import leaderboard.{LeaderboardTest, ProdTest}
import leaderboard.config.{ElasticsearchPortCfg, QdrantPortCfg}
import leaderboard.model.{MasterId, MasterLocationId, MasterServiceOfferId, MasterServiceOfferVariantId, QueryFailure, ServiceId}
import leaderboard.model.Category.CategoryId
import leaderboard.search.document.{InMemoryVariantSearchDocumentSnapshotProvider, VariantSearchDocument}
import leaderboard.search.dsl.{BeautySearchSpecV1, EmbeddingSpec, SearchGeoPoint, VectorDistance, VectorSearchSpec}
import leaderboard.search.elasticsearch.{ElasticsearchIngestionInterpreter, ElasticsearchMappingInterpreter, ElasticsearchSearchRequestInterpreter, ElasticsearchSearchResponseInterpreter}
import leaderboard.search.embedding.{LlamaCppEmbeddingClient, LlamaCppEmbeddingClientConfig}
import leaderboard.search.eval.*
import leaderboard.search.eval.M19IBeautyQComponentCombinationPolicyScaffold.ComponentCombinationPolicy
import leaderboard.search.eval.M20BControlledHybridServingOperationalControl.M20BOperationalControl
import leaderboard.search.eval.M20ControlledHybridServingSkeleton.M20HybridServingControl
import leaderboard.search.lexical.{LexicalDocumentBackend, LexicalDocumentHit}
import leaderboard.search.qdrant.{QdrantClient, QdrantCollectionReadinessConfig, QdrantCollectionReadinessInput, QdrantEmbeddingBenchmarkDefaultCompositionFactory, QdrantJsonInterpreter}
import zio.{IO, Runtime, Unsafe, ZIO}

import java.util.UUID

/**
 * W: measure-first ES-vs-Qdrant runtime scorecard from real candidate evidence.
 *
 * T ([[DisabledRuntimeHybridExecutionEsQdrantProofSpec]], scope
 * `real_es_and_real_qdrant_execute_for_same_query`) already proved that real ES and real Qdrant both
 * execute at runtime for the SAME BeautyQ query behind the disabled/internal
 * [[M18DualEngineOfflineEvalRunner]] surface, keeping the two legs structurally separate and capturing
 * per-leg latency. T deliberately does NOT measure whether Qdrant helps or adds noise.
 *
 * W adds the smallest focused proof that turns that real ES/Qdrant candidate evidence into a measured
 * quality scorecard, by feeding the real [[M18DualEngineOfflineEvalResult]] produced by a same-query
 * run straight into the existing pure [[M19DualEngineOfflineEvalMetrics]]. No parallel metric is
 * invented: M19 already computes candidate id sets, overlap, expected-aware Qdrant complement, Qdrant
 * noise, per-backend lookup counts, and per-backend latency availability over the M18 result.
 *
 * Scope `runtime_es_qdrant_scorecard_from_real_candidate_evidence`:
 *   - both legs execute against real, repo-local ES + Qdrant + embedding endpoint, exactly as in T;
 *   - the scorecard is computed from the REAL executed candidate rows (no synthetic M18 rows);
 *   - the scorecard reports ES candidate ids/count, Qdrant candidate ids/count, ES ∩ Qdrant overlap,
 *     expected-aware Qdrant complement over ES, Qdrant noise (only because this dataset carries
 *     expectations), per-backend lookup status, and per-leg ES/Qdrant latency availability.
 *
 * Honest fixture limits (encoded as assertions, NOT hidden):
 *   - this fixture seeds exactly one expected variant, and ES already retrieves it, so the
 *     expected-aware Qdrant COMPLEMENT over ES is measured as zero for this fixture — W makes no broad
 *     "Qdrant improves recall" claim;
 *   - Qdrant NOISE is measured only from the dataset's own expectations (available here), and is
 *     cross-checked against the real Qdrant rows, not asserted to a magic constant;
 *   - lookup is not wired for either leg (lookup = None in the T pattern), so lookup status is
 *     honestly `lookup_not_evaluated` rather than a faked hydration result.
 *
 * Honesty gate: when the embedding endpoint or Qdrant is unavailable, no Qdrant candidates are faked;
 * ES still executes and the test is CANCELLED (W is resource-gated, not cleared). No Qdrant evidence
 * is ever invented.
 *
 * Boundaries (asserted as data via the disabled M20B control surface): the scorecard is measurement
 * evidence only. W assembles no final hybrid response, fuses no scores, reranks nothing, adds no
 * fallback/shadow/mirror traffic, approves no route switch or Qdrant supplement, and never touches the
 * default `/beauty-search` route.
 */
final class RuntimeEsQdrantScorecardProofSpec extends LeaderboardTest with ProdTest {
  override def config = super.config.copy(
    activation = super.config.activation ++ Activation(Mode -> Mode.Test),
    memoizationRoots = super.config.memoizationRoots + DIKey[ElasticsearchPortCfg] + DIKey[QdrantPortCfg],
  )

  private val spec = BeautySearchSpecV1.spec

  private val variantId: MasterServiceOfferVariantId      = UUID.randomUUID()
  private val otherVariantId: MasterServiceOfferVariantId = UUID.randomUUID()

  // The single shared query/input source. Both ES and Qdrant retrieve for this exact dataset query;
  // the runner builds one UserSearchInput/ParsedSearchIntent per leg from it.
  private val sharedQueryId   = "q_shared_es_qdrant_scorecard"
  private val sharedQueryText = "balayage haircut"

  // Expectations are present (the seeded variant is the one expected result), so the expected-aware
  // complement and noise signals are meaningful for this fixture.
  private val dataset: M9OfflineEvalDataset =
    M9OfflineEvalDataset(
      evalDatasetId = EvalDatasetId("w-runtime-scorecard-dataset"),
      catalogSnapshotId = CatalogSnapshotId("w-runtime-scorecard-snapshot"),
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

  "Runtime ES-vs-Qdrant scorecard from real candidate evidence (scope runtime_es_qdrant_scorecard_from_real_candidate_evidence)" should {
    "compute the ES/Qdrant scorecard (ids, overlap, expected-aware complement, noise, lookup, per-leg latency) from REAL executed ES + Qdrant candidate rows for the same query, or honestly resource-gate Qdrant — never faking candidates, never assembling a hybrid response, never touching the default route" in {
      (esPortCfg: ElasticsearchPortCfg, qdrantPortCfg: QdrantPortCfg) =>
        // ---- The controlled/hybrid path is disabled/internal and is NOT the default route. ----
        // Same disabled M20B operational control as T: every dangerous flag fixed off, no serving
        // approval, default ES route unchanged. The scorecard below is measurement evidence only.
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

        assert(operationalControl.effectiveServingDisabled, "effective serving must be disabled")
        assert(!operationalControl.servingApproved, "no serving approval may exist")
        assert(operationalControl.executesNoHybridServing, "no hybrid serving behaviour may be enabled")
        assert(operationalControl.consumesPolicyAsEvidenceOnly, "policy must be consumed as evidence only")
        assert(status.defaultBeautySearchRouteUnchanged, "default /beauty-search route must remain unchanged")
        assert(!status.fallbackEnabled, "no fallback may be introduced")
        assert(!status.scoreFusionEnabled, "no score fusion may be introduced")
        assert(!status.rerankingEnabled, "no reranking may be introduced")
        assert(!status.automaticQdrantSupplementEnabled, "no automatic Qdrant supplement may be introduced")
        assert(!status.routeSwitchEnabled, "no route switch may be introduced")

        // ---- Probe the real Qdrant + embedding resources honestly (T pattern). ----
        val esClient          = new ElasticsearchTestClient(esPortCfg.host, esPortCfg.port)
        val qdrantClient      = new QdrantClient(qdrantPortCfg.host, qdrantPortCfg.port)
        val embeddingEndpoint = sys.env.getOrElse("M18_QDRANT_EMBEDDING_ENDPOINT", "http://localhost:8081")
        val embeddingClient   = new LlamaCppEmbeddingClient(LlamaCppEmbeddingClientConfig(baseUrl = embeddingEndpoint))

        val (embeddingProbe, qdrantProbe) = unsafeRun(
          for {
            embeddingResult <- embeddingClient.embed("w runtime es+qdrant scorecard probe").either
            qdrantResult    <- qdrantClient.collectionInfo("/collections").either
          } yield (embeddingResult, qdrantResult)
        )
        val embeddingConfigured = embeddingProbe.exists(_.nonEmpty)
        val qdrantConfigured    = qdrantProbe.isRight

        val indexName = s"${spec.variantDocument.indexName}_w_${UUID.randomUUID().toString.replace('-', '_')}"
        val testSpec  = spec.copy(variantDocument = spec.variantDocument.copy(indexName = indexName))

        (embeddingProbe, qdrantConfigured) match {
          case (Right(vector), true) if vector.nonEmpty =>
            // ---- Both real resources reachable: execute ES + Qdrant for the SAME query. ----
            val result = unsafeRun(runBothLegs(esClient, testSpec, qdrantClient, embeddingClient, vector.length))

            // Both legs must really have executed before any scorecard claim is made.
            assert(result.esExecuted, s"expected ES leg to execute, got ${result.es}")
            assert(result.qdrantExecuted, s"expected Qdrant leg to execute, got ${result.qdrant}")
            assert(result.esCandidateRows.nonEmpty, "expected non-empty real ES candidate rows")
            assert(result.qdrantCandidateRows.nonEmpty, "expected non-empty real Qdrant candidate rows")
            assert(result.separationViolations.isEmpty, "ES and Qdrant outputs must stay separate (no fusion)")

            // ---- The scorecard: computed by the existing pure M19 metrics over the REAL result. ----
            val scorecard = onlyQueryMetrics(M19DualEngineOfflineEvalMetrics.queryMetrics(result))
            val aggregate = M19DualEngineOfflineEvalMetrics.aggregate(List(scorecard))
            assert(scorecard.queryId == sharedQueryId, "scorecard must be for the shared query")

            // ES candidate ids/count — from the real executed ES rows.
            val esIds = scorecard.candidateIds.esCandidateIds
            assert(esIds.nonEmpty, "scorecard must record real ES candidate ids")
            assert(esIds.contains(variantId.toString), "ES must retrieve the seeded expected variant id")
            assert(esIds.size == result.esCandidateRows.size, "ES candidate count must match the real ES rows")

            // Qdrant candidate ids/count — from the real executed Qdrant rows.
            val qdrantIds = scorecard.candidateIds.qdrantCandidateIds
            assert(qdrantIds.nonEmpty, "scorecard must record real Qdrant candidate ids")
            assert(qdrantIds.contains(variantId.toString), "Qdrant must retrieve the seeded expected variant id")
            assert(qdrantIds.size == result.qdrantCandidateRows.size, "Qdrant candidate count must match the real Qdrant rows")

            // ES ∩ Qdrant overlap is measured and consistent with the two real id sets.
            assert(scorecard.overlapCount == esIds.toSet.intersect(qdrantIds.toSet).size, "overlap must equal the real ES ∩ Qdrant id intersection")
            assert(scorecard.overlapCount >= 1, "ES and Qdrant must overlap on at least the seeded variant for this fixture")

            // Expectations are available for this dataset, so complement/noise are meaningful.
            assert(scorecard.expectationsAvailable, "this dataset carries expectations, so expected-aware signals are meaningful")

            // Qdrant complement over ES: honest ZERO for this fixture. ES already retrieves the only
            // expected variant, so Qdrant supplies no NEW expected id over ES. No broad recall claim.
            assert(
              scorecard.qdrantComplementCount == 0,
              s"this fixture's single expected variant is already retrieved by ES, so Qdrant complement over ES is zero, got ${scorecard.qdrantComplementCount}",
            )

            // Qdrant noise measured ONLY from the dataset's own expectations, cross-checked against
            // the real Qdrant rows (not asserted to a magic constant).
            val realQdrantNoiseRows = result.qdrantCandidateRows.count(_.expectedMatch == M18ExpectedMatch.NotExpected)
            assert(scorecard.qdrantNoiseCount == realQdrantNoiseRows, "Qdrant noise must equal the real not-expected Qdrant rows")
            assert(scorecard.qdrantNoiseCount >= 0, "Qdrant noise must be a real count")

            // Lookup status: lookup is intentionally not wired (T pattern), so the honest status is
            // lookup_not_evaluated for every real row of both backends — no faked hydration.
            val esLookup     = lookupCountsFor(scorecard.lookupByBackend, M18OfflineEvalBackend.Es)
            val qdrantLookup = lookupCountsFor(scorecard.lookupByBackend, M18OfflineEvalBackend.Qdrant)
            assert(esLookup.lookupNotEvaluatedCount == result.esCandidateRows.size, "ES lookup is not wired: status must be lookup_not_evaluated")
            assert(esLookup.evaluatedCount == 0, "no ES lookup may be evaluated when no lookup is wired")
            assert(qdrantLookup.lookupNotEvaluatedCount == result.qdrantCandidateRows.size, "Qdrant lookup is not wired: status must be lookup_not_evaluated")
            assert(qdrantLookup.evaluatedCount == 0, "no Qdrant lookup may be evaluated when no lookup is wired")

            // Per-leg latency evidence is present for BOTH executed legs (real clock was attached).
            val esLatency     = latencyFor(scorecard.latencyByBackend, M18OfflineEvalBackend.Es)
            val qdrantLatency = latencyFor(scorecard.latencyByBackend, M18OfflineEvalBackend.Qdrant)
            assert(esLatency.availability == M19LatencyAvailability.Present, "ES latency evidence must be present for the executed ES leg")
            assert(qdrantLatency.availability == M19LatencyAvailability.Present, "Qdrant latency evidence must be present for the executed Qdrant leg")

            // The aggregate is a faithful roll-up of this single-query scorecard — still measurement
            // evidence, not a served/assembled response.
            assert(aggregate.queryCount == 1, "scorecard aggregate must cover exactly the one measured query")
            assert(aggregate.overlapCount == scorecard.overlapCount)
            assert(aggregate.qdrantComplementCount == scorecard.qdrantComplementCount)
            assert(aggregate.qdrantNoiseCount == scorecard.qdrantNoiseCount)
          // W CLEARED: a measured ES/Qdrant scorecard was computed from REAL executed candidate
          // evidence for the same query. It records overlap, an honest zero complement for this
          // fixture, expectation-bounded noise, lookup-not-evaluated status, and per-leg latency. It
          // assembles NO hybrid response and approves NO default route switch.

          case _ =>
            // ---- Qdrant resources unavailable: execute ES, honestly resource-gate Qdrant. ----
            val prerequisites = M18QdrantLegPrerequisites(
              realBackendOfflineEvalEnabled = true,
              embeddingClientConfigured = embeddingConfigured,
              qdrantClientConfigured = qdrantConfigured,
              collectionReadinessConfigured = true,
            )
            val result = unsafeRun(runEsLegWithGatedQdrant(esClient, testSpec, prerequisites))

            // ES still really executes, and its half of the scorecard is real.
            assert(result.esExecuted, s"expected ES leg to execute, got ${result.es}")
            assert(result.esCandidateRows.nonEmpty, "expected non-empty real ES candidate rows")
            val scorecard = onlyQueryMetrics(M19DualEngineOfflineEvalMetrics.queryMetrics(result))
            assert(scorecard.candidateIds.esCandidateIds.contains(variantId.toString), "ES must retrieve the seeded variant id")

            // No Qdrant candidate evidence exists, so the scorecard carries no Qdrant ids and the
            // Qdrant latency is honestly NotExecuted (distinct from a measured-but-absent clock).
            assert(result.qdrantCandidateRows.isEmpty, "no fake Qdrant rows may be emitted")
            assert(scorecard.candidateIds.qdrantCandidateIds.isEmpty, "no Qdrant ids may appear without real Qdrant evidence")
            assert(scorecard.overlapCount == 0, "no overlap is measurable without real Qdrant evidence")
            val qdrantLatency = latencyFor(scorecard.latencyByBackend, M18OfflineEvalBackend.Qdrant)
            assert(qdrantLatency.availability == M19LatencyAvailability.NotExecuted, "Qdrant latency must be NotExecuted when its resources are unavailable")

            val gateReason = result.qdrant match {
              case skipped: M18BackendLegOutcome.Skipped =>
                assert(skipped.skipKind == M18LegSkipKind.ResourceGated)
                assert(skipped.missingPrerequisites.nonEmpty)
                skipped.reason
              case other =>
                fail(s"expected resource-gated Qdrant leg, got $other")
            }
            // W did NOT clear the runtime scorecard proof: real Qdrant candidate evidence was absent.
            cancel(
              s"W did not clear the runtime ES/Qdrant scorecard proof: real ES candidate evidence was " +
                s"measured for the shared query but the Qdrant leg was honestly resource-gated (no " +
                s"candidates faked), so no ES-vs-Qdrant scorecard could be computed. $gateReason"
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
          requestJson <- ZIO.fromEither(ElasticsearchSearchRequestInterpreter.request(testSpec, input, intent))
          rawResponse <- client.postJson(s"/${testSpec.variantDocument.indexName}/_search", requestJson)
          hits        <- ZIO.fromEither(ElasticsearchSearchResponseInterpreter.documentHits(rawResponse))
        } yield hits
    }

  /** Prepare the real ES index and seed the shared documents. */
  private def prepareEsIndex(
    testSpec: leaderboard.search.dsl.BeautySearchSpec,
    client: ElasticsearchTestClient,
  ): IO[QueryFailure, Unit] =
    for {
      _ <- client.putJson(s"/${testSpec.variantDocument.indexName}", ElasticsearchMappingInterpreter.mapping(testSpec))
      _ <- client.postNdjson(
             s"/${testSpec.variantDocument.indexName}/_bulk",
             ElasticsearchIngestionInterpreter.bulkPayload(testSpec, sharedDocuments),
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
      modelName = "w-runtime-scorecard",
      dimension = vectorDimension,
      distance = VectorDistance.Cosine,
      sourceTextFieldPaths = List("serviceText", "attributeText", "allText", "categoryName"),
    )
    val readinessConfig = QdrantCollectionReadinessConfig.derive(
      QdrantCollectionReadinessInput(
        domainName = "beautyq",
        searchSpecVersion = "v1",
        purpose = s"w-runtime-scorecard-${UUID.randomUUID().toString.replace('-', '_')}",
        embeddingSpec = embeddingSpec,
        vectorSearchSpec = VectorSearchSpec(
          collectionName = "placeholder",
          vectorName = "llama-cpp-embedding",
          topK = 10,
          scoreThreshold = None,
        ),
      )
    )
    val collectionPath     = s"/collections/${readinessConfig.collectionName}"
    val snapshotProvider   = new InMemoryVariantSearchDocumentSnapshotProvider[IO](sharedDocuments)
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
      sys.error("must not connect Qdrant: this branch proves the honest resource-gated path")
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

  /** Take the single expected per-query scorecard without any unsafe extraction. */
  private def onlyQueryMetrics(perQuery: List[M19QueryMetrics]): M19QueryMetrics =
    perQuery match {
      case only :: Nil => only
      case other       => fail(s"expected exactly one query scorecard, got ${other.size}: $other")
    }

  private def lookupCountsFor(
    counts: List[M19BackendLookupCounts],
    backend: M18OfflineEvalBackend,
  ): M19BackendLookupCounts =
    counts.find(_.backend == backend) match {
      case Some(found) => found
      case None        => fail(s"expected lookup counts for backend $backend, got $counts")
    }

  private def latencyFor(
    latencies: List[M19BackendLatency],
    backend: M18OfflineEvalBackend,
  ): M19BackendLatency =
    latencies.find(_.backend == backend) match {
      case Some(found) => found
      case None        => fail(s"expected latency for backend $backend, got $latencies")
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
      masterName = "w-master",
      locationName = "w-location",
      address = "w-address",
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
      providerText = "w-master w-location",
      locationText = "w-location w-address hair",
    )
  }

  private def unsafeRun[E, A](effect: IO[E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
