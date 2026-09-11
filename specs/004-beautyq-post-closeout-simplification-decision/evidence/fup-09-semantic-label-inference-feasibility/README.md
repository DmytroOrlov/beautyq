# Feature 004 — FUP-09 Generic Query-Side Semantic Label Inference Feasibility

This directory is **repository-owned supporting evidence**, not a normative runtime/policy owner. Current
source, tests, and normative documents remain current truth. It **authorizes no implementation** — no
production source/test/runtime/config/corpus change, no new framework API, no prototype registry, no
runtime LLM dependency, no threshold/product policy, no Qdrant default/activation/rollout change, and no
Feature 004 disposition change.

It records a read-only architecture + offline feasibility spike so a later coordinator can recover the
decision without this agent session, the gitignored run directory, or another review bundle.

- **Outcome:** `GO_TO_SPEC_KIT_FEATURE` (a separate human decision is still required to start/specify the
  implementation feature; this is not implementation authorization).
- **Spike base:** `HEAD = af1b285f` (worktree also carries the pending FUP-06/FUP-08/queue evidence edits).
- **Evidence inputs fingerprinted:** visible corpus
  `beautyq_evaluation_corpus_v2.json` `2933a643723eaf216cfceaabdc43a914339c594bbac029ca5301de848fa78c4d`;
  canonical seed `wandsbek_hamburg_beauty_services_seed_ready.json`
  `08abc7a474ad978b7b360c7688cece418ed17a6332df66d478310d3f05d8d107`; intent vocabulary
  `BeautyQIntentVocabularyGen2.scala`
  `e74d77c1ec422566e30d98dcb2430d6dbd96f83930b2a8d4ce13b5be383eac0f`.
- **Local raw run (gitignored, reproducible):** `.evidence-runs/fup-09-semantic-label-inference/`
  (`labels.py`, `embed.py`, `run_experiment.py`, `run_experiment_v2.py`, `counterfactual.py`,
  `experiment-results-v2.json`, `counterfactual-results.json`, `embeddings-cache.json`).
- **Protected holdout was never read or used.** All numbers below are the visible 180-case corpus.

---

## 1. Current declaration ownership (source-confirmed)

| Concern | Canonical owner |
| --- | --- |
| Domain navigation root | `BeautyQSearchGen2` (`beautyq-search-gen2-wiring`) |
| Executable contract root | `BeautyQSearchDeclarations` — `catalog.topology`, `variants.Fields`, `variants.intent`, `variants.plan.candidate` |
| Catalog topology / hierarchy | `catalog.topology`: `branch[Category].rootTree(_.parentId, root = Category.rootCategoryId).child[Service](_.categoryId)` |
| Service/category stable identity | `ServiceCode`, `CategoryCode` (opaque `String` via `StableCodeCompanion`/`CanonicalStringValue`); typed field handles `serviceCode`, `categoryCode` |
| Category hierarchy data | `Category(id, code, parentId, depth, name)`; `Service(id, code, categoryId, name)` |
| Document identities | `VariantSearchDocumentGen2.serviceCode/categoryCode/serviceName/categoryName` |
| Intent vocabulary | `BeautyQIntentVocabularyGen2` (`BeautyQIntentRule(hardActions, semanticActions, aliases)`), `BeautyQIntentActionCompiler`, `BeautyQSemanticLabelPolicy.forAction` |
| Query semantic text | `BeautyQSemanticCandidatePolicy.semanticText` = normalized residual + parser `canonicalSemanticLabels`; `SemanticQueryText` |
| Document embedding text | `BeautyQVariantProjectionGen2.buildDocument` → `serviceText`/`allText`; field `Fields.allText` |
| Embedding use | `BeautyQQdrantPolicy`: `embeddingField = Fields.allText`, model `local-llama-cpp-embedding` 1024d, `QdrantDistance.Cosine`; `QdrantEmbeddingInput` carries a provenance fingerprint |
| Qdrant candidate retrieval | `QdrantCandidatePipeline`, `QdrantPolicy` (`topK = 20`, `scoreThreshold = None`), `QdrantCandidateResponseDecoder` returns ordered `(id, score)` hits |
| Candidate planning/hydration | `BeautyQCandidatePlanCompiler`, `CandidateHydrator` (`RequireAll`, `Fail`); `HydratedCandidateSearchResult.candidates` holds the **complete ordered top-K** with the full `VariantSearchDocumentGen2` per candidate |
| Supplement selection | `AppendOnlySupplementPolicy.select` (budget `MaxAppended = 1`), `BeautyQSearchOrchestrator` |
| Neutral fixture precedent | `SemanticCandidatePlanSpec` (`ArticleDocument`, no BeautyQ concepts), `SearchIntentMatcherSpec` (String actions) |
| Fingerprinting precedent | `CanonicalFingerprint`, `QdrantEmbeddingInput.fingerprint`, `PlanContractFingerprint`, `SearchProjectedDocumentsFingerprint` |

No generic Search Gen2 type names or branches on any BeautyQ service/category identity.

## 2. Can the label universe be derived? (Section 3 classification)

The label universe is the 9 services + 4 non-root categories already present in the canonical seed/domain.
Every semantic input classifies as:

| Required semantic input | Class | Source |
| --- | --- | --- |
| Stable key (`K`) | `ALREADY_DECLARED` | `ServiceCode`/`CategoryCode`; the existing `CanonicalSemanticLabel.stableKey` convention (`service:<code>`, `category:<code>`) already binds labels to stable keys |
| Human-readable label text | `ALREADY_DECLARED` | `BeautyQSemanticLabelPolicy.forAction` humanizes the code; category/service `name` is canonical catalog data surfaced on the document |
| Multilingual alias material | `ALREADY_DECLARED` | `BeautyQIntentVocabularyGen2` aliases are already bound to `Service`/`ServiceAny`/`Category` actions |
| Parent/category relation | `ALREADY_DECLARED` | `Category.parentId`/`depth` + `catalog.topology.rootTree`/`child[Service](_.categoryId)`; document carries both `serviceCode` and `categoryCode` |
| Action/intent relation | `ALREADY_DECLARED` | intent rules target service/category actions |
| Document declaration relation | `ALREADY_DECLARED` | `Fields.serviceCode`/`categoryCode`/`serviceName`/`categoryName` |
| A single projection from key → prototype material | `MECHANICALLY_DERIVABLE` | a thin domain adapter over the above; **not** a new business declaration |

**No `GENUINELY_MISSING_DOMAIN_CHOICE` was found.** The domain does not need to author a second semantic
ontology or registry. The only new code is mechanical projection of existing declarations + catalog names
into `Vector[(K, material)]`, plus offline prototype construction.

## 3. Hierarchy

Hierarchy is already an executable relation (`Category.parentId`/`depth`, catalog `rootTree`/`child`,
document `serviceCode`+`categoryCode`) and is reusable without duplication. It is only two levels
(root → 4 branch categories → 9 services) and the category prototypes are close to the union of their
children, so structural hierarchy adds little beyond a parent annotation. **v1 should stay flat** with an
optional declared parent key for evidence grouping; hierarchy-aware inference is not mandatory.

## 4. Proposed API shapes (Section 4)

All shapes keep the inference kernel domain-neutral (`Label`, `K` opaque) and make generic code unable to
name or branch on any BeautyQ concept.

### Shape 1 — PREFERRED: pure evidence kernel over a declared ordered label universe
```scala
// search-gen2-contract (generic; no domain concepts)
trait SemanticLabelView[Label, K] {
  def key(label: Label): K
  def material(label: Label): Vector[String]   // zero, one, or many declared texts for one label
  def parent(label: Label): Option[K]          // None => flat
}
final case class SemanticLabelScore[K](key: K, score: Double)
final class SemanticLabelEvidence[K] private (
  val ranked: Vector[SemanticLabelScore[K]],  // scored labels only, deterministic order
  val margin: Option[Double],                 // Some(top1 - top2) only when >= 2 scored labels
) {
  def best: Option[SemanticLabelScore[K]]
}
object SemanticLabelInference {
  def infer[Label, K, V](
    labels: Vector[Label],
    view: SemanticLabelView[Label, K],
    queryVector: V,
    labelVectors: Label => Vector[V],   // one independently materialized vector per declared material
    similarity: (V, V) => Double,       // cosine is one implementation, not assumed
  ): SemanticLabelEvidence[K]
}
```

`V` is a generic vector/value type, so the kernel names no embedding library and assumes no metric. The
invariant preserved from the measured experiment is:

```text
one domain label
  -> zero/one/many declared semantic materials   (view.material(label))
  -> independently materialized vectors, one per material   (labelVectors(label), index-aligned)
  -> per-label score = max over pairwise similarity(queryVector, v)
```

The per-label score is **`max` over independently materialized material vectors**; the kernel must not
collapse materials into one concatenated/centroid prototype, which the experiment measured as worse
(§5, `concat`).

**Zero-material labels.** A label whose `material` (and therefore `labelVectors`) is empty is **unscored**
and is **omitted from `ranked`**. No invented similarity (`0.0`, `-Infinity`, or a domain default) is
assigned to it, and it never appears in `ranked` or `best`. This is generic mechanics, not domain policy.

**Margin cardinality.** `margin` is `Option[Double]`:

| scored labels | `best` | `margin` |
| --- | --- | --- |
| 0 | `None` | `None` |
| 1 | `Some(top1)` | `None` |
| 2+ | `Some(top1)` | `Some(top1.score - top2.score)` |

No sentinel margin is invented. Declared order remains the deterministic tie-break among scored labels
(e.g. equal `max` scores); a zero-material label is simply absent, so it can neither win nor tie.

**Materialization boundary (two stages, not two declarations).** `view.material(label)` is the
domain-owned derivation source: canonical declarations (codes, names, declared aliases) projected into
semantic material strings. `labelVectors(label)` is the *derived* materialization of exactly those strings
(runtime or offline-built) and is index-aligned with `material(label)`. They are **not** independently
maintained sources of truth: changing the canonical declaration/material must change the vectors, and any
persisted/offline vector artifact must be fingerprinted against the exact canonical/material input it was
built from. Generic inference consumes only the query vector and the already-materialized per-label
vectors; it never re-derives material.

**Framework derives (generic mechanics):** declared-order traversal, `max`-over-material scoring,
deterministic ranking/tie-break, top1/top2 information, `margin` (present only with ≥ 2 scored labels), and
the evidence value; never an acceptance decision and never an abstention threshold.

**Domain/product policy:** `K`, material, parent, `labelVectors` (derived), `similarity`, and **what to do
with the evidence** — whether a given margin is sufficient, whether to abstain, and how the evidence
affects supplement selection/reranking. Generic code contains no threshold and depends on no embedding
library, no cosine specifically, no LLM, no backend type, and no BeautyQ concept.

### Shape 2 — inference service that hides the encoder boundary
```scala
trait SemanticLabelInference[K] {
  def infer(query: SemanticQueryText): Either[SemanticLabelInferenceError, SemanticLabelEvidence[K]]
}
```
Simpler call site, but pushes a runtime encoder dependency behind the abstraction, is harder to prove with
a pure neutral fixture, and risks importing backend/embedding types into the contract. Rejected for v1.

### Shape 3 — declaration DSL mirroring `searchFields[Document]`
A `semanticLabels[K]("domain")…declare` builder deriving registry + prototype inputs + fingerprint.
Highest authoring ergonomics but the most new machinery and the highest risk of becoming a second
registry/ontology. Rejected as premature.

**Selection:** Shape 1, maturity label **internal / experimental**, single-consumer. It contains no domain
concepts, is provable with a neutral fixture, and matches the repository's established split
(`SearchIntentRuleView`, `SemanticCandidateEvaluation`: domain declares, framework executes mechanics).
It is not claimed permanently stable until a second, materially different domain exercises it.

## 5. Offline feasibility experiment

**Method.** Read-only Python over the visible corpus + accepted FUP-06/FUP-08 local evidence. The lexical
family was reproduced with the already-validated FUP-08 replica (`scala_rules.py`/`match.py`). Label
prototypes and query vectors were embedded through the **repository's real local llama.cpp endpoint**
(same model/dimension as `BeautyQQdrantPolicy`: `local-llama-cpp-embedding`, 1024d) — the repo's
embedding path was reused, not simulated. Two prototype kernels were compared: `concat` (one joined
prototype per label) and `max` (max cosine over individual declared material texts).

**Prototype strategies.** A = canonical names + humanized stable codes. B = A + parent/child names +
existing domain-declared intent aliases bound to that key. C = B + offline LLM teacher paraphrases
**not executable here** (see §6).

**Top-1 agreement** (category predictions expanded to child services; lexical family expanded likewise):

| strategy/kernel | vs lexical family (69 cases) | vs corpus `serviceIntents` (108 cases) | top-3 services |
| --- | ---: | ---: | ---: |
| A concat | 85.5% (59) | 71.3% (77) | 88.0% (95) |
| A max | 79.7% (55) | 75.0% (81) | 84.3% (91) |
| B concat | 75.4% (52) | 67.6% (73) | 88.9% (96) |
| **B max** | **91.3% (63)** | **91.7% (99)** | **98.1% (106)** |

`concat` measurably *hurts* B (aliases dilute a single mean-pooled prototype); `max`-over-declared-material
is the sound kernel. The corpus `serviceIntents.acceptableIds` are source-owned expected service identities,
not English intuition.

**High-precision frontier, B max, service-only margin, vs corpus expected services.** The margins below
are generic evidence; coverage under hypothetical abstention thresholds illustrates what a **domain/product**
threshold could buy. No production threshold is selected, and none is owned by the generic kernel.

| threshold | coverage (all 108) | precision | coverage (no-lexical 83) | precision |
| ---: | ---: | ---: | ---: | ---: |
| ≥ 0.00 | 100% | 91.7% | 100% | 95.2% |
| ≥ 0.04 | 77.8% | 97.6% | 79.5% | 100% |
| ≥ 0.08 | 54.6% | 100% | 57.8% | 100% |

**Named rescues (B max, service top-1):**
- `q_semantic_001` → `facial` (expected `facial`), margin 0.014 — correct but **low margin**.
- `q_semantic_010` → `hair_removal` (expected `hair_removal`), margin 0.117.
- `q2i7_recovery_065` → `manicure` (appended `manicure`; parser has only non-family `r022`), margin 0.054.
- `q_broad_006` → `mobile_beauty`; the case is genuinely broad (expected set spans 5 services), margin 0.025
  — honestly ambiguous, not recovered as the appended `facial`.

All no-family served supplements: `7/15` recovered at top-1 (STRONG_GAIN rescues `3/4`).

**Known bad/weak cases (human FUP-06 calibration, unchanged):** service-level evidence flags the
*cross-family* mismatches (`q_semantic_008`, `q_holdout_face_aquafacial_001`,
`q_holdout_face_anti_age_decollete_001`, `q_holdout_hair_armpits_laser_course_001`, `q2i7_recovery_067`)
but **cannot** flag the on-family `q_semantic_003` (candidate and evidence are both `hair_removal`; the
human cost was wrong modality/zone). The spike-query `q_holdout_hair_armpits_laser_course_001` has margin
`0.008` and top label `facial` (wrong target), so it would **abstain** at any high-precision threshold
rather than be correctly classified. This is evidence that the signal is *precision-oriented optional
evidence*, not a hard family filter and not a complete harm detector.

## 6. Strategy C — offline LLM teacher (design only)

No generative model is available in this environment (only an embedding-only llama.cpp server; no
chat/completion endpoint or API key). Strategy C is therefore **not executed and no synthetic numbers are
reported**. Reproducible recipe:

```text
canonical declarations (codes, names, declared aliases)                     [CANONICAL]
  -> derived teacher prompt (one per key; prompt template is versioned)      [DERIVED]
  -> multilingual paraphrases / confusing hard negatives / seed labels       [DERIVED, content-addressed]
  -> provenance record {canonical-input fingerprint, teacher model id, prompt version,
                        generation config where available, generated-content hash}
  -> embeddings / prototype materialization (embedding-model provenance)
  -> offline calibration on the domain dev set (never the protected holdout)
  -> runtime prototype artifact pinning exact generated-content hash + embedding provenance
```

- **Canonical:** only the executable domain declarations. Teacher prompts are derived from them.
- **Derived teacher output:** generated paraphrases / hard negatives are non-normative, content-addressed
  (fingerprinted), and tied to teacher identity, prompt version, generation configuration where available,
  and a generated-content hash (reuse `CanonicalFingerprint.sha256HexTokens`).
- **Runtime prototype artifact:** pins the exact generated-material fingerprint/hash it was built from
  **and** the embedding-model/prototype-materialization provenance (the `QdrantEmbeddingInput.fingerprint`
  identity is one existing source for the latter).
- **Regeneration:** generated teacher material may be regenerated or replaced; regeneration is **allowed to
  produce different bytes/content** (external LLM generation is not assumed byte-reproducible from model id
  + prompt). Different generated content implies a different fingerprint and requires
  recalibration/revalidation before promotion. Exact deterministic regeneration, where a particular teacher
  provides it, is an implementation property, not a framework assumption.
- **Review:** any generated text that would change business meaning must be promoted into the canonical
  vocabulary through explicit human review, not embedded silently.
- **Prevents a second source of truth:** the guarantee is *not* byte-reproducible regeneration; it is that
  generated material can never silently redefine the business meaning owned by canonical declarations, and
  that the runtime artifact is pinned to the exact content and embedding provenance it was calibrated on.
- No ML training pipeline is needed.

## 7. Soft-selection feasibility (Section 9)

- **Architecturally credible.** The hydrated candidate result already exposes the full ordered top-K with
  each candidate's `serviceCode`/`categoryCode` (`HydratedCandidateSearchResult.candidates`), and
  `AppendOnlySupplementPolicy.select` consumes that vector. A domain policy could compare query-side label
  evidence with candidate identities without a second taxonomy, a duplicated mapping table, or any
  BeautyQ concept in generic backend code.
- **No measured reranking win is claimed.** The tracked FUP-06 artifacts contain only the *selected
  appended* candidate, not the served top-K, and the served ES/Qdrant generations were cleaned up (the
  running dev containers hold no collections/indices). An offline reconstruction of the 66-variant
  document set and `allText` **did not reproduce the served top-1 service in 5 of 15 no-family cases**
  (e.g. `q_broad_006`, `q_semantic_008`, `q_holdout_hair_armpits_laser_course_001`,
  `q2i7_recovery_065`), proving it omits served pipeline factors (hard-constraint filtering, exact
  query-text composition, ANN). A true win/loss counterfactual requires retaining the served top-K (or a
  full re-run), which was not authorized here.
- Directional finding only: evidence-aware selection avoids some cross-family appends but, for the
  `STRONG_HARM` query, inferred `facial` and would select a corpus-`forbidden` facial variant; abstention
  (preserve current baseline behaviour) is the mechanism that avoids it. The credible product shape is
  *high-precision optional evidence + abstention*, not a blanket rerank.

## 8. Neutral-fixture proof shape (design only; no tests added)

Reuse the existing neutral pattern (`SemanticCandidatePlanSpec`'s `ArticleDocument`). Suggested
`SemanticLabelInferenceSpec` uses a non-BeautyQ article/topic domain and proves:

- label materialization from declared labels (ordered registry), including a label with zero, one and
  many materials;
- zero-material label omission (unscored, absent from `ranked`/`best`) and the 0/1/2+ `margin` cardinality
  (`None`, `None`, `Some(top1 - top2)`), with no invented sentinel;
- deterministic `max`-over-material inference and ranking for fixed vectors (proving no concatenation is
  silently reintroduced);
- ordering/tie behaviour (declared order as tie-break);
- deterministic evidence/margin mechanics (top1/top2, `margin`, `best`) — **not** a product abstention
  threshold, which stays domain policy;
- provenance binding: changing declared material changes the derived vectors, and a persisted vector
  artifact is fingerprinted to its exact canonical/material input;
- a domain-supplied non-cosine similarity proving similarity is not hard-coded;
- optional parent handling only if v1 includes it;
- deterministic provenance: identical canonical input + identical generated content yields the same
  fingerprint; changed generated content yields a different fingerprint and requires recalibration.

## 9. Domain Authoring Principles audit (canonical `docs/search/DOMAIN_AUTHORING_PRINCIPLES.md`)

| Principle | Verdict | Where business decides / framework derives / neutral proof / duplication avoided / generated material |
| --- | --- | --- |
| 1 One readable declaration path | **PASS** | New policy is reachable from `BeautyQSearchGen2` → `BeautyQSearchDeclarations`; the adapter is a derived view, not a second path. |
| 2 Business declares policy; framework derives mechanics | **PASS** | Domain declares keys/material/parent/vectors/usage; framework derives traversal, ranking, ties, margin. No second vocabulary. |
| 3 Compose; do not clone | **CONDITIONAL** | Structurally neutral (no domain identity) and neutral-fixture provable, but no second domain exercises it yet → maturity **internal/experimental**, single-consumer, must not be claimed stable. |
| 4 One executable source of truth | **PASS** | Codes + aliases + catalog names stay authoritative; prototypes are a derived, fingerprinted view. |
| Stable semantic identity | **PASS** | Keys are existing `ServiceCode`/`CategoryCode`/`CanonicalSemanticLabel.stableKey`; no `toString` identity. |
| Canonical and supplemental result ownership | **PASS** | Inference returns evidence only; Elasticsearch remains canonical; any use is an explicitly declared supplement-preference policy. |
| Coordinator/reviewer gate | **CONDITIONAL** | Requires the future feature to name canonical entry point, domain-owned differences, derived mechanics, neutral proof, and executable owner; satisfiable but not yet exercised. |
| Current-state qualification | **PASS (with note)** | This document states the current gap (no retained top-K evidence; no second domain) rather than claiming generic implementation. |

No principle *fails*; two are conditional on a second-domain/maturity statement, which is stated here.

## 10. Code-size impact (measured denominators, estimated numerator)

**Measured (tracked `main` LOC):**

| Denominator | LOC |
| --- | ---: |
| Whole generic Search Gen2 production (`search-gen2-*` main) | 12,429 |
| Relevant generic intent/candidate/embedding seam | 1,124 |
| Relevant BeautyQ integration seam (intent vocabulary + declarations + parser + candidate policy/pipeline) | 1,223 |
| BeautyQ Gen2 production (`beautyq-*` + `beautyq-model` main) | 7,983 |
| Whole generic + BeautyQ production | 20,412 |

Relevant generic seam = `SearchIntentMatching` (157) + `SemanticCandidatePlan` (148) + `core/candidate/*`
(64) + `core/hydration/*` (255) + `QdrantCandidate*.scala` (388) + `QdrantEmbedding` (112).
Relevant BeautyQ seam = `BeautyQIntentVocabularyGen2` (489) + `BeautyQSemanticCandidatePolicy` (115) +
`BeautyQSearchDeclarations` (366) + `BeautyQIntentParserGen2` (120) + `BeautyQQdrantCandidatePipeline`
(73) + `BeautyQCandidatePlanCompiler` (60).

**Estimated incremental numerator (Shape 1, not implemented):**

| Layer | Estimated LOC |
| --- | ---: |
| Generic production (`SemanticLabelInference`, prototype/evidence types, fingerprint) | 200–300 |
| BeautyQ/domain adapter (+ optional soft-supplement policy) | 150–260 |
| Focused tests (neutral fixture + domain adapter) | 250–400 |
| Offline calibration/tooling (+ optional LLM teacher pipeline) | 250–500 |
| Declaration additions (key/parent projection) | ~0–30 |

**Estimated PRODUCTION increment** = generic 200–300 + BeautyQ/domain 150–260 + declaration additions
0–30 = **350–590 LOC**, compared against the measured **combined generic + BeautyQ production 20,412 LOC**
=> **≈ 1.7–2.9%** (like-for-like production vs production).

**Source-relevant local ratios** (production increment over the matching source seam):

| Ratio | Estimated |
| --- | ---: |
| generic production increment 200–300 / relevant generic seam 1,124 | ≈ 17.8–26.7% |
| BeautyQ/domain production increment 150–260 / relevant BeautyQ seam 1,223 | ≈ 12.3–21.3% |
| generic production increment 200–300 / whole generic Search Gen2 12,429 | ≈ 1.6–2.4% |

**TOTAL engineering increment** (production 350–590 + tests 250–400 + offline tooling 250–500) =
**850–1,490 LOC**, midpoint ≈ **1,170 LOC**. This total is deliberately **not** expressed as a percentage
of a production-only denominator: there is no comparable already-measured tests/tooling denominator in this
spike, and none was manufactured for this correction.

**Conclusion:** runtime/production code cost is modest globally but substantial relative to the narrow
seam (≈ 18–27% of the relevant generic seam); the total engineering footprint is larger because tests and
offline calibration tooling dominate. The `GO_TO_SPEC_KIT_FEATURE` verdict is unchanged by the lower,
like-for-like production percentage.

## 11. Limitations

- Offline reconstruction uses exact cosine over reconstructed vectors; it is not the served Qdrant ANN run,
  and it omits served hard-constraint filtering and exact query-text composition (§7).
- The protected holdout was not used; visible `q_holdout_*` cases are materialized visible fixtures.
- Only the repository's embedding model/kernel was available; Strategy C is design-only.
- The margin frontier is exploratory; a production threshold is domain policy and is deliberately not
  selected here.
- No second domain has exercised the API, so maturity stays internal/experimental.

## 12. Final FUP-09 outcome

`GO_TO_SPEC_KIT_FEATURE` — the label universe is derivable from existing declarations with no second
ontology, the generic/domain boundary is clean, and the visible-corpus label-inference evidence is strong
(91.7% top-1 / 98.1% top-3 vs corpus-owned service identities; 100% precision at ~55–80% coverage in a
service-only margin frontier). This is **not** implementation authorization and claims **no** measured
reranking win. A separate human decision is required to start/specify the implementation feature.
