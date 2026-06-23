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
 * X: extend the measure-first ES-vs-Qdrant runtime scorecard (W) to a small, source-confirmed query
 * set with meaningful query classes.
 *
 * W ([[RuntimeEsQdrantScorecardProofSpec]] before this change) measured a single shared query and
 * showed ES already worked, Qdrant overlapped, Qdrant complement was zero, and one Qdrant noise row.
 * That single-query fixture could not begin to answer where ES works, where Qdrant adds useful
 * semantic complement, where Qdrant adds noise, and where Qdrant should stay silent.
 *
 * X adds the smallest meaningful query set (three source-confirmed [[QueryClass]] roles) over the same
 * real-ES + real-Qdrant execution surface, still feeding the real [[M18DualEngineOfflineEvalResult]]
 * into the existing pure [[M19DualEngineOfflineEvalMetrics]]. No metric is invented, no candidate is
 * faked, no hybrid response is assembled, and the default `/beauty-search` route is never touched.
 *
 * Query set (scope runtime_es_qdrant_scorecard_meaningful_query_set):
 *   1. `lexical_exact_or_easy` ([[QueryClass.ExactProductNameBrand]]) — query text is the seeded
 *      service name. ES must retrieve the expected variant; Qdrant overlaps; expected-aware complement
 *      is zero; Qdrant noise is measured.
 *   2. `semantic_complement_candidate` ([[QueryClass.SemanticDescriptive]]) — query text shares NO
 *      lexical token with the expected variant (so the `operator=And` ES `multi_match` retrieves
 *      nothing for it) but is in the same hair-colouring domain. Qdrant's candidate set therefore
 *      carries the expected variant that ES missed, so the expected-aware Qdrant complement is
 *      measured POSITIVE here.
 *   3. `hard_negative_or_should_stay_silent` ([[QueryClass.NegativeOutOfCatalog]]) — a non-beauty
 *      query whose only "expected" answer is an explicitly out-of-catalog sentinel id. ES retrieves
 *      nothing; Qdrant should stay silent but cannot, so every Qdrant candidate is honest noise.
 *
 * Honest fixture limit (encoded as assertions, NOT hidden — this is why X is only PARTIALLY cleared):
 *   the seeded collection is tiny and the Qdrant search uses `limit=topK` with no score threshold, so
 *   Qdrant returns the ENTIRE seeded collection for every query (asserted: `qdrantIds == seeded ids`).
 *   Consequently:
 *     - the positive semantic complement in query 2 is a RECALL-FLOOR artifact of Qdrant returning the
 *       whole small collection, NOT proof of genuine semantic ranking quality;
 *     - Qdrant cannot "stay silent" on the hard negative (query 3): it returns pure noise.
 *   X therefore measures the four target behaviours but does NOT prove broad Qdrant semantic quality;
 *   it records that as a measured gap.
 *
 * Honesty gate: when the embedding endpoint or Qdrant is unavailable, no Qdrant candidates are faked;
 * ES still executes for every query, Qdrant rows are empty, Qdrant latency is NotExecuted, and the
 * test is CANCELLED (X is resource-gated, not cleared).
 *
 * Boundaries (asserted as data via the disabled M20B control surface): the scorecard is measurement
 * evidence only. X assembles no final hybrid response, fuses no scores, reranks nothing, adds no
 * fallback/shadow/mirror traffic, approves no route switch or Qdrant supplement, and never touches the
 * default `/beauty-search` route.
 */
final class RuntimeEsQdrantScorecardProofSpec extends LeaderboardTest with ProdTest {
  override def config = super.config.copy(
    activation = super.config.activation ++ Activation(Mode -> Mode.Test),
    memoizationRoots = super.config.memoizationRoots + DIKey[ElasticsearchPortCfg] + DIKey[QdrantPortCfg],
  )

  private val spec = BeautySearchSpecV1.spec

  // The expected hair-colouring variant (balayage). Both legs may retrieve it.
  private val variantId: MasterServiceOfferVariantId = UUID.randomUUID()
  // A second, unrelated seeded variant (manicure). It is the in-catalog Qdrant noise candidate.
  private val otherVariantId: MasterServiceOfferVariantId = UUID.randomUUID()
  // An explicitly OUT-OF-CATALOG sentinel: the only "right" answer for the hard-negative query. It is
  // never seeded, so every real catalog candidate Qdrant returns for that query is honest noise.
  private val outOfCatalogId: MasterServiceOfferVariantId = UUID.randomUUID()

  // The full seeded catalog id set. Because Qdrant searches with limit=topK and no score threshold
  // over this tiny collection, it returns exactly these ids for EVERY query (asserted below).
  private val seededVariantIds: Set[String] = Set(variantId.toString, otherVariantId.toString)

  // ---- Query 1: lexical_exact_or_easy. Query text is the seeded service name; ES retrieves it. ----
  private val lexicalQueryId   = "q_lexical_exact_balayage"
  private val lexicalQueryText = "balayage haircut"

  // ---- Query 2: semantic_complement_candidate. Same hair-colouring domain, but NO lexical token is
  // shared with the seeded variant's searchable text (serviceText/allText/attributeText/providerText/
  // locationText), so the `operator=And` ES multi_match retrieves nothing for it. ----
  private val semanticQueryId   = "q_semantic_complement_blonde"
  private val semanticQueryText = "blonde color highlights toning treatment"

  // ---- Query 3: hard_negative_or_should_stay_silent. Non-beauty query; the only expected answer is
  // the out-of-catalog sentinel, so Qdrant should stay silent but cannot over this tiny collection. ----
  private val hardNegativeQueryId   = "q_hard_negative_diesel"
  private val hardNegativeQueryText = "diesel engine timing belt replacement"

  private val dataset: M9OfflineEvalDataset =
    M9OfflineEvalDataset(
      evalDatasetId = EvalDatasetId("x-runtime-scorecard-dataset"),
      catalogSnapshotId = CatalogSnapshotId("x-runtime-scorecard-snapshot"),
      queries = List(
        M9OfflineEvalDatasetQuery(
          queryId = lexicalQueryId,
          rawQueryText = lexicalQueryText,
          normalizedQueryText = Some(lexicalQueryText),
          queryClass = QueryClass.ExactProductNameBrand,
          filters = Nil,
          categories = Nil,
          expectedResults = List(M9OfflineEvalExpectedResult(variantId.toString, None)),
          expectedNotes = Nil,
          negativeOutOfCatalog = false,
        ),
        M9OfflineEvalDatasetQuery(
          queryId = semanticQueryId,
          rawQueryText = semanticQueryText,
          normalizedQueryText = Some(semanticQueryText),
          queryClass = QueryClass.SemanticDescriptive,
          filters = Nil,
          categories = Nil,
          expectedResults = List(M9OfflineEvalExpectedResult(variantId.toString, None)),
          expectedNotes = Nil,
          negativeOutOfCatalog = false,
        ),
        M9OfflineEvalDatasetQuery(
          queryId = hardNegativeQueryId,
          rawQueryText = hardNegativeQueryText,
          normalizedQueryText = Some(hardNegativeQueryText),
          queryClass = QueryClass.NegativeOutOfCatalog,
          filters = Nil,
          categories = Nil,
          // The ideal answer is explicitly out of catalog: expectations are available (so noise is
          // measured), yet no seeded variant can ever match it.
          expectedResults = List(M9OfflineEvalExpectedResult(outOfCatalogId.toString, None)),
          expectedNotes = Nil,
          negativeOutOfCatalog = true,
        ),
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

  "Runtime ES-vs-Qdrant scorecard over a meaningful query set (scope runtime_es_qdrant_scorecard_meaningful_query_set)" should {
    "compute a per-query ES/Qdrant scorecard (ids, overlap, expected-aware complement, noise, lookup, per-leg latency) over a lexical-exact, a semantic-complement, and a hard-negative query from REAL executed ES + Qdrant candidate rows, or honestly resource-gate Qdrant — never faking candidates, never assembling a hybrid response, never touching the default route" in {
      (esPortCfg: ElasticsearchPortCfg, qdrantPortCfg: QdrantPortCfg) =>
        // ---- The controlled/hybrid path is disabled/internal and is NOT the default route. ----
        // Same disabled M20B operational control as T/W: every dangerous flag fixed off, no serving
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

        // ---- Probe the real Qdrant + embedding resources honestly (T/W pattern). ----
        val esClient          = new ElasticsearchTestClient(esPortCfg.host, esPortCfg.port)
        val qdrantClient      = new QdrantClient(qdrantPortCfg.host, qdrantPortCfg.port)
        val embeddingEndpoint = sys.env.getOrElse("M18_QDRANT_EMBEDDING_ENDPOINT", "http://localhost:8081")
        val embeddingClient   = new LlamaCppEmbeddingClient(LlamaCppEmbeddingClientConfig(baseUrl = embeddingEndpoint))

        val (embeddingProbe, qdrantProbe) = unsafeRun(
          for {
            embeddingResult <- embeddingClient.embed("x runtime es+qdrant scorecard probe").either
            qdrantResult    <- qdrantClient.collectionInfo("/collections").either
          } yield (embeddingResult, qdrantResult)
        )
        val embeddingConfigured = embeddingProbe.exists(_.nonEmpty)
        val qdrantConfigured    = qdrantProbe.isRight

        val indexName = s"${spec.variantDocument.indexName}_x_${UUID.randomUUID().toString.replace('-', '_')}"
        val testSpec  = spec.copy(variantDocument = spec.variantDocument.copy(indexName = indexName))

        (embeddingProbe, qdrantConfigured) match {
          case (Right(vector), true) if vector.nonEmpty =>
            // ---- Both real resources reachable: execute ES + Qdrant for the WHOLE query set. ----
            val result = unsafeRun(runBothLegs(esClient, testSpec, qdrantClient, embeddingClient, vector.length))

            // Both legs must really have executed before any scorecard claim is made.
            assert(result.esExecuted, s"expected ES leg to execute, got ${result.es}")
            assert(result.qdrantExecuted, s"expected Qdrant leg to execute, got ${result.qdrant}")
            assert(result.qdrantCandidateRows.nonEmpty, "expected non-empty real Qdrant candidate rows")
            assert(result.separationViolations.isEmpty, "ES and Qdrant outputs must stay separate (no fusion)")

            // ---- The scorecard: computed by the existing pure M19 metrics over the REAL result. ----
            val perQuery  = M19DualEngineOfflineEvalMetrics.queryMetrics(result)
            val aggregate = M19DualEngineOfflineEvalMetrics.aggregate(perQuery)
            assert(perQuery.size == 3, s"scorecard must cover the three measured queries, got ${perQuery.map(_.queryId)}")
            assert(perQuery.map(_.queryId).toSet == Set(lexicalQueryId, semanticQueryId, hardNegativeQueryId))

            // Fixture-wide honesty invariant: Qdrant returns the ENTIRE tiny seeded collection for every
            // query (limit=topK, no score threshold). Any complement below is a recall-floor artifact.
            perQuery.foreach { sc =>
              assert(
                sc.candidateIds.qdrantCandidateIds.toSet == seededVariantIds,
                s"Qdrant returns the whole seeded collection for ${sc.queryId} (recall-floor fixture limit), got ${sc.candidateIds.qdrantCandidateIds}",
              )
              assert(sc.expectationsAvailable, s"every query carries expectations, so expected-aware signals are meaningful for ${sc.queryId}")
              // Lookup is intentionally not wired (T/W pattern): honest lookup_not_evaluated, no faked hydration.
              val esLookup     = lookupCountsFor(sc.lookupByBackend, M18OfflineEvalBackend.Es)
              val qdrantLookup = lookupCountsFor(sc.lookupByBackend, M18OfflineEvalBackend.Qdrant)
              assert(esLookup.evaluatedCount == 0, s"no ES lookup may be evaluated when no lookup is wired for ${sc.queryId}")
              assert(qdrantLookup.evaluatedCount == 0, s"no Qdrant lookup may be evaluated when no lookup is wired for ${sc.queryId}")
              assert(qdrantLookup.lookupNotEvaluatedCount == seededVariantIds.size, s"Qdrant lookup is not wired for ${sc.queryId}")
              // Per-leg latency evidence is present for BOTH executed legs (real clock was attached).
              assert(latencyFor(sc.latencyByBackend, M18OfflineEvalBackend.Es).availability == M19LatencyAvailability.Present, s"ES latency must be present for ${sc.queryId}")
              assert(latencyFor(sc.latencyByBackend, M18OfflineEvalBackend.Qdrant).availability == M19LatencyAvailability.Present, s"Qdrant latency must be present for ${sc.queryId}")
              // Overlap and noise are cross-checked against the real rows (no magic constants).
              assert(
                sc.overlapCount == sc.candidateIds.esCandidateIds.toSet.intersect(sc.candidateIds.qdrantCandidateIds.toSet).size,
                s"overlap must equal the real ES ∩ Qdrant intersection for ${sc.queryId}",
              )
              assert(sc.qdrantNoiseCount == realQdrantNoise(result, sc.queryId), s"Qdrant noise must equal the real not-expected Qdrant rows for ${sc.queryId}")
              assert(sc.qdrantComplementCount == realQdrantComplement(result, sc.queryId), s"Qdrant complement must equal real expected-not-in-ES Qdrant rows for ${sc.queryId}")
            }

            // ---- Query 1: lexical_exact_or_easy. ES retrieves the expected variant; Qdrant overlaps. ----
            val lexical = scorecardFor(perQuery, lexicalQueryId)
            assert(lexical.candidateIds.esCandidateIds.contains(variantId.toString), "ES must retrieve the seeded expected variant for the lexical-exact query")
            assert(!lexical.candidateIds.esCandidateIds.contains(otherVariantId.toString), "ES must not retrieve the unrelated variant for the lexical-exact query")
            assert(lexical.overlapCount == 1, "ES and Qdrant must overlap on exactly the seeded expected variant for the lexical-exact query")
            // ES already has the only expected variant, so Qdrant supplies no NEW expected id over ES.
            assert(lexical.qdrantComplementCount == 0, s"Qdrant complement over ES must be zero for the lexical-exact query, got ${lexical.qdrantComplementCount}")
            // The unrelated seeded variant Qdrant returns is honest noise (1 row).
            assert(lexical.qdrantNoiseCount == 1, s"Qdrant noise must be the single unrelated seeded variant for the lexical-exact query, got ${lexical.qdrantNoiseCount}")

            // ---- Query 2: semantic_complement_candidate. ES misses the expected variant (no shared
            // lexical token, operator=And), so Qdrant's candidate set adds it back: complement POSITIVE. ----
            val semantic = scorecardFor(perQuery, semanticQueryId)
            assert(
              semantic.candidateIds.esCandidateIds.isEmpty,
              s"the semantic query shares no lexical token with the seeded variant, so ES must retrieve nothing, got ${semantic.candidateIds.esCandidateIds}",
            )
            assert(semantic.overlapCount == 0, "no ES ∩ Qdrant overlap is possible when ES retrieves nothing")
            // Measured POSITIVE complement — but a recall-floor artifact (Qdrant returns the whole tiny
            // collection), asserted above, NOT proof of genuine semantic ranking quality.
            assert(
              semantic.qdrantComplementCount == 1,
              s"Qdrant supplies the expected variant that ES missed for the semantic query (recall-floor artifact), got ${semantic.qdrantComplementCount}",
            )
            assert(semantic.qdrantNoiseCount == 1, s"the unrelated seeded variant is honest noise for the semantic query, got ${semantic.qdrantNoiseCount}")

            // ---- Query 3: hard_negative_or_should_stay_silent. The only expected answer is out of
            // catalog, so Qdrant should stay silent but cannot: every returned candidate is noise. ----
            val hardNegative = scorecardFor(perQuery, hardNegativeQueryId)
            assert(
              hardNegative.candidateIds.esCandidateIds.isEmpty,
              s"the hard-negative query shares no lexical token with any seeded variant, so ES must retrieve nothing, got ${hardNegative.candidateIds.esCandidateIds}",
            )
            assert(hardNegative.overlapCount == 0, "no ES ∩ Qdrant overlap is possible when ES retrieves nothing")
            // No seeded variant equals the out-of-catalog sentinel, so Qdrant adds no useful complement.
            assert(hardNegative.qdrantComplementCount == 0, s"Qdrant must add no useful complement for the hard-negative query, got ${hardNegative.qdrantComplementCount}")
            // Qdrant cannot stay silent over the tiny collection: both seeded variants are pure noise.
            assert(
              hardNegative.qdrantNoiseCount == seededVariantIds.size,
              s"Qdrant returns the whole catalog as pure noise for the hard-negative query (cannot stay silent), got ${hardNegative.qdrantNoiseCount}",
            )

            // ---- Aggregate: a faithful roll-up of the three measured queries (still evidence only). ----
            assert(aggregate.queryCount == perQuery.size, "aggregate query count must equal the number of measured queries")
            assert(aggregate.queryCount == 3, "exactly three queries were measured")
            assert(aggregate.overlapCount == perQuery.map(_.overlapCount).sum)
            assert(aggregate.overlapCount >= 1, "at least one query (the lexical-exact one) must measure ES ∩ Qdrant overlap")
            assert(aggregate.qdrantComplementCount == perQuery.map(_.qdrantComplementCount).sum)
            assert(aggregate.qdrantComplementCount >= 1, "the semantic query must measure a positive Qdrant complement (recall-floor artifact, not a quality proof)")
            assert(aggregate.qdrantNoiseCount == perQuery.map(_.qdrantNoiseCount).sum)
            assert(aggregate.qdrantNoiseCount >= 1, "at least one query must measure Qdrant noise")
            assert(aggregate.expectationsAvailableQueryCount == 3, "all three queries carry expectations, so all expected-aware signals are meaningful")
          // X PARTIALLY CLEARED: a per-query ES/Qdrant scorecard was measured from REAL executed
          // candidate evidence over a lexical-exact, a semantic-complement, and a hard-negative query.
          // It locates where ES works (q1), where Qdrant adds complement (q2, but only as a recall-floor
          // artifact of Qdrant returning the whole tiny collection), and where Qdrant adds noise / cannot
          // stay silent (q1/q3). It does NOT prove broad Qdrant semantic ranking quality; that gap is
          // measured, not hidden. It assembles NO hybrid response and approves NO default route switch.

          case _ =>
            // ---- Qdrant resources unavailable: execute ES for every query, resource-gate Qdrant. ----
            val prerequisites = M18QdrantLegPrerequisites(
              realBackendOfflineEvalEnabled = true,
              embeddingClientConfigured = embeddingConfigured,
              qdrantClientConfigured = qdrantConfigured,
              collectionReadinessConfigured = true,
            )
            val result = unsafeRun(runEsLegWithGatedQdrant(esClient, testSpec, prerequisites))

            // ES still really executes for the whole set, and the lexical-exact half is real.
            assert(result.esExecuted, s"expected ES leg to execute, got ${result.es}")
            val perQuery = M19DualEngineOfflineEvalMetrics.queryMetrics(result)
            assert(perQuery.size == 3, s"ES still measures all three queries, got ${perQuery.map(_.queryId)}")
            assert(scorecardFor(perQuery, lexicalQueryId).candidateIds.esCandidateIds.contains(variantId.toString), "ES must retrieve the seeded variant for the lexical-exact query")

            // No Qdrant candidate evidence exists for any query: no faked ids, Qdrant latency NotExecuted.
            assert(result.qdrantCandidateRows.isEmpty, "no fake Qdrant rows may be emitted")
            perQuery.foreach { sc =>
              assert(sc.candidateIds.qdrantCandidateIds.isEmpty, s"no Qdrant ids may appear without real Qdrant evidence for ${sc.queryId}")
              assert(sc.overlapCount == 0, s"no overlap is measurable without real Qdrant evidence for ${sc.queryId}")
              assert(latencyFor(sc.latencyByBackend, M18OfflineEvalBackend.Qdrant).availability == M19LatencyAvailability.NotExecuted, s"Qdrant latency must be NotExecuted for ${sc.queryId}")
            }

            val gateReason = result.qdrant match {
              case skipped: M18BackendLegOutcome.Skipped =>
                assert(skipped.skipKind == M18LegSkipKind.ResourceGated)
                assert(skipped.missingPrerequisites.nonEmpty)
                skipped.reason
              case other =>
                fail(s"expected resource-gated Qdrant leg, got $other")
            }
            // X did NOT clear: real Qdrant candidate evidence was absent for the whole query set.
            cancel(
              s"X did not clear the runtime ES/Qdrant scorecard query-set proof: real ES candidate " +
                s"evidence was measured for all three queries but the Qdrant leg was honestly " +
                s"resource-gated (no candidates faked), so no ES-vs-Qdrant scorecard could be computed. $gateReason"
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

  /** Run BOTH real legs (real ES + real Qdrant) over the whole dataset query set in one runner pass. */
  private def runBothLegs(
    esClient: ElasticsearchTestClient,
    testSpec: leaderboard.search.dsl.BeautySearchSpec,
    qdrantClient: QdrantClient,
    embeddingClient: LlamaCppEmbeddingClient,
    vectorDimension: Int,
  ): IO[QueryFailure, M18DualEngineOfflineEvalResult] = {
    val embeddingSpec = EmbeddingSpec[VariantSearchDocument](
      vectorName = "llama-cpp-embedding",
      modelName = "x-runtime-scorecard",
      dimension = vectorDimension,
      distance = VectorDistance.Cosine,
      sourceTextFieldPaths = List("serviceText", "attributeText", "allText", "categoryName"),
    )
    val readinessConfig = QdrantCollectionReadinessConfig.derive(
      QdrantCollectionReadinessInput(
        domainName = "beautyq",
        searchSpecVersion = "v1",
        purpose = s"x-runtime-scorecard-${UUID.randomUUID().toString.replace('-', '_')}",
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

  /** Look up the per-query scorecard by id without any unsafe extraction. */
  private def scorecardFor(perQuery: List[M19QueryMetrics], queryId: String): M19QueryMetrics =
    perQuery.find(_.queryId == queryId) match {
      case Some(found) => found
      case None        => fail(s"expected a scorecard for query $queryId, got ${perQuery.map(_.queryId)}")
    }

  /** Real not-expected Qdrant rows for a query, cross-checked against M19 noise (no magic constant). */
  private def realQdrantNoise(result: M18DualEngineOfflineEvalResult, queryId: String): Int =
    result.qdrantCandidateRows.count(row => row.queryId == queryId && row.expectedMatch == M18ExpectedMatch.NotExpected)

  /** Real expected-and-not-in-ES Qdrant rows for a query, cross-checked against M19 complement. */
  private def realQdrantComplement(result: M18DualEngineOfflineEvalResult, queryId: String): Int = {
    val esIds = result.esCandidateRows.filter(_.queryId == queryId).map(_.candidateId).toSet
    result.qdrantCandidateRows
      .filter(row => row.queryId == queryId && row.expectedMatch == M18ExpectedMatch.Matched)
      .map(_.candidateId)
      .toSet
      .diff(esIds)
      .size
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
      // The expected hair-colouring variant (balayage). Retrieved lexically by query 1.
      syntheticDocument(variantId, lexicalQueryText, "hair", "service_name"),
      // The unrelated variant (manicure). Never expected; the in-catalog Qdrant noise candidate.
      syntheticDocument(otherVariantId, "manicure gel polish", "nails", "other"),
    )

  private def syntheticDocument(
    id: MasterServiceOfferVariantId,
    serviceName: String,
    categoryName: String,
    tag: String,
  ): VariantSearchDocument = {
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
      categoryName = categoryName,
      masterName = "x-master",
      locationName = "x-location",
      address = "x-address",
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
      providerText = "x-master x-location",
      locationText = "x-location x-address",
    )
  }

  private def unsafeRun[E, A](effect: IO[E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
