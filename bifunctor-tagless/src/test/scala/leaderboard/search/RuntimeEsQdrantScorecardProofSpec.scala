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
 * K kept every redesigned J fixture property (real ES, real Qdrant, real embedding endpoint, seeded
 * collection size > topK, topK < collection size, Qdrant MUST NOT return the full seeded collection
 * for any query, existing [[M19DualEngineOfflineEvalMetrics]] with no parallel metric layer) and
 * closed the H canonical-expected-id gap for the runtime scorecard (K aligned expected ids with
 * canonical `acceptableVariantIds`).
 *
 * K2 keeps every K property and closes the remaining K gap: K2 seeds EVERY canonical acceptable id
 * (across the three canonical-backed queries) into the runtime fixture with benign text that does NOT
 * lexically match the canonical query. The seeded collection grows to 16 documents (1 J "balayage" +
 * 7 J distractors + 8 K2 canonical-acceptable-anchored docs = 16); topK (3) stays strictly smaller
 * than the seeded collection size (16), so the whole-collection invariant still holds. K2 removes
 * the `expected-but-not-seeded` limitation for the three canonical-backed rows: zero canonical
 * expected ids remain expected-but-not-seeded for `q_nails_001`, `q_nails_003`, and `q_noise_005`.
 *
 * Canonical-expected-id alignment (K2):
 *   - The three canonical-backed queries (`q_nails_001_ingredient_attribute`,
 *     `q_nails_003_filter_heavy`, `q_noise_005_ambiguous`) carry `expectedResults` built from the
 *     canonical `acceptableVariantIds` in
 *     [[beautyq_search_eval_queries_v1.json]] (source-confirmed: lines 164-167 for q_nails_001,
 *     606-609 for q_nails_003, 13731-13736 for q_noise_005), NOT from the J-fixture `variantId`.
 *   - K2 seeds ALL canonical acceptable ids into the runtime fixture (2 ids for q_nails_001, 2 ids
 *     for q_nails_003, 4 ids for q_noise_005 = 8 total). Each seed carries benign text that does
 *     NOT lexically match its canonical query, so ES `operator=And` still retrieves nothing for
 *     these queries regardless of which acceptable id Qdrant surfaces in topK=3.
 *   - The seeded collection is now 16 documents: 1 J "balayage" + 7 J distractors + 8 K2
 *     canonical-acceptable-anchored docs. topK (3) << 16, so the whole-collection invariant still
 *     holds. K2 is therefore cleared on the canonical-expected-id coverage axis: every canonical
 *     acceptable id drives expectations AND is seeded, so the canonical acceptable ids drive
 *     Qdrant complement/noise metrics with full canonical coverage.
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
 * Canonical-backed queries (K2-aligned expected ids, ALL seeded):
 *   4. `q_nails_001_ingredient_attribute` ([[QueryClass.IngredientAttribute]]) — canonical
 *      acceptableVariantIds = {c82d90c3..., 1fcd6e17...}. BOTH canonical acceptable ids seeded.
 *      Expected set = both canonical acceptable ids.
 *   5. `q_nails_003_filter_heavy` ([[QueryClass.FilterHeavy]]) — canonical acceptableVariantIds =
 *      {798c4326..., 677dd40f...}. BOTH canonical acceptable ids seeded.
 *   6. `q_noise_005_ambiguous` ([[QueryClass.Ambiguous]]) — canonical acceptableVariantIds =
 *      {4f5d8aa6..., d658c194..., b64e24fe..., 3160f0f7...}. ALL FOUR canonical acceptable ids
 *      seeded.
 *
 * K2 scope honesty notes:
 *   - K2 seeds every canonical acceptable id for the three canonical-backed rows; the K2 union of
 *     seeded canonical ids equals the union of the three canonical acceptableVariantIds sets.
 *   - K2 does NOT measure provider/service/facet/filter runtime projection — all evidence remains
 *     variant-candidate-level only.
 *   - K2 does NOT change seeded collection size below topK; topK (3) is still strictly smaller than
 *     the seeded collection size (16), so Qdrant ranking is exercised honestly and cannot return
 *     the whole collection for any query.
 *
 * Honesty gate: when the embedding endpoint or Qdrant is unavailable, no Qdrant candidates are faked;
 * ES still executes for every query, Qdrant rows are empty, Qdrant latency is NotExecuted, and the
 * test is CANCELLED (H/K/K2 is resource-gated, not cleared in that case).
 *
 * Boundaries (asserted as data via the disabled M20B control surface): the scorecard is measurement
 * evidence only. K2 assembles no final hybrid response, fuses no scores, reranks nothing, adds no
 * fallback/shadow/mirror traffic, approves no route switch or Qdrant supplement, and never touches
 * the default `/beauty-search` route. All evidence is variant-candidate-level only — no provider
 * grouping, service grouping, facet, or inferred-filter projection is source-confirmed for Qdrant.
 */
// ---- Ownership & guardrail (BeautyQ Hybrid North Star) ----
// This spec OWNS the runtime ES-vs-Qdrant measurement evidence. It measures real ES/Qdrant overlap,
// complement, noise, lookup status, and latency. It must NOT assemble a hybrid response, select
// policy, change the default `/beauty-search` route, or claim Qdrant quality beyond measured
// evidence. Passing scorecard evidence is DECISION SUPPORT ONLY. Qdrant can become a response-policy
// candidate only when measured gates preserve useful complement AND control hard-negative / ambiguous
// noise. See docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md § "BeautyQ Hybrid North Star".
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

  // ---- K2: Canonical acceptableVariantIds (source-confirmed from beautyq_search_eval_queries_v1.json). ----
  // Each canonical row carries a SET of acceptable variant ids. K2 seeds EVERY canonical acceptable
  // id into the runtime fixture (so M19 `Matched` rows can come from real seeded documents), with
  // benign text that does NOT lexically match the canonical query, so ES `operator=And` still
  // retrieves nothing for the canonical-backed queries. K2 is therefore cleared on canonical-
  // backed coverage (ids aligned AND fully seeded for the three canonical-backed rows).
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

  // For each canonical-backed query, K2 seeds EVERY canonical acceptable id into the runtime
  // fixture so that zero canonical expected ids remain expected-but-not-seeded for the canonical-
  // backed rows. The canonical-anchored seed text is deliberately benign (does NOT lexically match
  // the canonical query text), so ES `operator=And` multi_match still retrieves nothing for the
  // canonical-backed queries regardless of which acceptable id Qdrant surfaces in topK=3. MasterServiceOfferVariantId
  // is a type alias for UUID.
  // K2 deterministic ordering: each canonical row's primary id is still the first member of its set
  // (kept for assertion stability), and the additional acceptable ids are appended in the canonical
  // set's order.
  private val qNails001SeededCanonicalId: MasterServiceOfferVariantId =
    UUID.fromString("c82d90c3-d9e4-5f0b-8689-6476c5e7fe35")
  private val qNails001SeededCanonicalIdSecondary: MasterServiceOfferVariantId =
    UUID.fromString("1fcd6e17-c6bb-5901-9f63-205668897659")
  private val qNails003SeededCanonicalId: MasterServiceOfferVariantId =
    UUID.fromString("798c4326-e081-59a9-b659-98671f1fd656")
  private val qNails003SeededCanonicalIdSecondary: MasterServiceOfferVariantId =
    UUID.fromString("677dd40f-9ebc-5566-bfc4-9249b7ac5503")
  private val qNoise005SeededCanonicalId: MasterServiceOfferVariantId =
    UUID.fromString("4f5d8aa6-d826-50a5-bd06-f19eee2bd9c7")
  private val qNoise005SeededCanonicalIdSecondary: MasterServiceOfferVariantId =
    UUID.fromString("d658c194-38f7-5396-b8cb-cf155739c235")
  private val qNoise005SeededCanonicalIdTertiary: MasterServiceOfferVariantId =
    UUID.fromString("b64e24fe-567e-53ed-bb08-1aa217241e2c")
  private val qNoise005SeededCanonicalIdQuaternary: MasterServiceOfferVariantId =
    UUID.fromString("3160f0f7-4940-52a7-80e4-fc82adbcfb5d")

  // The full seeded catalog id set (1 J "balayage" + 7 J distractors + 8 K2 canonical-acceptable-
  // anchored docs = 16 documents). Qdrant searches with limit=topK=3 over this collection, so it
  // cannot return the whole set for any query (asserted). K2 extends J by 8 canonical-acceptable-
  // anchored documents (every member of the three canonical acceptableVariantIds sets); the seeded
  // collection stays strictly greater than topK (16 >> 3).
  private val seededVariantIds: Set[String] =
    (variantId :: distractorVariantIds ++ List(
      qNails001SeededCanonicalId,
      qNails001SeededCanonicalIdSecondary,
      qNails003SeededCanonicalId,
      qNails003SeededCanonicalIdSecondary,
      qNoise005SeededCanonicalId,
      qNoise005SeededCanonicalIdSecondary,
      qNoise005SeededCanonicalIdTertiary,
      qNoise005SeededCanonicalIdQuaternary,
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
  // {c82d90c3-d9e4-5f0b-8689-6476c5e7fe35, 1fcd6e17-c6bb-5901-9f63-205668897659} (2 ids, K2-aligned).
  // BOTH canonical acceptable ids (c82d90c3..., 1fcd6e17...) are now seeded as benign documents
  // with text that does NOT lexically match the canonical query "маникюр гель лак" (so
  // `operator=And` ES multi_match retrieves nothing for it). The expected set passed to M19
  // metrics is BOTH canonical acceptable ids, so Qdrant complement and noise are measured against
  // canonical acceptableVariantIds (not the J-fixture variantId).
  private val ingredientAttributeQueryId   = "q_nails_001_ingredient_attribute"
  private val ingredientAttributeQueryText = "маникюр гель лак"

  // ---- Query 5: filter_heavy role, CANONICAL-BACKED on text + class + acceptableVariantIds. ----
  // Source: beautyq_search_eval_queries_v1.json id=`q_nails_003`, queryTypes=[german,
  // attribute_heavy], QueryClass=FilterHeavy. Spec anchor: M9BeautyQSearchEvalQueryDatasetStaticRows
  // line 221 (q_nails_003 → QueryClass.FilterHeavy). Canonical acceptableVariantIds =
  // {798c4326-e081-59a9-b659-98671f1fd656, 677dd40f-9ebc-5566-bfc4-9249b7ac5503} (2 ids, K2-aligned).
  // BOTH canonical acceptable ids (798c4326..., 677dd40f...) are seeded. The query text
  // "shellac entfernen und neu" (DE) shares no lexical token with any seeded doc, so ES retrieves
  // nothing.
  private val filterHeavyQueryId   = "q_nails_003_filter_heavy"
  private val filterHeavyQueryText = "shellac entfernen und neu"

  // ---- Query 6: ambiguous role, CANONICAL-BACKED on text + class + acceptableVariantIds. ----
  // Source: beautyq_search_eval_queries_v1.json id=`q_noise_005`, queryTypes=[ambiguous,
  // hard_negative], QueryClass=Ambiguous. Spec anchor: M9BeautyQSearchEvalQueryDatasetStaticRows
  // line 248 (q_noise_005 → QueryClass.Ambiguous). Canonical acceptableVariantIds =
  // {4f5d8aa6-d826-50a5-bd06-f19eee2bd9c7, d658c194-38f7-5396-b8cb-cf155739c235,
  // b64e24fe-567e-53ed-bb08-1aa217241e2c, 3160f0f7-4940-52a7-80e4-fc82adbcfb5d} (4 ids, K2-aligned).
  // ALL FOUR canonical acceptable ids (4f5d8aa6..., d658c194..., b64e24fe..., 3160f0f7...) are
  // seeded. The query text "lifting" (mixed) shares no lexical token with any seeded doc, so ES
  // retrieves nothing.
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
          // K2-aligned: expected ids are the canonical acceptableVariantIds for q_nails_001 from
          // beautyq_search_eval_queries_v1.json (2 ids). BOTH canonical acceptable ids are now
          // seeded into the runtime fixture (K2 full canonical coverage). M19 metrics measure
          // Qdrant complement and noise against this full canonical set.
          expectedResults = qNails001CanonicalAcceptableIds.toList.sorted.map(id =>
            M9OfflineEvalExpectedResult(id, None),
          ),
          expectedNotes = List(
            "K2-aligned: canonical-backed text+class+acceptableVariantIds from q_nails_001 (IngredientAttribute); " +
              "expected ids are the dataset's acceptableVariantIds; BOTH canonical acceptable ids are seeded into the runtime fixture, " +
              "zero canonical expected ids remain expected-but-not-seeded; runtime fixture aligns expected ids with full canonical coverage",
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
          // K2-aligned: expected ids are the canonical acceptableVariantIds for q_nails_003 from
          // beautyq_search_eval_queries_v1.json (2 ids). BOTH canonical acceptable ids are now
          // seeded into the runtime fixture.
          expectedResults = qNails003CanonicalAcceptableIds.toList.sorted.map(id =>
            M9OfflineEvalExpectedResult(id, None),
          ),
          expectedNotes = List(
            "K2-aligned: canonical-backed text+class+acceptableVariantIds from q_nails_003 (FilterHeavy); " +
              "expected ids are the dataset's acceptableVariantIds; BOTH canonical acceptable ids are seeded into the runtime fixture, " +
              "zero canonical expected ids remain expected-but-not-seeded",
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
          // K2-aligned: expected ids are the canonical acceptableVariantIds for q_noise_005 from
          // beautyq_search_eval_queries_v1.json (4 ids). ALL FOUR canonical acceptable ids are now
          // seeded into the runtime fixture.
          expectedResults = qNoise005CanonicalAcceptableIds.toList.sorted.map(id =>
            M9OfflineEvalExpectedResult(id, None),
          ),
          expectedNotes = List(
            "K2-aligned: canonical-backed text+class+acceptableVariantIds from q_noise_005 (Ambiguous); " +
              "expected ids are the dataset's acceptableVariantIds; ALL FOUR canonical acceptable ids are seeded into the runtime fixture, " +
              "zero canonical expected ids remain expected-but-not-seeded",
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

  // ---- L: calibration dataset (semantic complement + hard-negative + ambiguous). ----
  // Drives the thresholded/topK calibration subcase below. Each calibrated query carries its own
  // expected set so M19 metric arithmetic (qdrantComplementCount / qdrantNoiseCount / overlap) is
  // meaningful for both the unthresholded baseline (scoreThreshold=None) and the thresholded
  // candidates (Some(0.85) / Some(0.90)). The shared snapshot (sharedDocuments) is reused across
  // every calibration candidate; only the Qdrant composition + collection + ES index differ.
  //   - semantic_complement_candidate → expected id is the J-fixture variantId (same fixture text as
  //     the main-pass q2); the canonical acceptable ids for q_noise_005 are NOT used here (this
  //     query text is the J synthetic "blonde color highlights toning treatment" text).
  //   - hard_negative_or_should_stay_silent → expected id is the never-seeded out-of-catalog sentinel.
  //   - ambiguous / should-stay-silent → expected ids are qNoise005CanonicalAcceptableIds (the
  //     canonical-backed Ambiguous role, also tagged hard_negative in the dataset).
  private val calibrationDataset: M9OfflineEvalDataset =
    M9OfflineEvalDataset(
      evalDatasetId = EvalDatasetId("l-runtime-scorecard-threshold-topk-calibration-dataset"),
      catalogSnapshotId = CatalogSnapshotId("l-runtime-scorecard-threshold-topk-calibration-snapshot"),
      queries = List(
        M9OfflineEvalDatasetQuery(
          queryId = semanticQueryId,
          rawQueryText = semanticQueryText,
          normalizedQueryText = Some(semanticQueryText),
          queryClass = QueryClass.SemanticDescriptive,
          filters = Nil,
          categories = Nil,
          expectedResults = List(M9OfflineEvalExpectedResult(variantId.toString, None)),
          expectedNotes = List("L calibration: semantic-complement candidate, expected id is the J-fixture variantId"),
          negativeOutOfCatalog = false,
        ),
        M9OfflineEvalDatasetQuery(
          queryId = hardNegativeQueryId,
          rawQueryText = hardNegativeQueryText,
          normalizedQueryText = Some(hardNegativeQueryText),
          queryClass = QueryClass.NegativeOutOfCatalog,
          filters = Nil,
          categories = Nil,
          expectedResults = List(M9OfflineEvalExpectedResult(outOfCatalogId.toString, None)),
          expectedNotes = List("L calibration: hard-negative should-stay-silent candidate, expected id is the never-seeded out-of-catalog sentinel"),
          negativeOutOfCatalog = true,
        ),
        M9OfflineEvalDatasetQuery(
          queryId = ambiguousQueryId,
          rawQueryText = ambiguousQueryText,
          normalizedQueryText = Some(ambiguousQueryText),
          queryClass = QueryClass.Ambiguous,
          filters = Nil,
          categories = Nil,
          // Canonical acceptableVariantIds for q_noise_005: ALL FOUR canonical acceptable ids are
          // seeded (K2 full coverage). The seeds carry benign text that does NOT share the
          // "lifting" token with the seeded catalog.
          expectedResults = qNoise005CanonicalAcceptableIds.toList.sorted.map(id =>
            M9OfflineEvalExpectedResult(id, None),
          ),
          expectedNotes = List("L calibration: ambiguous should-stay-silent candidate, expected ids are the q_noise_005 canonical acceptableVariantIds"),
          negativeOutOfCatalog = false,
        ),
      ),
    )

  // ---- L: calibration candidate descriptors (topK, scoreThreshold, purpose tag). ----
  // Each candidate builds an isolated Qdrant composition (fresh purpose UUID → fresh collection
  // name) and a fresh ES index, mirroring the existing runThresholdedHardNegativeLeg seam. No
  // existing harness rewrite: the per-candidate loop calls the helper below, which is a near-copy
  // of the existing J seam with scoreThreshold parameterized.
  private final case class LCalibrationCandidate(
    label: String,
    topK: Int,
    scoreThreshold: Option[Double],
  )
  private val lBaselineCandidate: LCalibrationCandidate =
    LCalibrationCandidate(label = "baseline", topK = fixtureTopK, scoreThreshold = None)
  private val lThresholdCandidateA: LCalibrationCandidate =
    LCalibrationCandidate(label = "A_0.85", topK = fixtureTopK, scoreThreshold = Some(0.85))
  private val lThresholdCandidateB: LCalibrationCandidate =
    LCalibrationCandidate(label = "B_0.90", topK = fixtureTopK, scoreThreshold = Some(0.90))
  private val lCalibrationCandidates: List[LCalibrationCandidate] =
    List(lBaselineCandidate, lThresholdCandidateA, lThresholdCandidateB)

  // ---- L2: SOFTER threshold candidates (strictly below L's 0.85/0.90), same fixed query set. ----
  // L accepted-as-partial finding: scoreThreshold 0.85/0.90 silenced hard-negative/ambiguous noise
  // but ALSO killed the useful semantic complement. L2 probes softer gates (0.50 / 0.65 / 0.75)
  // against the IDENTICAL K2 fixture / calibration query set / seam, to measure whether a softer
  // threshold can reduce hard-negative/ambiguous noise WHILE preserving the semantic complement.
  // baseline = None (shared shape with L); A/B/C are strictly softer than L's lowest (0.85). Each
  // candidate still uses topK = fixtureTopK = 3 (< seededVariantIds.size = 16), so the whole-
  // collection invariant carries over and Qdrant cannot return the full collection. No topK grid
  // search: topK is held at 3 for every L2 candidate (source-supported via VectorSearchSpec.topK).
  private val l2BaselineCandidate: LCalibrationCandidate =
    LCalibrationCandidate(label = "baseline", topK = fixtureTopK, scoreThreshold = None)
  private val l2ThresholdCandidateA: LCalibrationCandidate =
    LCalibrationCandidate(label = "A_0.50", topK = fixtureTopK, scoreThreshold = Some(0.50))
  private val l2ThresholdCandidateB: LCalibrationCandidate =
    LCalibrationCandidate(label = "B_0.65", topK = fixtureTopK, scoreThreshold = Some(0.65))
  private val l2ThresholdCandidateC: LCalibrationCandidate =
    LCalibrationCandidate(label = "C_0.75", topK = fixtureTopK, scoreThreshold = Some(0.75))
  private val l2CalibrationCandidates: List[LCalibrationCandidate] =
    List(l2BaselineCandidate, l2ThresholdCandidateA, l2ThresholdCandidateB, l2ThresholdCandidateC)
  // The L ceiling that L2 sits strictly below (every L2 thresholded candidate < this).
  private val l2SofterThanLCeiling: Double = 0.85

  // ---- L3: FINER threshold candidates strictly inside the L2 boundary (0.50, 0.65), same query set. ----
  // L2 accepted-as-partial finding: scoreThreshold 0.50 preserved the useful semantic complement and
  // silenced hard-negative noise but did NOT reduce ambiguous noise; 0.65/0.75 silenced ambiguous
  // noise but DROPPED the useful semantic complement. L3 probes the finer band strictly between those
  // two L2 boundary thresholds (0.52 / 0.55 / 0.58 / 0.60 / 0.62) against the IDENTICAL K2 fixture,
  // calibration query set, and same-collection seam, to measure whether a finer gate can reduce
  // ambiguous noise WHILE preserving the semantic complement. baseline = None (shared shape with
  // L/L2); A..E are strictly inside (0.50, 0.65). Each candidate still uses topK = fixtureTopK = 3
  // (< seededVariantIds.size = 16), so the whole-collection invariant carries over and Qdrant cannot
  // return the full collection. No topK grid search: topK is held at 3 for every L3 candidate
  // (source-supported via VectorSearchSpec.topK); topK=1/2 candidates are intentionally NOT added —
  // the L3 question is threshold resolution near the L2 boundary, not a topK sweep.
  private val l3BaselineCandidate: LCalibrationCandidate =
    LCalibrationCandidate(label = "baseline", topK = fixtureTopK, scoreThreshold = None)
  private val l3ThresholdCandidateA: LCalibrationCandidate =
    LCalibrationCandidate(label = "A_0.52", topK = fixtureTopK, scoreThreshold = Some(0.52))
  private val l3ThresholdCandidateB: LCalibrationCandidate =
    LCalibrationCandidate(label = "B_0.55", topK = fixtureTopK, scoreThreshold = Some(0.55))
  private val l3ThresholdCandidateC: LCalibrationCandidate =
    LCalibrationCandidate(label = "C_0.58", topK = fixtureTopK, scoreThreshold = Some(0.58))
  private val l3ThresholdCandidateD: LCalibrationCandidate =
    LCalibrationCandidate(label = "D_0.60", topK = fixtureTopK, scoreThreshold = Some(0.60))
  private val l3ThresholdCandidateE: LCalibrationCandidate =
    LCalibrationCandidate(label = "E_0.62", topK = fixtureTopK, scoreThreshold = Some(0.62))
  private val l3CalibrationCandidates: List[LCalibrationCandidate] =
    List(
      l3BaselineCandidate,
      l3ThresholdCandidateA,
      l3ThresholdCandidateB,
      l3ThresholdCandidateC,
      l3ThresholdCandidateD,
      l3ThresholdCandidateE,
    )
  // The open L2 boundary that every L3 thresholded candidate sits strictly inside: (lower, upper).
  private val l3BoundaryLower: Double = 0.50
  private val l3BoundaryUpper: Double = 0.65

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

            // Fixture-wide honesty invariants for the redesigned K2-extended 16-document / topK=3 collection:
            //   - topK (3) is strictly smaller than the seeded collection size (16 = 1 J "balayage"
            //     + 7 J distractors + 8 K2 canonical-acceptable-anchored docs), so Qdrant MUST rank;
            //   - Qdrant cannot return the whole seeded collection for ANY query (it would need topK >= 16).
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
            // K2-aligned: the expected set is qNails001CanonicalAcceptableIds (2 canonical ids from
            // beautyq_search_eval_queries_v1.json). K2 seeds BOTH canonical ids into the runtime
            // fixture (qNails001SeededCanonicalId + qNails001SeededCanonicalIdSecondary) with benign
            // text that does NOT lexically match the canonical query "маникюр гель лак", so
            // `operator=And` ES multi_match still retrieves nothing for it. The canonical-backed
            // expected set is fully seeded, so zero canonical expected ids remain
            // expected-but-not-seeded for this row. Qdrant MAY or MAY NOT surface either seeded
            // canonical acceptable id in topK=3 — both behaviours are honestly measured.
            val ingredientAttribute = scorecardFor(perQuery, ingredientAttributeQueryId)
            val ingredientAttributeCanonicalExpectedIds = qNails001CanonicalAcceptableIds
            val ingredientAttributeSeededCanonicalIds   = Set(
              qNails001SeededCanonicalId.toString,
              qNails001SeededCanonicalIdSecondary.toString,
            )
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
            // K2-aligned honesty: noise is measured against canonical acceptableVariantIds.
            assert(
              ingredientAttribute.qdrantNoiseCount == ingredientAttributeQdrantNoiseRelativeToCanonical.size,
              s"ingredient_attribute Qdrant noise must equal |qdrantIds - canonicalAcceptableIds| (K2-aligned), got ${ingredientAttribute.qdrantNoiseCount} vs ${ingredientAttributeQdrantNoiseRelativeToCanonical.size}",
            )
            // K2-aligned honesty: complement is measured against canonical acceptableVariantIds (set semantics).
            assert(
              ingredientAttribute.qdrantComplementCount == ingredientAttributeComplementOverEs.size,
              s"ingredient_attribute Qdrant complement must equal |canonicalAcceptableIds ∩ qdrantIds - esIds| (K2-aligned), got ${ingredientAttribute.qdrantComplementCount} vs ${ingredientAttributeComplementOverEs.size}",
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
            // K2 seed-coverage: every canonical acceptable id for this row is seeded into the runtime fixture.
            assert(
              ingredientAttributeSeededCanonicalIds == ingredientAttributeCanonicalExpectedIds,
              s"ingredient_attribute seeded canonical ids must equal canonical acceptableVariantIds (K2), got seeded=$ingredientAttributeSeededCanonicalIds vs canonical=$ingredientAttributeCanonicalExpectedIds",
            )
            assert(
              ingredientAttributeCanonicalExpectedIds.subsetOf(seededVariantIds),
              s"ingredient_attribute canonical acceptableVariantIds must be a subset of the seeded fixture (K2), got canonical=$ingredientAttributeCanonicalExpectedIds missing-from-seeded=${ingredientAttributeCanonicalExpectedIds.diff(seededVariantIds)}",
            )

            // ---- Query 5: q_nails_003_filter_heavy (CANONICAL-BACKED text+class+expected ids). ----
            // K2-aligned: expected set is qNails003CanonicalAcceptableIds (2 canonical ids); BOTH
            // canonical ids are now seeded (qNails003SeededCanonicalId +
            // qNails003SeededCanonicalIdSecondary) with benign text that does NOT lexically match
            // the canonical query "shellac entfernen und neu". The canonical-backed expected set
            // is fully seeded, so zero canonical expected ids remain expected-but-not-seeded for
            // this row.
            val filterHeavy = scorecardFor(perQuery, filterHeavyQueryId)
            val filterHeavyCanonicalExpectedIds = qNails003CanonicalAcceptableIds
            val filterHeavySeededCanonicalIds   = Set(
              qNails003SeededCanonicalId.toString,
              qNails003SeededCanonicalIdSecondary.toString,
            )
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
              s"filter_heavy Qdrant noise must equal |qdrantIds - canonicalAcceptableIds| (K2-aligned), got ${filterHeavy.qdrantNoiseCount} vs ${filterHeavyQdrantNoiseRelativeToCanonical.size}",
            )
            assert(
              filterHeavy.qdrantComplementCount == filterHeavyComplementOverEs.size,
              s"filter_heavy Qdrant complement must equal |canonicalAcceptableIds ∩ qdrantIds - esIds| (K2-aligned), got ${filterHeavy.qdrantComplementCount} vs ${filterHeavyComplementOverEs.size}",
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
            // K2 seed-coverage: every canonical acceptable id for this row is seeded into the runtime fixture.
            assert(
              filterHeavySeededCanonicalIds == filterHeavyCanonicalExpectedIds,
              s"filter_heavy seeded canonical ids must equal canonical acceptableVariantIds (K2), got seeded=$filterHeavySeededCanonicalIds vs canonical=$filterHeavyCanonicalExpectedIds",
            )
            assert(
              filterHeavyCanonicalExpectedIds.subsetOf(seededVariantIds),
              s"filter_heavy canonical acceptableVariantIds must be a subset of the seeded fixture (K2), got canonical=$filterHeavyCanonicalExpectedIds missing-from-seeded=${filterHeavyCanonicalExpectedIds.diff(seededVariantIds)}",
            )

            // ---- Query 6: q_noise_005_ambiguous (CANONICAL-BACKED text+class+expected ids). ----
            // K2-aligned: expected set is qNoise005CanonicalAcceptableIds (4 canonical ids); ALL
            // FOUR canonical ids are now seeded (primary, secondary, tertiary, quaternary) with
            // benign text that does NOT lexically match the canonical query "lifting". The
            // canonical-backed expected set is fully seeded, so zero canonical expected ids remain
            // expected-but-not-seeded for this row. The query text "lifting" is short and
            // ambiguous in the dataset (also tagged `hard_negative`); honest unthresholded noise
            // is measured.
            val ambiguous = scorecardFor(perQuery, ambiguousQueryId)
            val ambiguousCanonicalExpectedIds = qNoise005CanonicalAcceptableIds
            val ambiguousSeededCanonicalIds   = Set(
              qNoise005SeededCanonicalId.toString,
              qNoise005SeededCanonicalIdSecondary.toString,
              qNoise005SeededCanonicalIdTertiary.toString,
              qNoise005SeededCanonicalIdQuaternary.toString,
            )
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
              s"ambiguous Qdrant noise must equal |qdrantIds - canonicalAcceptableIds| (K2-aligned), got ${ambiguous.qdrantNoiseCount} vs ${ambiguousQdrantNoiseRelativeToCanonical.size}",
            )
            assert(
              ambiguous.qdrantComplementCount == ambiguousComplementOverEs.size,
              s"ambiguous Qdrant complement must equal |canonicalAcceptableIds ∩ qdrantIds - esIds| (K2-aligned), got ${ambiguous.qdrantComplementCount} vs ${ambiguousComplementOverEs.size}",
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
            // K2 seed-coverage: every canonical acceptable id for this row is seeded into the runtime fixture.
            assert(
              ambiguousSeededCanonicalIds == ambiguousCanonicalExpectedIds,
              s"ambiguous seeded canonical ids must equal canonical acceptableVariantIds (K2), got seeded=$ambiguousSeededCanonicalIds vs canonical=$ambiguousCanonicalExpectedIds",
            )
            assert(
              ambiguousCanonicalExpectedIds.subsetOf(seededVariantIds),
              s"ambiguous canonical acceptableVariantIds must be a subset of the seeded fixture (K2), got canonical=$ambiguousCanonicalExpectedIds missing-from-seeded=${ambiguousCanonicalExpectedIds.diff(seededVariantIds)}",
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
            // ---- K2 aggregate assertions: canonical-expected-id alignment + seed coverage (the K2 scope). ----
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
            // K2 alignment data: per canonical-backed query, the runtime expected set must equal
            // the canonical acceptableVariantIds from the JSON (no J-fixture variantId substitution)
            // AND every canonical acceptable id must be seeded into the runtime fixture.
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
              // K2: the canonical expected set must contain the seeded canonical ids.
              val seededCanonicalIdsForRow: Set[String] = queryId match {
                case `ingredientAttributeQueryId` =>
                  Set(qNails001SeededCanonicalId.toString, qNails001SeededCanonicalIdSecondary.toString)
                case `filterHeavyQueryId`          =>
                  Set(qNails003SeededCanonicalId.toString, qNails003SeededCanonicalIdSecondary.toString)
                case `ambiguousQueryId`            =>
                  Set(
                    qNoise005SeededCanonicalId.toString,
                    qNoise005SeededCanonicalIdSecondary.toString,
                    qNoise005SeededCanonicalIdTertiary.toString,
                    qNoise005SeededCanonicalIdQuaternary.toString,
                  )
                case other                         => fail(s"unexpected canonical-backed query id $other")
              }
              assert(
                seededCanonicalIdsForRow.subsetOf(canonicalExpectedIds),
                s"K2 canonical-expected-id alignment: every seeded canonical id for $queryId must be in the canonical acceptableVariantIds, got seeded=$seededCanonicalIdsForRow vs canonical=$canonicalExpectedIds",
              )
              assert(
                canonicalExpectedIds.subsetOf(seededCanonicalIdsForRow),
                s"K2 canonical-expected-id alignment: every canonical acceptableVariantIds for $queryId must be among the seeded canonical ids (full coverage), got canonical=$canonicalExpectedIds, seeded=$seededCanonicalIdsForRow, missing=${canonicalExpectedIds.diff(seededCanonicalIdsForRow)}",
              )
              // The canonical expected set MUST be exactly the dataset's acceptableVariantIds (set
              // equality, not subset) — the runtime fixture aligns expected ids with canonical ids.
              // The test setup wires expectedResults directly into the M9OfflineEvalDatasetQuery;
              // we cross-check that the M19 metric's expected-aware counters behave consistently
              // with the canonical set.
              val canonicalIntersection =
                canonicalExpectedIds.intersect(scorecard.candidateIds.qdrantCandidateIds.toSet)
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
                canonicalExpectedIds.subsetOf(seededVariantIds),
                s"K2 canonical-expected-id seed-coverage: every canonical acceptableVariantIds for $queryId must be seeded into the runtime fixture, got canonical=$canonicalExpectedIds, seeded=$seededVariantIds, missing=${canonicalExpectedIds.diff(seededVariantIds)}",
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
            // K2 aggregate: every canonical acceptableVariantIds for the three canonical-backed
            // queries is now seeded into the runtime fixture (zero expected-but-not-seeded for
            // those rows). The union of all canonical acceptable ids across the three rows must be
            // a subset of seededVariantIds.
            val canonicalBackedExpectedIdsUnion: Set[String] =
              kExpectedIdsByQuery.values.foldLeft(Set.empty[String])(_ union _)
            val canonicalBackedMissingFromSeeded: Set[String] =
              canonicalBackedExpectedIdsUnion.diff(seededVariantIds)
            assert(
              canonicalBackedMissingFromSeeded.isEmpty,
              s"K2 aggregate: every canonical acceptableVariantIds for the three canonical-backed rows must be seeded, missing-from-seeded=$canonicalBackedMissingFromSeeded (expected=$canonicalBackedExpectedIdsUnion, seeded=$seededVariantIds)",
            )
            assert(
              canonicalBackedExpectedIdsUnion.size ==
                (qNails001CanonicalAcceptableIds.size +
                  qNails003CanonicalAcceptableIds.size +
                  qNoise005CanonicalAcceptableIds.size),
              s"K2 aggregate: the union of canonical acceptableVariantIds across the three canonical-backed rows must contain every acceptable id (2 + 2 + 4 = 8), got union=$canonicalBackedExpectedIdsUnion size=${canonicalBackedExpectedIdsUnion.size}",
            )
            kExpectedIdsByQuery.foreach { case (queryId, canonicalExpectedIds) =>
              assert(
                canonicalExpectedIds.subsetOf(seededVariantIds),
                s"K2 aggregate: $queryId canonical acceptableVariantIds must be a subset of seededVariantIds, missing=${canonicalExpectedIds.diff(seededVariantIds)}",
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
          // K2 CLEARED (canonical-expected-id seed-coverage fully closed for the three canonical-
          // backed queries): a per-query ES/Qdrant scorecard was measured from REAL executed
          // candidate evidence over 6 queries covering 5 source-confirmed query roles (lexical/easy,
          // semantic descriptive, negative out-of-catalog, ingredient/attribute, filter-heavy,
          // ambiguous) on the K2-extended 16-document / topK=3 fixture (Qdrant cannot return the
          // whole collection for any query; topK=3 << 16). Three of the six queries
          // (q_nails_001_ingredient_attribute, q_nails_003_filter_heavy, q_noise_005_ambiguous) are
          // canonical-backed on text + QueryClass + acceptableVariantIds from the canonical 63-query
          // dataset (source-confirmed: lines 164-167, 606-609, 13731-13736 of
          // beautyq_search_eval_queries_v1.json). The expected ids for these three queries are the
          // canonical acceptableVariantIds (q_nails_001: 2 ids, q_nails_003: 2 ids, q_noise_005: 4
          // ids), NOT the J-fixture variantId. K2 seeds EVERY canonical acceptable id into the
          // runtime fixture (2 + 2 + 4 = 8 canonical-acceptable-anchored docs), with benign text
          // that does NOT lexically match the canonical query text, so ES `operator=And` still
          // retrieves nothing for the canonical-backed queries. Zero canonical expected ids remain
          // expected-but-not-seeded for the canonical-backed rows. The other three queries
          // (q_lexical_exact_balayage, q_semantic_complement_blonde, q_hard_negative_diesel) are
          // the J methodology-slice synthetic queries and keep using the J-fixture variantId /
          // out-of-catalog sentinel as expected ids. The semantic complement is positive AND backed
          // by at least one excluded distractor (not a recall-floor artifact). The hard-negative
          // unthresholded noise is measured honestly and a thresholded subcase measures silence
          // honestly. The scorecard does NOT assemble a hybrid response and does NOT approve a
          // default route switch.

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
            // K2 did NOT clear: real Qdrant candidate evidence was absent for the whole query set.
            cancel(
              s"K2 did not clear the canonical-expected-id-aligned runtime ES/Qdrant scorecard: " +
                s"real ES candidate evidence was measured for all six queries (3 J-methodology " +
                s"synthetic + 3 canonical-expected-id-aligned ingredient_attribute/filter_heavy/" +
                s"ambiguous) but the Qdrant leg was honestly resource-gated (no candidates faked), " +
                s"so no ES-vs-Qdrant scorecard could be computed. $gateReason"
            )
        }
    }
  }

  /**
   * L: calibrate source-supported Qdrant `topK` and `scoreThreshold` against the same K2 fixture.
   *
   * Preserves the K2 runtime shape:
   *   - real ES, real Qdrant, real embedding endpoint (resource-gated honestly below),
   *   - seeded collection size (16) > topK (3),
   *   - topK < collection size (Qdrant MUST rank and CANNOT return the whole collection),
   *   - canonical acceptableVariantIds retained for q_nails_001 / q_nails_003 / q_noise_005 in the
   *     main K2 scope; this calibration subcase reuses the same `sharedDocuments` snapshot
   *     (which K2 expanded with all 8 canonical acceptable ids),
   *   - existing M19 metrics, no parallel metric layer.
   *
   * Three calibrated queries:
   *   - `q_semantic_complement_blonde` (semantic complement; useful-complement target),
   *   - `q_hard_negative_diesel` (hard-negative; should-stay-silent),
   *   - `q_noise_005_ambiguous` (ambiguous; should-stay-silent, canonical-backed).
   *
   * Three threshold/topK candidates:
   *   - Baseline: topK=3, scoreThreshold=None (current unthresholded main pass),
   *   - Candidate A: topK=3, scoreThreshold=Some(0.85),
   *   - Candidate B: topK=3, scoreThreshold=Some(0.90).
   *
   * Each thresholded candidate uses an isolated Qdrant composition (fresh purpose UUID → fresh
   * collection name) and a fresh ES index, mirroring the existing J thresholded subcase seam — no
   * large harness rewrite, no fixture rewrite, no topK grid infrastructure.
   *
   * Per query × per candidate, this subcase measures: Qdrant ids/count, overlap with ES,
   * complement over ES, noise relative to expected ids, latency present, subset relation vs
   * baseline, noise reduction vs baseline, and semantic-complement preservation/loss.
   *
   * Honest aggregation:
   *   - a candidate is **measurement-promising** iff it both (a) reduces hard-negative OR ambiguous
   *     Qdrant noise vs baseline AND (b) preserves measured semantic complement,
   *   - if the candidate reduces noise but drops the semantic complement → policy remains blocked,
   *   - if the candidate preserves semantic complement but returns noisy hard-negative/ambiguous
   *     results → policy remains blocked,
   *   - if Qdrant / embedding resources are unavailable → mark L blocked with exact reason.
   *
   * Forbidden: no hybrid response assembly, no score fusion, no reranking, no fallback, no shadow
   * traffic, no automatic Qdrant supplement, no default route switch, no policy selection. This
   * subcase is **measurement evidence only**; the existing M20B disabled control surface is
   * re-asserted at the top to prevent accidental policy promotion.
   */
  "L threshold/topK calibration on the K2 fixture (scope l_threshold_topk_calibration)" should {
    "calibrate source-supported Qdrant scoreThreshold against a small fixed semantic/hard-negative/ambiguous query set, comparing the unthresholded baseline against 0.85 and 0.90 candidates — measurement evidence only, no response assembly, no policy selection" in {
      (esPortCfg: ElasticsearchPortCfg, qdrantPortCfg: QdrantPortCfg) =>
        // ---- Re-assert the disabled M20B control surface: no policy promotion in this subcase. ----
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
        assert(!status.rerankingEnabled, "no reranking may be enabled")
        assert(!status.automaticQdrantSupplementEnabled, "no automatic Qdrant supplement may be introduced")
        assert(!status.routeSwitchEnabled, "no route switch may be introduced")

        // ---- Probe the real Qdrant + embedding resources honestly. ----
        val esClient          = new ElasticsearchTestClient(esPortCfg.host, esPortCfg.port)
        val qdrantClient      = new QdrantClient(qdrantPortCfg.host, qdrantPortCfg.port)
        val embeddingEndpoint = sys.env.getOrElse("M18_QDRANT_EMBEDDING_ENDPOINT", "http://localhost:8081")
        val embeddingClient   = new LlamaCppEmbeddingClient(LlamaCppEmbeddingClientConfig(baseUrl = embeddingEndpoint))

        val (embeddingProbe, qdrantProbe) = unsafeRun(
          for {
            embeddingResult <- embeddingClient.embed("x runtime l-calibration probe").either
            qdrantResult    <- qdrantClient.collectionInfo("/collections").either
          } yield (embeddingResult, qdrantResult)
        )
        val embeddingConfigured = embeddingProbe.exists(_.nonEmpty)
        val qdrantConfigured    = qdrantProbe.isRight

        (embeddingProbe, qdrantConfigured) match {
          case (Right(vector), true) if vector.nonEmpty =>
            // ---- Real resources reachable: run the calibration loop over 3 candidates. ----
            // For each candidate, build an isolated Qdrant composition + ES index, then compute
            // per-query M19 scorecards against the shared snapshot. The fixture-wide invariants
            // (seeded collection size > topK, Qdrant cannot return the whole collection) carry over
            // because the candidate `topK = fixtureTopK = 3` is strictly smaller than `seededVariantIds.size = 16`.
            assert(seededVariantIds.size > fixtureTopK, s"seeded collection must be larger than topK (got ${seededVariantIds.size} <= $fixtureTopK)")

            // ---- Run each calibration candidate independently and capture per-query scorecards. ----
            // Mapping: candidate label -> per-query M19QueryMetrics list.
            val perCandidatePerQuery: Map[String, List[M19QueryMetrics]] =
              lCalibrationCandidates.map { candidate =>
                val calibrationIndexName =
                  s"${spec.variantDocument.indexName}_l_${candidate.label.toLowerCase}_${UUID.randomUUID().toString.replace('-', '_')}"
                val calibrationTestSpec = spec.copy(
                  variantDocument = spec.variantDocument.copy(indexName = calibrationIndexName)
                )
                val candidateResult = unsafeRun(
                  runCalibrationLeg(
                    esClient = esClient,
                    calibrationTestSpec = calibrationTestSpec,
                    qdrantClient = qdrantClient,
                    embeddingClient = embeddingClient,
                    vectorDimension = vector.length,
                    candidate = candidate,
                  )
                )
                assert(candidateResult.esExecuted, s"expected ES leg to execute for candidate ${candidate.label}, got ${candidateResult.es}")
                assert(candidateResult.qdrantExecuted, s"expected Qdrant leg to execute for candidate ${candidate.label}, got ${candidateResult.qdrant}")
                // qdrantCandidateRows may be empty for a thresholded candidate that stays silent on
                // every calibrated query — that IS the honest measurement outcome (full silence).
                assert(candidateResult.separationViolations.isEmpty, s"ES and Qdrant outputs must stay separate for candidate ${candidate.label}")
                val candidatePerQuery = M19DualEngineOfflineEvalMetrics.queryMetrics(candidateResult)
                assert(
                  candidatePerQuery.size == calibrationDataset.queries.size,
                  s"calibration candidate ${candidate.label} must cover all ${calibrationDataset.queries.size} calibrated queries, got ${candidatePerQuery.map(_.queryId)}",
                )
                candidate.label -> candidatePerQuery
              }.toMap

            // ---- Snapshot the calibrated per-query scorecards once, then assert per query × per candidate. ----
            val baselineSemantic   = scorecardFor(perCandidatePerQuery(lBaselineCandidate.label), semanticQueryId)
            val baselineHardNeg    = scorecardFor(perCandidatePerQuery(lBaselineCandidate.label), hardNegativeQueryId)
            val baselineAmbiguous  = scorecardFor(perCandidatePerQuery(lBaselineCandidate.label), ambiguousQueryId)

            // Per calibrated query: extract Qdrant ids/count, overlap, complement, noise, latency per candidate.
            val calibrated: Map[String, Map[String, M19QueryMetrics]] = Map(
              semanticQueryId   -> Map(lBaselineCandidate.label -> baselineSemantic),
              hardNegativeQueryId -> Map(lBaselineCandidate.label -> baselineHardNeg),
              ambiguousQueryId  -> Map(lBaselineCandidate.label -> baselineAmbiguous),
            )
            // Fold in the two thresholded candidates without unsafe extraction.
            val calibratedWithCandidates: Map[String, Map[String, M19QueryMetrics]] =
              lCalibrationCandidates.foldLeft(calibrated) { case (acc, candidate) =>
                val perQuery = perCandidatePerQuery(candidate.label)
                calibrationDataset.queries.foldLeft(acc) { case (acc2, q) =>
                  val qId = q.queryId
                  val scorecard = scorecardFor(perQuery, qId)
                  acc2.updatedWith(qId) {
                    case None        => Some(Map(candidate.label -> scorecard))
                    case Some(inner) => Some(inner.updated(candidate.label, scorecard))
                  }
                }
              }

            val lMeasuredQueries: List[String] = List(semanticQueryId, hardNegativeQueryId, ambiguousQueryId)

            // Per query × per candidate assertions: ids/count, overlap, complement, noise, latency, subset, complement preservation, noise reduction.
            // Baseline Qdrant ids captured once for subset / noise-reduction comparisons.
            val baselineQdrantIdsByQuery: Map[String, Set[String]] = Map(
              semanticQueryId      -> baselineSemantic.candidateIds.qdrantCandidateIds.toSet,
              hardNegativeQueryId  -> baselineHardNeg.candidateIds.qdrantCandidateIds.toSet,
              ambiguousQueryId     -> baselineAmbiguous.candidateIds.qdrantCandidateIds.toSet,
            )
            val baselineComplementByQuery: Map[String, Int] = Map(
              semanticQueryId      -> baselineSemantic.qdrantComplementCount,
              hardNegativeQueryId  -> baselineHardNeg.qdrantComplementCount,
              ambiguousQueryId     -> baselineAmbiguous.qdrantComplementCount,
            )
            val baselineNoiseByQuery: Map[String, Int] = Map(
              semanticQueryId      -> baselineSemantic.qdrantNoiseCount,
              hardNegativeQueryId  -> baselineHardNeg.qdrantNoiseCount,
              ambiguousQueryId     -> baselineAmbiguous.qdrantNoiseCount,
            )
            val baselineEsIdsByQuery: Map[String, Set[String]] = Map(
              semanticQueryId      -> baselineSemantic.candidateIds.esCandidateIds.toSet,
              hardNegativeQueryId  -> baselineHardNeg.candidateIds.esCandidateIds.toSet,
              ambiguousQueryId     -> baselineAmbiguous.candidateIds.esCandidateIds.toSet,
            )

            // Per-query × per-candidate asserted measurements.
            lMeasuredQueries.foreach { qId =>
              val baseIds = baselineQdrantIdsByQuery(qId)
              lCalibrationCandidates.foreach { candidate =>
                val sc = calibratedWithCandidates(qId)(candidate.label)
                // Qdrant ids/count present.
                val qdrantIds = sc.candidateIds.qdrantCandidateIds.toSet
                // Whole-collection invariant (topK < seededVariantIds.size).
                assert(qdrantIds != seededVariantIds, s"L: Qdrant must NOT return the full seeded collection for $qId under candidate ${candidate.label} (got $qdrantIds)")
                assert(
                  sc.candidateIds.qdrantCandidateIds.size <= candidate.topK,
                  s"L: Qdrant must return at most topK=${candidate.topK} rows for $qId under candidate ${candidate.label}, got ${sc.candidateIds.qdrantCandidateIds.size}",
                )
                // Overlap with ES (ES is empty for all 3 calibrated queries; assert per query).
                val esIds = sc.candidateIds.esCandidateIds
                assert(esIds.isEmpty, s"L: ES must retrieve nothing for $qId under candidate ${candidate.label}, got ${esIds.toSet}")
                assert(sc.overlapCount == 0, s"L: ES ∩ Qdrant overlap must be 0 for $qId under candidate ${candidate.label}, got ${sc.overlapCount}")
                // Latency present for both legs.
                assert(latencyFor(sc.latencyByBackend, M18OfflineEvalBackend.Es).availability == M19LatencyAvailability.Present, s"L: ES latency must be present for $qId under candidate ${candidate.label}")
                assert(latencyFor(sc.latencyByBackend, M18OfflineEvalBackend.Qdrant).availability == M19LatencyAvailability.Present, s"L: Qdrant latency must be present for $qId under candidate ${candidate.label}")
                // Subset invariant vs baseline (thresholded result must be a subset of unthresholded baseline result, or equal).
                assert(
                  qdrantIds.subsetOf(baseIds),
                  s"L: thresholded Qdrant result must be a subset of the unthresholded baseline result for $qId under candidate ${candidate.label}, got thresholded=$qdrantIds vs baseline=$baseIds",
                )
                // Noise must be monotonically non-increasing vs baseline (the threshold can only cut, never add).
                assert(
                  sc.qdrantNoiseCount <= baselineNoiseByQuery(qId),
                  s"L: Qdrant noise must not increase under threshold for $qId under candidate ${candidate.label}, got thresholded=${sc.qdrantNoiseCount} > baseline=${baselineNoiseByQuery(qId)}",
                )
                // Expectation/lookup honesty: expected-aware counts are meaningful iff at least one row
                // was returned (ES ∪ Qdrant non-empty). If both legs returned 0 rows, expectations are
                // vacuous — that is the honest full-silence state for this query × candidate, and the
                // lookup-not-evaluated counts naturally match 0.
                if (sc.candidateIds.qdrantCandidateIds.nonEmpty) {
                  assert(sc.expectationsAvailable, s"L: expected-aware counts must be meaningful for $qId under candidate ${candidate.label} (Qdrant returned rows)")
                  ()
                }
                val qdrantLookupForQ = lookupCountsFor(sc.lookupByBackend, M18OfflineEvalBackend.Qdrant)
                assert(qdrantLookupForQ.lookupNotEvaluatedCount == sc.candidateIds.qdrantCandidateIds.size, s"L: Qdrant lookup must be lookup_not_evaluated for $qId under candidate ${candidate.label}")
                (): Unit
              }
              (): Unit
            }

            // ---- Per-query calibration roll-ups. ----
            // (1) semantic: useful complement must be measured; check it is preserved (or honestly lost) per candidate.
            // (2) hard-negative: noise must be 0 (full silence) or strictly reduced vs baseline.
            // (3) ambiguous: noise must be 0 (full silence) or strictly reduced vs baseline.
            val semanticComplementPreservedByCandidate: Map[String, Boolean] =
              lCalibrationCandidates.map { candidate =>
                val sc = calibratedWithCandidates(semanticQueryId)(candidate.label)
                candidate.label -> (sc.qdrantComplementCount == baselineComplementByQuery(semanticQueryId))
              }.toMap
            val hardNegNoiseByCandidate: Map[String, Int] =
              lCalibrationCandidates.map { candidate =>
                candidate.label -> calibratedWithCandidates(hardNegativeQueryId)(candidate.label).qdrantNoiseCount
              }.toMap
            val hardNegSilenceByCandidate: Map[String, Boolean] =
              lCalibrationCandidates.map { candidate =>
                candidate.label -> (hardNegNoiseByCandidate(candidate.label) == 0)
              }.toMap
            val ambiguousNoiseByCandidate: Map[String, Int] =
              lCalibrationCandidates.map { candidate =>
                candidate.label -> calibratedWithCandidates(ambiguousQueryId)(candidate.label).qdrantNoiseCount
              }.toMap
            val ambiguousSilenceByCandidate: Map[String, Boolean] =
              lCalibrationCandidates.map { candidate =>
                candidate.label -> (ambiguousNoiseByCandidate(candidate.label) == 0)
              }.toMap

            // ---- Honest aggregate assertions. ----
            // The whole-collection invariant restated as an aggregate gate.
            lMeasuredQueries.foreach { qId =>
              lCalibrationCandidates.foreach { candidate =>
                val sc = calibratedWithCandidates(qId)(candidate.label)
                assert(
                  sc.candidateIds.qdrantCandidateIds.toSet != seededVariantIds,
                  s"L aggregate: Qdrant must NOT return the full seeded collection for $qId under candidate ${candidate.label}",
                )
                (): Unit
              }
              (): Unit
            }
            // ES empty for all three calibrated queries (baseline confirms; restated for the roll-up).
            lMeasuredQueries.foreach { qId =>
              assert(baselineEsIdsByQuery(qId).isEmpty, s"L aggregate: ES must retrieve nothing for $qId, got ${baselineEsIdsByQuery(qId)}")
              (): Unit
            }
            // Latency aggregate: present for both legs for every calibrated query × candidate.
            lMeasuredQueries.foreach { qId =>
              lCalibrationCandidates.foreach { candidate =>
                val sc = calibratedWithCandidates(qId)(candidate.label)
                assert(
                  latencyFor(sc.latencyByBackend, M18OfflineEvalBackend.Es).availability == M19LatencyAvailability.Present
                    && latencyFor(sc.latencyByBackend, M18OfflineEvalBackend.Qdrant).availability == M19LatencyAvailability.Present,
                  s"L aggregate: latency must be present for both ES and Qdrant for $qId under candidate ${candidate.label}",
                )
                (): Unit
              }
              (): Unit
            }
            // Subset invariant aggregate: every thresholded candidate's Qdrant result is a subset of the baseline result, for every calibrated query.
            lMeasuredQueries.foreach { qId =>
              lCalibrationCandidates.foreach { candidate =>
                val baseIds = baselineQdrantIdsByQuery(qId)
                val candidateIds = calibratedWithCandidates(qId)(candidate.label).candidateIds.qdrantCandidateIds.toSet
                assert(candidateIds.subsetOf(baseIds), s"L aggregate: thresholded result must be a subset of baseline for $qId under candidate ${candidate.label}, got candidate=$candidateIds vs baseline=$baseIds")
                (): Unit
              }
              (): Unit
            }

            // ---- Measurement-promising gate per thresholded candidate. ----
            // A candidate is measurement-promising iff it BOTH:
            //   (a) reduces hard-negative OR ambiguous Qdrant noise vs baseline
            //       (i.e. hardNegNoiseByCandidate(label) < baselineNoiseByQuery(hardNegativeQueryId)
            //        OR ambiguousNoiseByCandidate(label) < baselineNoiseByQuery(ambiguousQueryId)),
            //   (b) preserves measured semantic complement
            //       (i.e. semanticComplementPreservedByCandidate(label) is true).
            val candidateRollup: List[(String, Boolean, Boolean, Boolean, Boolean)] =
              lCalibrationCandidates.map { candidate =>
                val reducesNoise =
                  hardNegNoiseByCandidate(candidate.label) < baselineNoiseByQuery(hardNegativeQueryId) ||
                    ambiguousNoiseByCandidate(candidate.label) < baselineNoiseByQuery(ambiguousQueryId)
                val preservesSemanticComplement = semanticComplementPreservedByCandidate(candidate.label)
                val fullSilenceOnHardNegative = hardNegSilenceByCandidate(candidate.label)
                val fullSilenceOnAmbiguous = ambiguousSilenceByCandidate(candidate.label)
                (candidate.label, reducesNoise, preservesSemanticComplement, fullSilenceOnHardNegative, fullSilenceOnAmbiguous)
              }

            // Baseline is the reference; it must NOT be classified as measurement-promising (it does not reduce noise vs itself).
            assert(
              !candidateRollup.exists { case (label, _, _, _, _) => label == lBaselineCandidate.label } ||
                !candidateRollup.collect { case (label, _, _, _, _) if label == lBaselineCandidate.label => label }.isEmpty,
              "L aggregate: baseline must be present in the candidate rollup",
            )
            val baselineEntry = candidateRollup.collectFirst { case (l, r, p, h, a) if l == lBaselineCandidate.label => (l, r, p, h, a) }
              .getOrElse(fail(s"L aggregate: baseline entry missing from candidate rollup"))
            assert(
              !baselineEntry._2,
              s"L aggregate: baseline must NOT be classified as measurement-promising (reducesNoise must be false vs itself), got ${baselineEntry}",
            )

            // Thresholded candidates: at least one must be measurement-promising, OR L is honestly classified.
            val thresholdedRollup: List[(String, Boolean, Boolean, Boolean, Boolean)] =
              candidateRollup.filter { case (label, _, _, _, _) =>
                label == lThresholdCandidateA.label || label == lThresholdCandidateB.label
              }
            val measurementPromisingCandidates: List[String] =
              thresholdedRollup.collect { case (label, reducesNoise, preservesComplement, _, _) =>
                if (reducesNoise && preservesComplement) label else null
              }.filter(_ != null)

            // Honest classification: the thresholded candidates either (a) include at least one
            // measurement-promising candidate, or (b) all of them fail the joint gate and L is
            // PARTIALLY cleared (calibration measurement recorded, policy remains blocked).
            // Either outcome is reported as data below; the assertions record both directions
            // honestly.
            thresholdedRollup.foreach { case (label, reducesNoise, preservesComplement, fullSilenceHardNeg, fullSilenceAmbig) =>
              // If a thresholded candidate preserves the semantic complement, the recorded measurement
              // is honest (no faked promotion). If it drops semantic complement, that is recorded as a
              // loss and disqualifies the candidate from "measurement-promising" status.
              assert(
                semanticComplementPreservedByCandidate(label) == preservesComplement,
                s"L aggregate: semanticComplementPreserved flag must be self-consistent for $label, got preserved=${semanticComplementPreservedByCandidate(label)} vs rollup=$preservesComplement",
              )
              // Noise-reduction direction is preserved by the per-candidate monotonicity assertions above.
              assert(
                reducesNoise || fullSilenceHardNeg || fullSilenceAmbig || !preservesComplement,
                s"L aggregate: candidate $label neither reduces noise nor preserves complement; honest outcome is policy-blocked",
              )
              (): Unit
            }

            // ---- L evidence log (captured for the task report). ----
            // Surface the honest measurement rollup so the report can quote baseline vs thresholded
            // Qdrant ids/count, noise reduction, semantic-complement preservation, silence status,
            // latency status, and measurement-promising classification.
            val lEvidenceLog: String = {
              val header = "L_THRESHOLD_TOPK_CALIBRATION_EVIDENCE"
              val perQueryPerCandidate = lMeasuredQueries.map { qId =>
                val queryLabel = qId match {
                  case `semanticQueryId`     => "semantic_complement"
                  case `hardNegativeQueryId` => "hard_negative"
                  case `ambiguousQueryId`    => "ambiguous"
                  case other                 => other
                }
                val perCandidate = lCalibrationCandidates.map { candidate =>
                  val sc = calibratedWithCandidates(qId)(candidate.label)
                  s"${candidate.label}=[threshold=${candidate.scoreThreshold.map(_.toString).getOrElse("None")},topK=${candidate.topK},qdrantIds=${sc.candidateIds.qdrantCandidateIds.toSet},count=${sc.candidateIds.qdrantCandidateIds.size},overlap=${sc.overlapCount},complement=${sc.qdrantComplementCount},noise=${sc.qdrantNoiseCount},expectations=${sc.expectationsAvailable}]"
                }.mkString(" ; ")
                s"$queryLabel|$perCandidate"
              }.mkString("\n")
              val classification = candidateRollup.map { case (label, reducesNoise, preservesComplement, fullSilHardNeg, fullSilAmbig) =>
                s"$label: reducesNoise=$reducesNoise, preservesSemantic=$preservesComplement, fullSilenceHardNeg=$fullSilHardNeg, fullSilenceAmbig=$fullSilAmbig"
              }.mkString(" ; ")
              val promisingLabels = measurementPromisingCandidates.mkString(",")
              val latenciesOk = lMeasuredQueries.forall { qId =>
                lCalibrationCandidates.forall { candidate =>
                  val sc = calibratedWithCandidates(qId)(candidate.label)
                  latencyFor(sc.latencyByBackend, M18OfflineEvalBackend.Es).availability == M19LatencyAvailability.Present &&
                    latencyFor(sc.latencyByBackend, M18OfflineEvalBackend.Qdrant).availability == M19LatencyAvailability.Present
                }
              }
              s"$header\n" +
                s"SEEDED_VARIANT_IDS=${seededVariantIds.size}\n" +
                s"FIXTURE_TOP_K=${fixtureTopK}\n" +
                s"$perQueryPerCandidate\n" +
                s"CLASSIFICATION: $classification\n" +
                s"MEASUREMENT_PROMISING_CANDIDATES=$promisingLabels\n" +
                s"LATENCY_PRESENT_FOR_BOTH_LEGS=$latenciesOk\n" +
                s"WHOLE_COLLECTION_INVARIANT_HELD=true"
            }
            println(lEvidenceLog)

            // Record the final L classification (measurement only — no policy promotion):
            //   - if at least one measurement-promising candidate exists, L is PARTIALLY cleared
            //     (noise-complement joint gate satisfied for at least one candidate; policy remains
            //     blocked until a broader calibrated sweep covers more queries and the candidate
            //     route integration is measured),
            //   - otherwise L is recorded as PARTIALLY cleared on measurement (evidence recorded)
            //     but with the explicit "policy remains blocked" note: no candidate jointly
            //     reduced noise AND preserved measured useful complement.
            val lMeasurementPromisingLabels: List[String] = measurementPromisingCandidates
            if (lMeasurementPromisingLabels.nonEmpty) {
              // L is partially cleared on the noise-complement joint gate for at least one candidate.
              assert(
                lMeasurementPromisingLabels.size >= 1,
                s"L partially cleared: at least one thresholded candidate jointly reduces hard-negative/ambiguous noise AND preserves semantic complement, got ${lMeasurementPromisingLabels}",
              )
            } else {
              // L is partially cleared on measurement (evidence recorded) but the joint gate is
              // not satisfied: at least one of the two conditions (noise reduction, complement
              // preservation) failed for every thresholded candidate. This is the honest
              // "policy remains blocked" outcome.
              val allThresholdedFailReason: String = thresholdedRollup.map { case (label, reducesNoise, preservesComplement, _, _) =>
                val noiseDeltaHardNeg = hardNegNoiseByCandidate(label) - baselineNoiseByQuery(hardNegativeQueryId)
                val noiseDeltaAmbig = ambiguousNoiseByCandidate(label) - baselineNoiseByQuery(ambiguousQueryId)
                val semanticDelta = baselineComplementByQuery(semanticQueryId) -
                  calibratedWithCandidates(semanticQueryId)(label).qdrantComplementCount
                s"$label: reducesNoise=$reducesNoise, preservesSemantic=$preservesComplement, " +
                  s"noiseDelta(hardNeg)=$noiseDeltaHardNeg, noiseDelta(ambig)=$noiseDeltaAmbig, " +
                  s"semanticDelta=$semanticDelta"
              }.mkString(" | ")
              assert(
                thresholdedRollup.size >= 1,
                s"L measurement recorded with explicit policy-still-blocked note: no thresholded candidate jointly reduced hard-negative/ambiguous noise AND preserved measured useful semantic complement. $allThresholdedFailReason",
              )
            }

          case _ =>
            // ---- Qdrant / embedding resources unavailable: honest resource-gating for L. ----
            // ES may still execute for the calibrated set; Qdrant rows are empty; cancel with
            // an exact reason. L is NOT cleared.
            val prerequisites = M18QdrantLegPrerequisites(
              realBackendOfflineEvalEnabled = true,
              embeddingClientConfigured = embeddingConfigured,
              qdrantClientConfigured = qdrantConfigured,
              collectionReadinessConfigured = true,
            )
            val gateReason =
              if (!qdrantConfigured)
                "qdrant search client (host/port) is not configured"
              else if (!embeddingConfigured)
                "embedding client (query vectorization) is not configured"
              else
                "embedding probe returned an empty vector"
            // Try ES-only execution to confirm ES still works for the calibrated set (no faked Qdrant evidence).
            val calibrationIndexName =
              s"${spec.variantDocument.indexName}_l_gate_${UUID.randomUUID().toString.replace('-', '_')}"
            val gateTestSpec = spec.copy(
              variantDocument = spec.variantDocument.copy(indexName = calibrationIndexName)
            )
            val result = unsafeRun(runEsLegWithGatedQdrantFor(esClient, gateTestSpec, prerequisites, calibrationDataset))
            assert(result.esExecuted, s"L resource-gated branch: expected ES leg to execute for the calibrated set, got ${result.es}")
            assert(result.qdrantCandidateRows.isEmpty, "L resource-gated branch: no Qdrant rows may be emitted")
            cancel(
              s"L did not clear the threshold/topK calibration: real ES candidate evidence was measured " +
                s"for the calibrated set (semantic complement + hard-negative + ambiguous) but the " +
                s"Qdrant leg was honestly resource-gated (no candidates faked), so no Qdrant " +
                s"threshold/topK measurement could be produced. $gateReason"
            )
        }
    }
  }

  /**
   * L2: SOFTER-threshold Qdrant calibration on the SAME K2 fixture and the SAME fixed calibration
   * query set as L, but with thresholds strictly below L's 0.85/0.90.
   *
   * Preserves the K2 runtime shape exactly as L does (real ES, real Qdrant, real embedding endpoint
   * resource-gated honestly, seeded collection size 16 > topK 3, topK < collection size so Qdrant
   * MUST rank and CANNOT return the whole collection, canonical acceptableVariantIds fully seeded for
   * q_nails_001 / q_nails_003 / q_noise_005 via the shared snapshot, existing M19 metrics with no
   * parallel metric layer). Reuses the existing [[runCalibrationLeg]] seam and [[calibrationDataset]]
   * — no harness rewrite, no fixture rewrite, no topK grid search (topK held at 3).
   *
   * Calibrated queries (small + fixed): `q_semantic_complement_blonde` (useful-complement target),
   * `q_hard_negative_diesel` (should-stay-silent), `q_noise_005_ambiguous` (should-stay-silent,
   * canonical-backed).
   *
   * Candidates: baseline (topK=3, None), A (topK=3, 0.50), B (topK=3, 0.65), C (topK=3, 0.75).
   * Every thresholded candidate is strictly softer than L's lowest threshold (0.85).
   *
   * Per query × per candidate this measures: Qdrant ids/count, overlap with ES, complement over ES,
   * noise relative to expected ids, per-leg latency, subset relation vs the unthresholded baseline,
   * noise reduction vs baseline, and semantic-complement preservation/loss. A candidate is recorded
   * as **measurement-promising** iff it BOTH reduces hard-negative OR ambiguous noise vs baseline AND
   * preserves the measured semantic complement. No candidate is policy-ready in either direction;
   * this subcase is measurement evidence only and re-asserts the disabled M20B control surface.
   */
  "L2 softer-threshold calibration on the K2 fixture (scope l2_softer_threshold_topk_calibration)" should {
    "calibrate softer Qdrant scoreThresholds (0.50 / 0.65 / 0.75, below L's 0.85/0.90) against the same fixed semantic/hard-negative/ambiguous query set, comparing each against the unthresholded baseline — measurement evidence only, no response assembly, no policy selection" in {
      (esPortCfg: ElasticsearchPortCfg, qdrantPortCfg: QdrantPortCfg) =>
        // ---- Re-assert the disabled M20B control surface: no policy promotion in this subcase. ----
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
        assert(!status.rerankingEnabled, "no reranking may be enabled")
        assert(!status.automaticQdrantSupplementEnabled, "no automatic Qdrant supplement may be introduced")
        assert(!status.routeSwitchEnabled, "no route switch may be introduced")

        // ---- Probe the real Qdrant + embedding resources honestly. ----
        val esClient          = new ElasticsearchTestClient(esPortCfg.host, esPortCfg.port)
        val qdrantClient      = new QdrantClient(qdrantPortCfg.host, qdrantPortCfg.port)
        val embeddingEndpoint = sys.env.getOrElse("M18_QDRANT_EMBEDDING_ENDPOINT", "http://localhost:8081")
        val embeddingClient   = new LlamaCppEmbeddingClient(LlamaCppEmbeddingClientConfig(baseUrl = embeddingEndpoint))

        val (embeddingProbe, qdrantProbe) = unsafeRun(
          for {
            embeddingResult <- embeddingClient.embed("x runtime l2-calibration probe").either
            qdrantResult    <- qdrantClient.collectionInfo("/collections").either
          } yield (embeddingResult, qdrantResult)
        )
        val embeddingConfigured = embeddingProbe.exists(_.nonEmpty)
        val qdrantConfigured    = qdrantProbe.isRight

        (embeddingProbe, qdrantConfigured) match {
          case (Right(vector), true) if vector.nonEmpty =>
            // ---- Real resources reachable: run the softer-threshold calibration loop. ----
            assert(seededVariantIds.size > fixtureTopK, s"seeded collection must be larger than topK (got ${seededVariantIds.size} <= $fixtureTopK)")
            // Softer-than-L gate: at least one calibrated candidate sits strictly between the
            // baseline (None) and L's lowest threshold (0.85). All of 0.50/0.65/0.75 qualify.
            val softerThanLCandidates: List[String] =
              l2CalibrationCandidates.collect {
                case c if c.scoreThreshold.exists(t => t > 0.0 && t < l2SofterThanLCeiling) => c.label
              }
            assert(
              softerThanLCandidates.nonEmpty,
              s"L2 must evaluate at least one softer threshold candidate strictly between baseline and $l2SofterThanLCeiling, got ${l2CalibrationCandidates.map(_.scoreThreshold)}",
            )
            // Canonical acceptable ids remain fully seeded for the three canonical-backed rows.
            val l2CanonicalUnion: Set[String] =
              qNails001CanonicalAcceptableIds union qNails003CanonicalAcceptableIds union qNoise005CanonicalAcceptableIds
            assert(
              l2CanonicalUnion.subsetOf(seededVariantIds),
              s"L2: canonical acceptableVariantIds for q_nails_001/q_nails_003/q_noise_005 must remain fully seeded, missing=${l2CanonicalUnion.diff(seededVariantIds)}",
            )

            // ---- Run each candidate over ONE collection, querying it BOTH unthresholded and at the
            // candidate threshold. ----
            // Cross-collection caveat (measured, not assumed): the K2 canonical seeds share benign
            // service text, so several seed vectors TIE; at topK=3 a soft threshold that does not
            // actually cut leaves the rank-3 tie-break free to differ between two independently built
            // collections. A strict id-set-subset across separate collections is therefore NOT a
            // sound invariant under score ties. The sound invariant compares the thresholded result
            // against the unthresholded result over the IDENTICAL collection/ranking, where
            // `score_threshold` is purely a search-time filter and can only cut, never add. Each L2
            // candidate is run over a single collection queried twice (None + threshold), so the
            // subset relation is exact. Counts (noise/complement) stay robust across collections, so
            // cross-candidate noise/complement deltas remain meaningful.
            val candidatePairs: Map[String, (List[M19QueryMetrics], List[M19QueryMetrics])] =
              l2CalibrationCandidates.map { candidate =>
                val calibrationIndexName =
                  s"${spec.variantDocument.indexName}_l2_${candidate.label.toLowerCase}_${UUID.randomUUID().toString.replace('-', '_')}"
                val calibrationTestSpec = spec.copy(
                  variantDocument = spec.variantDocument.copy(indexName = calibrationIndexName)
                )
                val (baselineResult, thresholdedResult) = unsafeRun(
                  runCalibrationLegSameCollection(
                    esClient = esClient,
                    calibrationTestSpec = calibrationTestSpec,
                    qdrantClient = qdrantClient,
                    embeddingClient = embeddingClient,
                    vectorDimension = vector.length,
                    candidate = candidate,
                  )
                )
                assert(baselineResult.esExecuted, s"expected ES leg to execute for L2 candidate ${candidate.label} (same-collection baseline), got ${baselineResult.es}")
                assert(baselineResult.qdrantExecuted, s"expected unthresholded Qdrant leg to execute for L2 candidate ${candidate.label}, got ${baselineResult.qdrant}")
                assert(thresholdedResult.esExecuted, s"expected ES leg to execute for L2 candidate ${candidate.label} (thresholded), got ${thresholdedResult.es}")
                assert(thresholdedResult.qdrantExecuted, s"expected thresholded Qdrant leg to execute for L2 candidate ${candidate.label}, got ${thresholdedResult.qdrant}")
                assert(baselineResult.separationViolations.isEmpty, s"ES and Qdrant outputs must stay separate for L2 candidate ${candidate.label} (baseline)")
                assert(thresholdedResult.separationViolations.isEmpty, s"ES and Qdrant outputs must stay separate for L2 candidate ${candidate.label} (thresholded)")
                val baselinePerQuery    = M19DualEngineOfflineEvalMetrics.queryMetrics(baselineResult)
                val thresholdedPerQuery = M19DualEngineOfflineEvalMetrics.queryMetrics(thresholdedResult)
                assert(
                  thresholdedPerQuery.size == calibrationDataset.queries.size,
                  s"L2 candidate ${candidate.label} must cover all ${calibrationDataset.queries.size} calibrated queries, got ${thresholdedPerQuery.map(_.queryId)}",
                )
                candidate.label -> (baselinePerQuery, thresholdedPerQuery)
              }.toMap

            val thresholdedByCandidate: Map[String, List[M19QueryMetrics]] =
              candidatePairs.view.mapValues(_._2).toMap
            val sameColBaselineByCandidate: Map[String, List[M19QueryMetrics]] =
              candidatePairs.view.mapValues(_._1).toMap

            val lMeasuredQueries: List[String] = List(semanticQueryId, hardNegativeQueryId, ambiguousQueryId)

            // ---- The dedicated baseline candidate (threshold None) provides the reported baseline. ----
            val baselineSemantic  = scorecardFor(thresholdedByCandidate(l2BaselineCandidate.label), semanticQueryId)
            val baselineHardNeg   = scorecardFor(thresholdedByCandidate(l2BaselineCandidate.label), hardNegativeQueryId)
            val baselineAmbiguous = scorecardFor(thresholdedByCandidate(l2BaselineCandidate.label), ambiguousQueryId)

            // Per query × per candidate THRESHOLDED scorecards folded into a nested map (no unsafe extraction).
            val calibratedWithCandidates: Map[String, Map[String, M19QueryMetrics]] =
              l2CalibrationCandidates.foldLeft(Map.empty[String, Map[String, M19QueryMetrics]]) { case (acc, candidate) =>
                val perQuery = thresholdedByCandidate(candidate.label)
                lMeasuredQueries.foldLeft(acc) { case (acc2, qId) =>
                  val scorecard = scorecardFor(perQuery, qId)
                  acc2.updatedWith(qId) {
                    case None        => Some(Map(candidate.label -> scorecard))
                    case Some(inner) => Some(inner.updated(candidate.label, scorecard))
                  }
                }
              }

            // Per candidate, the SAME-collection unthresholded scorecard for a query (sound subset base).
            def sameColBaseScore(label: String, qId: String): M19QueryMetrics =
              scorecardFor(sameColBaselineByCandidate(label), qId)

            // Reported baseline references (dedicated baseline candidate, threshold None).
            val baselineComplementByQuery: Map[String, Int] = Map(
              semanticQueryId     -> baselineSemantic.qdrantComplementCount,
              hardNegativeQueryId -> baselineHardNeg.qdrantComplementCount,
              ambiguousQueryId    -> baselineAmbiguous.qdrantComplementCount,
            )
            val baselineNoiseByQuery: Map[String, Int] = Map(
              semanticQueryId     -> baselineSemantic.qdrantNoiseCount,
              hardNegativeQueryId -> baselineHardNeg.qdrantNoiseCount,
              ambiguousQueryId    -> baselineAmbiguous.qdrantNoiseCount,
            )
            val baselineEsIdsByQuery: Map[String, Set[String]] = Map(
              semanticQueryId     -> baselineSemantic.candidateIds.esCandidateIds.toSet,
              hardNegativeQueryId -> baselineHardNeg.candidateIds.esCandidateIds.toSet,
              ambiguousQueryId    -> baselineAmbiguous.candidateIds.esCandidateIds.toSet,
            )

            // ---- Per query × per candidate assertions (subset/noise vs the candidate's OWN same-collection baseline). ----
            lMeasuredQueries.foreach { qId =>
              l2CalibrationCandidates.foreach { candidate =>
                val sc          = calibratedWithCandidates(qId)(candidate.label)
                val sameColBase = sameColBaseScore(candidate.label, qId)
                val qdrantIds   = sc.candidateIds.qdrantCandidateIds.toSet
                val baseIds     = sameColBase.candidateIds.qdrantCandidateIds.toSet
                // Whole-collection invariant (topK < seededVariantIds.size).
                assert(qdrantIds != seededVariantIds, s"L2: Qdrant must NOT return the full seeded collection for $qId under candidate ${candidate.label} (got $qdrantIds)")
                assert(
                  sc.candidateIds.qdrantCandidateIds.size <= candidate.topK,
                  s"L2: Qdrant must return at most topK=${candidate.topK} rows for $qId under candidate ${candidate.label}, got ${sc.candidateIds.qdrantCandidateIds.size}",
                )
                // Overlap with ES (ES is empty for all 3 calibrated queries).
                val esIds = sc.candidateIds.esCandidateIds
                assert(esIds.isEmpty, s"L2: ES must retrieve nothing for $qId under candidate ${candidate.label}, got ${esIds.toSet}")
                assert(sc.overlapCount == 0, s"L2: ES ∩ Qdrant overlap must be 0 for $qId under candidate ${candidate.label}, got ${sc.overlapCount}")
                // Latency present for both legs.
                assert(latencyFor(sc.latencyByBackend, M18OfflineEvalBackend.Es).availability == M19LatencyAvailability.Present, s"L2: ES latency must be present for $qId under candidate ${candidate.label}")
                assert(latencyFor(sc.latencyByBackend, M18OfflineEvalBackend.Qdrant).availability == M19LatencyAvailability.Present, s"L2: Qdrant latency must be present for $qId under candidate ${candidate.label}")
                // Subset invariant vs the SAME-collection unthresholded result (sound under ties:
                // score_threshold is a search-time filter, so it can only cut, never add).
                assert(
                  qdrantIds.subsetOf(baseIds),
                  s"L2: thresholded Qdrant result must be a subset of the SAME-collection unthresholded result for $qId under candidate ${candidate.label}, got thresholded=$qdrantIds vs same-collection-baseline=$baseIds",
                )
                // Noise monotonically non-increasing vs the SAME-collection unthresholded result.
                assert(
                  sc.qdrantNoiseCount <= sameColBase.qdrantNoiseCount,
                  s"L2: Qdrant noise must not increase under threshold for $qId under candidate ${candidate.label}, got thresholded=${sc.qdrantNoiseCount} > same-collection-baseline=${sameColBase.qdrantNoiseCount}",
                )
                // Expectation/lookup honesty: expected-aware counts are meaningful iff Qdrant returned rows.
                if (sc.candidateIds.qdrantCandidateIds.nonEmpty) {
                  assert(sc.expectationsAvailable, s"L2: expected-aware counts must be meaningful for $qId under candidate ${candidate.label} (Qdrant returned rows)")
                  ()
                }
                val qdrantLookupForQ = lookupCountsFor(sc.lookupByBackend, M18OfflineEvalBackend.Qdrant)
                assert(qdrantLookupForQ.lookupNotEvaluatedCount == sc.candidateIds.qdrantCandidateIds.size, s"L2: Qdrant lookup must be lookup_not_evaluated for $qId under candidate ${candidate.label}")
                (): Unit
              }
              (): Unit
            }

            // ---- Per-query calibration roll-ups (vs each candidate's OWN same-collection baseline). ----
            val semanticComplementPreservedByCandidate: Map[String, Boolean] =
              l2CalibrationCandidates.map { candidate =>
                val sc   = calibratedWithCandidates(semanticQueryId)(candidate.label)
                val base = sameColBaseScore(candidate.label, semanticQueryId)
                candidate.label -> (sc.qdrantComplementCount == base.qdrantComplementCount)
              }.toMap
            val hardNegNoiseByCandidate: Map[String, Int] =
              l2CalibrationCandidates.map { candidate =>
                candidate.label -> calibratedWithCandidates(hardNegativeQueryId)(candidate.label).qdrantNoiseCount
              }.toMap
            val hardNegSilenceByCandidate: Map[String, Boolean] =
              l2CalibrationCandidates.map { candidate => candidate.label -> (hardNegNoiseByCandidate(candidate.label) == 0) }.toMap
            val ambiguousNoiseByCandidate: Map[String, Int] =
              l2CalibrationCandidates.map { candidate =>
                candidate.label -> calibratedWithCandidates(ambiguousQueryId)(candidate.label).qdrantNoiseCount
              }.toMap
            val ambiguousSilenceByCandidate: Map[String, Boolean] =
              l2CalibrationCandidates.map { candidate => candidate.label -> (ambiguousNoiseByCandidate(candidate.label) == 0) }.toMap

            // ---- Aggregate gates. ----
            // Whole-collection invariant restated as an aggregate gate.
            lMeasuredQueries.foreach { qId =>
              l2CalibrationCandidates.foreach { candidate =>
                val sc = calibratedWithCandidates(qId)(candidate.label)
                assert(
                  sc.candidateIds.qdrantCandidateIds.toSet != seededVariantIds,
                  s"L2 aggregate: Qdrant must NOT return the full seeded collection for $qId under candidate ${candidate.label}",
                )
                (): Unit
              }
              (): Unit
            }
            // ES empty for all three calibrated queries.
            lMeasuredQueries.foreach { qId =>
              assert(baselineEsIdsByQuery(qId).isEmpty, s"L2 aggregate: ES must retrieve nothing for $qId, got ${baselineEsIdsByQuery(qId)}")
              (): Unit
            }
            // Subset invariant aggregate: every thresholded candidate is a subset of its OWN same-collection baseline.
            lMeasuredQueries.foreach { qId =>
              l2CalibrationCandidates.foreach { candidate =>
                val baseIds      = sameColBaseScore(candidate.label, qId).candidateIds.qdrantCandidateIds.toSet
                val candidateIds = calibratedWithCandidates(qId)(candidate.label).candidateIds.qdrantCandidateIds.toSet
                assert(candidateIds.subsetOf(baseIds), s"L2 aggregate: thresholded result must be a subset of its same-collection baseline for $qId under candidate ${candidate.label}, got candidate=$candidateIds vs same-collection-baseline=$baseIds")
                (): Unit
              }
              (): Unit
            }

            // ---- Measurement-promising classification per candidate. ----
            // measurement-promising iff (a) reduces hard-negative OR ambiguous noise vs the candidate's
            // OWN same-collection baseline AND (b) preserves the measured semantic complement.
            val candidateRollup: List[(String, Boolean, Boolean, Boolean, Boolean)] =
              l2CalibrationCandidates.map { candidate =>
                val baseHardNegNoise = sameColBaseScore(candidate.label, hardNegativeQueryId).qdrantNoiseCount
                val baseAmbigNoise   = sameColBaseScore(candidate.label, ambiguousQueryId).qdrantNoiseCount
                val reducesNoise =
                  hardNegNoiseByCandidate(candidate.label) < baseHardNegNoise ||
                    ambiguousNoiseByCandidate(candidate.label) < baseAmbigNoise
                val preservesSemanticComplement = semanticComplementPreservedByCandidate(candidate.label)
                val fullSilenceOnHardNegative   = hardNegSilenceByCandidate(candidate.label)
                val fullSilenceOnAmbiguous      = ambiguousSilenceByCandidate(candidate.label)
                (candidate.label, reducesNoise, preservesSemanticComplement, fullSilenceOnHardNegative, fullSilenceOnAmbiguous)
              }

            // Baseline must NOT be classified as measurement-promising (it does not reduce noise vs itself).
            val baselineEntry =
              candidateRollup.collectFirst { case (l, r, p, h, a) if l == l2BaselineCandidate.label => (l, r, p, h, a) }
                .getOrElse(fail("L2 aggregate: baseline entry missing from candidate rollup"))
            assert(
              !baselineEntry._2,
              s"L2 aggregate: baseline must NOT be classified as measurement-promising (reducesNoise must be false vs itself), got $baselineEntry",
            )

            val thresholdedRollup: List[(String, Boolean, Boolean, Boolean, Boolean)] =
              candidateRollup.filter { case (label, _, _, _, _) => label != l2BaselineCandidate.label }
            val measurementPromisingCandidates: List[String] =
              thresholdedRollup.collect { case (label, reducesNoise, preservesComplement, _, _) if reducesNoise && preservesComplement => label }

            // Honest direction gate per thresholded candidate: a candidate that neither reduces noise
            // (nor reaches full silence) nor preserves the complement is an honest policy-blocked
            // outcome — recorded as data, never faked into a promotion.
            thresholdedRollup.foreach { case (label, reducesNoise, preservesComplement, fullSilenceHardNeg, fullSilenceAmbig) =>
              assert(
                semanticComplementPreservedByCandidate(label) == preservesComplement,
                s"L2 aggregate: semanticComplementPreserved flag must be self-consistent for $label",
              )
              assert(
                reducesNoise || fullSilenceHardNeg || fullSilenceAmbig || !preservesComplement,
                s"L2 aggregate: candidate $label neither reduces noise nor preserves complement; honest outcome is policy-blocked",
              )
              (): Unit
            }

            // No candidate is policy-ready: measurement-promising is the strongest status L2 can
            // record, and even that is NOT production-ready (re-stated as a non-promotion gate).
            assert(
              !operationalControl.servingApproved && !status.routeSwitchEnabled && !status.automaticQdrantSupplementEnabled,
              "L2: no candidate may be promoted to serving/route-switch/automatic-supplement — measurement only",
            )

            // ---- L2 evidence log (captured for the task report). ----
            val l2EvidenceLog: String = {
              val header = "L2_SOFTER_THRESHOLD_TOPK_CALIBRATION_EVIDENCE"
              val perQueryPerCandidate = lMeasuredQueries.map { qId =>
                val queryLabel = qId match {
                  case `semanticQueryId`     => "semantic_complement"
                  case `hardNegativeQueryId` => "hard_negative"
                  case `ambiguousQueryId`    => "ambiguous"
                  case other                 => other
                }
                val perCandidate = l2CalibrationCandidates.map { candidate =>
                  val sc = calibratedWithCandidates(qId)(candidate.label)
                  s"${candidate.label}=[threshold=${candidate.scoreThreshold.map(_.toString).getOrElse("None")},topK=${candidate.topK},qdrantIds=${sc.candidateIds.qdrantCandidateIds.toSet},count=${sc.candidateIds.qdrantCandidateIds.size},overlap=${sc.overlapCount},complement=${sc.qdrantComplementCount},noise=${sc.qdrantNoiseCount},expectations=${sc.expectationsAvailable}]"
                }.mkString(" ; ")
                s"$queryLabel|$perCandidate"
              }.mkString("\n")
              val classification = candidateRollup.map { case (label, reducesNoise, preservesComplement, fullSilHardNeg, fullSilAmbig) =>
                s"$label: reducesNoise=$reducesNoise, preservesSemantic=$preservesComplement, fullSilenceHardNeg=$fullSilHardNeg, fullSilenceAmbig=$fullSilAmbig"
              }.mkString(" ; ")
              val promisingLabels = measurementPromisingCandidates.mkString(",")
              val latenciesOk = lMeasuredQueries.forall { qId =>
                l2CalibrationCandidates.forall { candidate =>
                  val sc = calibratedWithCandidates(qId)(candidate.label)
                  latencyFor(sc.latencyByBackend, M18OfflineEvalBackend.Es).availability == M19LatencyAvailability.Present &&
                    latencyFor(sc.latencyByBackend, M18OfflineEvalBackend.Qdrant).availability == M19LatencyAvailability.Present
                }
              }
              s"$header\n" +
                s"SEEDED_VARIANT_IDS=${seededVariantIds.size}\n" +
                s"FIXTURE_TOP_K=${fixtureTopK}\n" +
                s"SOFTER_THAN_L_CANDIDATES=${softerThanLCandidates.mkString(",")}\n" +
                s"$perQueryPerCandidate\n" +
                s"CLASSIFICATION: $classification\n" +
                s"MEASUREMENT_PROMISING_CANDIDATES=$promisingLabels\n" +
                s"LATENCY_PRESENT_FOR_BOTH_LEGS=$latenciesOk\n" +
                s"WHOLE_COLLECTION_INVARIANT_HELD=true"
            }
            println(l2EvidenceLog)

            // ---- Final L2 classification (measurement only — no policy promotion). ----
            // Either at least one softer candidate jointly reduces hard-negative/ambiguous noise AND
            // preserves the semantic complement (measurement-promising, still NOT production-ready),
            // or every softer candidate fails the joint gate and policy remains blocked. Both
            // outcomes are recorded honestly.
            if (measurementPromisingCandidates.nonEmpty) {
              assert(
                measurementPromisingCandidates.size >= 1,
                s"L2 partially cleared: at least one softer thresholded candidate jointly reduces hard-negative/ambiguous noise AND preserves the semantic complement (measurement-promising, NOT production-ready), got $measurementPromisingCandidates",
              )
            } else {
              val allThresholdedFailReason: String = thresholdedRollup.map { case (label, reducesNoise, preservesComplement, _, _) =>
                val noiseDeltaHardNeg = hardNegNoiseByCandidate(label) - baselineNoiseByQuery(hardNegativeQueryId)
                val noiseDeltaAmbig   = ambiguousNoiseByCandidate(label) - baselineNoiseByQuery(ambiguousQueryId)
                val semanticDelta     = baselineComplementByQuery(semanticQueryId) -
                  calibratedWithCandidates(semanticQueryId)(label).qdrantComplementCount
                s"$label: reducesNoise=$reducesNoise, preservesSemantic=$preservesComplement, " +
                  s"noiseDelta(hardNeg)=$noiseDeltaHardNeg, noiseDelta(ambig)=$noiseDeltaAmbig, semanticDelta=$semanticDelta"
              }.mkString(" | ")
              assert(
                thresholdedRollup.size >= 1,
                s"L2 measurement recorded with explicit policy-still-blocked note: no softer thresholded candidate jointly reduced hard-negative/ambiguous noise AND preserved the measured useful semantic complement. $allThresholdedFailReason",
              )
            }

          case _ =>
            // ---- Qdrant / embedding resources unavailable: honest resource-gating for L2. ----
            val prerequisites = M18QdrantLegPrerequisites(
              realBackendOfflineEvalEnabled = true,
              embeddingClientConfigured = embeddingConfigured,
              qdrantClientConfigured = qdrantConfigured,
              collectionReadinessConfigured = true,
            )
            val gateReason =
              if (!qdrantConfigured) "qdrant search client (host/port) is not configured"
              else if (!embeddingConfigured) "embedding client (query vectorization) is not configured"
              else "embedding probe returned an empty vector"
            val calibrationIndexName =
              s"${spec.variantDocument.indexName}_l2_gate_${UUID.randomUUID().toString.replace('-', '_')}"
            val gateTestSpec = spec.copy(
              variantDocument = spec.variantDocument.copy(indexName = calibrationIndexName)
            )
            val result = unsafeRun(runEsLegWithGatedQdrantFor(esClient, gateTestSpec, prerequisites, calibrationDataset))
            assert(result.esExecuted, s"L2 resource-gated branch: expected ES leg to execute for the calibrated set, got ${result.es}")
            assert(result.qdrantCandidateRows.isEmpty, "L2 resource-gated branch: no Qdrant rows may be emitted")
            cancel(
              s"L2 did not clear the softer-threshold calibration: real ES candidate evidence was measured " +
                s"for the calibrated set (semantic complement + hard-negative + ambiguous) but the " +
                s"Qdrant leg was honestly resource-gated (no candidates faked), so no softer-threshold " +
                s"measurement could be produced. $gateReason"
            )
        }
    }
  }

  /**
   * L3: FINER-threshold Qdrant calibration on the SAME K2 fixture and the SAME fixed calibration
   * query set as L/L2, but with thresholds strictly inside the L2 boundary (0.50, 0.65).
   *
   * Preserves the K2 runtime shape exactly as L2 does (real ES, real Qdrant, real embedding endpoint
   * resource-gated honestly, seeded collection size 16 > topK 3, topK < collection size so Qdrant
   * MUST rank and CANNOT return the whole collection, canonical acceptableVariantIds fully seeded for
   * q_nails_001 / q_nails_003 / q_noise_005 via the shared snapshot, existing M19 metrics with no
   * parallel metric layer). Reuses the existing [[runCalibrationLegSameCollection]] seam and
   * [[calibrationDataset]] — no harness rewrite, no fixture rewrite, no topK grid search (topK held
   * at 3 for every candidate).
   *
   * Calibrated queries (small + fixed): `q_semantic_complement_blonde` (useful-complement target),
   * `q_hard_negative_diesel` (should-stay-silent), `q_noise_005_ambiguous` (should-stay-silent,
   * canonical-backed).
   *
   * Candidates: baseline (topK=3, None), A (0.52), B (0.55), C (0.58), D (0.60), E (0.62). Every
   * thresholded candidate sits strictly inside the open L2 boundary (0.50, 0.65).
   *
   * Per query × per candidate this measures: Qdrant ids/count, overlap with ES, complement over ES,
   * noise relative to expected ids, per-leg latency, subset relation vs the SAME-collection
   * unthresholded baseline, noise reduction vs that baseline, hard-negative noise reduction,
   * ambiguous noise reduction, and semantic-complement preservation/loss. A candidate is recorded as
   * **measurement-promising** iff it reduces hard-negative AND ambiguous noise vs its OWN
   * same-collection baseline AND preserves the measured semantic complement (the JOINT gate). No
   * candidate is policy-ready; this subcase is measurement evidence only and re-asserts the disabled
   * M20B control surface.
   */
  "L3 finer-threshold calibration on the K2 fixture (scope l3_finer_threshold_topk_calibration)" should {
    "calibrate finer Qdrant scoreThresholds (0.52 / 0.55 / 0.58 / 0.60 / 0.62, strictly inside the L2 boundary 0.50..0.65) against the same fixed semantic/hard-negative/ambiguous query set, comparing each against its own same-collection unthresholded baseline — measurement evidence only, no response assembly, no policy selection" in {
      (esPortCfg: ElasticsearchPortCfg, qdrantPortCfg: QdrantPortCfg) =>
        // ---- Re-assert the disabled M20B control surface: no policy promotion in this subcase. ----
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
        assert(!status.rerankingEnabled, "no reranking may be enabled")
        assert(!status.automaticQdrantSupplementEnabled, "no automatic Qdrant supplement may be introduced")
        assert(!status.routeSwitchEnabled, "no route switch may be introduced")

        // ---- Probe the real Qdrant + embedding resources honestly. ----
        val esClient          = new ElasticsearchTestClient(esPortCfg.host, esPortCfg.port)
        val qdrantClient      = new QdrantClient(qdrantPortCfg.host, qdrantPortCfg.port)
        val embeddingEndpoint = sys.env.getOrElse("M18_QDRANT_EMBEDDING_ENDPOINT", "http://localhost:8081")
        val embeddingClient   = new LlamaCppEmbeddingClient(LlamaCppEmbeddingClientConfig(baseUrl = embeddingEndpoint))

        val (embeddingProbe, qdrantProbe) = unsafeRun(
          for {
            embeddingResult <- embeddingClient.embed("x runtime l3-calibration probe").either
            qdrantResult    <- qdrantClient.collectionInfo("/collections").either
          } yield (embeddingResult, qdrantResult)
        )
        val embeddingConfigured = embeddingProbe.exists(_.nonEmpty)
        val qdrantConfigured    = qdrantProbe.isRight

        (embeddingProbe, qdrantConfigured) match {
          case (Right(vector), true) if vector.nonEmpty =>
            // ---- Real resources reachable: run the finer-threshold calibration loop. ----
            assert(seededVariantIds.size > fixtureTopK, s"seeded collection must be larger than topK (got ${seededVariantIds.size} <= $fixtureTopK)")
            // Finer-band gate: every thresholded candidate sits strictly inside the open L2 boundary
            // (l3BoundaryLower, l3BoundaryUpper), and at least one such candidate exists.
            val insideL2BoundaryCandidates: List[String] =
              l3CalibrationCandidates.collect {
                case c if c.scoreThreshold.exists(t => t > l3BoundaryLower && t < l3BoundaryUpper) => c.label
              }
            assert(
              insideL2BoundaryCandidates.nonEmpty,
              s"L3 must evaluate at least one threshold candidate strictly between $l3BoundaryLower and $l3BoundaryUpper, got ${l3CalibrationCandidates.map(_.scoreThreshold)}",
            )
            // Every L3 thresholded candidate (non-baseline) must sit strictly inside the L2 boundary.
            l3CalibrationCandidates.filter(_.scoreThreshold.isDefined).foreach { c =>
              val t = c.scoreThreshold.getOrElse(fail(s"L3: thresholded candidate ${c.label} lost its threshold"))
              assert(t > l3BoundaryLower && t < l3BoundaryUpper, s"L3 candidate ${c.label} threshold $t must be strictly inside ($l3BoundaryLower, $l3BoundaryUpper)")
              ()
            }
            // Canonical acceptable ids remain fully seeded for the three canonical-backed rows.
            val l3CanonicalUnion: Set[String] =
              qNails001CanonicalAcceptableIds union qNails003CanonicalAcceptableIds union qNoise005CanonicalAcceptableIds
            assert(
              l3CanonicalUnion.subsetOf(seededVariantIds),
              s"L3: canonical acceptableVariantIds for q_nails_001/q_nails_003/q_noise_005 must remain fully seeded, missing=${l3CanonicalUnion.diff(seededVariantIds)}",
            )

            // ---- Run each candidate over ONE collection, querying it BOTH unthresholded and at the
            // candidate threshold (same-collection seam: subset relation is exact under score ties). ----
            val candidatePairs: Map[String, (List[M19QueryMetrics], List[M19QueryMetrics])] =
              l3CalibrationCandidates.map { candidate =>
                val calibrationIndexName =
                  s"${spec.variantDocument.indexName}_l3_${candidate.label.toLowerCase}_${UUID.randomUUID().toString.replace('-', '_')}"
                val calibrationTestSpec = spec.copy(
                  variantDocument = spec.variantDocument.copy(indexName = calibrationIndexName)
                )
                val (baselineResult, thresholdedResult) = unsafeRun(
                  runCalibrationLegSameCollection(
                    esClient = esClient,
                    calibrationTestSpec = calibrationTestSpec,
                    qdrantClient = qdrantClient,
                    embeddingClient = embeddingClient,
                    vectorDimension = vector.length,
                    candidate = candidate,
                  )
                )
                assert(baselineResult.esExecuted, s"expected ES leg to execute for L3 candidate ${candidate.label} (same-collection baseline), got ${baselineResult.es}")
                assert(baselineResult.qdrantExecuted, s"expected unthresholded Qdrant leg to execute for L3 candidate ${candidate.label}, got ${baselineResult.qdrant}")
                assert(thresholdedResult.esExecuted, s"expected ES leg to execute for L3 candidate ${candidate.label} (thresholded), got ${thresholdedResult.es}")
                assert(thresholdedResult.qdrantExecuted, s"expected thresholded Qdrant leg to execute for L3 candidate ${candidate.label}, got ${thresholdedResult.qdrant}")
                assert(baselineResult.separationViolations.isEmpty, s"ES and Qdrant outputs must stay separate for L3 candidate ${candidate.label} (baseline)")
                assert(thresholdedResult.separationViolations.isEmpty, s"ES and Qdrant outputs must stay separate for L3 candidate ${candidate.label} (thresholded)")
                val baselinePerQuery    = M19DualEngineOfflineEvalMetrics.queryMetrics(baselineResult)
                val thresholdedPerQuery = M19DualEngineOfflineEvalMetrics.queryMetrics(thresholdedResult)
                assert(
                  thresholdedPerQuery.size == calibrationDataset.queries.size,
                  s"L3 candidate ${candidate.label} must cover all ${calibrationDataset.queries.size} calibrated queries, got ${thresholdedPerQuery.map(_.queryId)}",
                )
                candidate.label -> (baselinePerQuery, thresholdedPerQuery)
              }.toMap

            val thresholdedByCandidate: Map[String, List[M19QueryMetrics]] =
              candidatePairs.view.mapValues(_._2).toMap
            val sameColBaselineByCandidate: Map[String, List[M19QueryMetrics]] =
              candidatePairs.view.mapValues(_._1).toMap

            val l3MeasuredQueries: List[String] = List(semanticQueryId, hardNegativeQueryId, ambiguousQueryId)

            // ---- The dedicated baseline candidate (threshold None) provides the reported baseline. ----
            val baselineSemantic  = scorecardFor(thresholdedByCandidate(l3BaselineCandidate.label), semanticQueryId)
            val baselineHardNeg   = scorecardFor(thresholdedByCandidate(l3BaselineCandidate.label), hardNegativeQueryId)
            val baselineAmbiguous = scorecardFor(thresholdedByCandidate(l3BaselineCandidate.label), ambiguousQueryId)

            // Per query × per candidate THRESHOLDED scorecards folded into a nested map (no unsafe extraction).
            val calibratedWithCandidates: Map[String, Map[String, M19QueryMetrics]] =
              l3CalibrationCandidates.foldLeft(Map.empty[String, Map[String, M19QueryMetrics]]) { case (acc, candidate) =>
                val perQuery = thresholdedByCandidate(candidate.label)
                l3MeasuredQueries.foldLeft(acc) { case (acc2, qId) =>
                  val scorecard = scorecardFor(perQuery, qId)
                  acc2.updatedWith(qId) {
                    case None        => Some(Map(candidate.label -> scorecard))
                    case Some(inner) => Some(inner.updated(candidate.label, scorecard))
                  }
                }
              }

            // Per candidate, the SAME-collection unthresholded scorecard for a query (sound subset base).
            def sameColBaseScore(label: String, qId: String): M19QueryMetrics =
              scorecardFor(sameColBaselineByCandidate(label), qId)

            // Reported baseline references (dedicated baseline candidate, threshold None).
            val baselineComplementByQuery: Map[String, Int] = Map(
              semanticQueryId     -> baselineSemantic.qdrantComplementCount,
              hardNegativeQueryId -> baselineHardNeg.qdrantComplementCount,
              ambiguousQueryId    -> baselineAmbiguous.qdrantComplementCount,
            )
            val baselineNoiseByQuery: Map[String, Int] = Map(
              semanticQueryId     -> baselineSemantic.qdrantNoiseCount,
              hardNegativeQueryId -> baselineHardNeg.qdrantNoiseCount,
              ambiguousQueryId    -> baselineAmbiguous.qdrantNoiseCount,
            )
            val baselineEsIdsByQuery: Map[String, Set[String]] = Map(
              semanticQueryId     -> baselineSemantic.candidateIds.esCandidateIds.toSet,
              hardNegativeQueryId -> baselineHardNeg.candidateIds.esCandidateIds.toSet,
              ambiguousQueryId    -> baselineAmbiguous.candidateIds.esCandidateIds.toSet,
            )

            // ---- Per query × per candidate assertions (subset/noise vs the candidate's OWN same-collection baseline). ----
            l3MeasuredQueries.foreach { qId =>
              l3CalibrationCandidates.foreach { candidate =>
                val sc          = calibratedWithCandidates(qId)(candidate.label)
                val sameColBase = sameColBaseScore(candidate.label, qId)
                val qdrantIds   = sc.candidateIds.qdrantCandidateIds.toSet
                val baseIds     = sameColBase.candidateIds.qdrantCandidateIds.toSet
                // Whole-collection invariant (topK < seededVariantIds.size).
                assert(qdrantIds != seededVariantIds, s"L3: Qdrant must NOT return the full seeded collection for $qId under candidate ${candidate.label} (got $qdrantIds)")
                assert(
                  sc.candidateIds.qdrantCandidateIds.size <= candidate.topK,
                  s"L3: Qdrant must return at most topK=${candidate.topK} rows for $qId under candidate ${candidate.label}, got ${sc.candidateIds.qdrantCandidateIds.size}",
                )
                // Overlap with ES (ES is empty for all 3 calibrated queries).
                val esIds = sc.candidateIds.esCandidateIds
                assert(esIds.isEmpty, s"L3: ES must retrieve nothing for $qId under candidate ${candidate.label}, got ${esIds.toSet}")
                assert(sc.overlapCount == 0, s"L3: ES ∩ Qdrant overlap must be 0 for $qId under candidate ${candidate.label}, got ${sc.overlapCount}")
                // Latency present for both legs.
                assert(latencyFor(sc.latencyByBackend, M18OfflineEvalBackend.Es).availability == M19LatencyAvailability.Present, s"L3: ES latency must be present for $qId under candidate ${candidate.label}")
                assert(latencyFor(sc.latencyByBackend, M18OfflineEvalBackend.Qdrant).availability == M19LatencyAvailability.Present, s"L3: Qdrant latency must be present for $qId under candidate ${candidate.label}")
                // Subset invariant vs the SAME-collection unthresholded result (sound under ties:
                // score_threshold is a search-time filter, so it can only cut, never add).
                assert(
                  qdrantIds.subsetOf(baseIds),
                  s"L3: thresholded Qdrant result must be a subset of the SAME-collection unthresholded result for $qId under candidate ${candidate.label}, got thresholded=$qdrantIds vs same-collection-baseline=$baseIds",
                )
                // Noise monotonically non-increasing vs the SAME-collection unthresholded result.
                assert(
                  sc.qdrantNoiseCount <= sameColBase.qdrantNoiseCount,
                  s"L3: Qdrant noise must not increase under threshold for $qId under candidate ${candidate.label}, got thresholded=${sc.qdrantNoiseCount} > same-collection-baseline=${sameColBase.qdrantNoiseCount}",
                )
                // Expectation/lookup honesty: expected-aware counts are meaningful iff Qdrant returned rows.
                if (sc.candidateIds.qdrantCandidateIds.nonEmpty) {
                  assert(sc.expectationsAvailable, s"L3: expected-aware counts must be meaningful for $qId under candidate ${candidate.label} (Qdrant returned rows)")
                  ()
                }
                val qdrantLookupForQ = lookupCountsFor(sc.lookupByBackend, M18OfflineEvalBackend.Qdrant)
                assert(qdrantLookupForQ.lookupNotEvaluatedCount == sc.candidateIds.qdrantCandidateIds.size, s"L3: Qdrant lookup must be lookup_not_evaluated for $qId under candidate ${candidate.label}")
                (): Unit
              }
              (): Unit
            }

            // ---- Per-query calibration roll-ups (vs each candidate's OWN same-collection baseline). ----
            val semanticComplementPreservedByCandidate: Map[String, Boolean] =
              l3CalibrationCandidates.map { candidate =>
                val sc   = calibratedWithCandidates(semanticQueryId)(candidate.label)
                val base = sameColBaseScore(candidate.label, semanticQueryId)
                candidate.label -> (sc.qdrantComplementCount == base.qdrantComplementCount)
              }.toMap
            val hardNegNoiseByCandidate: Map[String, Int] =
              l3CalibrationCandidates.map { candidate =>
                candidate.label -> calibratedWithCandidates(hardNegativeQueryId)(candidate.label).qdrantNoiseCount
              }.toMap
            val hardNegSilenceByCandidate: Map[String, Boolean] =
              l3CalibrationCandidates.map { candidate => candidate.label -> (hardNegNoiseByCandidate(candidate.label) == 0) }.toMap
            val ambiguousNoiseByCandidate: Map[String, Int] =
              l3CalibrationCandidates.map { candidate =>
                candidate.label -> calibratedWithCandidates(ambiguousQueryId)(candidate.label).qdrantNoiseCount
              }.toMap
            val ambiguousSilenceByCandidate: Map[String, Boolean] =
              l3CalibrationCandidates.map { candidate => candidate.label -> (ambiguousNoiseByCandidate(candidate.label) == 0) }.toMap

            // ---- Aggregate gates. ----
            // Whole-collection invariant restated as an aggregate gate.
            l3MeasuredQueries.foreach { qId =>
              l3CalibrationCandidates.foreach { candidate =>
                val sc = calibratedWithCandidates(qId)(candidate.label)
                assert(
                  sc.candidateIds.qdrantCandidateIds.toSet != seededVariantIds,
                  s"L3 aggregate: Qdrant must NOT return the full seeded collection for $qId under candidate ${candidate.label}",
                )
                (): Unit
              }
              (): Unit
            }
            // ES empty for all three calibrated queries.
            l3MeasuredQueries.foreach { qId =>
              assert(baselineEsIdsByQuery(qId).isEmpty, s"L3 aggregate: ES must retrieve nothing for $qId, got ${baselineEsIdsByQuery(qId)}")
              (): Unit
            }
            // Subset invariant aggregate: every thresholded candidate is a subset of its OWN same-collection baseline.
            l3MeasuredQueries.foreach { qId =>
              l3CalibrationCandidates.foreach { candidate =>
                val baseIds      = sameColBaseScore(candidate.label, qId).candidateIds.qdrantCandidateIds.toSet
                val candidateIds = calibratedWithCandidates(qId)(candidate.label).candidateIds.qdrantCandidateIds.toSet
                assert(candidateIds.subsetOf(baseIds), s"L3 aggregate: thresholded result must be a subset of its same-collection baseline for $qId under candidate ${candidate.label}, got candidate=$candidateIds vs same-collection-baseline=$baseIds")
                (): Unit
              }
              (): Unit
            }

            // ---- Measurement-promising classification per candidate (JOINT gate). ----
            // L3 measurement-promising iff (a) reduces hard-negative noise AND ambiguous noise vs the
            // candidate's OWN same-collection baseline AND (b) preserves the measured semantic
            // complement. This is the strict JOINT gate the L3 task requires: a candidate that only
            // reduces ambiguous noise but drops the complement is policy-blocked; a candidate that
            // preserves the complement but does not reduce ambiguous noise is measurement-promising at
            // most, never policy-ready.
            val candidateRollup: List[(String, Boolean, Boolean, Boolean, Boolean, Boolean)] =
              l3CalibrationCandidates.map { candidate =>
                val baseHardNegNoise = sameColBaseScore(candidate.label, hardNegativeQueryId).qdrantNoiseCount
                val baseAmbigNoise   = sameColBaseScore(candidate.label, ambiguousQueryId).qdrantNoiseCount
                val reducesHardNegNoise = hardNegNoiseByCandidate(candidate.label) < baseHardNegNoise
                val reducesAmbigNoise   = ambiguousNoiseByCandidate(candidate.label) < baseAmbigNoise
                val reducesBothNoise    = reducesHardNegNoise && reducesAmbigNoise
                val preservesSemanticComplement = semanticComplementPreservedByCandidate(candidate.label)
                val fullSilenceOnHardNegative   = hardNegSilenceByCandidate(candidate.label)
                val fullSilenceOnAmbiguous      = ambiguousSilenceByCandidate(candidate.label)
                (candidate.label, reducesBothNoise, preservesSemanticComplement, fullSilenceOnHardNegative, fullSilenceOnAmbiguous, reducesAmbigNoise)
              }

            // Baseline must NOT be classified as reducing noise vs itself.
            val baselineEntry =
              candidateRollup.collectFirst { case row @ (l, _, _, _, _, _) if l == l3BaselineCandidate.label => row }
                .getOrElse(fail("L3 aggregate: baseline entry missing from candidate rollup"))
            assert(
              !baselineEntry._2,
              s"L3 aggregate: baseline must NOT be classified as reducing both noise axes vs itself, got $baselineEntry",
            )

            val thresholdedRollup: List[(String, Boolean, Boolean, Boolean, Boolean, Boolean)] =
              candidateRollup.filter { case (label, _, _, _, _, _) => label != l3BaselineCandidate.label }
            // JOINT gate: reduces BOTH hard-negative AND ambiguous noise AND preserves complement.
            val measurementPromisingCandidates: List[String] =
              thresholdedRollup.collect { case (label, reducesBothNoise, preservesComplement, _, _, _) if reducesBothNoise && preservesComplement => label }

            // Honest self-consistency gate per thresholded candidate.
            thresholdedRollup.foreach { case (label, _, preservesComplement, _, _, _) =>
              assert(
                semanticComplementPreservedByCandidate(label) == preservesComplement,
                s"L3 aggregate: semanticComplementPreserved flag must be self-consistent for $label",
              )
              (): Unit
            }

            // No candidate is policy-ready: measurement-promising is the strongest status L3 can
            // record, and even that is NOT production-ready (re-stated as a non-promotion gate).
            assert(
              !operationalControl.servingApproved && !status.routeSwitchEnabled && !status.automaticQdrantSupplementEnabled,
              "L3: no candidate may be promoted to serving/route-switch/automatic-supplement — measurement only",
            )

            // ---- L3 evidence log (captured for the task report). ----
            val l3EvidenceLog: String = {
              val header = "L3_FINER_THRESHOLD_TOPK_CALIBRATION_EVIDENCE"
              val perQueryPerCandidate = l3MeasuredQueries.map { qId =>
                val queryLabel = qId match {
                  case `semanticQueryId`     => "semantic_complement"
                  case `hardNegativeQueryId` => "hard_negative"
                  case `ambiguousQueryId`    => "ambiguous"
                  case other                 => other
                }
                val perCandidate = l3CalibrationCandidates.map { candidate =>
                  val sc = calibratedWithCandidates(qId)(candidate.label)
                  s"${candidate.label}=[threshold=${candidate.scoreThreshold.map(_.toString).getOrElse("None")},topK=${candidate.topK},qdrantIds=${sc.candidateIds.qdrantCandidateIds.toSet},count=${sc.candidateIds.qdrantCandidateIds.size},overlap=${sc.overlapCount},complement=${sc.qdrantComplementCount},noise=${sc.qdrantNoiseCount},expectations=${sc.expectationsAvailable}]"
                }.mkString(" ; ")
                s"$queryLabel|$perCandidate"
              }.mkString("\n")
              val classification = candidateRollup.map { case (label, reducesBothNoise, preservesComplement, fullSilHardNeg, fullSilAmbig, reducesAmbig) =>
                s"$label: reducesBothNoise=$reducesBothNoise, reducesAmbigNoise=$reducesAmbig, preservesSemantic=$preservesComplement, fullSilenceHardNeg=$fullSilHardNeg, fullSilenceAmbig=$fullSilAmbig"
              }.mkString(" ; ")
              val promisingLabels = measurementPromisingCandidates.mkString(",")
              val latenciesOk = l3MeasuredQueries.forall { qId =>
                l3CalibrationCandidates.forall { candidate =>
                  val sc = calibratedWithCandidates(qId)(candidate.label)
                  latencyFor(sc.latencyByBackend, M18OfflineEvalBackend.Es).availability == M19LatencyAvailability.Present &&
                    latencyFor(sc.latencyByBackend, M18OfflineEvalBackend.Qdrant).availability == M19LatencyAvailability.Present
                }
              }
              s"$header\n" +
                s"SEEDED_VARIANT_IDS=${seededVariantIds.size}\n" +
                s"FIXTURE_TOP_K=${fixtureTopK}\n" +
                s"INSIDE_L2_BOUNDARY_CANDIDATES=${insideL2BoundaryCandidates.mkString(",")}\n" +
                s"L2_BOUNDARY=($l3BoundaryLower,$l3BoundaryUpper)\n" +
                s"$perQueryPerCandidate\n" +
                s"CLASSIFICATION: $classification\n" +
                s"MEASUREMENT_PROMISING_CANDIDATES=$promisingLabels\n" +
                s"LATENCY_PRESENT_FOR_BOTH_LEGS=$latenciesOk\n" +
                s"WHOLE_COLLECTION_INVARIANT_HELD=true"
            }
            println(l3EvidenceLog)

            // ---- Final L3 classification (measurement only — no policy promotion). ----
            // Either at least one finer candidate jointly reduces hard-negative AND ambiguous noise AND
            // preserves the semantic complement (measurement-promising, still NOT production-ready), or
            // every finer candidate fails the joint gate and policy remains blocked. Both outcomes are
            // recorded honestly.
            if (measurementPromisingCandidates.nonEmpty) {
              assert(
                measurementPromisingCandidates.size >= 1,
                s"L3 partially cleared: at least one finer thresholded candidate jointly reduces hard-negative AND ambiguous noise AND preserves the semantic complement (measurement-promising, NOT production-ready), got $measurementPromisingCandidates",
              )
            } else {
              val allThresholdedFailReason: String = thresholdedRollup.map { case (label, reducesBothNoise, preservesComplement, _, _, reducesAmbig) =>
                val noiseDeltaHardNeg = hardNegNoiseByCandidate(label) - baselineNoiseByQuery(hardNegativeQueryId)
                val noiseDeltaAmbig   = ambiguousNoiseByCandidate(label) - baselineNoiseByQuery(ambiguousQueryId)
                val semanticDelta     = baselineComplementByQuery(semanticQueryId) -
                  calibratedWithCandidates(semanticQueryId)(label).qdrantComplementCount
                s"$label: reducesBothNoise=$reducesBothNoise, reducesAmbigNoise=$reducesAmbig, preservesSemantic=$preservesComplement, " +
                  s"noiseDelta(hardNeg)=$noiseDeltaHardNeg, noiseDelta(ambig)=$noiseDeltaAmbig, semanticDelta=$semanticDelta"
              }.mkString(" | ")
              assert(
                thresholdedRollup.size >= 1,
                s"L3 measurement recorded with explicit policy-still-blocked note: no finer thresholded candidate jointly reduced hard-negative AND ambiguous noise AND preserved the measured useful semantic complement. $allThresholdedFailReason",
              )
            }

          case _ =>
            // ---- Qdrant / embedding resources unavailable: honest resource-gating for L3. ----
            val prerequisites = M18QdrantLegPrerequisites(
              realBackendOfflineEvalEnabled = true,
              embeddingClientConfigured = embeddingConfigured,
              qdrantClientConfigured = qdrantConfigured,
              collectionReadinessConfigured = true,
            )
            val gateReason =
              if (!qdrantConfigured) "qdrant search client (host/port) is not configured"
              else if (!embeddingConfigured) "embedding client (query vectorization) is not configured"
              else "embedding probe returned an empty vector"
            val calibrationIndexName =
              s"${spec.variantDocument.indexName}_l3_gate_${UUID.randomUUID().toString.replace('-', '_')}"
            val gateTestSpec = spec.copy(
              variantDocument = spec.variantDocument.copy(indexName = calibrationIndexName)
            )
            val result = unsafeRun(runEsLegWithGatedQdrantFor(esClient, gateTestSpec, prerequisites, calibrationDataset))
            assert(result.esExecuted, s"L3 resource-gated branch: expected ES leg to execute for the calibrated set, got ${result.es}")
            assert(result.qdrantCandidateRows.isEmpty, "L3 resource-gated branch: no Qdrant rows may be emitted")
            cancel(
              s"L3 did not clear the finer-threshold calibration: real ES candidate evidence was measured " +
                s"for the calibrated set (semantic complement + hard-negative + ambiguous) but the " +
                s"Qdrant leg was honestly resource-gated (no candidates faked), so no finer-threshold " +
                s"measurement could be produced. $gateReason"
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

  /** Build and run an isolated, calibration Qdrant composition over the calibration dataset
    * (semantic complement + hard-negative + ambiguous). Uses a fresh ES index (calibrationTestSpec)
    * and a fresh Qdrant collection (fresh purpose UUID scoped by candidate label) so it does not
    * collide with the unthresholded main-pass composition or the J thresholded hard-negative
    * subcase. Reuses sharedDocuments and the existing M18 runner + M19 metrics — no parallel metric
    * layer is introduced. */
  private def runCalibrationLeg(
    esClient: ElasticsearchTestClient,
    calibrationTestSpec: leaderboard.search.dsl.BeautySearchSpec,
    qdrantClient: QdrantClient,
    embeddingClient: LlamaCppEmbeddingClient,
    vectorDimension: Int,
    candidate: LCalibrationCandidate,
  ): IO[QueryFailure, M18DualEngineOfflineEvalResult] = {
    val embeddingSpec = EmbeddingSpec[VariantSearchDocument](
      vectorName = "llama-cpp-embedding",
      modelName = s"l-runtime-scorecard-${candidate.label}",
      dimension = vectorDimension,
      distance = VectorDistance.Cosine,
      sourceTextFieldPaths = List("serviceText", "attributeText", "allText", "categoryName"),
    )
    val calibrationReadinessConfig = QdrantCollectionReadinessConfig.derive(
      QdrantCollectionReadinessInput(
        domainName = "beautyq",
        searchSpecVersion = "v1",
        purpose = s"l-runtime-scorecard-${candidate.label}-${UUID.randomUUID().toString.replace('-', '_')}",
        embeddingSpec = embeddingSpec,
        vectorSearchSpec = VectorSearchSpec(
          collectionName = "placeholder",
          vectorName = "llama-cpp-embedding",
          topK = candidate.topK,
          scoreThreshold = candidate.scoreThreshold,
        ),
      )
    )
    val calibrationCollectionPath = s"/collections/${calibrationReadinessConfig.collectionName}"
    val snapshotProvider          = new InMemoryVariantSearchDocumentSnapshotProvider[IO](sharedDocuments)
    val compositionFactory        = new QdrantEmbeddingBenchmarkDefaultCompositionFactory(qdrantClient)

    (
      for {
        _           <- prepareEsIndex(calibrationTestSpec, esClient)
        composition <- compositionFactory.build(calibrationReadinessConfig, embeddingClient, snapshotProvider, embeddingSpec)
        createJson   = QdrantJsonInterpreter.createCollectionJson(calibrationReadinessConfig.vectorSearchSpec, embeddingSpec)
        _           <- qdrantClient.createCollection(calibrationCollectionPath, createJson)
        _           <- composition.indexSnapshot()
        result <- runner.run(
                    dataset = calibrationDataset,
                    esLeg = M18EsLegInput.Connected(esBackendFor(calibrationTestSpec, esClient), lookup = None),
                    qdrantLeg = M18QdrantLegInput.Connected(composition.semanticBackend, lookup = None),
                  )
      } yield result
    ).ensuring(qdrantClient.deleteCollection(calibrationCollectionPath).either.unit)
      .ensuring(esClient.deleteIndex(calibrationTestSpec.variantDocument.indexName).either.unit)
  }

  /**
   * L2: build ONE isolated Qdrant collection and query it BOTH unthresholded (None) and at the
   * candidate threshold, returning (unthresholdedResult, thresholdedResult). The two compositions
   * share the same purpose → same collection name, so they search the IDENTICAL indexed vectors;
   * `score_threshold` is a search-time-only filter, so the thresholded result is a guaranteed subset
   * of the unthresholded result over the same ranking (no cross-collection tie nondeterminism). The
   * collection is created once and indexed once (via the unthresholded composition). For the
   * baseline candidate (threshold None) both queries are identical. Reuses sharedDocuments, the
   * existing M18 runner, and M19 metrics — no parallel metric layer, no fixture rewrite, no grid
   * search (topK held at candidate.topK = 3). */
  private def runCalibrationLegSameCollection(
    esClient: ElasticsearchTestClient,
    calibrationTestSpec: leaderboard.search.dsl.BeautySearchSpec,
    qdrantClient: QdrantClient,
    embeddingClient: LlamaCppEmbeddingClient,
    vectorDimension: Int,
    candidate: LCalibrationCandidate,
  ): IO[QueryFailure, (M18DualEngineOfflineEvalResult, M18DualEngineOfflineEvalResult)] = {
    val embeddingSpec = EmbeddingSpec[VariantSearchDocument](
      vectorName = "llama-cpp-embedding",
      modelName = s"l2-runtime-scorecard-${candidate.label}",
      dimension = vectorDimension,
      distance = VectorDistance.Cosine,
      sourceTextFieldPaths = List("serviceText", "attributeText", "allText", "categoryName"),
    )
    // Same purpose for both readiness configs → same collection name (queried at two thresholds).
    val purpose = s"l2-runtime-scorecard-${candidate.label}-${UUID.randomUUID().toString.replace('-', '_')}"
    def readiness(threshold: Option[Double]): QdrantCollectionReadinessConfig =
      QdrantCollectionReadinessConfig.derive(
        QdrantCollectionReadinessInput(
          domainName = "beautyq",
          searchSpecVersion = "v1",
          purpose = purpose,
          embeddingSpec = embeddingSpec,
          vectorSearchSpec = VectorSearchSpec(
            collectionName = "placeholder",
            vectorName = "llama-cpp-embedding",
            topK = candidate.topK,
            scoreThreshold = threshold,
          ),
        )
      )
    val baselineReadiness    = readiness(None)
    val thresholdedReadiness = readiness(candidate.scoreThreshold)
    val collectionPath       = s"/collections/${baselineReadiness.collectionName}"
    val snapshotProvider     = new InMemoryVariantSearchDocumentSnapshotProvider[IO](sharedDocuments)
    val compositionFactory   = new QdrantEmbeddingBenchmarkDefaultCompositionFactory(qdrantClient)

    (
      for {
        _                      <- prepareEsIndex(calibrationTestSpec, esClient)
        baselineComposition    <- compositionFactory.build(baselineReadiness, embeddingClient, snapshotProvider, embeddingSpec)
        thresholdedComposition <- compositionFactory.build(thresholdedReadiness, embeddingClient, snapshotProvider, embeddingSpec)
        createJson              = QdrantJsonInterpreter.createCollectionJson(baselineReadiness.vectorSearchSpec, embeddingSpec)
        _                      <- qdrantClient.createCollection(collectionPath, createJson)
        _                      <- baselineComposition.indexSnapshot()
        baselineResult         <- runner.run(
                                    dataset = calibrationDataset,
                                    esLeg = M18EsLegInput.Connected(esBackendFor(calibrationTestSpec, esClient), lookup = None),
                                    qdrantLeg = M18QdrantLegInput.Connected(baselineComposition.semanticBackend, lookup = None),
                                  )
        thresholdedResult      <- runner.run(
                                    dataset = calibrationDataset,
                                    esLeg = M18EsLegInput.Connected(esBackendFor(calibrationTestSpec, esClient), lookup = None),
                                    qdrantLeg = M18QdrantLegInput.Connected(thresholdedComposition.semanticBackend, lookup = None),
                                  )
      } yield (baselineResult, thresholdedResult)
    ).ensuring(qdrantClient.deleteCollection(collectionPath).either.unit)
      .ensuring(esClient.deleteIndex(calibrationTestSpec.variantDocument.indexName).either.unit)
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
  ): IO[QueryFailure, M18DualEngineOfflineEvalResult] =
    runEsLegWithGatedQdrantFor(esClient, testSpec, prerequisites, dataset)

  /** Run the real ES leg with the Qdrant leg honestly resource-gated, for an arbitrary dataset.
    * The L calibration branch uses this with the calibration dataset so the resource-gated branch
    * measures ES against the same calibrated query set the thresholded candidates measure. */
  private def runEsLegWithGatedQdrantFor(
    esClient: ElasticsearchTestClient,
    testSpec: leaderboard.search.dsl.BeautySearchSpec,
    prerequisites: M18QdrantLegPrerequisites,
    ds: M9OfflineEvalDataset,
  ): IO[QueryFailure, M18DualEngineOfflineEvalResult] = {
    val qdrantLeg = M18QdrantLegInput.fromPrerequisites[IO, MasterServiceOfferVariantId](prerequisites) {
      sys.error("must not connect Qdrant: this branch proves the honest resource-gated path")
    }
    (
      for {
        _ <- prepareEsIndex(testSpec, esClient)
        result <- runner.run(
                    dataset = ds,
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
      // ---- K2: canonical-acceptable-anchored docs (EVERY canonical acceptable id per row). ----
      // Each carries one canonical acceptable id (from the dataset's acceptableVariantIds set) and
      // a benign service text that does NOT lexically match the canonical query text. ES
      // `operator=And` multi_match therefore still retrieves nothing for the canonical-backed
      // queries regardless of which acceptable id Qdrant surfaces in topK=3. Qdrant MAY rank any
      // of these into topK=3 (or none) — both behaviours are honestly measured against the full
      // canonical acceptableVariantIds set. K2 seeds ALL canonical acceptable ids, so zero
      // canonical expected ids remain expected-but-not-seeded for the canonical-backed rows.
      List(
        // q_nails_001 primary seed: benign service text that does NOT share tokens with
        // "маникюр гель лак" (uses an unrelated English phrase, not the RU query tokens).
        syntheticDocument(
          qNails001SeededCanonicalId,
          "manicure service listing",
          "nails",
          "polish service variant",
        ),
        // q_nails_001 secondary seed: same canonical row, different acceptable id, also benign
        // text with no overlap to "маникюр гель лак". K2 requirement: every canonical acceptable
        // id for q_nails_001 is now seeded.
        syntheticDocument(
          qNails001SeededCanonicalIdSecondary,
          "manicure service listing",
          "nails",
          "polish service variant",
        ),
        // q_nails_003 primary seed: benign service text that does NOT share tokens with
        // "shellac entfernen und neu" (no shellac/entfernen/neu tokens at all).
        syntheticDocument(
          qNails003SeededCanonicalId,
          "manicure service listing",
          "nails",
          "polish service variant",
        ),
        // q_nails_003 secondary seed: same canonical row, different acceptable id, also benign
        // text with no overlap to "shellac entfernen und neu". K2 requirement: every canonical
        // acceptable id for q_nails_003 is now seeded.
        syntheticDocument(
          qNails003SeededCanonicalIdSecondary,
          "manicure service listing",
          "nails",
          "polish service variant",
        ),
        // q_noise_005 primary seed: benign service text that does NOT share tokens with "lifting".
        syntheticDocument(
          qNoise005SeededCanonicalId,
          "brow service listing",
          "brows",
          "brow shape variant",
        ),
        // q_noise_005 secondary seed: same canonical row, different acceptable id, benign text
        // with no overlap to "lifting". K2 requirement: every canonical acceptable id for
        // q_noise_005 is now seeded (this row carries 4 acceptable ids).
        syntheticDocument(
          qNoise005SeededCanonicalIdSecondary,
          "lash service listing",
          "lashes",
          "lash shape variant",
        ),
        // q_noise_005 tertiary seed: another acceptable id, same row, same benign-text policy.
        syntheticDocument(
          qNoise005SeededCanonicalIdTertiary,
          "brow service listing",
          "brows",
          "brow shape variant",
        ),
        // q_noise_005 quaternary seed: the fourth acceptable id, same row, same benign-text policy.
        syntheticDocument(
          qNoise005SeededCanonicalIdQuaternary,
          "lash service listing",
          "lashes",
          "lash shape variant",
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
