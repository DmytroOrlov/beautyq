# BeautyQ Full Eval-Query & Seed Coverage Inventory (G)

Source-confirmed coverage-truth inventory of current BeautyQ eval-query and seed coverage. This task
is about **coverage truth, not policy**. It selects no policy, assembles no hybrid response, edits no
Scala/test/route/plugin, and deletes no cleanup candidate. Every claim is grounded in the files
listed in §10; no `NEED_BUNDLE` was needed.

> **Key coverage-truth finding up front:** the runtime ES-vs-Qdrant scorecard (X) was measured over a
> **separate, synthetic 3-query fixture** (`balayage` / `blonde` / `diesel`) defined inline in
> [`RuntimeEsQdrantScorecardProofSpec`](../../bifunctor-tagless/src/test/scala/leaderboard/search/RuntimeEsQdrantScorecardProofSpec.scala),
> **not** over the canonical 63-query dataset
> ([`beautyq_search_eval_queries_v1.json`](../../bifunctor-tagless/src/test/resources/leaderboard/search/eval/beautyq_search_eval_queries_v1.json)).
> The canonical 63-query dataset is currently only **statically mapped** (manual rows, no backend
> execution; [`M9BeautyQSearchEvalQueryDatasetStaticRowsSpec`](../../bifunctor-tagless/src/test/scala/leaderboard/search/M9BeautyQSearchEvalQueryDatasetStaticRowsSpec.scala)).
> Therefore **zero canonical eval rows have runtime scorecard evidence today.**

---

## 1. Current main-task status

| Step | State | Evidence |
|------|-------|----------|
| AP1 | **Cleared** | Default graph-wired `/beauty-search` returns `200 OK` with non-empty `variantCarousel` against real ES, with coarse latency evidence ([`BeautySearchRealEsRouteRegressionSpec`](../../bifunctor-tagless/src/test/scala/leaderboard/search/BeautySearchRealEsRouteRegressionSpec.scala), scope `real_es_default_route_graph_non_empty`). |
| T | **Cleared** | Real ES and real Qdrant both execute for the **same** BeautyQ query behind the disabled/internal `M18DualEngineOfflineEvalRunner` surface ([`DisabledRuntimeHybridExecutionEsQdrantProofSpec`](../../bifunctor-tagless/src/test/scala/leaderboard/search/DisabledRuntimeHybridExecutionEsQdrantProofSpec.scala), scope `real_es_and_real_qdrant_execute_for_same_query`). |
| W | **Cleared** | First runtime ES-vs-Qdrant scorecard computed from real candidate rows via pure `M19DualEngineOfflineEvalMetrics`. |
| X | **Partially cleared — 3-query methodology slice** | Per-query scorecard over a lexical-exact, a semantic-complement, and a hard-negative synthetic query ([`RuntimeEsQdrantScorecardProofSpec`](../../bifunctor-tagless/src/test/scala/leaderboard/search/RuntimeEsQdrantScorecardProofSpec.scala)). Methodology proven; full coverage not. |
| F | **Cleared as taxonomy / source-confirmation** | Response-shape ↔ eval/seed taxonomy matrix exists ([`BEAUTYQ_RESPONSE_SHAPE_EVAL_TAXONOMY.md`](BEAUTYQ_RESPONSE_SHAPE_EVAL_TAXONOMY.md)); confirms policy remains blocked. |
| Response assembly policy | **Still BLOCKED** | Blocked by the gaps this inventory makes explicit (§5, §8). |

---

## 2. Why G exists

- **X covered three representative roles, not all current eval coverage.** X exercised exactly three
  `QueryClass` roles (`ExactProductNameBrand`, `SemanticDescriptive`, `NegativeOutOfCatalog`) over a
  synthetic 2-document fixture. The other six query classes and the entire 63-query canonical dataset
  were never runtime-measured.
- **F mapped response components but did not inventory all eval rows.** F is a per-component
  owner/origin/evidence matrix for the five `BeautySearchResponse` parts. It did not enumerate which
  canonical eval queries exist, which carry which component expectations, or which are seed-backed.
- **Policy cannot be selected from a tiny Qdrant fixture that returns the whole collection.** X's
  fixture seeds two variants and searches Qdrant with `limit=topK` and **no score threshold**, so
  Qdrant returns the entire seeded collection for every query (`qdrantIds == seeded ids` asserted).
  Any "positive complement" is a recall-floor artifact, not ranking-quality evidence.
- **G must prevent policy-by-abstraction.** Without an explicit row-by-row and component-by-component
  coverage inventory, a policy step risks reading X's tiny variant-candidate slice as if it covered
  the whole dataset and all five components. G pins the actual coverage so the next step is chosen on
  source-confirmed footing.

---

## 3. QueryClass inventory

Source: `QueryClass` enum and `QueryClass.stableOrder`
([`M8M9EvalContracts.scala:89-126`](../../bifunctor-tagless/src/main/scala/leaderboard/search/eval/M8M9EvalContracts.scala:89)).

**Coverage-truth caveat on counts.** The canonical dataset JSON does **not** carry the `QueryClass`
enum; it carries its own free-form `queryTypes` string tags (e.g. `direct`, `attribute`,
`hard_negative`). The mapping from the 63 canonical queries onto the nine `QueryClass` values is owned
by `M9BeautyQSearchEvalQueryDatasetStaticRows` (a file **outside this task's read scope**). Only three
canonical→`QueryClass` mappings are source-confirmed here, from
[`M9BeautyQSearchEvalQueryDatasetStaticRowsSpec`](../../bifunctor-tagless/src/test/scala/leaderboard/search/M9BeautyQSearchEvalQueryDatasetStaticRowsSpec.scala):
`q_nails_001 → IngredientAttribute`, `q_nails_003 → FilterHeavy`, `q_noise_005 → Ambiguous`.
Therefore **per-class eval-row counts across the 63 are NOT safely countable** from the allowed
sources; that itself is a coverage-truth gap (see §6).

"X scorecard covered it" / ES / Qdrant evidence below refer to the **runtime** X fixture
(`RuntimeEsQdrantScorecardProofSpec`), which is synthetic, not the canonical dataset.

| # | QueryClass (rendered) | Current eval rows exist? | Eval-row count (safely countable?) | X runtime scorecard covered? | ES evidence (runtime)? | Qdrant evidence (runtime)? | Complement/noise measurable now? | Coverage status |
|---|------------------------|--------------------------|------------------------------------|------------------------------|------------------------|----------------------------|----------------------------------|-----------------|
| 1 | `exact_product_name_brand` | Yes (canonical dataset has direct/exact queries) | Not safely countable¹ | **Yes** (q1 `lexical_exact_or_easy`) | Yes (ES retrieves expected variant) | Yes (overlap=1; complement=0; noise=1) | Yes (synthetic fixture only) | `covered_by_runtime_scorecard` |
| 2 | `category` | Yes (canonical dataset has category/broad queries) | Not safely countable¹ | No | No | No | No | `present_but_not_runtime_measured` |
| 3 | `ingredient_attribute` | Yes (`q_nails_001` confirmed) | Not safely countable¹ | No | No | No | No | `present_but_not_runtime_measured` |
| 4 | `semantic_descriptive` | Yes (canonical dataset has conversational/synonym queries) | Not safely countable¹ | **Yes** (q2 `semantic_complement_candidate`) | Yes (ES retrieves nothing — `operator=And`) | Yes (complement=1 **recall-floor artifact**; noise=1) | Yes (synthetic fixture only) | `covered_by_runtime_scorecard` |
| 5 | `typo_noisy` | Yes (canonical `queryTypes.typo = 2`) | Not safely countable¹ | No | No | No | No | `present_but_not_runtime_measured` |
| 6 | `filter_heavy` | Yes (`q_nails_003` confirmed; `attribute_heavy = 11`) | Not safely countable¹ | No | No | No | No | `present_but_not_runtime_measured` |
| 7 | `broad_discovery` | Yes (canonical `queryTypes.broad = 9`) | Not safely countable¹ | No | No | No | No | `present_but_not_runtime_measured` |
| 8 | `ambiguous` | Yes (`q_noise_005` confirmed; `queryTypes.ambiguous = 2`) | Not safely countable¹ | No | No | No | No | `present_but_not_runtime_measured` |
| 9 | `negative_out_of_catalog` | Yes (canonical `queryTypes.hard_negative = 7`) | Not safely countable¹ | **Yes** (q3 `hard_negative_or_should_stay_silent`) | Yes (ES retrieves nothing) | Yes (complement=0; **pure noise** = whole collection) | Yes (synthetic fixture only) | `covered_by_runtime_scorecard` |

¹ Not safely countable: the canonical JSON groups by `queryTypes`, not `QueryClass`; the
`QueryClass` mapping is owned by the out-of-scope `M9BeautyQSearchEvalQueryDatasetStaticRows`. Reported
as a coverage gap rather than guessed.

**Classes still missing runtime scorecard coverage (6 of 9):** `category`, `ingredient_attribute`,
`typo_noisy`, `filter_heavy`, `broad_discovery`, `ambiguous`.

---

## 4. Eval-query inventory (canonical `beautyq_search_eval_queries_v1.json`)

The canonical file is **484 KB / 13,953 lines** and too large for a complete per-row table here. Per
the task fallback, this section gives aggregate counts, a representative table, and a canonical-file
note. **The full file is canonical.**

### 4.1 Aggregate counts (source-confirmed from the file's own `summary` block and direct grep)

- **Total queries:** `63` (`summary.queryCount = 63`; 63 distinct `q_*` ids confirmed by grep).
- **Languages:** `ru = 31`, `en = 21`, `de = 6`, `mixed = 5`.
- **`queryTypes` tag distribution** (file's own taxonomy, multi-label per query): `attribute = 24`,
  `direct = 19`, `english = 13`, `attribute_heavy = 11`, `broad = 9`, `synonym = 9`,
  `german = 7`, `hard_negative = 7`, `conversational = 5`, `mixed_language = 4`, `price = 4`,
  `location = 3`, `numeric = 3`, `ambiguous = 2`, `home_visit = 2`, `typo = 2`, `multi_intent = 1`,
  `negative_attribute = 1`, `technical_token = 1`.
- **Id prefix groups (sum = 63):** `q_nails_*` = 12, `q_hair_*` = 10, `q_lashes_*` = 8,
  `q_face_*` = 8, `q_brows_*` = 6, `q_pmu_*` = 6, `q_broad_*` = 6, `q_noise_*` = 5, `q_home_*` = 2.

### 4.2 Aggregate counts by response-component expectation (source-confirmed by grep)

- `variantCarousel` expectations: **63 / 63** (every query).
- `providerCarousel` expectations: **63 / 63** (every query) — each carries
  `expectedGroupingField: "masterLocationId"` (63 occurrences).
- `serviceIntentCarousel` expectations: **63 / 63** (every query) — each carries
  `expectedGroupingField: "serviceId"` (63 occurrences).
- `facets` expectations: **0 / 63** (the key does not appear in the dataset).
- `inferredFilters` expectations: **0 / 63** (the key does not appear in the dataset).

So **every** canonical query is a triple-component (variant + provider-group + service-group)
expectation row; **no** query carries facet or inferred-filter expectations.

### 4.3 Representative table

`query_class` is shown only where source-confirmed; `?` marks the not-safely-countable mapping (§3).
`has_*` columns are source-confirmed from the dataset structure (§4.2). `seed_backed_status` reflects
the file's `coverageNotes` ("Every query has at least one seed-backed MasterServiceOfferVariant
match") and the embedded `acceptableVariantIds` / `sampleAcceptableVariants`. `runtime_scorecard_status`
is `none` for all canonical rows (X used a separate synthetic fixture).

| query_id | query_class | query_text | has_variant_exp | has_provider_exp | has_service_intent_exp | has_facet_exp | has_inferred_filter_exp | expected_grouping_fields | seed_backed_status | runtime_scorecard_status | known_gap |
|----------|-------------|------------|-----------------|------------------|------------------------|---------------|-------------------------|--------------------------|--------------------|--------------------------|-----------|
| `q_nails_001` | IngredientAttribute | "маникюр гель лак" | yes | yes | yes | no | no | masterLocationId, serviceId | seed-backed (acceptableVariantIds) | none | not runtime-measured |
| `q_nails_003` | FilterHeavy | (attribute-heavy nails) | yes | yes | yes | no | no | masterLocationId, serviceId | seed-backed | none | not runtime-measured |
| `q_lashes_001` | ? | (lash query) | yes | yes | yes | no | no | masterLocationId, serviceId | seed-backed | none | class mapping + not runtime-measured |
| `q_brows_001` | ? | (brow query) | yes | yes | yes | no | no | masterLocationId, serviceId | seed-backed | none | class mapping + not runtime-measured |
| `q_hair_001` | ? | (hair query) | yes | yes | yes | no | no | masterLocationId, serviceId | seed-backed | none | class mapping + not runtime-measured |
| `q_pmu_001` | ? | (PMU query) | yes | yes | yes | no | no | masterLocationId, serviceId | seed-backed | none | class mapping + not runtime-measured |
| `q_face_001` | ? | (facial query) | yes | yes | yes | no | no | masterLocationId, serviceId | seed-backed | none | class mapping + not runtime-measured |
| `q_home_001` | ? | (home-visit query) | yes | yes | yes | no | no | masterLocationId, serviceId | seed-backed | none | class mapping + not runtime-measured |
| `q_broad_001` | ? (broad_discovery-ish) | (broad query) | yes | yes | yes | no | no | masterLocationId, serviceId | seed-backed | none | class mapping + not runtime-measured |
| `q_noise_005` | Ambiguous | (noise/hard-negative) | yes | yes | yes | no | no | masterLocationId, serviceId | seed-backed | none | class mapping + not runtime-measured |

> **Canonical-file note:** the complete, authoritative per-row data lives in
> [`beautyq_search_eval_queries_v1.json`](../../bifunctor-tagless/src/test/resources/leaderboard/search/eval/beautyq_search_eval_queries_v1.json)
> (63 queries). The table above is representative (one or two rows per id-group) and intentionally
> does not restate all 63 rows.

---

## 5. Response-component coverage

Response shape: `BeautySearchResponse(variantCarousel, providerCarousel, serviceIntentCarousel,
facets, inferredFilters)`
([`BeautySearchModels.scala:120-130`](../../bifunctor-tagless/src/main/scala/leaderboard/search/BeautySearchModels.scala:120)).
Owner/policy columns are read from `M19IBeautyQComponentCombinationPolicyScaffold` and the F taxonomy.

| response_component | source owner | expectation coverage (canonical dataset) | ES evidence | Qdrant evidence | X runtime coverage | policy readiness |
|--------------------|--------------|------------------------------------------|-------------|-----------------|--------------------|------------------|
| `variantCarousel` | ES lexical backend (deterministic lexical ranking) | **63 / 63** variant expectations | Yes (AP1 default route; X q1/q2/q3 ES legs) | Yes — real per-backend candidate evidence, but **noisy & recall-floor-only** | **Yes** (3 synthetic queries) | `EsPrimaryWithQdrantSemanticSupplement` — **offline/eval-only**, NOT serving-approved |
| `providerCarousel` | ES domain projection grouped by `masterLocationId` | **63 / 63** provider-group expectations (`expectedGroupingField: masterLocationId`) | Yes (ES projection) | **Candidate-level only**; no Qdrant→provider grouping projection measured | **No** | `NeedsMoreEvidence` — Qdrant contribution **not allowed** |
| `serviceIntentCarousel` | ES domain projection grouped by `serviceId` | **63 / 63** service-group expectations (`expectedGroupingField: serviceId`) | Yes (ES projection) | **Candidate-level only**; no Qdrant→service grouping projection measured | **No** | `NeedsMoreEvidence` — Qdrant contribution **not allowed** |
| `facets` | ES aggregations over `FacetSpec.fields` | **0 / 63** — no facet expectations in dataset | ES-owned only | **None** (`NotApplicable`; Qdrant does not produce facets) | **No** | `EsOrCurrentOwnerOnly` — Qdrant **not approved**; eval-coverage-blocked |
| `inferredFilters` | ES / parser-owned (parser constraints + `FacetSpec` dominance threshold) | **0 / 63** — no inferred-filter expectations in dataset | ES/parser-owned only | **None** (`NotApplicable`; Qdrant does not produce inferred filters) | **No** | `EsOrCurrentOwnerOnly` — Qdrant **not approved**; eval-coverage-blocked |

**Required conclusions:**
- `variantCarousel` has Qdrant candidate evidence, but current runtime quality is **noisy** and
  **complement-positive only as a recall-floor artifact** (Qdrant returns the whole tiny collection),
  not a ranking-quality proof.
- `providerCarousel` and `serviceIntentCarousel` have expectation/grouping concepts (and full 63/63
  grouping-field coverage in the dataset), **but no source-confirmed Qdrant grouping projection from
  X**; their Qdrant evidence is candidate-level only.
- `facets` and `inferredFilters` remain current-owner / parser / ES-owned and have **no Qdrant
  evidence**, and **no expectations at all** in the dataset.
- **No component is ready for broad hybrid policy.**

---

## 6. Seed / expectation coverage

- **Canonical seed/eval-query resource:**
  [`beautyq_search_eval_queries_v1.json`](../../bifunctor-tagless/src/test/resources/leaderboard/search/eval/beautyq_search_eval_queries_v1.json)
  (`dataset = wandsbek_hamburg_beauty_services_seed_ready`, `version = 1`, 63 queries). This is the
  single canonical eval-query resource; it embeds seed-derived `targetSeedCounts`
  (services=9, masterLocations=8, masterServiceOfferVariants=66, …) and per-query
  `acceptableVariantIds` / `sampleAcceptableVariants` that bind expectations to seeded variant ids.
- **Canonical seed-backing claim:** the file's `coverageNotes` assert "Every query has at least one
  seed-backed MasterServiceOfferVariant match" and full service/enum/boolean/numeric attribute
  coverage.
- **Expectation levels present:**
  - **variant-level** — `variantCarousel.acceptableVariantIds` / `forbiddenVariantIds` / `topK`
    (`requiredInTopK` / `preferredInTopK` / `forbiddenNotInTopK`): **63 / 63**.
  - **provider-group-level** — `providerCarousel` with `expectedGroupingField: masterLocationId`:
    **63 / 63**.
  - **service-group-level** — `serviceIntentCarousel` with `expectedGroupingField: serviceId`:
    **63 / 63**.
  - **facet-level** — **absent** (0 / 63).
  - **inferred-filter-level** — **absent** (0 / 63).
- **Runtime-measured vs static/source-confirmed:** the canonical 63-query expectations are currently
  **only static / source-confirmed** — mapped into manual placeholder rows by
  `M9BeautyQSearchEvalQueryDatasetStaticRows` with `candidateSource = Manual`,
  `servingMode = Unknown`, `qualityGateDecision = "static_dataset_mapping_only"`, and explicit
  warnings ("not backend retrieval results", "No ES, Qdrant, route, plugin, DI, HTTP, hybrid, fusion,
  or reranking execution"). **No canonical eval row is runtime-measured.** The only runtime ES/Qdrant
  measurement (X) used a **separate synthetic 2-document fixture**, not these seed-backed
  expectations.

---

## 7. X coverage gap summary

- X covered exactly `ExactProductNameBrand`, `SemanticDescriptive`, and `NegativeOutOfCatalog`.
- X did **not** cover all `QueryClass` values (6 of 9 are unmeasured: `Category`,
  `IngredientAttribute`, `TypoNoisy`, `FilterHeavy`, `BroadDiscovery`, `Ambiguous`).
- X's semantic complement (q2) is a **tiny-collection recall-floor artifact**: Qdrant returns the
  whole seeded collection (`qdrantIds == seeded ids` asserted), so the positive complement is not
  evidence of semantic ranking quality.
- X's hard-negative result (q3) shows Qdrant **cannot stay silent** in that fixture — every returned
  candidate is pure noise (`noise == seeded-collection size`).
- X also did not use the canonical 63-query seed-backed dataset at all; it used a synthetic 2-document
  fixture.
- Therefore **X supports methodology, not policy.**

---

## 8. Policy blockers

1. **Runtime scorecard does not cover all query classes** — only 3 of 9 `QueryClass` roles are
   runtime-measured, and even those over a synthetic fixture, not the canonical dataset.
2. **Qdrant has no threshold / silence behaviour proven** — search runs `limit=topK` with no score
   threshold; the hard-negative case proves Qdrant cannot stay silent over the tiny collection.
3. **Semantic complement is not ranking-quality proof** — it is a recall-floor artifact of returning
   the entire collection.
4. **Provider/service grouping projection is not measured from Qdrant runtime evidence** — Qdrant
   evidence for these components is candidate-level only; no Qdrant→provider/service projection exists
   (`NeedsMoreEvidence`).
5. **`facets` / `inferredFilters` lack Qdrant evidence** *and* lack any dataset expectations (0 / 63),
   so they cannot be evaluated at all today (`EsOrCurrentOwnerOnly`).
6. **Lookup / hydration status is incomplete** — X wires no lookup; every candidate is
   `lookup_not_evaluated` (`evaluatedCount == 0`), so hydration correctness is unproven.

**Net:** four of five components are policy-blocked for missing eval coverage / missing projection,
and the fifth (`variantCarousel`) is blocked by Qdrant noise + recall-floor-only evidence. Response
assembly policy remains **BLOCKED**.

---

## 9. Next step recommendation

**Recommendation: `J-fixture-redesign`.**

Rationale (source-confirmed): the single thing preventing meaningful complement/noise/ranking
measurement is the **tiny synthetic Qdrant fixture** used by X — two seeded variants searched with
`limit=topK` and no score threshold, so Qdrant returns the whole collection for every query and every
"signal" is a recall-floor artifact or pure noise. This is asserted directly in
`RuntimeEsQdrantScorecardProofSpec` as a fixture limit. Until the fixture can return a non-trivial,
thresholded candidate set over a realistic collection, no broader runtime scorecard
(`H-runtime-expanded-scorecard`) can produce ranking-quality evidence — it would only multiply the
same recall-floor artifact across more query classes. The canonical 63-query dataset *does* carry rich
seed-backed variant/provider/service expectations (§4–§6) that an expanded scorecard could eventually
consume, so `H` is the natural step **after** the fixture is redesigned, not before.

`V-cleanup` is not yet the right call: G shows policy is blocked, but a **useful next runtime
measurement can be built** (a redesigned fixture), so cleanup is premature. `E-policy` is **not**
recommended: the only narrow candidate (`variantCarousel`'s offline ES-primary + Qdrant supplement)
still rests on recall-floor-only evidence with measured noise, so it lacks source-confirmed
runtime evidence with acceptable noise.

---

## 10. Source files confirmed

- [`BEAUTYQ_RESPONSE_SHAPE_EVAL_TAXONOMY.md`](BEAUTYQ_RESPONSE_SHAPE_EVAL_TAXONOMY.md) — F component taxonomy.
- [`M21_POST_DEFER_INVENTORY.md`](M21_POST_DEFER_INVENTORY.md) — M18–M21 classification, consolidation candidates.
- [`BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md`](../BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md) — canonical status.
- [`M8M9EvalContracts.scala`](../../bifunctor-tagless/src/main/scala/leaderboard/search/eval/M8M9EvalContracts.scala) — `QueryClass` enum + stable order.
- [`M18DualEngineOfflineEval.scala`](../../bifunctor-tagless/src/main/scala/leaderboard/search/eval/M18DualEngineOfflineEval.scala) — dual-engine execution surface; expected/lookup tri-states.
- [`M19DualEngineOfflineEvalMetrics.scala`](../../bifunctor-tagless/src/main/scala/leaderboard/search/eval/M19DualEngineOfflineEvalMetrics.scala) — pure variant-candidate metrics (overlap/complement/noise).
- [`M19IBeautyQComponentCombinationPolicyScaffold.scala`](../../bifunctor-tagless/src/main/scala/leaderboard/search/eval/M19IBeautyQComponentCombinationPolicyScaffold.scala) — per-component `PolicyState`.
- [`BeautySearchModels.scala`](../../bifunctor-tagless/src/main/scala/leaderboard/search/BeautySearchModels.scala) — `BeautySearchResponse` five-component shape.
- [`beautyq_search_eval_queries_v1.json`](../../bifunctor-tagless/src/test/resources/leaderboard/search/eval/beautyq_search_eval_queries_v1.json) — canonical 63-query eval/seed dataset.
- [`RuntimeEsQdrantScorecardProofSpec.scala`](../../bifunctor-tagless/src/test/scala/leaderboard/search/RuntimeEsQdrantScorecardProofSpec.scala) — X synthetic 3-query scorecard.
- [`DisabledRuntimeHybridExecutionEsQdrantProofSpec.scala`](../../bifunctor-tagless/src/test/scala/leaderboard/search/DisabledRuntimeHybridExecutionEsQdrantProofSpec.scala) — T proof.
- [`BeautySearchRealEsRouteRegressionSpec.scala`](../../bifunctor-tagless/src/test/scala/leaderboard/search/BeautySearchRealEsRouteRegressionSpec.scala) — AP1 proof.
- [`M9BeautyQSearchEvalQueryDatasetStaticRowsSpec.scala`](../../bifunctor-tagless/src/test/scala/leaderboard/search/M9BeautyQSearchEvalQueryDatasetStaticRowsSpec.scala) — 63-query static mapping; 3 confirmed `QueryClass` anchors.
- [`M19DualEngineOfflineEvalMetricsSpec.scala`](../../bifunctor-tagless/src/test/scala/leaderboard/search/M19DualEngineOfflineEvalMetricsSpec.scala) — metric semantics for overlap/complement/noise.

**G verdict: G is CLEARED as a coverage-truth inventory** (built from source truth; no
`NEED_BUNDLE`). It confirms response-assembly policy remains **BLOCKED** and recommends
`J-fixture-redesign` as the next step.
