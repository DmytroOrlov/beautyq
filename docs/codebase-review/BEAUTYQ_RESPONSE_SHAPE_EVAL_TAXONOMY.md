# BeautyQ Response-Shape ↔ Eval/Seed Taxonomy Matrix (F)

Source-confirmed cross-reference connecting the five `BeautySearchResponse` components to their
current owners, data origins, runtime status, eval/seed coverage, the Qdrant evidence X actually
produced, the risks the evidence revealed, and the hybrid-policy options that remain open per
component.

**This task is source-confirmation only.** It selects no policy, assembles no hybrid response,
deletes no cleanup candidate, and adds no roadmap layer. It does **not** approve hybrid response
assembly for any component. Every claim below is grounded in the files listed at the bottom; no
`NEED_BUNDLE` was needed.

---

## 1. Current main-task status

| Step | State | Evidence |
|------|-------|----------|
| AP1 | Cleared | Default graph-wired `/beauty-search` returns 200 OK with non-empty `variantCarousel` against real ES, with coarse latency evidence ([`BeautySearchRealEsRouteRegressionSpec`](../../bifunctor-tagless/src/test/scala/leaderboard/search/BeautySearchRealEsRouteRegressionSpec.scala), scope `real_es_default_route_graph_non_empty`). |
| T | Cleared | Real ES and real Qdrant both execute for the **same** BeautyQ query behind the disabled/internal `M18DualEngineOfflineEvalRunner` surface ([`DisabledRuntimeHybridExecutionEsQdrantProofSpec`](../../bifunctor-tagless/src/test/scala/leaderboard/search/DisabledRuntimeHybridExecutionEsQdrantProofSpec.scala), scope `real_es_and_real_qdrant_execute_for_same_query`). |
| W | Cleared | First ES-vs-Qdrant scorecard computed from real candidate rows via pure `M19DualEngineOfflineEvalMetrics`. |
| X | **Partially cleared** | Per-query scorecard over a lexical-exact, a semantic-complement, and a hard-negative query ([`RuntimeEsQdrantScorecardProofSpec`](../../bifunctor-tagless/src/test/scala/leaderboard/search/RuntimeEsQdrantScorecardProofSpec.scala)). Found the Qdrant positive complement is a tiny-collection recall-floor artifact and that Qdrant adds noise (incl. pure noise on the hard negative). |
| Response assembly policy | **NOT ready** | Blocked by the gaps this taxonomy makes explicit (§7). |
| F (this doc) | See §3/§7 verdict | Source-confirmed taxonomy matrix. |

---

## 2. Why F comes before policy

X measured ES-vs-Qdrant candidate behaviour for **one** response surface (variant-level candidate
ids) and found the only Qdrant-positive signal is a recall-floor artifact plus noise. Before any
`E`-style component policy can even be discussed, the open question is *which response component each
piece of evidence actually speaks to*. The five `BeautySearchResponse` components have different
owners (ES lexical backend, ES domain projections, ES aggregations, parser/DSL) and different eval
coverage. Without a per-component map of owner / data-origin / eval-coverage / Qdrant-evidence, a
policy step risks attributing variant-level candidate evidence to provider, service-intent, facet, or
inferred-filter components that X never measured. F builds that map so policy (or cleanup) can be
chosen on source-confirmed footing rather than on an over-broad reading of X.

---

## 3. Response component matrix

Response shape source-confirmed at
[`BeautySearchModels.scala:120-130`](../../bifunctor-tagless/src/main/scala/leaderboard/search/BeautySearchModels.scala:120)
(`BeautySearchResponse(variantCarousel, providerCarousel, serviceIntentCarousel, facets,
inferredFilters)`). Owner/origin/evidence columns are read from
[`M19CBeautyQResponseComponentTaxonomy.scala`](../../bifunctor-tagless/src/main/scala/leaderboard/search/eval/M19CBeautyQResponseComponentTaxonomy.scala);
policy-state column from
[`M19IBeautyQComponentCombinationPolicyScaffold.scala`](../../bifunctor-tagless/src/main/scala/leaderboard/search/eval/M19IBeautyQComponentCombinationPolicyScaffold.scala);
Qdrant-evidence-from-X column from
[`RuntimeEsQdrantScorecardProofSpec.scala`](../../bifunctor-tagless/src/test/scala/leaderboard/search/RuntimeEsQdrantScorecardProofSpec.scala).

| response_component | current_owner | data_origin | current_runtime_status | eval_seed_coverage | qdrant_evidence_from_X | known_risk | hybrid_policy_options | policy_status_now |
|--------------------|---------------|-------------|------------------------|--------------------|------------------------|------------|-----------------------|-------------------|
| `variantCarousel` | ES lexical backend with deterministic lexical ranking (`CarouselSpec` ranking weights) | `VariantSearchResult` rows from `VariantSearchDocument` over the ES seed-resource catalog snapshot | Default ES route serves it 200 OK non-empty (AP1). Qdrant executes only behind the disabled offline surface (T/W/X) | **Dataset carries variant-carousel expectations** (`informVariantRecall` true; `M9OfflineEvalExpectedResult` variant ids). M18/M19 metrics are variant-candidate-id metrics | **Has real per-backend Qdrant candidate evidence.** But X evidence is **noisy**, and the positive complement is **complement-positive only as a tiny-collection recall-floor artifact** (Qdrant returns the whole seeded collection: `qdrantIds == seeded ids` for every query), **not** proof of semantic ranking quality. X also measured Qdrant noise on q1 and q2 | (a) keep ES-only; (b) ES-primary + **offline** Qdrant semantic candidate *supplement* (ES ids first, Qdrant-only appended, no fusion/rerank) — exactly `EsPrimaryWithQdrantSemanticSupplement` in M19I | **Offline/eval-only candidate supplement decision exists in M19I; NOT serving-approved and NOT response-assembly-approved.** Quality proof still missing (recall-floor artifact + noise) |
| `providerCarousel` | ES lexical backend; **domain projection** over variant candidates grouped by `masterLocationId` (`CarouselSpec.providerGroupField`) | `ProviderSearchResult` from `VariantSearchDocument` rows grouped by `masterLocationId` | ES-owned projection; no separate Qdrant provider projection exists | Dataset carries provider-carousel expectations (`informProviderCarousel` true), **but no source-confirmed provider-grouping projection turns a Qdrant candidate set into a provider carousel** | **Candidate-level only** (M19C `qdrantEvidence = CandidateLevelOnly`). **X did not measure provider grouping at all** — X measures variant candidate ids, not provider projections | (a) ES/parser-owner only; (b) future provider-grouping projection — but unproven | **`NeedsMoreEvidence` (M19I). Qdrant contribution NOT allowed.** No source-confirmed projection; no X evidence for this component |
| `serviceIntentCarousel` | ES lexical backend; **domain projection** over variant candidates grouped by `serviceId` (`CarouselSpec.serviceIntentGroupField`) | `ServiceIntentSearchResult` from `VariantSearchDocument` rows grouped by `serviceId` | ES-owned projection; no separate Qdrant service projection exists | Dataset carries service-intent expectations (`informServiceIntentCarousel` true), **but no source-confirmed service-grouping projection turns a Qdrant candidate set into a service-intent carousel** | **Candidate-level only** (M19C `qdrantEvidence = CandidateLevelOnly`). **X did not measure service grouping** | (a) ES/parser-owner only; (b) future service-grouping projection — but unproven | **`NeedsMoreEvidence` (M19I). Qdrant contribution NOT allowed.** No source-confirmed projection; no X evidence for this component |
| `facets` | ES backend: aggregations over `FacetSpec.fields` | `BeautySearchFacet` from ES aggregations over `serviceName`, `categoryName`, `priceFrom` ranges, `durationMin` ranges, enum/boolean attribute terms | ES-owned aggregations only | **No facets expectations in the dataset** (`hasFacetsExpectations` false; `informFacets` hard-coded false). Cannot evaluate facet correctness at all today | **Not applicable** (M19C `qdrantEvidence = NotApplicable`). Listed hybrid plan: "Qdrant does not produce facets." **X produced no facet evidence** | ES/current-owner only | **`EsOrCurrentOwnerOnly` (M19I). Qdrant does NOT own facets; contribution NOT approved.** Policy-blocked by missing eval coverage |
| `inferredFilters` | ES / parser-owned: parser constraints + `FacetSpec` dominance threshold (`0.70`) / min count (`2`) | `BeautySearchAppliedFilter` from ES facet values reduced by the dominance threshold, paired with the `BeautySearchIntentParser` NearUser constraint | ES/parser-owned only | **No inferred-filters expectations in the dataset** (`hasInferredFiltersExpectations` false; `informInferredFilters` hard-coded false). Cannot evaluate correctness at all today | **Not applicable** (M19C `qdrantEvidence = NotApplicable`). Listed hybrid plan: "Qdrant does not produce inferred filters." **X produced no inferred-filter evidence** | ES/parser/current-owner only | **`EsOrCurrentOwnerOnly` (M19I). Qdrant does NOT own inferred filters; contribution NOT approved.** Policy-blocked by missing eval coverage |

**Owner-vs-Qdrant distinction (required):** in every row the `current_owner`/`data_origin` columns
record current ES/DSL/parser ownership; the `qdrant_evidence_from_X` column records, separately, what
Qdrant candidate evidence actually exists. Only `variantCarousel` has real per-backend Qdrant
candidate evidence; the other four are ES/parser/current-owner only with at most candidate-level
(provider/service) or no (facets/inferred) Qdrant evidence.

---

## 4. Eval query/seed taxonomy summary

- **Query classes** (`QueryClass`, [`M8M9EvalContracts.scala`](../../bifunctor-tagless/src/main/scala/leaderboard/search/eval/M8M9EvalContracts.scala)):
  `exact_product_name_brand`, `category`, `ingredient_attribute`, `semantic_descriptive`,
  `typo_noisy`, `filter_heavy`, `broad_discovery`, `ambiguous`, `negative_out_of_catalog`.
- **X exercised three roles** over the same real ES + real Qdrant surface:
  1. `lexical_exact_or_easy` → `ExactProductNameBrand` (query text = seeded service name; ES retrieves).
  2. `semantic_complement_candidate` → `SemanticDescriptive` (no shared lexical token → ES `operator=And` retrieves nothing; Qdrant carries the expected variant).
  3. `hard_negative_or_should_stay_silent` → `NegativeOutOfCatalog` (only expected answer is an out-of-catalog sentinel; Qdrant cannot stay silent).
- **Seed shape:** two seeded `VariantSearchDocument` rows (one expected balayage variant, one unrelated
  manicure noise variant) plus one never-seeded out-of-catalog sentinel id. Qdrant search uses
  `limit=topK`, **no score threshold**, over this tiny collection.
- **Component expectation coverage** (`EvalQueryCoverage` in M19C):
  - `variantCarousel`, `providerCarousel`, `serviceIntentCarousel` → dataset can carry expectations
    (`inform*` true when present).
  - `facets`, `inferredFilters` → **no expectations in the dataset**; `informFacets` and
    `informInferredFilters` are hard-coded `false`. These components are not evaluable today.
- **What X's metrics actually measure:** per-query **variant candidate id** sets, overlap,
  expected-aware Qdrant complement, Qdrant noise, per-leg latency, and lookup-not-evaluated — all at
  the variant-candidate level. They do **not** measure provider/service projections, facet
  aggregations, or inferred filters.

---

## 5. What X proved

- Real ES and real Qdrant both **execute** for the same query set behind the disabled surface, with
  per-leg latency present and ES/Qdrant outputs kept structurally separate (no fusion).
- **q1 (lexical-exact):** ES retrieves the expected variant; ES ∩ Qdrant overlap = 1; Qdrant
  complement over ES = 0; Qdrant noise = 1 (the unrelated seeded variant).
- **q2 (semantic-complement):** ES retrieves nothing (no shared lexical token); Qdrant supplies the
  expected variant ES missed → complement = 1; noise = 1.
- **q3 (hard-negative):** ES retrieves nothing; Qdrant returns the whole catalog as **pure noise**
  (noise = seeded-collection size); complement = 0.
- A per-query, source-confirmed ES-vs-Qdrant **variant-candidate** scorecard can be computed from real
  executed rows, or honestly resource-gated (no faked candidates) when Qdrant/embedding are absent.

---

## 6. What X did NOT prove

- **Not** broad Qdrant semantic ranking quality. The q2 positive complement is a **recall-floor
  artifact** of Qdrant returning the entire tiny seeded collection (`qdrantIds == seeded ids` asserted
  for every query), not evidence of genuine ranking.
- **Not** that Qdrant can "stay silent" on a hard negative — q3 shows it cannot over the tiny
  collection; every returned candidate is noise.
- **Nothing** about `providerCarousel`, `serviceIntentCarousel`, `facets`, or `inferredFilters`. X is
  a variant-candidate scorecard; it measured no provider/service grouping projection, no facet
  aggregation, and no inferred-filter derivation.
- **No** hybrid response assembly, score fusion, reranking, fallback, automatic Qdrant supplement, or
  default route switch — all asserted disabled via the M20B control surface.

---

## 7. Policy blockers revealed by taxonomy

**Policy-blocked due to missing eval coverage:**
- `facets` — no facets expectations exist in the dataset (`hasFacetsExpectations`/`informFacets`
  false); correctness cannot be evaluated at all.
- `inferredFilters` — no inferred-filters expectations exist in the dataset
  (`hasInferredFiltersExpectations`/`informInferredFilters` false); correctness cannot be evaluated.
- `providerCarousel` / `serviceIntentCarousel` — even though the dataset *can* carry grouping
  expectations, there is **no source-confirmed Qdrant→provider or Qdrant→service grouping projection**,
  so no Qdrant policy for these is evaluable (M19I `NeedsMoreEvidence`).

**Policy-blocked due to Qdrant noise / hard-negative behaviour:**
- `variantCarousel` — the only component with real Qdrant candidate evidence, but X's positive
  complement is a recall-floor artifact and X measured Qdrant noise on q1/q2 and **pure noise** on the
  q3 hard negative. There is no score threshold and no silence behaviour, so no quality-grade policy
  can be approved from X alone.

**This taxonomy does NOT approve hybrid response assembly** for any component. The single
Qdrant-positive decision that exists (`variantCarousel` → `EsPrimaryWithQdrantSemanticSupplement` in
M19I) is explicitly **offline/eval-only** and serving-unapproved; it is recorded here as an option,
not endorsed.

**F verdict: F is CLEARED as a source-confirmation task** (the matrix and taxonomy are built from
source truth with no missing bundle), **but it confirms response assembly policy remains BLOCKED** —
four of five components are policy-blocked for missing eval coverage / missing projection, and the
fifth is blocked by Qdrant noise + recall-floor-only evidence.

---

## 8. Next decision options after F

- **E-policy** — pursue *only if* this taxonomy shows a narrowly safe component policy candidate.
  The only candidate is `variantCarousel`'s offline ES-primary + Qdrant semantic supplement (M19I), and
  even that is blocked by the recall-floor-artifact + noise gap in §6/§7. A safe E-policy step would
  therefore have to first close the variant Qdrant **quality** gap (real collection, score threshold,
  silence behaviour) before any narrow component policy is selected — it is **not** unblocked by F
  alone.
- **V-cleanup** — pursue *if* this taxonomy shows policy is still blocked (it does) and duplicate
  report/policy layers can be removed or merged. The M21 inventory already lists overlapping layers as
  consolidation candidates (e.g. `M19E` proposal foldable into `M19D`; `M19B` ↔ `M19H` evidence
  reports mergeable; `M21PostDeferralActivationBlockerActionPlan` foldable into the M21 decision
  package). Since four of five components are eval-coverage/projection-blocked and the fifth is
  noise-blocked, V-cleanup (merge/remove duplicate report/policy layers) is the safe next move while
  the eval-coverage and Qdrant-quality gaps remain open.

---

## Source files confirmed

- [`BeautySearchModels.scala`](../../bifunctor-tagless/src/main/scala/leaderboard/search/BeautySearchModels.scala) — `BeautySearchResponse` + the five component case classes (`VariantSearchResult`, `ProviderSearchResult`, `ServiceIntentSearchResult`, `BeautySearchFacet`, `BeautySearchAppliedFilter`).
- [`M19CBeautyQResponseComponentTaxonomy.scala`](../../bifunctor-tagless/src/main/scala/leaderboard/search/eval/M19CBeautyQResponseComponentTaxonomy.scala) — per-component owner / data-origin / ES+Qdrant evidence facts; per-query eval coverage.
- [`M19IBeautyQComponentCombinationPolicyScaffold.scala`](../../bifunctor-tagless/src/main/scala/leaderboard/search/eval/M19IBeautyQComponentCombinationPolicyScaffold.scala) — per-component `PolicyState` (offline/eval-only, no serving).
- [`M19DualEngineOfflineEvalMetrics.scala`](../../bifunctor-tagless/src/main/scala/leaderboard/search/eval/M19DualEngineOfflineEvalMetrics.scala) — pure variant-candidate metrics over the M18 result.
- [`M8M9EvalContracts.scala`](../../bifunctor-tagless/src/main/scala/leaderboard/search/eval/M8M9EvalContracts.scala) — `QueryClass` taxonomy and metric/telemetry contracts.
- [`BeautySearchSpecV1.scala`](../../bifunctor-tagless/src/main/scala/leaderboard/search/dsl/BeautySearchSpecV1.scala) — `CarouselSpec` (`providerGroupField=masterLocationId`, `serviceIntentGroupField=serviceId`), `FacetSpec`.
- [`RuntimeEsQdrantScorecardProofSpec.scala`](../../bifunctor-tagless/src/test/scala/leaderboard/search/RuntimeEsQdrantScorecardProofSpec.scala) — X scorecard (recall-floor + noise findings).
- [`DisabledRuntimeHybridExecutionEsQdrantProofSpec.scala`](../../bifunctor-tagless/src/test/scala/leaderboard/search/DisabledRuntimeHybridExecutionEsQdrantProofSpec.scala) — T proof.
- [`BeautySearchRealEsRouteRegressionSpec.scala`](../../bifunctor-tagless/src/test/scala/leaderboard/search/BeautySearchRealEsRouteRegressionSpec.scala) — AP1 proof.
- [`M21_POST_DEFER_INVENTORY.md`](M21_POST_DEFER_INVENTORY.md) — consolidation candidates for V-cleanup.
