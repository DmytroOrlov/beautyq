# RAW QDRANT / FRAMEWORK HISTORY

History frontier: `develop` @ `d66766c0f66f81a0e390c94588b30e65ab325675` (2026-09-10),
also current `HEAD`. All searches were develop-reachable. `--all` not used; `git branch`/`worktree`
not used. The only non-develop ref inspected was `refs/heads/d1`, checked once and found to be an
unrelated June 2026 hybrid-experiment lineage (merge-base `a2ac36a9`), not the future-domain "D1";
it was not used as evidence. Historical material here is rationale/ownership-evolution evidence only,
not current normative authority.

Scope note: the repository uses "Q1/Q2/D1" internally for a post-cutover *delivery* sequence
(Q1 = evaluation corpus/kernel, Q2 = BeautyQ full-corpus measurement, D1 = second domain), which is
NOT the coordinator's Q1/Q2. This file keeps the coordinator's Q1/Q2 meaning throughout and flags the
repo-internal labels explicitly where relevant. No Feature 004 disposition is decided here.

---

## 1. Q1 — reusable heterogeneous retrieval capability

Primary evidence that support for retrieval sources/backends with materially different characteristics
was an intended reusable capability of Search Gen2.

- The pre-Gen2 Search DSL was already described as a tapir-like, multi-interpreter architecture:
  a small immutable description layer plus multiple interpreters, reusable by a new domain without
  copying domain logic into backend interpreters (`docs/search-dsl-domain-onboarding.md`, present at
  `fa78c402`, 2026-06-26; introduced by `a39bdeee`, 2026-06-03). "BeautyQ is the first concrete domain
  using this pattern." Reusable part listed: document spec, field metadata, mapping/ingestion/request/
  response interpreters, in-memory backend, eval workflow.
- Gen2 technical spec made this a first principle: "Common semantic plan, backend-specific policy";
  one `SearchPlan` compiled by both an `ES compiler` and a `Qdrant compiler`; "The framework must
  remove mechanical duplication while keeping true business decisions explicit." Module ownership
  gives `search-elasticsearch` and `search-qdrant` generic backend modules, while BeautyQ owns
  "backend policy values" (`af28e77c`, 2026-07-13, `docs/gen2/BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md`).
  The current spec still states "domain-free backend compilers" and "Qdrant owns filtered semantic
  candidate retrieval only" (`d66766c0`).
- The normative domain-authoring contract: "Business declares policy; the framework derives mechanics";
  domain code declares "source topology and identity selection", "String keyword/text meaning", and
  "facet, group, ranking, backend activation, and response policy"; a neutral or adversarial fixture
  proves a reusable representation independently of BeautyQ; a second production domain is "valuable
  evidence but is not required" (`6baaaea5`, 2026-07-13, `docs/search/DOMAIN_AUTHORING_PRINCIPLES.md`).
- Brick 4G-A is an explicit executable separation: a backend-neutral `CandidatePlan` contract plus a
  reusable `SemanticCandidateEvaluation` kernel that "carr[ies] semantic text and hard constraints
  without owning domain rejection vocabulary or backend retrieval configuration", with
  `BeautyQSemanticCandidatePolicy` owning eligibility policy, and neutral `ArticleDocument` proofs
  (`78b85584`, 2026-07-14).
- A synthetic non-BeautyQ "second-domain" seam proof existed at test level, proving generic lexical +
  semantic hit containers and Qdrant point/document seams while "keeping BeautyQ projection/merge
  policy domain-specific" (`c55cf196`, 2026-06-07; spec later removed, rationale survived in docs).
- Generic Qdrant pieces were deliberately extracted: a dependency-light `search-qdrant` module for
  client/interpreter/indexing/collection-compatibility, leaving BeautyQ wrappers app-side
  (`59da265b`, 2026-06-30), and generic semantic hit decoding parameterized by document/id with a
  BeautyQ compatibility wrapper (`447f6b57`, 2026-06-30).
- Gen2 added `search-gen2-transport` as backend-neutral synchronous JSON/HTTP mechanics, rebuilt the
  Elasticsearch client as a thin adapter over it, and added the Qdrant wire client on the same shared
  transport (`89fdfa08`, 2026-07-19).
- Gen2 framework scope ledger (present at `78b85584`, later removed) framed the kernel as a
  deliberately-calibrated single-consumer extraction, with a `LibraryTracerDomain` and `ArticleDocument`
  proving expressibility "beyond the BeautyQ extraction", explicitly stating the tracer "is not a
  second production or product consumer" and that backend representation concerns are
  non-goals for the generic kernel.
- Strongest generic intent statement: "Do not add new generic seams unless they are needed by a second
  domain proof, projection/merge policy, or a concrete correctness gap"
  (`docs/search-dsl-qdrant-vector-backend.md`, present at `fa78c402`).

Support: EXPLICIT for intent. A full second *production* consumer was never built/committed; the
generic shape was proven with neutral/tracer fixtures and one synthetic test-level domain. So Q1 is
EXPLICIT as an intended reusable capability while remaining a single-production-consumer (BeautyQ)
extraction at freeze.

## 2. Q2 — BeautyQ-specific supplement value / policy

Primary evidence that measurement/policy was a *domain-specific* decision.

- Explicit reusable-vs-transferable distinction: "The BeautyQ-specific thresholds ... are **not** the
  transferable asset. They were fit to BeautyQ's canonical eval set, embedding model, and source text
  and must not be reused blindly in another domain. The transferable asset is the operational method."
  This same section notes the method "does not guarantee a domain will reach a safe Qdrant supplement
  policy... For BeautyQ, following it left policy blocked." (`7dfe6aef`, 2026-06-24,
  `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md`).
- `docs/SEARCH_SUPPLEMENT_ARCHITECTURE.md` (present from `10e22316`, 2026-06-26) separated a generic
  baseline/supplement architecture from a "BeautyQ concrete example"; domains own thresholds, query
  sets and harm budget; gate/benchmark output is "local/test evidence only, never automatic
  production/default route approval".
- `docs/SEARCH_SUPPLEMENT_FUTURE_DOMAIN_GATE_TEMPLATE.md` (`fa78c402`, 2026-06-26) made future domains
  define their own baseline backend, supplement candidate source, and source-confirmed query set, with
  per-domain pass conditions and a user-approved harm budget.
- The BeautyQ Qdrant opt-in route was disabled by default and its production activation was governed by
  a separate decision doc: activation is an explicit approval built from offline evidence plus route/
  control safety, "not a telemetry threshold"; non-requirements included real production traffic,
  shadow serving, mirroring (`docs/codebase-review/QDRANT_PRODUCTION_ACTIVATION_DECISION_CRITERIA.md`,
  present at `d144633b`, 2026-06-22).
- BeautyQ-specific measurement gates: `QP18` proved Qdrant supplement improvement without worsening
  ES (`438c3407`, 2026-06-26) and `QP19` added a measured acceptance gate with per-domain metrics and
  hard-fail conditions (`50650002`, 2026-06-26). Both are BeautyQ/ES/Qdrant-specific.
- The post-cutover plan names the domain-specific measurement phase explicitly: repo-internal "Q2 runs
  the full BeautyQ evaluation corpus through the actual Gen2 application, records relevance ... and
  defines the correction gate for regressions", while "O0" adds the Required/Preferred/Disabled
  startup policy and "FullSearch/BaselineOnly" serving mode (`1b3bac45`, 2026-07-27).
- Current state confirms policy is domain-owned: `SupplementStartupPolicy` (Required default /
  Preferred / Disabled) is "BeautyQ wiring-owned", applied before DI planning
  (`docs/gen2/BEAUTYQ_SEARCH_GEN2_OPERATIONS.md`, `d66766c0`); the current technical spec states
  `SupplementStartupPolicy.Required|Preferred|Disabled` is domain configuration, with activation
  choice applied before planning.
- Latest cleanup explicitly defers the domain-value question: "do not decide Qdrant marginal product
  value"; product/quality-semantic decisions are out of scope (`dee0c458`, 2026-09-10). The current
  simplification proposal says Qdrant "proved operation and no-harm, not marginal product value" and
  its long-term ownership "must be decided by a separate, predeclared product-value comparison"
  (`docs/gen2/BEAUTYQ_SEARCH_GEN2_SIMPLIFICATION_PLAN.md`, `d66766c0`).

Support: EXPLICIT. Q2 is domain-specific threshold/policy/product-value measurement.

## 3. Evidence separating Q1 from Q2

- `6baaaea5` (2026-07-13): reusable components are generic; "backend activation" and route/ranking
  policy are explicitly listed as domain-owned differences. Reuse is proven by neutral fixtures, not
  by a BeautyQ uplift result.
- `af28e77c` (2026-07-13): "Common semantic plan, backend-specific policy"; generic `search-qdrant`
  module vs BeautyQ-owned "backend policy values"/"quality corpus and thresholds".
- `78b85584` (2026-07-14): generic candidate contract/kernel owns no BeautyQ reasons or labels;
  BeautyQ owns reason vocabulary, stable IDs/codes, and eligibility-gate order.
- `c55cf196` (2026-06-07): synthetic second-domain proof proves generic seams while "keeping BeautyQ
  projection/merge policy domain-specific".
- `7dfe6aef` (2026-06-24): transferable asset = reusable method; BeautyQ thresholds are explicitly
  non-transferable and policy stayed blocked.
- `1b3bac45` (2026-07-27): sequences BeautyQ measurement (repo-Q2) and second-domain work (repo-D1)
  as different patches; D1 "develops the second domain evaluation-first, using the neutral evaluation
  kernel from its first vertical slice." BeautyQ's supplement policy is decided in repo-Q2, not by the
  existence of the neutral kernel.
- `dee0c458` (2026-09-10): the cleanup "preserv[es] ... generic Search Gen2 main-source BeautyQ
  neutrality, serving/eval classpath isolation, BeautyQ materialization SQL confinement" while
  explicitly deferring "Qdrant marginal product value" — the reusable neutrality and the domain-value
  question are treated as different concerns in the latest develop-reachable state.

EXPLICIT Q1/Q2-separation evidence WAS found (see above, especially `6baaaea5`, `7dfe6aef`,
`78b85584`, `1b3bac45`, `dee0c458`).

## 4. Evidence potentially coupling Q1 and Q2

- The abstraction-discipline rule "do not add new generic seams unless they are needed by a second
  domain proof, projection/merge policy, or a concrete correctness gap"
  (`docs/search-dsl-qdrant-vector-backend.md`, present at `fa78c402`) can *delay* generic extraction
  until cross-domain/structured evidence exists, but it conditions *new seam creation*, not the
  deletion of an existing generic backend on BeautyQ value.
- The supplement "Failure policy: if no improvement, stop" (`docs/SEARCH_SUPPLEMENT_ARCHITECTURE.md`,
  `10e22316`) governs whether a domain adopts/keeps a supplement path, i.e. per-domain policy, not
  whether the reusable backend capability exists.
- `89f69706` (2026-07-23) cut BeautyQ production over to Gen2 with Qdrant candidate-only under
  `Required` default and append-only supplement semantics. This makes Qdrant load-bearing for the
  BeautyQ default route, which can superficially look like coupling of capability and domain policy,
  but the commit frames it as BeautyQ activation/ops behavior, not as proof that the generic backend
  must exist.

No develop-reachable evidence was found stating that the *reusable* heterogeneous-backend/Qdrant
capability should be removed if BeautyQ supplement value were unproven. Coupling evidence is weak and
is about extraction timing / domain activation, not about capability existence being conditional on
BeautyQ product value.

## 5. Qdrant ownership evolution

Transitions, with preserved vs retired:

- (A) Qdrant as BeautyQ's concrete semantic/vector supplement implementation: hybrid roadmap and
  activation criteria (`d144633b`, 2026-06-22); QP18/QP19 BeautyQ gates (`438c3407`, `50650002`,
  2026-06-26).
- (B/E) Generic/reusable Qdrant implementation proving framework seams: `search-qdrant` module
  extraction keeping BeautyQ wrappers app-side (`59da265b`, 2026-06-30); generic semantic hit decoding
  and `QdrantSemanticDocumentSearch` with BeautyQ compatibility wrapper (`447f6b57`, 2026-06-30);
  Gen2 `search-gen2-qdrant` + neutral `search-gen2-transport` (`89fdfa08`, 2026-07-19); backend-neutral
  candidate contract (`78b85584`, 2026-07-14). Preserved across the Gen1→Gen2 cutover as the
  `search-gen2-qdrant` module; no BeautyQ names allowed in generic main sources.
- (C) BeautyQ-specific operational/readiness/no-harm obligations: Required/Preferred/Disabled startup
  policy and serving mode (`1b3bac45`, 2026-07-27), explicit supplement startup control
  (`86975cb2`, 2026-07-31), supplement startup construction ownership (`681e23c8`, 2026-08-02),
  current operations runbook (`d66766c0`). Preserved.
- (D) BeautyQ-specific product-value evidence: QP19 measured gate (`50650002`); post-cutover repo-Q2
  full-corpus measurement; current simplification proposal treats marginal product value as an open
  separate comparison (`d66766c0`). Preserved as unresolved/deferred.
- Activation-policy transition: Qdrant moved from disabled-by-default explicit opt-in
  (`d144633b`/`fa78c402` era) to a native Gen2 production path where Qdrant is candidate-only under
  `Required` default (`89f69706`, 2026-07-23). What changed is BeautyQ activation/ops policy; the
  generic backend module and the domain-owned policy boundary both remained.
- Latest status: `dee0c458` (2026-09-10) keeps generic neutrality/eval isolation/SQL confinement checks
  and defers Qdrant marginal product value; the simplification proposal lists Qdrant as "conditional,
  not a deletion candidate by footprint alone" (`d66766c0`).

## 6. D1 / future-domain rationale

- EXPLICIT (`1b3bac45`, 2026-07-27, `docs/gen2/BEAUTYQ_SEARCH_GEN2_POST_CUTOVER_PLAN.md` §Patch D1):
  "eval-first second-domain vertical. This begins only after the product identity and source topology
  of the second domain are supplied. It starts with its corpus and simplest baseline, not with a
  copied BeautyQ module tree. Every subsequent domain slice carries its quality evidence." D1 is
  developed "using the neutral evaluation kernel from its first vertical slice."
- EXPLICIT (`6baaaea5`/current `docs/search/DOMAIN_AUTHORING_PRINCIPLES.md`): a second production
  domain is "valuable evidence but is not required"; adding a new domain must not require copying
  BeautyQ policy and must not change the generic framework unless it introduces a genuinely reusable
  semantic; on genuine cross-domain duplication, extract the neutral operation.
- EXPLICIT (current `docs/search/NEW_DOMAIN_ONBOARDING.md`, `d66766c0`): a new domain chooses its own
  baseline/result owner, decides whether a secondary backend is candidate-only/advisory/supplemental,
  declares its own no-harm rule, defines its own measured gate and metric thresholds, and gets a
  separate approval boundary before any production/default route change. "Eval-first domain delivery"
  starts from 10–20 operator-approved anchor queries and the simplest executable baseline.
- EXPLICIT (current `specs/004-.../spec.md`, `d66766c0`): D1 is listed as an explicit non-goal of
  Feature 004 ("begin a second production domain (D1) or pull in its deferred prerequisites").
- INFERRED: the motivating purpose of D1 was to challenge BeautyQ-specific assumptions and to provide
  structurally different product evidence before further framework extraction. The extraction-discipline
  rule ("needed by a second domain proof") and the authoring contract support this, but no single doc
  states it as a goal in one sentence.
- Later-history status: D1 remained deferred at the develop freeze (`d66766c0`); the post-cutover plan
  that named it was later deleted (`c8f1c95d`, 2026-09-10), but the deferral is still recorded in the
  current Feature 004 spec and simplification proposal ("begin a second production domain" is a
  non-goal). The rationale is PRESERVED; the concrete D1 plan artifact is SUPERSEDED.

## 7. Project-boundary rationale delta

Only items not already obvious from current `build.sbt`:

- `search-gen2-transport` is neutral because both backend implementations consume it: `89fdfa08`
  extracted backend-neutral JSON/HTTP mechanics, rebuilt the Elasticsearch client as a thin adapter
  over the shared transport, and added the Qdrant wire client on the same transport. Current `build.sbt`
  keeps `searchGen2Transport` under the independent Gen2 DAG.
- Generic backend modules must stay domain-free: `cc769ad4` (2026-07-07) hardened the
  `search-elasticsearch`/`search-qdrant` guardrail to reject BeautyQ contract/runtime, repository,
  materialization, HTTP/Tapir, config/plugin, SQL/Doobie, routing/activation, response-assembly,
  parser/eval and app-wiring imports. Current `build.sbt` comment still cites "generic neutrality,
  aggregate coverage, eval isolation, SQL confinement, and BeautyQ production package ownership".
- Eval isolation is intentional: `dee0c458` (2026-09-10) retains "serving/eval classpath isolation" and
  requires exactly one BeautyQ eval dependency in `leaderboard-app-shell` kept test->test.
- SQL is intentionally confined to materialization: `dee0c458` retains "BeautyQ materialization SQL
  confinement" and fails closed on alternate `leaderboard.sql` package spellings while keeping one
  direct `leaderboard.sql.SQL` materialization carve-out.
- ES/Qdrant app runtime port config is not a generic-backend concern: `e48b9d8f` (2026-07-07) moved
  `ElasticsearchPortCfg`/`QdrantPortCfg` out of `leaderboard-core` into the app shell because generic
  `search-elasticsearch`/`search-qdrant` do not consume them.

## 8. Historical Q1/Q2 evidence matrix

| evidence | SHA | historical proposition | Q1/Q2 | strength | later status |
|---|---|---|---|---|---|
| Search DSL domain onboarding guide | `a39bdeee` (2026-06-03) | Reuse tapir-like DSL + multiple backend interpreters for a new domain; BeautyQ is first domain | Q1 | EXPLICIT | SUPERSEDED (doc consolidated); principle PRESERVED |
| Synthetic second-domain generic seam proof | `c55cf196` (2026-06-07) | Test-level non-BeautyQ proof of generic lexical/semantic/Qdrant seams; BeautyQ merge policy stays domain-specific | BOTH | EXPLICIT | REMOVED (spec absent on develop); rationale preserved in docs |
| Qdrant activation criteria / hybrid roadmap | `d144633b` (2026-06-22) | Qdrant opt-in disabled by default; default-route activation is a separate approval, offline-evidence based | Q2 | EXPLICIT | SUPERSEDED at Gen2 cutover |
| Reusable Qdrant domain method | `7dfe6aef` (2026-06-24) | BeautyQ thresholds not transferable; reusable asset = operational method; BeautyQ policy stayed blocked | Q1+Q2 | EXPLICIT | PRESERVED in onboarding wording |
| SEARCH_SUPPLEMENT_ARCHITECTURE | `10e22316` (2026-06-26) | Generic baseline/supplement pattern with BeautyQ concrete example; domains own thresholds; gate is local evidence only | Q1+Q2 | EXPLICIT | NARROWED (doc removed at Gen2 cutover; mechanics folded into Gen2 spec/onboarding) |
| Future-domain supplement gate template | `fa78c402` (2026-06-26) | Future domains define own baseline/supplement/query set and pass conditions | Q2 | EXPLICIT | NARROWED (merged into architecture `6cf16f27`; later superseded) |
| QP18 no-worsening proof | `438c3407` (2026-06-26) | Real-ES/real-Qdrant proof of additive supplement; ES baseline unchanged; future-domain rule recorded | Q2 | EXPLICIT | SUPERSEDED by Gen2 cutover gate; shape preserved |
| QP19 measured acceptance gate | `50650002` (2026-06-26) | BeautyQ per-domain metrics; hard-fail unless positive improvement + zero regressions | Q2 | EXPLICIT | SUPERSEDED by Gen2 cutover gate; shape preserved |
| Extract reusable `search-qdrant` module | `59da265b` (2026-06-30) | Generic Qdrant client/interpreter/indexing reusable; BeautyQ wrappers app-side | Q1 | EXPLICIT | SUPERSEDED by `search-gen2-qdrant`; principle PRESERVED |
| Generic Qdrant hit decoding | `447f6b57` (2026-06-30) | Decode generic over document/id; BeautyQ compatibility wrapper | Q1 | EXPLICIT | SUPERSEDED by Gen2; principle PRESERVED |
| Generic backend import guardrail | `cc769ad4` (2026-07-07) | Both backend modules must stay free of domain/repo/materialization/HTTP/SQL/routing imports | Q1 | EXPLICIT | PRESERVED (current build comment) |
| ES/Qdrant port configs out of core | `e48b9d8f` (2026-07-07) | Port/runtime config belongs shell-side, not in generic backends | CONTEXT_ONLY | EXPLICIT | PRESERVED |
| Domain-authoring contract | `6baaaea5` (2026-07-13) | Business declares policy; framework derives mechanics; backend activation domain-owned; second domain optional but valuable | Q1+Q2 | EXPLICIT | PRESERVED (current doc) |
| Gen2 technical spec: common plan / backend policy | `af28e77c` (2026-07-13) | One `SearchPlan`; ES + Qdrant compilers; generic backend modules; BeautyQ owns backend policy values and thresholds | Q1+Q2 | EXPLICIT | PRESERVED (current spec) |
| Gen2 module boundaries and firewalls | `038606c7` (2026-07-13) | Generic vs BeautyQ module separation and firewall proofs | Q1 | EXPLICIT | PARTIALLY NARROWED (`dee0c458` collapsed exact-DAG mirrors, kept neutrality/eval/SQL checks) |
| Brick 4G-A candidate planning | `78b85584` (2026-07-14) | Backend-neutral `CandidatePlan` + reusable evaluation kernel; BeautyQ owns reason/eligibility policy; neutral `ArticleDocument` proof | Q1+Q2 | EXPLICIT | PRESERVED |
| Neutral Gen2 transport + Qdrant wire client | `89fdfa08` (2026-07-19) | Shared backend-neutral transport because both ES and Qdrant consume it | Q1 | EXPLICIT | PRESERVED |
| Native Gen2 cutover | `89f69706` (2026-07-23) | Gen1 stack retired; ES complete baseline owner; Qdrant candidate-only append-only under Required default | Q2 | EXPLICIT | PRESERVED (current architecture) |
| Post-cutover plan (Q1/O0/Q2/O1/D1) | `1b3bac45` (2026-07-27) | BeautyQ measurement phase (repo-Q2) separate from second-domain eval-first phase (repo-D1, reuses neutral kernel); Required/Preferred/Disabled domain policy | BOTH + scope Q1/Q2 | EXPLICIT | NARROWED; plan artifact later deleted but deferral persists |
| Explicit supplement startup control | `86975cb2` (2026-07-31) | Supplement activation is explicit, domain-owned startup policy | Q2 | EXPLICIT | PRESERVED (ops runbook) |
| Supplement startup construction ownership | `681e23c8` (2026-08-02) | Disabled must not construct Qdrant/embedding heavy deps; activation ownership explicit | Q2 | EXPLICIT | PRESERVED |
| Cleanup wave | `dee0c458` (2026-09-10) | Keeps generic neutrality/eval isolation/SQL confinement; explicitly defers "Qdrant marginal product value" | BOTH | EXPLICIT | CURRENT |

## 9. Unresolved questions

History could not answer:

1. No develop-reachable commit records the *final* BeautyQ product-value verdict for Qdrant
   supplementation. Measurement machinery and gates were repeatedly built/retired, and the question is
   explicitly left to a future predeclared comparison. History therefore cannot say BeautyQ "benefits
   enough"; it can only say the domain-value question was deliberately deferred.
2. No second *production* domain was ever committed, so history cannot show a real cross-domain
   adoption choosing a different source mix/balance. Reuse was proven with the `LibraryTracerDomain`
   and `ArticleDocument` neutral fixtures plus a later-removed synthetic test-level domain.
3. No primary document states in one sentence that "the reusable heterogeneous-backend capability is
   unconditionally independent of BeautyQ's measured value." The separation is established by
   ownership boundaries and sequencing (policy/activation domain-owned; generic modules BeautyQ-free),
   not by one explicit anti-coupling statement.
4. It is not recorded whether the Gen2 cutover's move to `Required`/Qdrant-candidate-only by default
   was meant to be permanent BeautyQ policy or a reversible default pending product value; the current
   simplification proposal treats it as still-open.
5. The concrete D1 product identity, source topology, and supplement gate were never supplied in
   develop-reachable history, so history cannot describe D1's intended source topology beyond
   "domain-supplied, eval-first, not a copied BeautyQ tree."
