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
 * H: expand the runtime ES-vs-Qdrant scorecard from the redesigned J 3-query methodology slice to a
 * small canonical-backed representative query set.
 *
 * X ([[RuntimeEsQdrantScorecardProofSpec]] before the J redesign) measured three source-confirmed
 * [[QueryClass]] roles (lexical-exact, semantic-complement, hard-negative) over a 2-document fixture
 * with `topK=10` and no score threshold, so Qdrant returned the ENTIRE tiny seeded collection for
 * every query. The q2 positive complement was a recall-floor artifact and q3 measured pure noise with
 * no silence behaviour.
 *
 * J redesigned the fixture so Qdrant ranking is exercised honestly:
 *   - Seeded collection is now 8 documents: 1 expected `balayage` variant + 7 distractors spanning
 *     distinct beauty subdomains (nails, lashes, brows, face, pmu, body-wax, body-massage).
 *   - `topK = 3` is strictly smaller than the seeded collection size, so Qdrant MUST rank and CANNOT
 *     trivially return the whole collection for any query.
 *   - A positive-complement quality gate asserts that the q2 Qdrant complement is backed by at least
 *     one excluded seeded distractor (policy-quality evidence), not a recall-floor artifact.
 *   - A hard-negative subcase uses a separate, thresholded Qdrant composition (`scoreThreshold = 0.9`,
 *     source-supported by [[VectorSearchSpec.scoreThreshold]]) to honestly measure silence: the
 *     thresholded result must be a subset of the unthresholded result; full silence is the ideal.
 *
 * H keeps every redesigned J fixture property (real ES, real Qdrant, real embedding endpoint, seeded
 * collection size > topK, topK < collection size, Qdrant MUST NOT return the full seeded collection
 * for any query, existing [[M19DualEngineOfflineEvalMetrics]] with no parallel metric layer) and
 * expands the measured query set from the 3-query J methodology slice to a 6-query canonical-backed
 * representative set covering FIVE source-confirmed query roles:
 *
 *   1. `q_lexical_exact_balayage` ([[QueryClass.ExactProductNameBrand]]) — lexical/easy role.
 *      Query text = seeded service name. ES retrieves the expected variant; Qdrant overlaps; ES ∩
 *      Qdrant overlap = 1; Qdrant complement = 0; Qdrant noise is the count of returned distractors
 *      (>=1, because topK=3 forces at least one distractor in the result). SYNTHETIC fixture text
 *      (no canonical lexical-easy query text matches the seeded "balayage haircut" doc).
 *   2. `q_semantic_complement_blonde` ([[QueryClass.SemanticDescriptive]]) — semantic descriptive.
 *      Query text shares NO lexical token with the seeded variant, so `operator=And` ES `multi_match`
 *      retrieves nothing. Qdrant supplies the expected variant (positive complement) AND the
 *      complement is policy-quality evidence only when at least one seeded distractor is EXCLUDED
 *      from the Qdrant result set. SYNTHETIC fixture text (no canonical semantic-complement query
 *      text matches the seeded "balayage haircut" doc while staying in the same hair-colouring
 *      domain).
 *   3. `q_hard_negative_diesel` ([[QueryClass.NegativeOutOfCatalog]]) — negative out-of-catalog.
 *      Non-beauty query whose only "expected" answer is the explicitly out-of-catalog sentinel. ES
 *      retrieves nothing. UNTHRESHOLDED main pass measures honest noise (qdrantIds.size == 0..topK,
 *      all noise). THRESHOLDED subcase measures silence: qdrantIds.size <= unthresholded.size
 *      (ideally == 0). SYNTHETIC fixture text.
 *   4. `q_nails_001_ingredient_attribute` ([[QueryClass.IngredientAttribute]]) — ingredient/attribute
 *      role. CANONICAL-BACKED: query text "маникюр гель лак" and `IngredientAttribute` class are
 *      source-confirmed from the canonical
 *      [[beautyq_search_eval_queries_v1.json]] dataset
 *      (id=`q_nails_001`, queryTypes=[direct, attribute]) and from the spec anchor in
 *      [[M9BeautyQSearchEvalQueryDatasetStaticRows]] (line 191-194). The expected variant id is the
 *      J-fixture `variantId` (not the canonical dataset's own `acceptableVariantIds`); the canonical
 *      text + class are source-confirmed, the expected-id anchoring is J-fixture-only and recorded
 *      as a deliberate deviation. Qdrant MAY or MAY NOT surface the expected variant in topK=3.
 *   5. `q_nails_003_filter_heavy` ([[QueryClass.FilterHeavy]]) — filter-heavy role. CANONICAL-BACKED:
 *      query text "shellac entfernen und neu" and `FilterHeavy` class are source-confirmed from the
 *      canonical dataset (id=`q_nails_003`, queryTypes=[german, attribute_heavy]) and from the spec
 *      anchor in [[M9BeautyQSearchEvalQueryDatasetStaticRows]] (line 217-221). Same expected-id
 *      deviation as #4.
 *   6. `q_noise_005_ambiguous` ([[QueryClass.Ambiguous]]) — ambiguous role. CANONICAL-BACKED: query
 *      text "lifting" and `Ambiguous` class are source-confirmed from the canonical dataset
 *      (id=`q_noise_005`, queryTypes=[ambiguous, hard_negative]) and from the spec anchor in
 *      [[M9BeautyQSearchEvalQueryDatasetStaticRows]] (line 244-248). Same expected-id deviation as
 *      #4. H is partially cleared (not fully cleared) on the canonical-backed coverage axis: three
 *      of the six queries are source-confirmed canonical query ids/classes, but their expected
 *      variant ids are the J-fixture id, not the dataset's own `acceptableVariantIds`. Honest
 *      coverage report:
 *        - canonical-backed query text + class: q_nails_001, q_nails_003, q_noise_005 (3 of 6)
 *        - synthetic fixture query text:      q_lexical_exact_balayage,
 *          q_semantic_complement_blonde, q_hard_negative_diesel (3 of 6)
 *        - canonical-backed expected variant ids: NONE (expected ids are the J-fixture
 *          `variantId`, NOT the dataset's own `acceptableVariantIds`, because the seeded
 *          collection has only 1 expected + 7 distractors).
 *
 * Honesty gate: when the embedding endpoint or Qdrant is unavailable, no Qdrant candidates are faked;
 * ES still executes for every query, Qdrant rows are empty, Qdrant latency is NotExecuted, and the
 * test is CANCELLED (H is resource-gated, not cleared in that case).
 *
 * Boundaries (asserted as data via the disabled M20B control surface): the scorecard is measurement
 * evidence only. H assembles no final hybrid response, fuses no scores, reranks nothing, adds no
 * fallback/shadow/mirror traffic, approves no route switch or Qdrant supplement, and never touches
 * the default `/beauty-search` route. All evidence is variant-candidate-level only — no provider
 * grouping, service grouping, facet, or inferred-filter projection is source-confirmed for Qdrant.
 */
final class RuntimeEsQdrantScorecardProofSpec extends LeaderboardTest with ProdTest {
  override def config = super.config.copy(
    activation = super.config.activation ++ Activation(Mode -> Mode.Test),
    memoizationRoots = super.config.memoizationRoots + DIKey[ElasticsearchPortCfg] + DIKey[QdrantPortCfg],
  )

  private val spec = BeautySearchSpecV1.spec

  // The expected hair-colouring variant (balayage). Both legs may retrieve it.
  private val variantId: MasterServiceOfferVariantId = UUID.randomUUID()
  // Seven unrelated seeded distractor variants across distinct beauty subdomains (nails, lashes, brows,
  // face, pmu, body-wax, body-massage). With topK=3 and a 8-document collection, Qdrant MUST rank and
  // CANNOT trivially return the whole collection: a real positive complement for the semantic query is
  // now backed by at least one excluded distractor, not just a recall-floor artifact.
  private val distractorVariantIds: List[MasterServiceOfferVariantId] =
    List.fill(7)(UUID.randomUUID())
  // An explicitly OUT-OF-CATALOG sentinel: the only "right" answer for the hard-negative query. It is
  // never seeded, so every real catalog candidate Qdrant returns for that query is honest noise.
  private val outOfCatalogId: MasterServiceOfferVariantId = UUID.randomUUID()

  // The full seeded catalog id set (1 expected + 7 distractor = 8 documents). Qdrant searches with
  // limit=topK=3 over this collection, so it cannot return the whole set for any query (asserted).
  private val seededVariantIds: Set[String] =
    (variantId :: distractorVariantIds).map(_.toString).toSet

  // topK is intentionally smaller than the seeded collection size (3 < 8) so that Qdrant ranking is
  // exercised for every query; the runner wires this through VectorSearchSpec.
  private val fixtureTopK: Int = 3

  // Descriptor for a single distractor document. Spans distinct beauty subdomains so distractors are
  // semantically diverse and Qdrant ranking has a real non-trivial signal to surface.
  private final case class DistractorDescriptor(serviceName: String, categoryName: String, tag: String)

  // Seven distractor documents across distinct beauty subdomains. Each is unrelated to the expected
  // balayage variant lexically or semantically (in distinct subdomains), so the seeded collection
  // contains 8 total documents: 1 expected + 7 distractors.
  private val distractorDescriptors: List[DistractorDescriptor] = List(
    DistractorDescriptor("manicure gel polish",         "nails", "other"),
    DistractorDescriptor("lash extensions volume",      "lashes", "other"),
    DistractorDescriptor("brow lamination tint",        "brows",  "other"),
    DistractorDescriptor("hydrating facial treatment",  "face",  "other"),
    DistractorDescriptor("permanent makeup eyebrows",   "pmu",   "other"),
    DistractorDescriptor("full body waxing",            "body",  "other"),
    DistractorDescriptor("relaxing massage session",    "body",  "other"),
  )

  // ---- Query 1: lexical_exact_or_easy. Query text is the seeded service name; ES retrieves it. ----
  // SYNTHETIC fixture text: no canonical lexical-easy query text matches the seeded "balayage haircut"
  // doc; the J fixture stands in as the lexical-easy role.
  private val lexicalQueryId   = "q_lexical_exact_balayage"
  private val lexicalQueryText = "balayage haircut"

  // ---- Query 2: semantic_complement_candidate. Same hair-colouring domain, but NO lexical token is
  // shared with the seeded variant's searchable text (serviceText/allText/attributeText/providerText/
  // locationText), so the `operator=And` ES multi_match retrieves nothing for it. ----
  // SYNTHETIC fixture text: no canonical semantic-complement query text matches the seeded
  // "balayage haircut" doc while staying in the same hair-colouring domain.
  private val semanticQueryId   = "q_semantic_complement_blonde"
  private val semanticQueryText = "blonde color highlights toning treatment"

  // ---- Query 3: hard_negative_or_should_stay_silent. Non-beauty query; the only expected answer is
  // the out-of-catalog sentinel, so Qdrant should stay silent. The unthresholded main scorecard measures
  // its returned noise honestly; the thresholded subcase below uses scoreThreshold=Some(0.9) to
  // honestly measure silence. ----
  // SYNTHETIC fixture text: the canonical dataset's `hard_negative` rows are real beauty queries
  // (e.g. "снять ресницы") that DO have a seeded match; a non-beauty "diesel" is the only
  // genuinely out-of-catalog query available to the J fixture.
  private val hardNegativeQueryId   = "q_hard_negative_diesel"
  private val hardNegativeQueryText = "diesel engine timing belt replacement"

  // ---- Query 4: ingredient_attribute role, CANONICAL-BACKED on text + class. ----
  // Source: beautyq_search_eval_queries_v1.json id=`q_nails_001`, queryTypes=[direct, attribute],
  // QueryClass=IngredientAttribute. Spec anchor: M9BeautyQSearchEvalQueryDatasetStaticRows line 194
  // (q_nails_001 → QueryClass.IngredientAttribute). Expected variant id is the J-fixture
  // `variantId` (NOT the canonical dataset's own acceptableVariantIds); the canonical text + class
  // are source-confirmed, the expected-id anchoring is J-fixture-only and recorded as a
  // deliberate deviation. The query text is RU ("маникюр гель лак" = "manicure gel polish") and
  // shares no lexical token with the seeded "balayage haircut" doc, so `operator=And` ES
  // multi_match retrieves nothing for it; Qdrant MAY or MAY NOT surface the expected variant in
  // topK=3 — both behaviours are honestly measured.
  private val ingredientAttributeQueryId   = "q_nails_001_ingredient_attribute"
  private val ingredientAttributeQueryText = "маникюр гель лак"

  // ---- Query 5: filter_heavy role, CANONICAL-BACKED on text + class. ----
  // Source: beautyq_search_eval_queries_v1.json id=`q_nails_003`, queryTypes=[german,
  // attribute_heavy], QueryClass=FilterHeavy. Spec anchor: M9BeautyQSearchEvalQueryDatasetStaticRows
  // line 221 (q_nails_003 → QueryClass.FilterHeavy). Same expected-id deviation as #4. The query
  // text is DE ("shellac entfernen und neu" = "remove shellac and redo") and shares no lexical token
  // with the seeded "balayage haircut" doc, so ES retrieves nothing; Qdrant MAY or MAY NOT surface
  // the expected variant in topK=3.
  private val filterHeavyQueryId   = "q_nails_003_filter_heavy"
  private val filterHeavyQueryText = "shellac entfernen und neu"

  // ---- Query 6: ambiguous role, CANONICAL-BACKED on text + class. ----
  // Source: beautyq_search_eval_queries_v1.json id=`q_noise_005`, queryTypes=[ambiguous,
  // hard_negative], QueryClass=Ambiguous. Spec anchor: M9BeautyQSearchEvalQueryDatasetStaticRows
  // line 248 (q_noise_005 → QueryClass.Ambiguous). Same expected-id deviation as #4. The query
  // text is mixed-language "lifting" — semantically in the lash-lifting / brow-lamination
  // neighbourhood but completely unrelated lexically to the seeded "balayage haircut" doc; ES
  // retrieves nothing; Qdrant MAY or MAY NOT surface the expected variant in topK=3.
  private val ambiguousQueryId   = "q_noise_005_ambiguous"
  private val ambiguousQueryText = "lifting"

  // A high cosine-similarity floor for the thresholded hard-negative subcase. The hard-negative query
  // shares no semantic neighbourhood with any beauty-domain seed, so Qdrant with this threshold can
  // honestly stay silent (return zero rows) when no score clears the bar. 0.9 is a deliberately high
  // floor; the assertion compares thresholded vs. unthresholded size, not the floor value itself.
  private val hardNegativeScoreThreshold: Double = 0.9

  private val dataset: M9OfflineEvalDataset =
    M9OfflineEvalDataset(
      evalDatasetId = EvalDatasetId("h-runtime-scorecard-canonical-backed-dataset"),
      catalogSnapshotId = CatalogSnapshotId("h-runtime-scorecard-canonical-backed-snapshot"),
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
        M9OfflineEvalDatasetQuery(
          queryId = ingredientAttributeQueryId,
          rawQueryText = ingredientAttributeQueryText,
          normalizedQueryText = Some(ingredientAttributeQueryText),
          queryClass = QueryClass.IngredientAttribute,
          filters = Nil,
          categories = Nil,
          // Expected-id DEVIATION: the J-fixture `variantId` is the expected id, NOT the canonical
          // dataset's own acceptableVariantIds for q_nails_001. The query text + class are
          // source-confirmed; the expected-id anchoring is J-fixture-only.
          expectedResults = List(M9OfflineEvalExpectedResult(variantId.toString, None)),
          expectedNotes = List(
            "canonical-backed text+class from q_nails_001 (IngredientAttribute); expected id is the J-fixture variantId, not the canonical dataset's acceptableVariantIds",
          ),
          negativeOutOfCatalog = false,
        ),
        M9OfflineEvalDatasetQuery(
          queryId = filterHeavyQueryId,
          rawQueryText = filterHeavyQueryText,
          normalizedQueryText = Some(filterHeavyQueryText),
          queryClass = QueryClass.FilterHeavy,
          filters = Nil,
          categories = Nil,
          // Expected-id DEVIATION: same pattern as ingredientAttributeQueryId.
          expectedResults = List(M9OfflineEvalExpectedResult(variantId.toString, None)),
          expectedNotes = List(
            "canonical-backed text+class from q_nails_003 (FilterHeavy); expected id is the J-fixture variantId, not the canonical dataset's acceptableVariantIds",
          ),
          negativeOutOfCatalog = false,
        ),
        M9OfflineEvalDatasetQuery(
          queryId = ambiguousQueryId,
          rawQueryText = ambiguousQueryText,
          normalizedQueryText = Some(ambiguousQueryText),
          queryClass = QueryClass.Ambiguous,
          filters = Nil,
          categories = Nil,
          // Expected-id DEVIATION: same pattern as ingredientAttributeQueryId.
          expectedResults = List(M9OfflineEvalExpectedResult(variantId.toString, None)),
          expectedNotes = List(
            "canonical-backed text+class from q_noise_005 (Ambiguous); expected id is the J-fixture variantId, not the canonical dataset's acceptableVariantIds",
          ),
          negativeOutOfCatalog = false,
        ),
      ),
    )

  // A single-query dataset carrying ONLY the hard-negative query, used to drive the thresholded Qdrant
  // subcase via the same existing M18 runner. The thresholded composition is built independently
  // (separate purpose UUID → separate Qdrant collection) and reuses the same shared snapshot.
  private val hardNegativeOnlyDataset: M9OfflineEvalDataset =
    M9OfflineEvalDataset(
      evalDatasetId = EvalDatasetId("j-runtime-scorecard-hardneg-thresholded-dataset"),
      catalogSnapshotId = CatalogSnapshotId("j-runtime-scorecard-hardneg-thresholded-snapshot"),
      queries = List(
        M9OfflineEvalDatasetQuery(
          queryId = hardNegativeQueryId,
          rawQueryText = hardNegativeQueryText,
          normalizedQueryText = Some(hardNegativeQueryText),
          queryClass = QueryClass.NegativeOutOfCatalog,
          filters = Nil,
          categories = Nil,
          expectedResults = List(M9OfflineEvalExpectedResult(outOfCatalogId.toString, None)),
          expectedNotes = Nil,
          negativeOutOfCatalog = true,
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

  "Runtime ES-vs-Qdrant scorecard over the redesigned 8-document / topK=3 fixture (scope h_runtime_es_qdrant_scorecard_canonical_backed_query_set)" should {
    "compute a per-query ES/Qdrant scorecard (ids, overlap, expected-aware complement, noise, lookup, per-leg latency) over 6 queries (3 J-methodology synthetic + 3 canonical-backed ingredient_attribute/filter_heavy/ambiguous) covering 5 source-confirmed query roles from REAL executed ES + Qdrant candidate rows on a redesigned 8-document / topK=3 fixture, with a thresholded hard-negative subcase measuring silence honestly — never faking candidates, never assembling a hybrid response, never touching the default route" in {
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
            val expectedQueryIds = Set(
              lexicalQueryId,
              semanticQueryId,
              hardNegativeQueryId,
              ingredientAttributeQueryId,
              filterHeavyQueryId,
              ambiguousQueryId,
            )
            assert(
              perQuery.size == expectedQueryIds.size,
              s"scorecard must cover the six measured queries (3 J-methodology synthetic + 3 canonical-backed), got ${perQuery.map(_.queryId)}",
            )
            assert(perQuery.map(_.queryId).toSet == expectedQueryIds, "all six measured query ids must be present in the scorecard")
            assert(perQuery.size > 3, "H expands beyond the J 3-query methodology slice")

            // Fixture-wide honesty invariants for the redesigned 8-document / topK=3 collection:
            //   - topK (3) is strictly smaller than the seeded collection size (8), so Qdrant MUST rank;
            //   - Qdrant cannot return the whole seeded collection for ANY query (it would need topK >= 8).
            // This is the redesigned equivalent of the old recall-floor-artifact assertion. Any
            // complement below is now a real ranking signal, not a floor artifact.
            assert(seededVariantIds.size > fixtureTopK, s"seeded collection must be larger than topK (got ${seededVariantIds.size} <= $fixtureTopK)")
            perQuery.foreach { sc =>
              val qdrantIdSet = sc.candidateIds.qdrantCandidateIds.toSet
              assert(qdrantIdSet != seededVariantIds, s"Qdrant must NOT return the full seeded collection for ${sc.queryId} (topK<${seededVariantIds.size} check), got ${qdrantIdSet}")
              assert(
                sc.candidateIds.qdrantCandidateIds.size <= fixtureTopK,
                s"Qdrant must return at most topK rows for ${sc.queryId}, got ${sc.candidateIds.qdrantCandidateIds.size}",
              )
              assert(sc.expectationsAvailable, s"every query carries expectations, so expected-aware signals are meaningful for ${sc.queryId}")
              // Lookup is intentionally not wired (T/W pattern): honest lookup_not_evaluated, no faked hydration.
              val esLookup     = lookupCountsFor(sc.lookupByBackend, M18OfflineEvalBackend.Es)
              val qdrantLookup = lookupCountsFor(sc.lookupByBackend, M18OfflineEvalBackend.Qdrant)
              assert(esLookup.evaluatedCount == 0, s"no ES lookup may be evaluated when no lookup is wired for ${sc.queryId}")
              assert(qdrantLookup.evaluatedCount == 0, s"no Qdrant lookup may be evaluated when no lookup is wired for ${sc.queryId}")
              assert(qdrantLookup.lookupNotEvaluatedCount == sc.candidateIds.qdrantCandidateIds.size, s"Qdrant lookup is not wired for ${sc.queryId}")
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
            // ES must not retrieve any DISTACTOR variant for the lexical-exact query (i.e. only the
            // expected variant is allowed). Excluding variantId from seededVariantIds makes the check
            // specific to distractors, not the expected document itself.
            val lexicalEsDistractors = lexical.candidateIds.esCandidateIds.filter(_ != variantId.toString)
            assert(
              lexicalEsDistractors.isEmpty,
              s"ES must not retrieve any distractor variant for the lexical-exact query, got ${lexicalEsDistractors}",
            )
            // ES already has the only expected variant, so Qdrant supplies no NEW expected id over ES.
            assert(lexical.qdrantComplementCount == 0, s"Qdrant complement over ES must be zero for the lexical-exact query, got ${lexical.qdrantComplementCount}")
            // Qdrant rank may return only distractors alongside the expected (topK=3): noise is the count
            // of returned distractors; total Qdrant size is at most topK.
            val lexicalQdrantIds = lexical.candidateIds.qdrantCandidateIds.toSet
            assert(
              lexicalQdrantIds.size == lexical.qdrantNoiseCount + lexical.overlapCount,
              s"Qdrant noise + overlap must equal total Qdrant ids for the lexical-exact query, got ${lexical.qdrantNoiseCount} + ${lexical.overlapCount} vs ${lexicalQdrantIds.size}",
            )
            assert(
              lexical.qdrantNoiseCount >= 1,
              s"Qdrant noise must be at least 1 distractor for the lexical-exact query (topK=$fixtureTopK forces distractors in the result), got ${lexical.qdrantNoiseCount}",
            )
            // The whole-collection invariant for this query: at least one seeded variant is NOT in the
            // Qdrant result (i.e. Qdrant ranking excluded something, so q1 cannot be a recall-floor).
            val lexicalExcluded = seededVariantIds.diff(lexicalQdrantIds)
            assert(lexicalExcluded.nonEmpty, s"Qdrant must exclude at least one seeded variant for the lexical-exact query (not a recall-floor), got excluded=$lexicalExcluded")

            // ---- Query 2: semantic_complement_candidate. ES misses the expected variant (no shared
            // lexical token, operator=And), so Qdrant's candidate set adds it back: complement POSITIVE.
            // With topK=3 and an 8-doc collection, this is now a real ranking signal — at least one
            // distractor must be excluded for the complement to be policy-quality evidence. ----
            val semantic = scorecardFor(perQuery, semanticQueryId)
            assert(
              semantic.candidateIds.esCandidateIds.isEmpty,
              s"the semantic query shares no lexical token with the seeded variant, so ES must retrieve nothing, got ${semantic.candidateIds.esCandidateIds}",
            )
            assert(semantic.overlapCount == 0, "no ES ∩ Qdrant overlap is possible when ES retrieves nothing")
            assert(
              semantic.qdrantComplementCount == 1,
              s"Qdrant must supply the expected variant that ES missed for the semantic query, got ${semantic.qdrantComplementCount}",
            )
            val semanticQdrantIds = semantic.candidateIds.qdrantCandidateIds.toSet
            assert(
              semantic.qdrantNoiseCount + semantic.qdrantComplementCount == semanticQdrantIds.size,
              s"Qdrant noise + complement must equal total Qdrant ids for the semantic query, got ${semantic.qdrantNoiseCount} + ${semantic.qdrantComplementCount} vs ${semanticQdrantIds.size}",
            )
            // Positive-complement quality gate: a complement is policy-quality evidence only when at
            // least one non-relevant seeded document is EXCLUDED from the Qdrant result set. Over the
            // redesigned 8-doc / topK=3 collection, Qdrant MUST rank out at least one distractor.
            val semanticExcluded = seededVariantIds.diff(semanticQdrantIds)
            assert(
              semanticExcluded.nonEmpty,
              s"Qdrant must exclude at least one seeded distractor for the semantic query (positive complement is policy-quality evidence only when ranking excludes something, not a recall-floor), got excluded=$semanticExcluded, qdrantIds=$semanticQdrantIds, seeded=$seededVariantIds",
            )
            assert(
              semanticExcluded.intersect(distractorVariantIds.map(_.toString).toSet).nonEmpty,
              s"the excluded set for the semantic query must contain at least one distractor, got $semanticExcluded",
            )

            // ---- Query 3: hard_negative_or_should_stay_silent (UNTHRESHOLDED leg of the main pass).
            // The only expected answer is out of catalog, so Qdrant should stay silent. With topK=3
            // and no score threshold, it cannot: the unthresholded result is honest noise. Silence
            // itself is measured in the thresholded subcase below. ----
            val hardNegative = scorecardFor(perQuery, hardNegativeQueryId)
            assert(
              hardNegative.candidateIds.esCandidateIds.isEmpty,
              s"the hard-negative query shares no lexical token with any seeded variant, so ES must retrieve nothing, got ${hardNegative.candidateIds.esCandidateIds}",
            )
            assert(hardNegative.overlapCount == 0, "no ES ∩ Qdrant overlap is possible when ES retrieves nothing")
            // No seeded variant equals the out-of-catalog sentinel, so Qdrant adds no useful complement.
            assert(hardNegative.qdrantComplementCount == 0, s"Qdrant must add no useful complement for the hard-negative query, got ${hardNegative.qdrantComplementCount}")
            // Unthresholded noise honesty: with topK=3 the Qdrant result is at most 3 rows of pure
            // noise (no expected variant can match the out-of-catalog sentinel), and at least one
            // seeded variant is excluded (the whole-collection invariant above). Noise is therefore
            // honestly measurable as |qdrantIds| (0..3) for this query.
            val hardNegQdrantIds = hardNegative.candidateIds.qdrantCandidateIds.toSet
            assert(
              hardNegative.qdrantNoiseCount == hardNegQdrantIds.size,
              s"unthresholded Qdrant noise must equal |qdrantIds| for the hard-negative query (no expected match possible), got ${hardNegative.qdrantNoiseCount} vs ${hardNegQdrantIds.size}",
            )
            assert(
              hardNegative.qdrantNoiseCount <= fixtureTopK,
              s"unthresholded Qdrant noise must be at most topK for the hard-negative query, got ${hardNegative.qdrantNoiseCount} > $fixtureTopK",
            )

            // ---- Query 4: q_nails_001_ingredient_attribute (CANONICAL-BACKED text+class). ----
            // The query text "маникюр гель лак" shares NO lexical token with the seeded
            // "balayage haircut" doc, so `operator=And` ES multi_match retrieves nothing. Qdrant MAY
            // or MAY NOT surface the expected variant in topK=3; the canonical text + class are
            // source-confirmed, the expected-id anchoring is J-fixture-only.
            val ingredientAttribute = scorecardFor(perQuery, ingredientAttributeQueryId)
            assert(
              ingredientAttribute.candidateIds.esCandidateIds.isEmpty,
              s"the ingredient_attribute query shares no lexical token with the seeded variant, so ES must retrieve nothing, got ${ingredientAttribute.candidateIds.esCandidateIds}",
            )
            assert(ingredientAttribute.overlapCount == 0, "no ES ∩ Qdrant overlap is possible when ES retrieves nothing for the ingredient_attribute query")
            val ingredientAttributeQdrantIds = ingredientAttribute.candidateIds.qdrantCandidateIds.toSet
            // Whole-collection invariant: at least one seeded variant is NOT in the Qdrant result.
            val ingredientAttributeExcluded = seededVariantIds.diff(ingredientAttributeQdrantIds)
            assert(
              ingredientAttributeExcluded.nonEmpty,
              s"Qdrant must exclude at least one seeded variant for the ingredient_attribute query (not a recall-floor), got excluded=$ingredientAttributeExcluded, qdrantIds=$ingredientAttributeQdrantIds, seeded=$seededVariantIds",
            )
            // Expected-aware arithmetic: noise + complement + overlap == total Qdrant size.
            assert(
              ingredientAttribute.qdrantNoiseCount + ingredientAttribute.qdrantComplementCount + ingredientAttribute.overlapCount == ingredientAttributeQdrantIds.size,
              s"ingredient_attribute Qdrant noise + complement + overlap must equal total Qdrant ids, got ${ingredientAttribute.qdrantNoiseCount} + ${ingredientAttribute.qdrantComplementCount} + ${ingredientAttribute.overlapCount} vs ${ingredientAttributeQdrantIds.size}",
            )
            assert(ingredientAttribute.qdrantComplementCount <= 1, s"complement must be at most 1 for the ingredient_attribute query (single expected id), got ${ingredientAttribute.qdrantComplementCount}")

            // ---- Query 5: q_nails_003_filter_heavy (CANONICAL-BACKED text+class). ----
            // Same pattern as #4 but with the DE shellac-removal text. ES retrieves nothing;
            // Qdrant MAY or MAY NOT surface the expected variant.
            val filterHeavy = scorecardFor(perQuery, filterHeavyQueryId)
            assert(
              filterHeavy.candidateIds.esCandidateIds.isEmpty,
              s"the filter_heavy query shares no lexical token with the seeded variant, so ES must retrieve nothing, got ${filterHeavy.candidateIds.esCandidateIds}",
            )
            assert(filterHeavy.overlapCount == 0, "no ES ∩ Qdrant overlap is possible when ES retrieves nothing for the filter_heavy query")
            val filterHeavyQdrantIds = filterHeavy.candidateIds.qdrantCandidateIds.toSet
            val filterHeavyExcluded = seededVariantIds.diff(filterHeavyQdrantIds)
            assert(
              filterHeavyExcluded.nonEmpty,
              s"Qdrant must exclude at least one seeded variant for the filter_heavy query (not a recall-floor), got excluded=$filterHeavyExcluded, qdrantIds=$filterHeavyQdrantIds, seeded=$seededVariantIds",
            )
            assert(
              filterHeavy.qdrantNoiseCount + filterHeavy.qdrantComplementCount + filterHeavy.overlapCount == filterHeavyQdrantIds.size,
              s"filter_heavy Qdrant noise + complement + overlap must equal total Qdrant ids, got ${filterHeavy.qdrantNoiseCount} + ${filterHeavy.qdrantComplementCount} + ${filterHeavy.overlapCount} vs ${filterHeavyQdrantIds.size}",
            )
            assert(filterHeavy.qdrantComplementCount <= 1, s"complement must be at most 1 for the filter_heavy query (single expected id), got ${filterHeavy.qdrantComplementCount}")

            // ---- Query 6: q_noise_005_ambiguous (CANONICAL-BACKED text+class). ----
            // The query text "lifting" is short and ambiguous in the dataset (also tagged
            // `hard_negative` in the canonical dataset), so it is a measured hard-negative-or-
            // ambiguous-silence candidate. ES retrieves nothing; Qdrant MAY or MAY NOT surface
            // the expected variant; honest unthresholded noise is measured.
            val ambiguous = scorecardFor(perQuery, ambiguousQueryId)
            assert(
              ambiguous.candidateIds.esCandidateIds.isEmpty,
              s"the ambiguous query shares no lexical token with the seeded variant, so ES must retrieve nothing, got ${ambiguous.candidateIds.esCandidateIds}",
            )
            assert(ambiguous.overlapCount == 0, "no ES ∩ Qdrant overlap is possible when ES retrieves nothing for the ambiguous query")
            val ambiguousQdrantIds = ambiguous.candidateIds.qdrantCandidateIds.toSet
            val ambiguousExcluded = seededVariantIds.diff(ambiguousQdrantIds)
            assert(
              ambiguousExcluded.nonEmpty,
              s"Qdrant must exclude at least one seeded variant for the ambiguous query (not a recall-floor), got excluded=$ambiguousExcluded, qdrantIds=$ambiguousQdrantIds, seeded=$seededVariantIds",
            )
            assert(
              ambiguous.qdrantNoiseCount + ambiguous.qdrantComplementCount + ambiguous.overlapCount == ambiguousQdrantIds.size,
              s"ambiguous Qdrant noise + complement + overlap must equal total Qdrant ids, got ${ambiguous.qdrantNoiseCount} + ${ambiguous.qdrantComplementCount} + ${ambiguous.overlapCount} vs ${ambiguousQdrantIds.size}",
            )
            assert(ambiguous.qdrantComplementCount <= 1, s"complement must be at most 1 for the ambiguous query (single expected id), got ${ambiguous.qdrantComplementCount}")
            // Per-leg latency evidence is present for the ambiguous query (real clock attached).
            assert(latencyFor(ambiguous.latencyByBackend, M18OfflineEvalBackend.Es).availability == M19LatencyAvailability.Present, "ambiguous ES latency must be present")
            assert(latencyFor(ambiguous.latencyByBackend, M18OfflineEvalBackend.Qdrant).availability == M19LatencyAvailability.Present, "ambiguous Qdrant latency must be present")
            // Lookup not evaluated for the canonical-backed queries too (T/W pattern).
            val ambiguousEsLookup     = lookupCountsFor(ambiguous.lookupByBackend, M18OfflineEvalBackend.Es)
            val ambiguousQdrantLookup = lookupCountsFor(ambiguous.lookupByBackend, M18OfflineEvalBackend.Qdrant)
            assert(ambiguousEsLookup.evaluatedCount == 0, "no ES lookup may be evaluated when no lookup is wired for the ambiguous query")
            assert(ambiguousQdrantLookup.evaluatedCount == 0, "no Qdrant lookup may be evaluated when no lookup is wired for the ambiguous query")

            // ---- Aggregate: a faithful roll-up of the six measured queries (still evidence only). ----
            assert(aggregate.queryCount == perQuery.size, "aggregate query count must equal the number of measured queries")
            assert(aggregate.queryCount == 6, "exactly six queries were measured (3 J-methodology synthetic + 3 canonical-backed)")
            assert(aggregate.queryCount > 3, "H expands beyond the J 3-query methodology slice")
            assert(aggregate.overlapCount == perQuery.map(_.overlapCount).sum)
            assert(aggregate.overlapCount >= 1, "at least one query (the lexical-exact one) must measure ES ∩ Qdrant overlap")
            assert(aggregate.qdrantComplementCount == perQuery.map(_.qdrantComplementCount).sum)
            assert(
              aggregate.qdrantComplementCount >= 1,
              "at least one query (the semantic-complement one) must measure a positive Qdrant complement (now backed by an excluded distractor, not a recall-floor)",
            )
            assert(aggregate.qdrantNoiseCount == perQuery.map(_.qdrantNoiseCount).sum)
            assert(aggregate.qdrantNoiseCount >= 1, "at least one query must measure Qdrant noise")
            // The hard-negative query is the source-confirmed should-stay-silent candidate; its
            // unthresholded noise is measured honestly and the thresholded subcase below measures
            // silence. The hard-negative-or-ambiguous silence signal is the q_hard_negative_diesel
            // query (synthetic) and the q_noise_005_ambiguous query (canonical-backed, also tagged
            // hard_negative in the dataset): both have ES=[] and a non-trivial Qdrant candidate set
            // in the unthresholded pass, which the metric arithmetic above records honestly.
            val negativeOrSilentQueries = perQuery.filter(sc => sc.candidateIds.esCandidateIds.isEmpty)
            assert(
              negativeOrSilentQueries.size >= 2,
              s"at least one negative-or-should-stay-silent query must be measured (hard-negative + ambiguous are both ES-empty), got ${negativeOrSilentQueries.map(_.queryId)}",
            )
            val hardNegativeScorecard = scorecardFor(perQuery, hardNegativeQueryId)
            val ambiguousScorecard    = scorecardFor(perQuery, ambiguousQueryId)
            assert(
              hardNegativeScorecard.candidateIds.qdrantCandidateIds.size <= fixtureTopK,
              "hard-negative unthresholded Qdrant size must be at most topK (honest noise measurement)",
            )
            assert(
              ambiguousScorecard.candidateIds.qdrantCandidateIds.size <= fixtureTopK,
              "ambiguous unthresholded Qdrant size must be at most topK (honest noise measurement)",
            )
            assert(aggregate.expectationsAvailableQueryCount == 6, "all six queries carry expectations, so all expected-aware signals are meaningful")
            // Canonical-backed coverage report (recorded as data, not a pass/fail).
            val canonicalBackedQueryIds = Set(ingredientAttributeQueryId, filterHeavyQueryId, ambiguousQueryId)
            val syntheticFixtureQueryIds = Set(lexicalQueryId, semanticQueryId, hardNegativeQueryId)
            assert(
              perQuery.map(_.queryId).toSet.intersect(canonicalBackedQueryIds) == canonicalBackedQueryIds,
              "all three canonical-backed query ids must be present in the scorecard",
            )
            assert(
              perQuery.map(_.queryId).toSet.intersect(syntheticFixtureQueryIds) == syntheticFixtureQueryIds,
              "all three synthetic fixture query ids must be present in the scorecard",
            )

            // ---- Hard-negative subcase: thresholded Qdrant composition. ----
            // The unthresholded main pass measured honest noise for the hard-negative query. The
            // thresholded subcase (scoreThreshold=$hardNegativeScoreThreshold) attempts honest silence:
            // a fresh, isolated Qdrant collection with the same shared snapshot and a fresh ES index
            // (a separate testSpec is used so the two Qdrant compositions do not collide). Silence
            // is measured as: thresholdedQdrantIds.size <= unthresholdedHardNegQdrantIds.size and
            // (ideally) == 0.
            val unthresholdedHardNegQdrantIds = hardNegQdrantIds.size
            val thresholdedIndexName =
              s"${spec.variantDocument.indexName}_j_th_${UUID.randomUUID().toString.replace('-', '_')}"
            val thresholdedTestSpec = spec.copy(
              variantDocument = spec.variantDocument.copy(indexName = thresholdedIndexName)
            )
            val thresholdedResult = unsafeRun(
              runThresholdedHardNegativeLeg(
                esClient = esClient,
                thresholdedTestSpec = thresholdedTestSpec,
                qdrantClient = qdrantClient,
                embeddingClient = embeddingClient,
                vectorDimension = vector.length,
              )
            )
            assert(
              thresholdedResult.qdrantExecuted,
              s"expected thresholded Qdrant leg to execute, got ${thresholdedResult.qdrant}",
            )
            assert(thresholdedResult.esExecuted, s"expected thresholded ES leg to execute, got ${thresholdedResult.es}")
            val thresholdedPerQuery = M19DualEngineOfflineEvalMetrics.queryMetrics(thresholdedResult)
            assert(thresholdedPerQuery.size == 1, s"thresholded subcase must cover exactly the hard-negative query, got ${thresholdedPerQuery.map(_.queryId)}")
            val thresholdedScorecard = scorecardFor(thresholdedPerQuery, hardNegativeQueryId)
            val thresholdedQdrantIds = thresholdedScorecard.candidateIds.qdrantCandidateIds.toSet
            // Silence measurement: the thresholded Qdrant result must be a strict subset of (or equal
            // to) the unthresholded result, and ideally empty. The lower bound is the honest partial-
            // silence signal: silence_floor = unthresholdedHardNegQdrantIds.size; honest subcase can be
            // anywhere in [0, silence_floor].
            assert(
              thresholdedQdrantIds.size <= unthresholdedHardNegQdrantIds,
              s"thresholded Qdrant result must be a subset of the unthresholded result (or equal) for the hard-negative query, got thresholded=${thresholdedQdrantIds.size} > unthresholded=$unthresholdedHardNegQdrantIds",
            )
            // Expected-mismatch invariant: the out-of-catalog sentinel is never seeded, so any
            // returned row is honest noise (no match).
            assert(
              thresholdedScorecard.qdrantComplementCount == 0,
              s"thresholded subcase must add no useful complement for the hard-negative query, got ${thresholdedScorecard.qdrantComplementCount}",
            )
            assert(
              thresholdedScorecard.qdrantNoiseCount == thresholdedQdrantIds.size,
              s"thresholded subcase Qdrant noise must equal |qdrantIds| (no expected match possible), got ${thresholdedScorecard.qdrantNoiseCount} vs ${thresholdedQdrantIds.size}",
            )
            // Per-leg latency evidence is present for the thresholded leg (real clock attached).
            assert(latencyFor(thresholdedScorecard.latencyByBackend, M18OfflineEvalBackend.Es).availability == M19LatencyAvailability.Present, "thresholded ES latency must be present")
            assert(latencyFor(thresholdedScorecard.latencyByBackend, M18OfflineEvalBackend.Qdrant).availability == M19LatencyAvailability.Present, "thresholded Qdrant latency must be present")
            // Silence status: report the honest truth. Empty = full silence (most informative outcome,
            // no assertions needed). Non-empty = partial silence, which must be a strict reduction
            // vs the unthresholded result (otherwise the threshold is doing nothing).
            thresholdedQdrantIds.isEmpty match {
              case true =>
                () // full silence: the threshold kept Qdrant silent on the hard-negative query
              case false =>
                assert(
                  thresholdedQdrantIds.size < unthresholdedHardNegQdrantIds,
                  s"thresholded Qdrant returned rows; partial silence requires strict reduction vs unthresholded, got thresholded=${thresholdedQdrantIds.size} == unthresholded=$unthresholdedHardNegQdrantIds",
                )
                ()
            }
          // H CLEARED (PARTIALLY on canonical-backed coverage): a per-query ES/Qdrant scorecard
          // was measured from REAL executed candidate evidence over 6 queries covering 5 source-
          // confirmed query roles (lexical/easy, semantic descriptive, negative out-of-catalog,
          // ingredient/attribute, filter-heavy) on the redesigned 8-document / topK=3 fixture
          // (Qdrant cannot return the whole collection for any query). Three of the six queries
          // (q_nails_001_ingredient_attribute, q_nails_003_filter_heavy, q_noise_005_ambiguous)
          // are canonical-backed on text + QueryClass from the canonical 63-query dataset and the
          // M9BeautyQSearchEvalQueryDatasetStaticRows spec anchors. The other three are the J
          // methodology-slice synthetic queries (lexical-exact, semantic-complement, hard-negative).
          // The expected variant id for every canonical-backed query is the J-fixture variantId,
          // NOT the canonical dataset's own acceptableVariantIds, because the seeded collection
          // is a 1-expected + 7-distractor fixture; the canonical text + class are
          // source-confirmed, the expected-id anchoring is J-fixture-only. H is therefore
          // partially cleared (not fully cleared) on the canonical-backed coverage axis. The
          // semantic complement is positive AND backed by at least one excluded distractor (not
          // a recall-floor artifact). The hard-negative unthresholded noise is measured honestly
          // and a thresholded subcase measures silence honestly. The scorecard does NOT assemble
          // a hybrid response and does NOT approve a default route switch.

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
            assert(perQuery.size == 6, s"ES still measures all six queries (3 J-methodology synthetic + 3 canonical-backed), got ${perQuery.map(_.queryId)}")
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
            // H did NOT clear: real Qdrant candidate evidence was absent for the whole query set.
            cancel(
              s"H did not clear the canonical-backed runtime ES/Qdrant scorecard: real ES candidate " +
                s"evidence was measured for all six queries (3 J-methodology synthetic + 3 " +
                s"canonical-backed ingredient_attribute/filter_heavy/ambiguous) but the Qdrant leg " +
                s"was honestly resource-gated (no candidates faked), so no ES-vs-Qdrant scorecard " +
                s"could be computed. $gateReason"
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
          topK = fixtureTopK,
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

  /** Build and run an isolated, thresholded Qdrant composition over the hard-negative query only.
    * Uses a fresh ES index (thresholdedTestSpec) and a fresh Qdrant collection (fresh purpose UUID)
    * so it does not collide with the unthresholded main-pass composition. Reuses sharedDocuments and
    * the existing M18 runner + M19 metrics — no parallel metric layer is introduced. */
  private def runThresholdedHardNegativeLeg(
    esClient: ElasticsearchTestClient,
    thresholdedTestSpec: leaderboard.search.dsl.BeautySearchSpec,
    qdrantClient: QdrantClient,
    embeddingClient: LlamaCppEmbeddingClient,
    vectorDimension: Int,
  ): IO[QueryFailure, M18DualEngineOfflineEvalResult] = {
    val embeddingSpec = EmbeddingSpec[VariantSearchDocument](
      vectorName = "llama-cpp-embedding",
      modelName = "j-runtime-scorecard-thresholded",
      dimension = vectorDimension,
      distance = VectorDistance.Cosine,
      sourceTextFieldPaths = List("serviceText", "attributeText", "allText", "categoryName"),
    )
    val thresholdedReadinessConfig = QdrantCollectionReadinessConfig.derive(
      QdrantCollectionReadinessInput(
        domainName = "beautyq",
        searchSpecVersion = "v1",
        purpose = s"j-runtime-scorecard-th-${UUID.randomUUID().toString.replace('-', '_')}",
        embeddingSpec = embeddingSpec,
        vectorSearchSpec = VectorSearchSpec(
          collectionName = "placeholder",
          vectorName = "llama-cpp-embedding",
          topK = fixtureTopK,
          scoreThreshold = Some(hardNegativeScoreThreshold),
        ),
      )
    )
    val thresholdedCollectionPath = s"/collections/${thresholdedReadinessConfig.collectionName}"
    val snapshotProvider          = new InMemoryVariantSearchDocumentSnapshotProvider[IO](sharedDocuments)
    val compositionFactory        = new QdrantEmbeddingBenchmarkDefaultCompositionFactory(qdrantClient)

    (
      for {
        _           <- prepareEsIndex(thresholdedTestSpec, esClient)
        composition <- compositionFactory.build(thresholdedReadinessConfig, embeddingClient, snapshotProvider, embeddingSpec)
        createJson   = QdrantJsonInterpreter.createCollectionJson(thresholdedReadinessConfig.vectorSearchSpec, embeddingSpec)
        _           <- qdrantClient.createCollection(thresholdedCollectionPath, createJson)
        _           <- composition.indexSnapshot()
        result <- runner.run(
                    dataset = hardNegativeOnlyDataset,
                    esLeg = M18EsLegInput.Connected(esBackendFor(thresholdedTestSpec, esClient), lookup = None),
                    qdrantLeg = M18QdrantLegInput.Connected(composition.semanticBackend, lookup = None),
                  )
      } yield result
    ).ensuring(qdrantClient.deleteCollection(thresholdedCollectionPath).either.unit)
      .ensuring(esClient.deleteIndex(thresholdedTestSpec.variantDocument.indexName).either.unit)
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
      // The expected hair-colouring variant (balayage). Retrieved lexically by query 1 and expected
      // back as a Qdrant complement for query 2.
      syntheticDocument(variantId, lexicalQueryText, "hair", "service_name"),
    ) ++ distractorVariantIds.zip(distractorDescriptors).map { case (id, descriptor) =>
      syntheticDocument(id, descriptor.serviceName, descriptor.categoryName, descriptor.tag)
    }

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
