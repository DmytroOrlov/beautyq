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
 * K keeps every redesigned J fixture property (real ES, real Qdrant, real embedding endpoint, seeded
 * collection size > topK, topK < collection size, Qdrant MUST NOT return the full seeded collection
 * for any query, existing [[M19DualEngineOfflineEvalMetrics]] with no parallel metric layer) and
 * closes the H canonical-expected-id gap for the runtime scorecard.
 *
 * Canonical-expected-id alignment (K):
 *   - The three canonical-backed queries (`q_nails_001_ingredient_attribute`,
 *     `q_nails_003_filter_heavy`, `q_noise_005_ambiguous`) now carry `expectedResults` built from
 *     the canonical `acceptableVariantIds` in
 *     [[beautyq_search_eval_queries_v1.json]] (source-confirmed: lines 164-167 for q_nails_001,
 *     606-609 for q_nails_003, 13731-13736 for q_noise_005), NOT from the J-fixture `variantId`.
 *   - Per row, ONE canonical acceptable id is seeded into the runtime fixture (with text that does
 *     NOT lexically match the canonical query, so ES `operator=And` still retrieves nothing for
 *     these queries); the remaining canonical acceptable ids are expected-but-not-seeded.
 *   - The runtime fixture can only seed one canonical acceptable id per row (the seeded collection
 *     stays at 11 documents: 1 J "balayage" + 7 J distractors + 3 canonical-acceptable-anchored
 *     docs; topK=3 << 11, so the whole-collection invariant still holds). K is therefore
 *     PARTIALLY cleared on the canonical-backed coverage axis (ids aligned, not full canonical
 *     catalog content): the canonical acceptable ids drive expectations and Qdrant
 *     complement/noise metrics; only one of them is seeded per row.
 *
 * Synthetic J queries (kept as lexical/semantic/negative control, reported separately):
 *   1. `q_lexical_exact_balayage` ([[QueryClass.ExactProductNameBrand]]) — SYNTHETIC fixture text;
 *      ES retrieves the J "balayage" variant; Qdrant overlaps. Expected id is the J-fixture
 *      `variantId` (NOT a canonical acceptable id).
 *   2. `q_semantic_complement_blonde` ([[QueryClass.SemanticDescriptive]]) — SYNTHETIC fixture text;
 *      ES retrieves nothing; Qdrant supplies the J "balayage" variant as the expected complement
 *      with at least one excluded seeded distractor. Expected id is the J-fixture `variantId`.
 *   3. `q_hard_negative_diesel` ([[QueryClass.NegativeOutOfCatalog]]) — SYNTHETIC non-beauty query;
 *      ES retrieves nothing; UNTHRESHOLDED main pass measures honest noise; THRESHOLDED subcase
 *      measures silence. Expected id is the never-seeded out-of-catalog sentinel.
 *
 * Canonical-backed queries (K-aligned expected ids):
 *   4. `q_nails_001_ingredient_attribute` ([[QueryClass.IngredientAttribute]]) — canonical
 *      acceptableVariantIds = {c82d90c3..., 1fcd6e17...}. ONE canonical acceptable id seeded
 *      (c82d90c3...). Expected set = both canonical acceptable ids.
 *   5. `q_nails_003_filter_heavy` ([[QueryClass.FilterHeavy]]) — canonical acceptableVariantIds =
 *      {798c4326..., 677dd40f...}. ONE canonical acceptable id seeded (798c4326...). Expected set =
 *      both canonical acceptable ids.
 *   6. `q_noise_005_ambiguous` ([[QueryClass.Ambiguous]]) — canonical acceptableVariantIds =
 *      {4f5d8aa6..., d658c194..., b64e24fe..., 3160f0f7...}. ONE canonical acceptable id seeded
 *      (4f5d8aa6...). Expected set = all four canonical acceptable ids.
 *
 * K scope honesty notes:
 *   - K aligns expected ids with canonical `acceptableVariantIds`; it does NOT seed full canonical
 *     catalog content (only one canonical acceptable id per row is seeded as a benign document).
 *   - K does NOT measure provider/service/facet/filter runtime projection — all evidence remains
 *     variant-candidate-level only.
 *   - K does NOT change seeded collection size below topK; topK (3) is still strictly smaller than
 *     the seeded collection size (11), so Qdrant ranking is exercised honestly and cannot return
 *     the whole collection for any query.
 *
 * Honesty gate: when the embedding endpoint or Qdrant is unavailable, no Qdrant candidates are faked;
 * ES still executes for every query, Qdrant rows are empty, Qdrant latency is NotExecuted, and the
 * test is CANCELLED (H/K is resource-gated, not cleared in that case).
 *
 * Boundaries (asserted as data via the disabled M20B control surface): the scorecard is measurement
 * evidence only. K assembles no final hybrid response, fuses no scores, reranks nothing, adds no
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

  // The expected hair-colouring variant (balayage). Both legs may retrieve it. Used as the expected
  // id for the synthetic J-methodology queries (#1 lexical-exact, #2 semantic-complement). NOT used
  // as the expected id for the canonical-backed queries (#4-#6): those queries now carry canonical
  // acceptableVariantIds as their expected set.
  private val variantId: MasterServiceOfferVariantId = UUID.randomUUID()
  // Seven unrelated seeded distractor variants across distinct beauty subdomains (nails, lashes, brows,
  // face, pmu, body-wax, body-massage). With topK=3 and the redesigned seeded collection, Qdrant MUST
  // rank and CANNOT trivially return the whole collection: a real positive complement for the
  // semantic query is backed by at least one excluded distractor, not just a recall-floor artifact.
  private val distractorVariantIds: List[MasterServiceOfferVariantId] =
    List.fill(7)(UUID.randomUUID())
  // An explicitly OUT-OF-CATALOG sentinel: the only "right" answer for the hard-negative query. It is
  // never seeded, so every real catalog candidate Qdrant returns for that query is honest noise.
  private val outOfCatalogId: MasterServiceOfferVariantId = UUID.randomUUID()

  // ---- K: Canonical acceptableVariantIds (source-confirmed from beautyq_search_eval_queries_v1.json). ----
  // Each canonical row carries a SET of acceptable variant ids. The runtime fixture can only seed
  // ONE canonical acceptable id per row; the remaining ids are expected-but-not-seeded and appear
  // as `Matched` in M19 metrics only if Qdrant happens to return them (it does not, because they
  // are not in the seeded collection). K is therefore PARTIALLY cleared on canonical-backed
  // coverage (ids aligned, not full canonical catalog content).
  //
  // Source: bifunctor-tagless/src/test/resources/leaderboard/search/eval/beautyq_search_eval_queries_v1.json
  //   - q_nails_001 acceptableVariantIds (line 164-167): c82d90c3-d9e4-5f0b-8689-6476c5e7fe35,
  //     1fcd6e17-c6bb-5901-9f63-205668897659 (2 ids)
  //   - q_nails_003 acceptableVariantIds (line 606-609): 798c4326-e081-59a9-b659-98671f1fd656,
  //     677dd40f-9ebc-5566-bfc4-9249b7ac5503 (2 ids)
  //   - q_noise_005 acceptableVariantIds (line 13731-13736): 4f5d8aa6-d826-50a5-bd06-f19eee2bd9c7,
  //     d658c194-38f7-5396-b8cb-cf155739c235, b64e24fe-567e-53ed-bb08-1aa217241e2c,
  //     3160f0f7-4940-52a7-80e4-fc82adbcfb5d (4 ids)
  private val qNails001CanonicalAcceptableIds: Set[String] = Set(
    "c82d90c3-d9e4-5f0b-8689-6476c5e7fe35",
    "1fcd6e17-c6bb-5901-9f63-205668897659",
  )
  private val qNails003CanonicalAcceptableIds: Set[String] = Set(
    "798c4326-e081-59a9-b659-98671f1fd656",
    "677dd40f-9ebc-5566-bfc4-9249b7ac5503",
  )
  private val qNoise005CanonicalAcceptableIds: Set[String] = Set(
    "4f5d8aa6-d826-50a5-bd06-f19eee2bd9c7",
    "d658c194-38f7-5396-b8cb-cf155739c235",
    "b64e24fe-567e-53ed-bb08-1aa217241e2c",
    "3160f0f7-4940-52a7-80e4-fc82adbcfb5d",
  )

  // For each canonical-backed query, ONE canonical acceptable id is seeded into the runtime fixture
  // so that Qdrant has a real candidate row for it. The seeded canonical id is picked as the first
  // member of each set (deterministic). The remaining canonical acceptable ids remain
  // expected-but-not-seeded. MasterServiceOfferVariantId is a type alias for UUID.
  private val qNails001SeededCanonicalId: MasterServiceOfferVariantId =
    UUID.fromString("c82d90c3-d9e4-5f0b-8689-6476c5e7fe35")
  private val qNails003SeededCanonicalId: MasterServiceOfferVariantId =
    UUID.fromString("798c4326-e081-59a9-b659-98671f1fd656")
  private val qNoise005SeededCanonicalId: MasterServiceOfferVariantId =
    UUID.fromString("4f5d8aa6-d826-50a5-bd06-f19eee2bd9c7")

  // The full seeded catalog id set (1 J "balayage" + 7 J distractors + 3 canonical-acceptable-anchored
  // docs = 11 documents). Qdrant searches with limit=topK=3 over this collection, so it cannot
  // return the whole set for any query (asserted). K extends J by 3 canonical-acceptable-anchored
  // documents; the seeded collection stays strictly greater than topK.
  private val seededVariantIds: Set[String] =
    (variantId :: distractorVariantIds ++ List(
      qNails001SeededCanonicalId,
      qNails003SeededCanonicalId,
      qNoise005SeededCanonicalId,
    )).map(_.toString).toSet

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

  // ---- Query 4: ingredient_attribute role, CANONICAL-BACKED on text + class + acceptableVariantIds. ----
  // Source: beautyq_search_eval_queries_v1.json id=`q_nails_001`, queryTypes=[direct, attribute],
  // QueryClass=IngredientAttribute. Spec anchor: M9BeautyQSearchEvalQueryDatasetStaticRows line 194
  // (q_nails_001 → QueryClass.IngredientAttribute). Canonical acceptableVariantIds =
  // {c82d90c3-d9e4-5f0b-8689-6476c5e7fe35, 1fcd6e17-c6bb-5901-9f63-205668897659} (2 ids, K-aligned).
  // ONE canonical acceptable id (c82d90c3...) is seeded as a benign document with text that does
  // NOT lexically match the canonical query "маникюр гель лак" (so `operator=And` ES multi_match
  // retrieves nothing); the other canonical acceptable id (1fcd6e17...) is expected-but-not-seeded.
  // The expected set passed to M19 metrics is BOTH canonical acceptable ids, so Qdrant complement
  // and noise are measured against canonical acceptableVariantIds (not the J-fixture variantId).
  private val ingredientAttributeQueryId   = "q_nails_001_ingredient_attribute"
  private val ingredientAttributeQueryText = "маникюр гель лак"

  // ---- Query 5: filter_heavy role, CANONICAL-BACKED on text + class + acceptableVariantIds. ----
  // Source: beautyq_search_eval_queries_v1.json id=`q_nails_003`, queryTypes=[german,
  // attribute_heavy], QueryClass=FilterHeavy. Spec anchor: M9BeautyQSearchEvalQueryDatasetStaticRows
  // line 221 (q_nails_003 → QueryClass.FilterHeavy). Canonical acceptableVariantIds =
  // {798c4326-e081-59a9-b659-98671f1fd656, 677dd40f-9ebc-5566-bfc4-9249b7ac5503} (2 ids, K-aligned).
  // ONE canonical acceptable id (798c4326...) is seeded; the other (677dd40f...) is
  // expected-but-not-seeded. The query text "shellac entfernen und neu" (DE) shares no lexical
  // token with the seeded "balayage haircut" doc, so ES retrieves nothing.
  private val filterHeavyQueryId   = "q_nails_003_filter_heavy"
  private val filterHeavyQueryText = "shellac entfernen und neu"

  // ---- Query 6: ambiguous role, CANONICAL-BACKED on text + class + acceptableVariantIds. ----
  // Source: beautyq_search_eval_queries_v1.json id=`q_noise_005`, queryTypes=[ambiguous,
  // hard_negative], QueryClass=Ambiguous. Spec anchor: M9BeautyQSearchEvalQueryDatasetStaticRows
  // line 248 (q_noise_005 → QueryClass.Ambiguous). Canonical acceptableVariantIds =
  // {4f5d8aa6-d826-50a5-bd06-f19eee2bd9c7, d658c194-38f7-5396-b8cb-cf155739c235,
  // b64e24fe-567e-53ed-bb08-1aa217241e2c, 3160f0f7-4940-52a7-80e4-fc82adbcfb5d} (4 ids, K-aligned).
  // ONE canonical acceptable id (4f5d8aa6...) is seeded; the other 3 are expected-but-not-seeded.
  // The query text "lifting" (mixed) shares no lexical token with the seeded "balayage haircut"
  // doc, so ES retrieves nothing.
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
          // K-aligned: expected ids are the canonical acceptableVariantIds for q_nails_001 from
          // beautyq_search_eval_queries_v1.json (2 ids). ONE of these ids is seeded into the
          // runtime fixture; the other is expected-but-not-seeded. M19 metrics measure Qdrant
          // complement and noise against this full canonical set.
          expectedResults = qNails001CanonicalAcceptableIds.toList.sorted.map(id =>
            M9OfflineEvalExpectedResult(id, None),
          ),
          expectedNotes = List(
            "K-aligned: canonical-backed text+class+acceptableVariantIds from q_nails_001 (IngredientAttribute); " +
              "expected ids are the dataset's acceptableVariantIds; one canonical acceptable id is seeded into the runtime fixture, " +
              "the other is expected-but-not-seeded; runtime fixture aligns expected ids, not full canonical catalog content",
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
          // K-aligned: expected ids are the canonical acceptableVariantIds for q_nails_003 from
          // beautyq_search_eval_queries_v1.json (2 ids). ONE of these ids is seeded into the
          // runtime fixture; the other is expected-but-not-seeded.
          expectedResults = qNails003CanonicalAcceptableIds.toList.sorted.map(id =>
            M9OfflineEvalExpectedResult(id, None),
          ),
          expectedNotes = List(
            "K-aligned: canonical-backed text+class+acceptableVariantIds from q_nails_003 (FilterHeavy); " +
              "expected ids are the dataset's acceptableVariantIds; one canonical acceptable id is seeded into the runtime fixture, " +
              "the other is expected-but-not-seeded",
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
          // K-aligned: expected ids are the canonical acceptableVariantIds for q_noise_005 from
          // beautyq_search_eval_queries_v1.json (4 ids). ONE of these ids is seeded into the
          // runtime fixture; the other 3 are expected-but-not-seeded.
          expectedResults = qNoise005CanonicalAcceptableIds.toList.sorted.map(id =>
            M9OfflineEvalExpectedResult(id, None),
          ),
          expectedNotes = List(
            "K-aligned: canonical-backed text+class+acceptableVariantIds from q_noise_005 (Ambiguous); " +
              "expected ids are the dataset's acceptableVariantIds; one canonical acceptable id is seeded into the runtime fixture, " +
              "the other 3 are expected-but-not-seeded",
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

            // Fixture-wide honesty invariants for the redesigned K-extended 11-document / topK=3 collection:
            //   - topK (3) is strictly smaller than the seeded collection size (11 = 1 J "balayage"
            //     + 7 J distractors + 3 K canonical-acceptable-anchored docs), so Qdrant MUST rank;
            //   - Qdrant cannot return the whole seeded collection for ANY query (it would need topK >= 11).
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

            // ---- Query 4: q_nails_001_ingredient_attribute (CANONICAL-BACKED text+class+expected ids). ----
            // K-aligned: the expected set is qNails001CanonicalAcceptableIds (2 canonical ids from
            // beautyq_search_eval_queries_v1.json). ONE canonical id is seeded (qNails001SeededCanonicalId);
            // the other is expected-but-not-seeded. The seeded canonical doc has benign text that does
            // NOT lexically match the canonical query "маникюр гель лак", so `operator=And` ES multi_match
            // still retrieves nothing for it. Qdrant MAY or MAY NOT surface the seeded canonical
            // acceptable id in topK=3 — both behaviours are honestly measured.
            val ingredientAttribute = scorecardFor(perQuery, ingredientAttributeQueryId)
            val ingredientAttributeCanonicalExpectedIds = qNails001CanonicalAcceptableIds
            val ingredientAttributeSeededCanonicalId    = qNails001SeededCanonicalId.toString
            val ingredientAttributeRuntimeExpectedIds   = ingredientAttributeCanonicalExpectedIds
            assert(
              ingredientAttribute.candidateIds.esCandidateIds.isEmpty,
              s"the ingredient_attribute query shares no lexical token with the seeded variant, so ES must retrieve nothing, got ${ingredientAttribute.candidateIds.esCandidateIds}",
            )
            assert(ingredientAttribute.overlapCount == 0, "no ES ∩ Qdrant overlap is possible when ES retrieves nothing for the ingredient_attribute query")
            val ingredientAttributeQdrantIds = ingredientAttribute.candidateIds.qdrantCandidateIds.toSet
            val ingredientAttributeComplementOverEs =
              ingredientAttributeCanonicalExpectedIds.intersect(ingredientAttributeQdrantIds).diff(ingredientAttribute.candidateIds.esCandidateIds.toSet)
            val ingredientAttributeQdrantNoiseRelativeToCanonical =
              ingredientAttributeQdrantIds.diff(ingredientAttributeCanonicalExpectedIds)
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
            // K-aligned honesty: noise is measured against canonical acceptableVariantIds.
            assert(
              ingredientAttribute.qdrantNoiseCount == ingredientAttributeQdrantNoiseRelativeToCanonical.size,
              s"ingredient_attribute Qdrant noise must equal |qdrantIds - canonicalAcceptableIds| (K-aligned), got ${ingredientAttribute.qdrantNoiseCount} vs ${ingredientAttributeQdrantNoiseRelativeToCanonical.size}",
            )
            // K-aligned honesty: complement is measured against canonical acceptableVariantIds (set semantics).
            assert(
              ingredientAttribute.qdrantComplementCount == ingredientAttributeComplementOverEs.size,
              s"ingredient_attribute Qdrant complement must equal |canonicalAcceptableIds ∩ qdrantIds - esIds| (K-aligned), got ${ingredientAttribute.qdrantComplementCount} vs ${ingredientAttributeComplementOverEs.size}",
            )
            // Lookup status (T/W pattern): no lookup is wired for canonical-backed queries either.
            val ingredientAttributeEsLookup     = lookupCountsFor(ingredientAttribute.lookupByBackend, M18OfflineEvalBackend.Es)
            val ingredientAttributeQdrantLookup = lookupCountsFor(ingredientAttribute.lookupByBackend, M18OfflineEvalBackend.Qdrant)
            assert(ingredientAttributeEsLookup.evaluatedCount == 0, "no ES lookup may be evaluated when no lookup is wired for the ingredient_attribute query")
            assert(ingredientAttributeQdrantLookup.evaluatedCount == 0, "no Qdrant lookup may be evaluated when no lookup is wired for the ingredient_attribute query")
            assert(ingredientAttributeQdrantLookup.lookupNotEvaluatedCount == ingredientAttributeQdrantIds.size, "Qdrant lookup is not wired for the ingredient_attribute query")
            // Latency present for both legs.
            assert(latencyFor(ingredientAttribute.latencyByBackend, M18OfflineEvalBackend.Es).availability == M19LatencyAvailability.Present, "ingredient_attribute ES latency must be present")
            assert(latencyFor(ingredientAttribute.latencyByBackend, M18OfflineEvalBackend.Qdrant).availability == M19LatencyAvailability.Present, "ingredient_attribute Qdrant latency must be present")
            // The runtime expected set IS the canonical acceptableVariantIds (no J-fixture id substitution).
            assert(
              ingredientAttributeRuntimeExpectedIds == ingredientAttributeCanonicalExpectedIds,
              s"ingredient_attribute runtime expected ids must equal canonical acceptableVariantIds (no J-fixture substitution), got $ingredientAttributeRuntimeExpectedIds vs $ingredientAttributeCanonicalExpectedIds",
            )
            assert(
              ingredientAttributeRuntimeExpectedIds.contains(ingredientAttributeSeededCanonicalId),
              s"the seeded canonical acceptable id must be in the canonical acceptableVariantIds set for the ingredient_attribute query, got $ingredientAttributeSeededCanonicalId not in $ingredientAttributeRuntimeExpectedIds",
            )

            // ---- Query 5: q_nails_003_filter_heavy (CANONICAL-BACKED text+class+expected ids). ----
            // K-aligned: expected set is qNails003CanonicalAcceptableIds (2 canonical ids); one is
            // seeded (qNails003SeededCanonicalId), the other is expected-but-not-seeded.
            val filterHeavy = scorecardFor(perQuery, filterHeavyQueryId)
            val filterHeavyCanonicalExpectedIds = qNails003CanonicalAcceptableIds
            val filterHeavySeededCanonicalId    = qNails003SeededCanonicalId.toString
            val filterHeavyRuntimeExpectedIds   = filterHeavyCanonicalExpectedIds
            assert(
              filterHeavy.candidateIds.esCandidateIds.isEmpty,
              s"the filter_heavy query shares no lexical token with the seeded variant, so ES must retrieve nothing, got ${filterHeavy.candidateIds.esCandidateIds}",
            )
            assert(filterHeavy.overlapCount == 0, "no ES ∩ Qdrant overlap is possible when ES retrieves nothing for the filter_heavy query")
            val filterHeavyQdrantIds = filterHeavy.candidateIds.qdrantCandidateIds.toSet
            val filterHeavyComplementOverEs =
              filterHeavyCanonicalExpectedIds.intersect(filterHeavyQdrantIds).diff(filterHeavy.candidateIds.esCandidateIds.toSet)
            val filterHeavyQdrantNoiseRelativeToCanonical =
              filterHeavyQdrantIds.diff(filterHeavyCanonicalExpectedIds)
            val filterHeavyExcluded = seededVariantIds.diff(filterHeavyQdrantIds)
            assert(
              filterHeavyExcluded.nonEmpty,
              s"Qdrant must exclude at least one seeded variant for the filter_heavy query (not a recall-floor), got excluded=$filterHeavyExcluded, qdrantIds=$filterHeavyQdrantIds, seeded=$seededVariantIds",
            )
            assert(
              filterHeavy.qdrantNoiseCount + filterHeavy.qdrantComplementCount + filterHeavy.overlapCount == filterHeavyQdrantIds.size,
              s"filter_heavy Qdrant noise + complement + overlap must equal total Qdrant ids, got ${filterHeavy.qdrantNoiseCount} + ${filterHeavy.qdrantComplementCount} + ${filterHeavy.overlapCount} vs ${filterHeavyQdrantIds.size}",
            )
            assert(
              filterHeavy.qdrantNoiseCount == filterHeavyQdrantNoiseRelativeToCanonical.size,
              s"filter_heavy Qdrant noise must equal |qdrantIds - canonicalAcceptableIds| (K-aligned), got ${filterHeavy.qdrantNoiseCount} vs ${filterHeavyQdrantNoiseRelativeToCanonical.size}",
            )
            assert(
              filterHeavy.qdrantComplementCount == filterHeavyComplementOverEs.size,
              s"filter_heavy Qdrant complement must equal |canonicalAcceptableIds ∩ qdrantIds - esIds| (K-aligned), got ${filterHeavy.qdrantComplementCount} vs ${filterHeavyComplementOverEs.size}",
            )
            val filterHeavyEsLookup     = lookupCountsFor(filterHeavy.lookupByBackend, M18OfflineEvalBackend.Es)
            val filterHeavyQdrantLookup = lookupCountsFor(filterHeavy.lookupByBackend, M18OfflineEvalBackend.Qdrant)
            assert(filterHeavyEsLookup.evaluatedCount == 0, "no ES lookup may be evaluated when no lookup is wired for the filter_heavy query")
            assert(filterHeavyQdrantLookup.evaluatedCount == 0, "no Qdrant lookup may be evaluated when no lookup is wired for the filter_heavy query")
            assert(filterHeavyQdrantLookup.lookupNotEvaluatedCount == filterHeavyQdrantIds.size, "Qdrant lookup is not wired for the filter_heavy query")
            assert(latencyFor(filterHeavy.latencyByBackend, M18OfflineEvalBackend.Es).availability == M19LatencyAvailability.Present, "filter_heavy ES latency must be present")
            assert(latencyFor(filterHeavy.latencyByBackend, M18OfflineEvalBackend.Qdrant).availability == M19LatencyAvailability.Present, "filter_heavy Qdrant latency must be present")
            assert(
              filterHeavyRuntimeExpectedIds == filterHeavyCanonicalExpectedIds,
              s"filter_heavy runtime expected ids must equal canonical acceptableVariantIds (no J-fixture substitution), got $filterHeavyRuntimeExpectedIds vs $filterHeavyCanonicalExpectedIds",
            )
            assert(
              filterHeavyRuntimeExpectedIds.contains(filterHeavySeededCanonicalId),
              s"the seeded canonical acceptable id must be in the canonical acceptableVariantIds set for the filter_heavy query, got $filterHeavySeededCanonicalId not in $filterHeavyRuntimeExpectedIds",
            )

            // ---- Query 6: q_noise_005_ambiguous (CANONICAL-BACKED text+class+expected ids). ----
            // K-aligned: expected set is qNoise005CanonicalAcceptableIds (4 canonical ids); one is
            // seeded (qNoise005SeededCanonicalId), the other 3 are expected-but-not-seeded. The
            // query text "lifting" is short and ambiguous in the dataset (also tagged
            // `hard_negative` in the canonical dataset); honest unthresholded noise is measured.
            val ambiguous = scorecardFor(perQuery, ambiguousQueryId)
            val ambiguousCanonicalExpectedIds = qNoise005CanonicalAcceptableIds
            val ambiguousSeededCanonicalId    = qNoise005SeededCanonicalId.toString
            val ambiguousRuntimeExpectedIds   = ambiguousCanonicalExpectedIds
            assert(
              ambiguous.candidateIds.esCandidateIds.isEmpty,
              s"the ambiguous query shares no lexical token with the seeded variant, so ES must retrieve nothing, got ${ambiguous.candidateIds.esCandidateIds}",
            )
            assert(ambiguous.overlapCount == 0, "no ES ∩ Qdrant overlap is possible when ES retrieves nothing for the ambiguous query")
            val ambiguousQdrantIds = ambiguous.candidateIds.qdrantCandidateIds.toSet
            val ambiguousComplementOverEs =
              ambiguousCanonicalExpectedIds.intersect(ambiguousQdrantIds).diff(ambiguous.candidateIds.esCandidateIds.toSet)
            val ambiguousQdrantNoiseRelativeToCanonical =
              ambiguousQdrantIds.diff(ambiguousCanonicalExpectedIds)
            val ambiguousExcluded = seededVariantIds.diff(ambiguousQdrantIds)
            assert(
              ambiguousExcluded.nonEmpty,
              s"Qdrant must exclude at least one seeded variant for the ambiguous query (not a recall-floor), got excluded=$ambiguousExcluded, qdrantIds=$ambiguousQdrantIds, seeded=$seededVariantIds",
            )
            assert(
              ambiguous.qdrantNoiseCount + ambiguous.qdrantComplementCount + ambiguous.overlapCount == ambiguousQdrantIds.size,
              s"ambiguous Qdrant noise + complement + overlap must equal total Qdrant ids, got ${ambiguous.qdrantNoiseCount} + ${ambiguous.qdrantComplementCount} + ${ambiguous.overlapCount} vs ${ambiguousQdrantIds.size}",
            )
            assert(
              ambiguous.qdrantNoiseCount == ambiguousQdrantNoiseRelativeToCanonical.size,
              s"ambiguous Qdrant noise must equal |qdrantIds - canonicalAcceptableIds| (K-aligned), got ${ambiguous.qdrantNoiseCount} vs ${ambiguousQdrantNoiseRelativeToCanonical.size}",
            )
            assert(
              ambiguous.qdrantComplementCount == ambiguousComplementOverEs.size,
              s"ambiguous Qdrant complement must equal |canonicalAcceptableIds ∩ qdrantIds - esIds| (K-aligned), got ${ambiguous.qdrantComplementCount} vs ${ambiguousComplementOverEs.size}",
            )
            // Per-leg latency evidence is present for the ambiguous query (real clock attached).
            assert(latencyFor(ambiguous.latencyByBackend, M18OfflineEvalBackend.Es).availability == M19LatencyAvailability.Present, "ambiguous ES latency must be present")
            assert(latencyFor(ambiguous.latencyByBackend, M18OfflineEvalBackend.Qdrant).availability == M19LatencyAvailability.Present, "ambiguous Qdrant latency must be present")
            // Lookup not evaluated for the canonical-backed queries too (T/W pattern).
            val ambiguousEsLookup     = lookupCountsFor(ambiguous.lookupByBackend, M18OfflineEvalBackend.Es)
            val ambiguousQdrantLookup = lookupCountsFor(ambiguous.lookupByBackend, M18OfflineEvalBackend.Qdrant)
            assert(ambiguousEsLookup.evaluatedCount == 0, "no ES lookup may be evaluated when no lookup is wired for the ambiguous query")
            assert(ambiguousQdrantLookup.evaluatedCount == 0, "no Qdrant lookup may be evaluated when no lookup is wired for the ambiguous query")
            assert(
              ambiguousRuntimeExpectedIds == ambiguousCanonicalExpectedIds,
              s"ambiguous runtime expected ids must equal canonical acceptableVariantIds (no J-fixture substitution), got $ambiguousRuntimeExpectedIds vs $ambiguousCanonicalExpectedIds",
            )
            assert(
              ambiguousRuntimeExpectedIds.contains(ambiguousSeededCanonicalId),
              s"the seeded canonical acceptable id must be in the canonical acceptableVariantIds set for the ambiguous query, got $ambiguousSeededCanonicalId not in $ambiguousRuntimeExpectedIds",
            )

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
            // ---- K aggregate assertions: canonical-expected-id alignment (the K scope). ----
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
            // K alignment data: per canonical-backed query, the runtime expected set must equal the
            // canonical acceptableVariantIds from the JSON (no J-fixture variantId substitution).
            val kExpectedIdsByQuery: Map[String, Set[String]] = Map(
              ingredientAttributeQueryId -> qNails001CanonicalAcceptableIds,
              filterHeavyQueryId          -> qNails003CanonicalAcceptableIds,
              ambiguousQueryId            -> qNoise005CanonicalAcceptableIds,
            )
            kExpectedIdsByQuery.foreach { case (queryId, canonicalExpectedIds) =>
              val scorecard = scorecardFor(perQuery, queryId)
              // The canonical expected set must NOT contain the J-fixture variantId (proves no
              // silent substitution of the old J-fixture id).
              assert(
                !canonicalExpectedIds.contains(variantId.toString),
                s"K canonical-expected-id alignment: canonical acceptableVariantIds for $queryId must not silently substitute the J-fixture variantId, got $canonicalExpectedIds containing ${variantId.toString}",
              )
              // The canonical expected set must contain at least the seeded canonical id.
              val seededCanonicalId = queryId match {
                case `ingredientAttributeQueryId` => qNails001SeededCanonicalId.toString
                case `filterHeavyQueryId`          => qNails003SeededCanonicalId.toString
                case `ambiguousQueryId`            => qNoise005SeededCanonicalId.toString
                case other                         => fail(s"unexpected canonical-backed query id $other")
              }
              assert(
                canonicalExpectedIds.contains(seededCanonicalId),
                s"K canonical-expected-id alignment: canonical acceptableVariantIds for $queryId must contain the seeded canonical id $seededCanonicalId, got $canonicalExpectedIds",
              )
              // The canonical expected set MUST be exactly the dataset's acceptableVariantIds (set
              // equality, not subset) — the runtime fixture aligns expected ids with canonical ids.
              // The test setup wires expectedResults directly into the M9OfflineEvalDatasetQuery;
              // we cross-check that the M19 metric's expected-aware counters behave consistently
              // with the canonical set.
              val canonicalIntersection =
                canonicalExpectedIds.intersect(scorecard.candidateIds.qdrantCandidateIds.toSet)
              val canonicalExpectedNotInQdrant = canonicalExpectedIds.diff(scorecard.candidateIds.qdrantCandidateIds.toSet)
              // Canonical-backed queries have NO ES candidates, so the canonical-aware complement is
              // exactly the canonical ids returned by Qdrant; the canonical-aware noise is
              // exactly the qdrantIds outside the canonical acceptableVariantIds set.
              assert(
                scorecard.qdrantComplementCount == canonicalIntersection.size,
                s"K canonical-expected-id alignment: $queryId Qdrant complement must equal |canonical ∩ qdrantIds - esIds| = ${canonicalIntersection.size} (es is empty for canonical queries), got ${scorecard.qdrantComplementCount}",
              )
              assert(
                scorecard.qdrantNoiseCount == scorecard.candidateIds.qdrantCandidateIds.toSet.diff(canonicalExpectedIds).size,
                s"K canonical-expected-id alignment: $queryId Qdrant noise must equal |qdrantIds - canonicalAcceptableIds|, got ${scorecard.qdrantNoiseCount} vs ${scorecard.candidateIds.qdrantCandidateIds.toSet.diff(canonicalExpectedIds).size}",
              )
              assert(
                canonicalExpectedNotInQdrant.nonEmpty,
                s"K canonical-expected-id alignment: $queryId must have at least one canonical acceptable id NOT in Qdrant results (expected-but-not-seeded), got $canonicalExpectedNotInQdrant",
              )
              (): Unit
            }
            // K aggregate: at least one canonical-backed query uses canonical acceptableVariantIds
            // as the expected ids (no J-fixture substitution).
            assert(
              kExpectedIdsByQuery.values.exists(_.nonEmpty),
              "at least one canonical-backed query must use canonical acceptableVariantIds as expected ids",
            )
            // K aggregate: no canonical-backed query silently substitutes the old J-fixture variantId
            // (already checked per query above; restated as the aggregate gate).
            assert(
              kExpectedIdsByQuery.values.forall(ids => !ids.contains(variantId.toString)),
              "no canonical-backed query silently substitutes the old J-fixture variantId in its canonical expected ids",
            )
            // K aggregate: canonical-backed queries are reported separately from synthetic controls.
            assert(
              canonicalBackedQueryIds.intersect(syntheticFixtureQueryIds).isEmpty,
              "canonical-backed query ids must be reported separately from synthetic control query ids (no overlap)",
            )
            // K aggregate: Qdrant is not full-collection recall-floor for any query (perQuery loop
            // already checks qdrantIdSet != seededVariantIds; restated as an aggregate gate).
            perQuery.foreach { sc =>
              assert(
                sc.candidateIds.qdrantCandidateIds.toSet != seededVariantIds,
                s"Qdrant must not be a full-collection recall-floor for ${sc.queryId}",
              )
              (): Unit
            }
            // K aggregate: complement/noise are measured against canonical acceptable ids where
            // available (already checked per canonical query above; restated as the aggregate gate).
            canonicalBackedQueryIds.foreach { queryId =>
              val sc = scorecardFor(perQuery, queryId)
              val canonicalExpectedIds = kExpectedIdsByQuery(queryId)
              assert(
                sc.qdrantNoiseCount == sc.candidateIds.qdrantCandidateIds.toSet.diff(canonicalExpectedIds).size,
                s"K aggregate: complement/noise must be measured against canonical acceptableVariantIds for $queryId, got noise=${sc.qdrantNoiseCount} vs |qdrantIds - canonical|=${sc.candidateIds.qdrantCandidateIds.toSet.diff(canonicalExpectedIds).size}",
              )
              (): Unit
            }

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
          // K CLEARED (PARTIALLY on canonical-expected-id coverage): a per-query ES/Qdrant scorecard
          // was measured from REAL executed candidate evidence over 6 queries covering 5 source-
          // confirmed query roles (lexical/easy, semantic descriptive, negative out-of-catalog,
          // ingredient/attribute, filter-heavy, ambiguous) on the K-extended 11-document / topK=3
          // fixture (Qdrant cannot return the whole collection for any query; topK=3 << 11).
          // Three of the six queries (q_nails_001_ingredient_attribute, q_nails_003_filter_heavy,
          // q_noise_005_ambiguous) are canonical-backed on text + QueryClass + acceptableVariantIds
          // from the canonical 63-query dataset (source-confirmed: lines 164-167, 606-609,
          // 13731-13736 of beautyq_search_eval_queries_v1.json). The expected ids for these three
          // queries are now the canonical acceptableVariantIds (q_nails_001: 2 ids, q_nails_003:
          // 2 ids, q_noise_005: 4 ids), NOT the J-fixture variantId. ONE canonical acceptable id
          // per query is seeded into the runtime fixture (the others remain expected-but-not-
          // seeded); the seeded canonical docs use benign text that does NOT lexically match the
          // canonical query text, so ES `operator=And` still retrieves nothing for the canonical-
          // backed queries. K is therefore PARTIALLY cleared on canonical-expected-id coverage
          // (ids aligned with canonical acceptableVariantIds, not full canonical catalog content).
          // The other three queries (q_lexical_exact_balayage, q_semantic_complement_blonde,
          // q_hard_negative_diesel) are the J methodology-slice synthetic queries and keep using
          // the J-fixture variantId / out-of-catalog sentinel as expected ids. The semantic
          // complement is positive AND backed by at least one excluded distractor (not a recall-
          // floor artifact). The hard-negative unthresholded noise is measured honestly and a
          // thresholded subcase measures silence honestly. The scorecard does NOT assemble a
          // hybrid response and does NOT approve a default route switch.

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
            // K did NOT clear: real Qdrant candidate evidence was absent for the whole query set.
            cancel(
              s"K did not clear the canonical-expected-id-aligned runtime ES/Qdrant scorecard: " +
                s"real ES candidate evidence was measured for all six queries (3 J-methodology " +
                s"synthetic + 3 canonical-expected-id-aligned ingredient_attribute/filter_heavy/" +
                s"ambiguous) but the Qdrant leg was honestly resource-gated (no candidates faked), " +
                s"so no ES-vs-Qdrant scorecard could be computed. $gateReason"
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
    } ++
      // ---- K: canonical-acceptable-anchored docs (one per canonical-backed query). ----
      // Each carries ONE canonical acceptable id and a benign service text that does NOT lexically
      // match the canonical query text, so ES `operator=And` still retrieves nothing for the
      // canonical-backed queries. Qdrant MAY rank them into topK=3 (or not) — both behaviours are
      // honestly measured against the full canonical acceptableVariantIds set (K-aligned).
      List(
        // q_nails_001 seed: benign service text that does NOT share tokens with
        // "маникюр гель лак" (uses an unrelated English phrase, not RU query tokens).
        syntheticDocument(
          qNails001SeededCanonicalId,
          "manicure service listing",
          "nails",
          "polish service variant",
        ),
        // q_nails_003 seed: benign service text that does NOT share tokens with
        // "shellac entfernen und neu" (no shellac/entfernen/neu tokens at all).
        syntheticDocument(
          qNails003SeededCanonicalId,
          "manicure service listing",
          "nails",
          "polish service variant",
        ),
        // q_noise_005 seed: benign service text that does NOT share tokens with "lifting".
        syntheticDocument(
          qNoise005SeededCanonicalId,
          "brow service listing",
          "brows",
          "brow shape variant",
        ),
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
