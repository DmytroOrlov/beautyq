# BeautyQ Search Gen2 — promise audit before Brick 5D

Status: **audit gate active; Brick 5D must not start until the `fix before 5D` decisions below are closed**

Audit baseline: `87f47796` (`feat(search-gen2-es): add physical generation lifecycle and baseline execution`)

Reconciliation baseline: this source audit and one independent review of the same commit. The independent
review's source claims were rechecked here claim by claim (see the reconciliation table). The
execution-evidence question is fully closed and owner-accepted: D-05 retains the repository owner's own
full-suite run (2,097 tests, 0 failed/canceled, executed before both audit passes) together with two
reproduced raw-output runs of the unchanged local Elasticsearch spec.

This is a value-and-architecture audit, not another local code review. It checks whether Gen2 is
delivering the reasons it was created: learn from Gen1 without depending on it, make business policy
readable and reusable, prevent inconsistent states at the right boundary, preserve search semantics,
use backend-specific capabilities without contaminating the shared plan, and make every architectural
claim traceable to executable code or an honestly named missing proof.

Canonical context:

- [Gen1 evidence review](BEAUTYQ_SEARCH_GEN2_REVIEW.md) owns the original source-confirmed pains;
- [semantics ADR](BEAUTYQ_SEARCH_GEN2_SEMANTICS_ADR.md) owns accepted business/search decisions;
- [technical specification](BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md) owns accepted target architecture;
- [implementation plan](BEAUTYQ_SEARCH_GEN2_IMPLEMENTATION_PLAN.md) owns live status and sequencing;
- [domain authoring principles](../search/DOMAIN_AUTHORING_PRINCIPLES.md) own the normative values used
  by this audit.

This document owns only the cross-cutting checkpoint and its decisions. It must not become another API
reference or implementation-history log.

## Audit method and limits

The audit re-read the baseline build DAG, current Gen1 request/response/materialization/Qdrant/startup
paths, every Gen2 main-source module, the focused test inventory and the canonical Gen2 documents. It
did not infer implementation from commit messages alone. Representative Gen1 claims were reconfirmed in
current source: ES still requests totals/aggregations while its decoder returns hits only; response facets
and groups are still rebuilt from hit documents; the public input still lacks structured filters/sort/
cursor; the managed seed path still prepares search resources from the ready seed catalog; and the Qdrant
route still hydrates candidate IDs against ready catalog documents.

No sbt, database, Elasticsearch, Qdrant or HTTP resource command was run for this documentation audit.
Existing tests are counted as structural proof only where the repository contains the exact focused spec.
An independent audit later reported a successful run of `ElasticsearchLocalResourceSpec` against
Elasticsearch 8.14.3, initially without raw command output. The requested unchanged rerun has since been
executed and its raw result is retained in D-05, upgrading that claim from reported evidence to a
reproduced, recorded run. Test greenness itself is settled by a stronger record: the repository owner
personally ran the complete suite before either audit pass and retained the raw result in D-05 — 2,097
tests across 248 suites, 0 failed, 0 canceled. Zero cancellations means the local resource spec itself
executed and passed inside that owner run. Per-model focused counts (such as the independent review's
995) are therefore context only; the owner record is the acceptance baseline.

## Independent-audit reconciliation

Conflicting reviews are reconciled claim by claim, never by averaging their headlines:

1. inspect the executable owner and every consumer;
2. inspect the exact focused proof and what it can establish;
3. require raw output or a fresh run for claims about an executed external resource;
4. classify current correctness, principle conformance and future operational work separately;
5. keep the result here as the only audit verdict and route live sequencing to the implementation plan.

| Independent claim | Source check | Unified decision |
|---|---|---|
| Most Gen1 failures are already corrected | Confirmed for the implemented contract/core/ES slice | Agree; later Qdrant/groups/runtime/HTTP work remains partial, not failed |
| No P0 principle violation exists | No current production path was found to forge materialization identity | Agree on severity: D-01 is a P1 structural API breach, not a present P0 incident; it still violates the impossible-state promise |
| Materialization is fully trusted | Rejected: `VersionedSnapshot` and `MaterializedSearchDocuments` are public case classes accepted by generation compilation | Keep D-01; the independent audit omitted this construction boundary |
| There is no ownership leak | Agree that no duplicate ES policy owner exists | D-02 is instead a source-confirmed authoring/navigation and documentation mismatch: the promised full facade does not exist |
| The local ES gate is green | Reproduced twice with raw output retained in D-05, and confirmed inside the owner's own full-suite run (2,097/2,097, 0 canceled) | Resolved and owner-accepted; the greenness question is closed |
| `KeepAll` is dead ceremony | Confirmed: config stores it and lifecycle never reads it | Remove before 5D |
| Active authorization costs three ES round trips | Confirmed for `Active`: alias lookup, mapping read and count | Fix before runtime integration; correctness is currently fail-closed |
| First count mismatch has misleading `live` data | Confirmed: metadata is passed as both metadata and live before live count is read | Fix the diagnostic before 5D |
| General “declaration order” wording is inaccurate | Confirmed: field order is `staticFields ++ dynamicFields`, preserving order only inside each partition | Correct source/docs wording before 5D |
| Custom analyzer availability lacks an executable owner | Confirmed; arbitrary names reach mapping creation, while BeautyQ uses built-in `standard` only | Before runtime, either constrain support to built-ins or add settings/availability ownership |
| Cursor-version failures need a client contract | Confirmed; typed errors exist, HTTP mapping does not yet | Brick 8 must map them to restart pagination, not 500 |
| Mutable declaration builder is an undocumented hazard | Rejected as a blocker: mutation is private, initialization-scoped and already documented beside the code | Accept intentionally; do not add another policy abstraction |

The independent audit is therefore valuable evidence but does not justify its headline transition to 5D.
Brick 5D is technically implementable; the pause is the deliberately chosen audit gate while the cheap
cross-cutting corrections remain cheap.

## Executive verdict

Gen2 has already corrected the most expensive Gen1 failures:

- it is a side-by-side module DAG with an import firewall rather than a migration inside Gen1;
- persisted SQL state is read through a consistent snapshot and explicitly projected;
- document fields, public input, intent, plan, candidate eligibility and Elasticsearch policy are typed
  executable values rather than one lossy descriptive aggregate;
- constraint, price-overlap and geo semantics are explicit;
- Elasticsearch now has declaration-derived mapping/source/request/response/lifecycle mechanics,
  exact totals, typed facets, cursor binding and lifecycle-authorized physical targets;
- Qdrant is structurally a candidate-only backend, not a fake full-result peer;
- compiler-owned aggregates and typed errors close many impossible-state and semantic-loss paths;
- neutral fixtures prove the reusable kernels independently of BeautyQ.

The architecture is therefore worth continuing. No current P0 production failure was found. It is still
not ready to start Brick 5D because the checkpoint was explicitly placed here to close structural drift
before another backend-result surface is added. Two promises remain structurally incomplete:

1. materialization identity is still forgeable before it reaches the otherwise strict generation/lifecycle
   boundary;
2. the documented single business-visible root is not the actual full composition root.

Brick 5C's real Elasticsearch proof is closed and owner-accepted: the repository owner's own full-suite
run (2,097 tests, 0 canceled — the resource spec executed and passed inside it) plus two recorded audit
reproductions of the unchanged spec are retained in D-05. Nothing about 5C needs redesign; the earlier
back-and-forth was evidence bookkeeping, not a product gap.

One smaller surface is also pure future ceremony: lifecycle configuration exposes a `KeepAll` retention
policy even though it is the only case and no retention behavior reads it. Remove that choice until a
second executable policy exists.

Verdict: **CONTINUE_GEN2, PAUSE_BEFORE_5D**.

### Dimension verdicts

| Dimension | Verdict | Why |
|---|---|---|
| Ownership | **Strong with two P1 corrections** | Plan/candidate/ES owners are singular and executable; materialization aggregate construction and the missing full composition facade are not yet aligned with that standard. |
| Impossible states and totality | **Strong after validation boundaries, weak at materialization input** | Typed handles, closed compiler results and typed decoders are good. Snapshot/materialized linked facts remain independently constructible. |
| Semantic integrity | **Strong for shared plan + ES** | Price overlap, provenance, precedence, facets, pagination and geo are explicit. Groups, Qdrant and public response are still unimplemented and therefore unproved, not silently approximated. |
| Domain-authoring value | **Substantially improved, not yet one-path** | Fields and repeated mechanics are derived; business policy remains explicit. Backend/materialization navigation and registry declaration safety still require work. |
| Backend neutrality | **Architecturally sound, only ES exercised** | Generic contract/core contain no BeautyQ/backend semantics, and ES contains no BeautyQ policy. Qdrant's separate role/module is designed but not implemented. |
| Proof quality | **High focused evidence; live ES proof recorded** | Neutral fixtures, compile-negative boundaries, goldens and scripted lifecycle proofs are strong. D-05 retains the reproduced local ES result; no real second domain exists. |
| Documents as contract | **Owner map is good; truthfulness/size need correction** | ADR/principles/plan/spec owners are named, but the spec presents a target full root as current and plan/spec duplicate too much source/history. |

### Source evidence anchors

- contract root and field declaration:
  [`BeautyQSearchDeclarations.scala`](../../beautyq-search-gen2-contract/src/main/scala/leaderboard/search/beautyq/gen2/contract/BeautyQSearchDeclarations.scala);
- public request and intent policy:
  [`BeautyQPublicInputGen2.scala`](../../beautyq-search-gen2-contract/src/main/scala/leaderboard/search/beautyq/gen2/contract/BeautyQPublicInputGen2.scala),
  [`BeautyQIntentVocabularyGen2.scala`](../../beautyq-search-gen2-contract/src/main/scala/leaderboard/search/beautyq/gen2/contract/BeautyQIntentVocabularyGen2.scala);
- plan and candidate policy:
  [`BeautyQSearchPlanPolicy.scala`](../../beautyq-search-gen2-contract/src/main/scala/leaderboard/search/beautyq/gen2/contract/BeautyQSearchPlanPolicy.scala),
  [`BeautyQSemanticCandidatePolicy.scala`](../../beautyq-search-gen2-contract/src/main/scala/leaderboard/search/beautyq/gen2/contract/BeautyQSemanticCandidatePolicy.scala);
- snapshot, projection and materialization:
  [`BeautyQSearchSnapshotSource.scala`](../../beautyq-search-gen2-materialization/src/main/scala/leaderboard/search/beautyq/gen2/materialization/BeautyQSearchSnapshotSource.scala),
  [`BeautyQVariantProjectionGen2.scala`](../../beautyq-search-gen2-materialization/src/main/scala/leaderboard/search/beautyq/gen2/materialization/BeautyQVariantProjectionGen2.scala),
  [`SearchMaterializationIdentity.scala`](../../search-gen2-core/src/main/scala/leaderboard/search/gen2/core/materialization/SearchMaterializationIdentity.scala),
  [`SearchMaterializer.scala`](../../search-gen2-core/src/main/scala/leaderboard/search/gen2/core/materialization/SearchMaterializer.scala);
- plan identity and cursor binding:
  [`PlanContractFingerprint.scala`](../../search-gen2-core/src/main/scala/leaderboard/search/gen2/core/plan/PlanContractFingerprint.scala),
  [`SearchCursorEnvelope.scala`](../../search-gen2-core/src/main/scala/leaderboard/search/gen2/core/plan/SearchCursorEnvelope.scala);
- complete BeautyQ ES policy and plan binding:
  [`BeautyQElasticsearchPolicy.scala`](../../beautyq-search-gen2-wiring/src/main/scala/leaderboard/search/beautyq/gen2/wiring/BeautyQElasticsearchPolicy.scala),
  [`BeautyQSearchPlanCompiler.scala`](../../beautyq-search-gen2-wiring/src/main/scala/leaderboard/search/beautyq/gen2/wiring/BeautyQSearchPlanCompiler.scala);
- generic ES request, response, lifecycle and authorization:
  [`ElasticsearchSearchRequestCompiler.scala`](../../search-gen2-elasticsearch/src/main/scala/leaderboard/search/gen2/elasticsearch/ElasticsearchSearchRequestCompiler.scala),
  [`ElasticsearchSearchResponseDecoder.scala`](../../search-gen2-elasticsearch/src/main/scala/leaderboard/search/gen2/elasticsearch/ElasticsearchSearchResponseDecoder.scala),
  [`ElasticsearchGenerationLifecycle.scala`](../../search-gen2-elasticsearch/src/main/scala/leaderboard/search/gen2/elasticsearch/lifecycle/ElasticsearchGenerationLifecycle.scala),
  [`ElasticsearchSearchRequestAuthorization.scala`](../../search-gen2-elasticsearch/src/main/scala/leaderboard/search/gen2/elasticsearch/lifecycle/ElasticsearchSearchRequestAuthorization.scala);
- proof boundaries:
  [`SearchGen2ModuleFirewallSpec.scala`](../../search-gen2-contract/src/test/scala/leaderboard/search/gen2/contract/SearchGen2ModuleFirewallSpec.scala),
  [`LibraryTracerDomain.scala`](../../search-gen2-core/src/test/scala/leaderboard/search/gen2/core/tracer/LibraryTracerDomain.scala),
  [`ElasticsearchTestFixtures.scala`](../../search-gen2-elasticsearch/src/test/scala/leaderboard/search/gen2/elasticsearch/ElasticsearchTestFixtures.scala),
  [`ElasticsearchLocalResourceSpec.scala`](../../search-gen2-elasticsearch/src/test/scala/leaderboard/search/gen2/elasticsearch/ElasticsearchLocalResourceSpec.scala).

## 1. Gen1 pain → Gen2 promise → actual proof

Legend:

- **Proven** — executable source plus a focused structural/behavioral proof exists;
- **Partial** — the implemented slice proves the claim, but a named later boundary is still absent;
- **Planned** — no production implementation exists yet; the plan is not evidence;
- **Deviation** — current code or documentation contradicts the promise.

| Gen1 pain | Gen2 promise | Actual evidence at the audit baseline | Status |
|---|---|---|---|
| G-01: ES requested totals/aggs but decoded only hits; facets/groups were recounted from a bounded hit window | ES owns a role-specific full baseline result with exact totals, backend facets, groups and page state | `ElasticsearchSearchResponseDecoder` returns a closed `BaselineSearchPage` with exact total, typed terms/range facets, diagnostics and next cursor. Groups are deliberately rejected by the 5B compiler until 5D. | **Partial** — totals/facets/page proven; groups are 5D |
| G-02: seed data could feed SQL and search indexes through parallel paths | SQL/repositories are the only source; a consistent snapshot is projected before backend generation | `BeautyQSearchSnapshotSource.Postgres` performs all reads inside one repeatable-read read-only transaction; `BeautyQVariantMaterializer` feeds `ElasticsearchGenerationCompiler`. Runtime repository→materializer composition is still Brick 8. | **Partial** — correct path exists, runtime ownership is later |
| G-03: response facets could not be sent back as typed request filters | Public names/operators, selected facets, planned constraints and applied provenance form one closed loop | `BeautySearchRequestGen2.validate`, `BeautyQPublicFilterRegistry`, `FacetPlanRegistry`, `SearchPlan.appliedFilters` and ES facet compilation cover request→plan→backend. The public Gen2 response/HTTP projection does not exist yet. | **Partial** — serving response closes in Bricks 8/9 |
| G-04: price, range and geo constraints meant different things in different paths | One backend-neutral algebra and ADR-defined semantics compile into each backend | `PlannedConstraint`, `RangeBounds`, `IntervalOverlap`, the three geo operations and ES request goldens implement the accepted semantics. Qdrant compilation is not implemented. | **Partial** — shared/ES proven; Qdrant is Brick 6 |
| G-05: `SearchDomainSpec` was populated but not executable | Every visible policy branch is consumed by a compiler/interpreter; generated views are derived | Fields, public input, intent, plan and ES policies are executable. `BeautyQSearchDeclarations` still does not expose materialization/backend/resource ownership while the technical spec calls the full tree current. | **Deviation** — see D-02 |
| G-06: broad backend capability booleans could not prove support | Typed field capabilities plus compiler validation and typed unsupported errors | Field/document validators enforce kind/capability combinations; ES policies validate analyzers, weighted fields, finite values and identity sort; unsupported groups fail explicitly. | **Proven for contract + ES** |
| G-07: `filterable`/`facetable`/`sortable` flags were descriptive | Invalid constraint/facet/sort/group uses cannot cross plan/backend validation | `PlannedConstraint.validate`, `FacetRequest.validate`, `GroupRequest.validate`, `SearchPlan.validate`, ES policy validation and compile-negative field tests exercise the boundary. | **Proven** |
| G-08: two BeautyQ ES adaptation paths duplicated request/response mechanics | One domain-neutral ES compiler/decoder/lifecycle path, with thin domain composition | `ElasticsearchGenerationCompiler`, `ElasticsearchSearchRequestCompiler`, `ElasticsearchSearchResponseDecoder`, `ElasticsearchGenerationLifecycle` and `ElasticsearchBaselineService`; BeautyQ wiring only supplies policy/resource inputs. | **Proven** |
| G-09: Qdrant payload could not push down BeautyQ constraints | Qdrant policy derives payload/index/filter coverage from canonical fields and rejects unsupported plans | `search-gen2-qdrant` is still a module marker. | **Planned** — Brick 6 |
| G-10: index management was destructive bootstrap, not versioned lifecycle | Deterministic immutable generations, validation, atomic alias activation and pinned-page authorization | Brick 5C implements generation names/metadata, bounded bulk ingestion, reuse validation, alias activation and lifecycle authorization. Scripted proofs exist; D-05 retains the reproduced 257-document local Elasticsearch result. | **Implemented; evidence recorded, gate acceptance pending** |
| G-11: one common DSL risked collapsing ES/Qdrant to their lowest common denominator | Shared semantic plan plus typed role-specific backend policies/results | ES has its own mapping/query/lifecycle types; candidate planning is backend-neutral; Qdrant has a separate module and candidate role. | **Partial** — architecture proven, Qdrant implementation pending |
| G-12: compatibility aliases obscured canonical ownership | Gen2 has no dependency on Gen1 search modules and one navigable business composition root | Build/import firewalls prove independence. The complete composition root is not yet real; backend/materialization policies remain discoverable only through docs and separate files. | **Deviation** — see D-02 |
| G-13: `inferredFilters` mixed explicit and inferred facts | `appliedFilters` retain typed provenance and suppressed filters retain typed reasons | `SourcedConstraint`, `AppliedFilter`, `SuppressedFilter`, precedence resolution and `SearchPlan` store one execution/provenance authority. Public response projection is later. | **Partial** |
| G-14: intent used display names as identity | Stable service/category codes cross persistence, document, intent, filters and facets | `ServiceCode`/`CategoryCode`, canonical fields, public decoders and intent actions use stable codes; names remain display/search text. | **Proven** |
| G-15: public API had only `limit`, with no sort/cursor/total | Typed filters/facets/sort/page request and versioned cursor; exact total and next cursor in ES result | Contract/core/ES slices implement the complete internal path and production BeautyQ compiler mismatch matrix. HTTP codecs/routes remain Gen1. | **Partial** — internal contract proven; cutover API later |
| G-16: gradual migration would preserve Gen1 seams and delay the target | Build Gen2 beside Gen1, then cut over once and delete Gen1 | Eight independent Gen2 modules exist; no Gen1 search import is present in Gen2 main source; Gen1 route remains live. | **Proven** |
| G-17: putting Gen2 in Gen1 modules would create hidden dependency | Separate sbt DAG plus executable module/import firewall | `build.sbt` and `SearchGen2ModuleFirewallSpec` pin project edges and source boundaries, including generic test sources. | **Proven** |
| G-18: one result type would misrepresent ES and Qdrant roles | ES baseline page and Qdrant candidate result remain different algebras | `BaselineSearchPage` is ES-specific; `CandidatePlan`/`CandidatePlanDecision` carry candidate-only work and no fake facet/group/page slots. Qdrant result is not implemented yet. | **Partial, structurally proven** |
| G-19: price is an interval, not `priceFrom` alone | Explicit interval-overlap filter/facet semantics | `PlannedConstraint.IntervalOverlap`, `FacetRequest.IntervalOverlap`, BeautyQ price policy and ES filters-aggregation compilation use the ADR rule. | **Proven for plan + ES** |
| G-20: proximity scoring, radius filtering and distance sort were conflated | Three independent typed geo operations; coordinates alone do nothing | `GeoProximitySignal`, `GeoDistanceFilter`, `PlannedSort.GeoDistance`, public geo-origin resolution and ES compilation are independent. | **Proven** |
| G-21: terms groups cannot produce exact UI carousels | Explicit group representative, metrics, precision and order, compiled by an ES-owned implementation | Generic `GroupRequest` validates the required shape, but BeautyQ policy is explicitly empty and ES rejects groups. | **Planned** — Brick 5D |
| G-22: snapshot/version claims lacked transactional consistency and stable identity | One consistent snapshot, canonical source/projected fingerprints and complete generation identity | Repeatable-read snapshot, canonical row/document fingerprints and complete ES generation identity exist. Their values are currently publicly forgeable/pairable outside their executable owners. | **Deviation** — see D-01 |
| G-23: eval history dominated serving code | Eval corpus/metrics/gates live in a downstream module and never activate serving | `beautyq-search-gen2-eval` depends on wiring; firewall prevents reverse dependency. It is still only a module marker. | **Proven structurally; implementation planned** |
| Later principle: business authors should state only legitimate differences | Selectors/types/order derive tautological field/document evidence; domain code owns joins, names, intent, semantics and backend choices | `searchFields`, `completeDocument`, generic materializer/plan/ES kernels and neutral fixtures remove large mechanical folds. Public registries and the full root still have gaps. | **Partial** — see D-02/D-03 |
| Later principle: docs, traces, fingerprints and ledgers derive from executable owners | No reviewer view becomes a parallel runtime authority | Structure/plan/candidate traces and ES fingerprints are derived. The technical spec currently presents a target root as current, and materialization fingerprints can still be supplied independently. | **Deviation** — see D-01/D-04 |

## 2. Ownership map

The table names the one decision owner. A compiler may consume a decision; that does not make it a
second owner.

| Decision | Canonical owner | Derived mechanics/consumers | Audit result |
|---|---|---|---|
| Normalized catalog read topology | `BeautyQSearchDeclarations.catalog.topology` | Repo catalog interpreter and reviewer structure | Clear |
| SQL snapshot transaction and selected tables | `BeautyQSearchSnapshotSource.Postgres` | `SearchSnapshotSource` orchestration | Clear, intentionally domain/materialization-owned |
| Canonical source-row field selection | `BeautyQSnapshotCanonicalRows` | `CanonicalSnapshot` traversal and `BeautyQSnapshotFingerprint` hashing | Clear, but final aggregate construction is open (D-01) |
| Projection joins, cross-owner invariants and text composition | `BeautyQVariantProjectionGen2` | `SearchMaterializer` orchestration | Clear and correctly domain-owned |
| Search field identity/path/kind/presence/capabilities/order | `BeautyQSearchDeclarations.variants.Fields` | document declaration, request/intent/plan/backend policies, mapping/source/fingerprints | Clear |
| Document identity and exhaustive product coverage | `Fields.document` from `completeDocument(variantId)` | declaration structure, projected fingerprint and backend compilers | Clear; value-level identity uniqueness is not generic yet (D-01) |
| Public filter names/operators/decoding | `BeautyQPublicFilterRegistry` declaration inventory | generic `PublicInputRegistry` lookup/operator gate | Clear, but uniqueness/completeness assumptions are unchecked (D-03) |
| Public sort names/decoding | `BeautyQPublicSortRegistry` declaration inventory | generic `PublicSortRegistry` | Clear, same uniqueness gap (D-03) |
| Intent aliases, contextual rules and stable semantic labels | `BeautyQIntentVocabulary.sourceRules` | generic intent matcher and BeautyQ parser | Clear |
| Constraint precedence, geo origin, facets, groups, mode and default browse | `BeautyQSearchPlanPolicy` plus `variants.plan.contractVersion` | core resolution/assembly, BeautyQ compiler and traces | Clear |
| Candidate semantic parts, gate order and ineligibility reasons | `BeautyQSemanticCandidatePolicy` and its typed enums | generic one-pass candidate evaluation, compiler-bound result and trace | Clear |
| Generic plan validity and precedence mechanics | `SearchPlan` validators and `SearchPlanCompilationKernel` | every domain compiler | Clear and neutral |
| Cursor-compatible value projection and envelope | `PlanContractFingerprint`, `CanonicalPlanView`, `SearchCursorEnvelope` | BeautyQ plan binding and ES pagination | Clear; deliberately baseline-execution scoped |
| ES analyzer/ranking/geo/total/default-sort choices | `BeautyQElasticsearchPolicy.value` | mapping, source, request, response, generation and cursor contract fingerprint | Clear, but not reachable from the documented canonical root (D-02) |
| ES physical names and operational batching | `BeautyQSearchGen2ResourceNames` and BeautyQ baseline composition | generic lifecycle | Clear, but not root-visible (D-02) |
| ES generation naming, metadata, activation and authorization mechanics | `ElasticsearchGenerationNaming`, metadata codec, lifecycle and authorization | BeautyQ composition | Clear and domain-neutral |
| ES response total/facet/page semantics | `ElasticsearchSearchResponseDecoder` over the compiled request/policy | baseline service | Clear for pre-group result |
| ES group/carousel business policy | Future non-empty `BeautyQSearchPlanPolicy.groups` | Brick 5D compiler/decoder/projection | Unimplemented, owner already named |
| Qdrant payload/retrieval policy and candidate result | Brick 6 BeautyQ Qdrant policy + generic Qdrant compiler | supplement orchestration | Unimplemented, module boundary exists |
| Public response/provenance and baseline-plus-supplement composition | Bricks 6/8 BeautyQ orchestration/response policy | HTTP projection in Brick 9 | Unimplemented |
| Corpora, metrics and gates | `beautyq-search-gen2-eval` | cutover evidence only | Boundary clear, implementation later |

The ownership problem is navigational, not that ES currently has two executable policy owners. The
contract-layer `BeautyQSearchDeclarations` cannot import materialization or backend modules without
reversing the DAG. Therefore the repository needs a higher, wiring-owned **composition facade** that
references the existing exact values. It must not copy policies into the contract module or invent a
descriptive backend section.

## 3. Invariant ledger

Enforcement levels:

- **Compile-time** — an ill-typed program cannot be built;
- **Static validation** — a source declaration is validated once during construction/object
  initialization; `unsafeFrom` is acceptable only here;
- **Runtime validation** — untrusted request/backend data returns typed errors;
- **Trusted boundary** — a closed result proves that validation/compilation happened and linked facts
  cannot be replaced independently.

| Invariant | Enforcement | Trusted output / evidence | Remaining limitation |
|---|---|---|---|
| A field belongs to one document/value type | Compile-time typed selectors/handles | `SearchField[Document, Value]`; compile-negative field specs | None for supported scalar shapes |
| Direct selectors are field selections, not arbitrary expressions | Compile-time macro/selector derivation | `SearchFieldDeclarationsSpec` compile-negative expressions | G-3 multi-value shape unsupported |
| String keyword/text meaning is explicit | Compile-time authoring method choice | `.keyword` / `.text`; neutral and BeautyQ declarations | Legitimate business choice remains explicit |
| Non-String kind/path/type/default ID derive from the selector/type | Compile-time + declaration construction | field structures and structure renderer | None for supported kinds |
| Field capabilities are compatible with field kind | Static typed builder surface plus declaration validation | `SearchDocumentDeclaration` or typed construction failure | Backend-specific acceptance still belongs to policy |
| Document identity belongs to the declaration and product coverage is exhaustive | Static `completeDocument` validation | closed `SearchDocumentDeclaration` | Exhaustiveness is object-init validation, not compile-time; G-8 remains |
| Dynamic family codes are unique and fields derive from one definition inventory | Static declaration validation | closed `DynamicFieldFamily` | public dynamic specs can still silently omit a missing field (D-03) |
| One SQL snapshot observes one database state | Runtime effect boundary | `BeautyQSearchSnapshotSource.Postgres` repeatable-read transaction | Full proof requires Postgres/resource execution in later composition |
| Snapshot value and content fingerprint were produced together | Intended trusted boundary | `VersionedSnapshot` | Public case-class construction permits arbitrary pairing (D-01) |
| Projected documents and fingerprint were produced together | Intended trusted boundary | `MaterializedSearchDocuments` | Public case-class construction bypasses `SearchMaterializer` (D-01) |
| Projected document identities are unique before backend ingestion | Domain runtime validation in BeautyQ projection | `DuplicateEntityId` errors | Generic materializer/backend contract does not enforce it (D-01) |
| Public names are independent of storage paths | Explicit domain policy | `PublicFieldName`, registries and decoding tests | Correct |
| Public registry names are unique | Convention only | BeautyQ inventory tests | Generic registries use last-wins `toMap` (D-03) |
| Untrusted public input becomes typed clauses once | Runtime validation | closed `ValidatedBeautySearchRequestGen2` | Correct after registry declaration validity is trusted |
| Constraint precedence has exactly two distinct typed sources | Static policy validation + closed tiers | `ConstraintPrecedence`, private `ConstraintPriorityTiers` | Supported shape deliberately limited to two tiers |
| Facet declarations are valid and uniquely identified | Static validation | closed `FacetPlanRegistry`; indexed duplicate errors | Correct |
| Plan constraints/sorts/facets/groups satisfy capabilities | Runtime validation | `SearchPlan.validate`; `PreparedSearchPlanCompilation` | Raw `SearchPlan` is constructible by design; backends accept only bound plans |
| Applied constraints and provenance cannot drift | Structural derived view | `appliedFilters` is stored; `hardConstraints` derives | Correct |
| Candidate gate outcomes and decision come from one pass | Trusted boundary | closed `CandidateEvaluation` and compiler-owned BeautyQ aggregate | Correct |
| Plan and cursor identity/backend state are bound | Trusted boundary | closed `BoundSearchPlan` | Cursor state is untrusted, not authenticated; accepted limitation below |
| Contract fingerprint is declaration/policy-derived | Compile-time visibility + trusted factory | opaque `ContractFingerprint`, plan fingerprint tests | Correct for current ES baseline contribution |
| Prepared ES request has no executable target | Trusted boundary | closed prepared request carries Active/Pinned requirement | Correct |
| Cursor generation reference cannot select an arbitrary target | Runtime validation + trusted boundary | lifecycle resolution and closed authorized request | Correct; target authorization is not cursor authenticity |
| ES generation reuse means complete identity/mapping/count equality | Runtime validation | lifecycle resolved generation and typed errors | Real-cluster result reproduced, retained in D-05 and owner-accepted |
| ES hit/facet/page output matches exactly the compiled request | Runtime validation + trusted boundary | closed `BaselineSearchPage` | Groups are deliberately unsupported before 5D |
| Partial/malformed ES responses cannot become trusted pages | Runtime validation | typed decoder errors and bounded hit window | Correct in focused fixtures |
| Generic modules remain domain-free | Structural build/import proof | module firewall + neutral fixtures | Qdrant generic proof starts in Brick 6 |
| Eval cannot activate serving | Module DAG/firewall | downstream eval marker/module | Runtime eval work not implemented |

This ledger deliberately does not claim that every raw algebra value is impossible to construct.
`SearchPlan` and several untrusted DTOs remain ordinary values; safety comes from validators and from
backends accepting only their closed trusted aggregates. That is a valid totality model. The violation is
when a type itself claims linked/trusted facts while allowing those facts to be supplied independently.

## 4. Deviation register

### D-01 — Materialization trust aggregates are forgeable

Severity: **P1 architectural**

Decision: **fix before 5D**

`ContentFingerprint`, `ProjectedDocumentsFingerprint`, `VersionedSnapshot` and
`MaterializedSearchDocuments` are public case classes, while `ElasticsearchGenerationCompiler` trusts a
supplied materialized value when deriving reusable physical generation identity. Production BeautyQ is
well behaved: its snapshot source computes the source fingerprint over the exact snapshot inside the
transaction, and `SearchMaterializer` applies the domain fingerprint callback to the exact projected
vector. The gap is structural: callers can bypass both executable owners by directly constructing or
copying the linked result values. Tests deliberately use that escape hatch and prove the downstream
compiler preserves supplied fingerprints.

This is the same invariant class already rejected for candidate evaluation and compiled plan results: a
value claims several facts came from one canonical computation, but its API permits the facts to be
paired independently or the computation to be bypassed.

Required correction:

- make `VersionedSnapshot` and `MaterializedSearchDocuments` closed, read-only framework results;
- provide one framework-owned capture path that applies the domain's canonical source-row fingerprint
  callback to the exact snapshot value;
- retain `SearchMaterializer`'s existing same-vector fingerprint callback, but make it the only
  construction path for the linked projected result;
- reject duplicate canonical document identities before a backend compiler sees the materialization;
- retain domain ownership of source-row field selection, joins, invariants, text composition and explicit
  projection-format versions;
- prove the kernel with a neutral fixture and add compile-negative construction/copy/subclass proofs.

Do not solve this by recomputing BeautyQ facts in Elasticsearch or by adding a second comparison hash.

### D-02 — The documented full business root is not the actual API

Severity: **P1 authoring/ownership**

Decision: **fix before 5D**

The actual contract root exposes catalog topology, Fields/document, request, intent, plan and candidate
policy. Materialization/projection, Elasticsearch policy/resource names and lifecycle composition live in
downstream modules and are not reachable from it. This is not a duplicate-owner defect: the existing
downstream policies are singular. It is an authoring/navigation defect plus a documentation contradiction,
because the technical specification says that a full root containing snapshot policy, validation,
response, backends and quality already exists.

The contract module must not reverse-depend on materialization/backend modules. Add one wiring-owned
composition facade that references the existing canonical values and becomes the business/reviewer entry
point. Keep the contract root as the backend-neutral declaration owner. The facade is navigation and
composition, not a compatibility alias and not a copied policy tree. Its generated structure must derive
from those same references.

Before 5D, the facade must at least expose:

```text
contract catalog/variant declarations
materialization snapshot/projection owners
Elasticsearch policy/resource/lifecycle composition
planned group-policy owner
```

Do not add typed `not implemented` facade branches for Qdrant, response or quality. The composition facade
must expose only executable values and grow additively; the implementation plan owns future branches, and
the technical specification must label them as target-only until they exist.

### D-03 — Public registries rely on silent declaration conventions

Severity: **P1 reusable authoring**

Decision: **fix before runtime integration**

`PublicInputRegistry` and `PublicSortRegistry` convert ordered declarations with `.toMap`, so duplicate
public names silently make the last declaration authoritative while reviewer inventories still contain
both. BeautyQ dynamic filter authoring uses `definitions.flatMap(fields.get(...))`, so a definition/field
drift silently removes a public contract entry.

These are plausible authoring mistakes with user-visible consequences, not speculative defense against
impossible Scala behavior. Introduce one validated static registry construction path with deterministic
duplicate positions and derive dynamic specs from the exact declared dynamic-family entries (or return a
static declaration error). Do not add per-request defensive scans.

### D-04 — Long-lived docs mix current truth, target truth and code walkthrough

Severity: **P1 documentation contract**

Decision: **fix before 5D, then keep lean**

At this baseline the implementation plan is about 1,398 lines and the technical specification about
1,797 lines. The technical spec presents the full root as current even though multiple branches do not
exist; both documents repeat method/type/test detail that changes with small code edits.

Required correction:

- mark each target-only backend/runtime section explicitly;
- keep the implementation plan's live state, open decisions, sequence and gates; delete completed
  implementation narration already owned by Git/tests;
- keep architecture, supported shapes and trust boundaries in the technical spec; remove method-by-method
  walkthroughs that merely restate source;
- keep this audit as the promise/decision checkpoint only; do not copy its matrices elsewhere.

### D-05 — Brick 5C live proof: reproduced, retained and owner-accepted

Severity: **P2 evidence closeout**

Decision: **resolved and accepted; the owner-run full-suite record below is the acceptance baseline**

The committed 5C report records 222 non-resource focused tests and a canceled
`ElasticsearchLocalResourceSpec` because `localhost:9200` was unavailable. Pure/scripted tests strongly
prove the state machine and contracts, but they cannot prove Elasticsearch accepts the emitted mapping,
bulk, metadata, alias, exact-total, facet and cursor behavior together.

The independent audit reports a later green run against Elasticsearch 8.14.3 with 257 documents, exact
totals/facets and disjoint cursor pages. The test source exactly contains that scenario. The requested
unchanged rerun was subsequently executed and its raw result is retained here:

```text
rerun 2026-07-16T20:20:15Z @ 87f47796 (working tree clean for search-gen2-elasticsearch)
resource: docker.elastic.co/elasticsearch/elasticsearch:8.14.3, single-node, localhost:9200
command: sbt 'searchGen2Elasticsearch/testOnly leaderboard.search.gen2.elasticsearch.ElasticsearchLocalResourceSpec'
[info] ElasticsearchLocalResourceSpec:
[info] - should build, activate, search, facet and continue by cursor through the Gen2 lifecycle
[info] Run completed in 898 milliseconds.
[info] Total number of tests run: 1
[info] Tests: succeeded 1, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
container removed after the run; endpoint confirmed unreachable again
```

The repository owner additionally ran the complete test suite by hand before either audit pass and
explicitly accepted it as the greenness baseline; the raw result is retained here:

```text
owner-run full suite, completed Jul 16, 2026, 9:42:45 PM (before both audit passes)
[info] Run completed in 2 minutes, 17 seconds.
[info] Total number of tests run: 2097
[info] Suites: completed 248, aborted 0
[info] Tests: succeeded 2097, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
[success] Total time: 189 s (0:03:09.0)
```

Zero cancellations in that owner run means `ElasticsearchLocalResourceSpec` itself executed and passed
against a live local Elasticsearch inside it. The communication-proof question is therefore closed three
times over — the owner's full-suite run plus two recorded audit reproductions of the unchanged spec — and
the acceptance step is complete: the owner explicitly accepted this record. Do not change product code to
manufacture a different proof.

### D-06 — Only one production domain exists

Severity: **P2 generality confidence**

Decision: **defer with explicit risk**

`LibraryTracerDomain` and the ES `BookDocument` fixture are valuable neutral proofs and have already found
real genericity bugs. They prove supported shapes, not the complete cost of onboarding a second production
domain with its own persistence, intent and policy.

Do not block BeautyQ on an invented second domain. Do not call the framework universally generic either.
Before promising a stable public framework API outside this repository, onboard one unrelated real domain
or retain the right to change the authoring surface.

### D-07 — Known representation/initialization gaps remain

Severity: **P2 known gaps**

Decision: **defer with explicit risk**

- G-3: multi-valued searchable/filterable fields have no declared semantics or backend contract;
- G-8: a nested `Fields` object can still trigger JVM initialization re-entry if it captures a sibling
  root value while the root aliases `Fields.document`.

The implementation plan remains the live owner of these gaps. G-3 requires a real domain need and neutral
proof. G-8 must be fixed before recommending unsupported sibling capture in onboarding; until then the
documented safe shape is mandatory.

### D-08 — Retention is a non-executable authoring choice

Severity: **P2 unnecessary complexity**

Decision: **remove before 5D**

`ElasticsearchGenerationRetentionPolicy` has exactly one case, `KeepAll`. Lifecycle configuration stores
it, but lifecycle behavior never reads it. BeautyQ therefore supplies a value that cannot affect behavior.

Pinned cursors currently require old validated generations to remain available, so `KeepAll` is the fixed
behavior. Remove the enum/config argument and state that behavior directly. Reintroduce a validated policy
only with a real second behavior, cursor-safety semantics and executable tests.

### D-09 — Small 5C contract and operational gaps

Severity: **P2 mixed closeout/runtime**

Decision: **split by owning boundary**

Source re-check of the independent audit confirmed five additional issues:

- before 5D, correct the first `DocumentCountMismatch`: before live count is read it currently reports
  metadata as both `metadata` and `live`;
- before 5D, describe field order exactly as static declaration order followed by dynamic-family order;
- before runtime integration, define active-generation resolution caching/invalidation instead of three
  Elasticsearch round trips on every first-page request;
- before runtime integration, either restrict analyzer support to known built-ins or add an executable
  settings/availability owner; arbitrary names are not currently validated against the live cluster;
- in Brick 8, map cursor protocol/identity failures to an explicit restart-pagination response.

These do not reopen 5C's lifecycle architecture. The count and wording items are small correctness/docs
closeout; the others belong to serving composition and HTTP policy.

## 5. Decision list and next sequence

### Fix before Brick 5D

1. **Materialization trust closure**
   - closed snapshot/materialized aggregates;
   - same-value fingerprint computation owned by the framework orchestration;
   - generic duplicate document-identity rejection;
   - neutral and BeautyQ proofs; no backend-specific workaround.
2. **Canonical composition root**
   - one wiring-owned navigation/composition facade over contract, materialization and ES values;
   - no copied policies or reverse module dependency;
   - generated reviewer tree and onboarding read order from that facade.
3. **Documentation truth cleanup**
   - distinguish current from target branches;
   - compact plan/spec duplication;
   - update the audit deviations instead of deleting acceptance gates.
4. **Remove ceremonial retention configuration**
   - fixed KeepAll behavior remains until a real alternative is designed.
5. **Close the two small 5C accuracy items**
   - correct the pre-live-count diagnostic;
   - state static-then-dynamic field ordering precisely.
6. **Retain or explicitly accept the reported Elasticsearch communication proof** — *done and
   owner-accepted: D-05 retains the owner's full-suite run (2,097/2,097, 0 canceled) and two reproduced
   runs of the unchanged spec.*

Only then activate Brick 5D. Brick 5D must consume the canonical group declarations and extend the same
composition root; it must not create a separate carousel policy island.

Recommended reviewable delivery order:

1. one materialization-trust commit closing D-01, including all neutral/BeautyQ/ES fixture adaptations;
2. one authoring/composition closeout closing D-02/D-08/D-09 and correcting current-vs-target
   documentation;
3. one verification closeout retaining or explicitly accepting the unchanged local Elasticsearch result.

Do not mix Brick 5D group semantics into these corrections.

### Fix before runtime integration/cutover

- validated public filter/sort registry construction and total dynamic inventory derivation;
- effect-safe suspension/lifecycle integration around the synchronous ES transport;
- repository source → materializer → generation → lifecycle readiness as one application composition;
- public HTTP request/response projection proving the facet/filter/applied-provenance/cursor loop;
- active-generation resolution caching/invalidation;
- built-in-only analyzer support or an executable custom-analyzer settings owner;
- explicit operational decision on cursor tamper evidence, rate/size limits and restart-pagination error
  mapping;
- all resource-backed Postgres/Elasticsearch/Qdrant communication proofs and production graph/readiness
  verification.

### Accept intentionally

- normalized catalog, denormalized search document and user search intent/plan are different semantic
  representations, not duplication to collapse;
- SQL selection, joins, invariants and search-text composition remain explicit domain code;
- static `unsafeFrom` constructors are acceptable for source policy when a broken declaration fails at
  initialization and the validated constructor remains available;
- raw request/plan/backend DTOs may be constructible when every execution boundary accepts only a validated
  closed aggregate;
- the declaration DSL's private initialization-time buffers are an implementation detail; public results
  are immutable snapshots and no second policy abstraction is needed;
- Elasticsearch owns the public baseline cursor. Its policy contribution belongs in that cursor/generation
  compatibility identity; Qdrant candidate policy must not be added merely to invalidate ES page cursors;
- the cursor envelope is currently compatibility-checked and lifecycle-authorized, not authenticated.
  Mutating backend `search_after` state must never authorize another physical target, but tamper evidence is
  a separate HTTP threat-model decision before cutover.

### Defer with explicit risk

- G-3 multi-valued fields until semantics are source-confirmed;
- G-8 constructive prevention while the safe authoring shape remains documented;
- a real second production domain; neutral fixtures are sufficient for current internal extraction, not
  proof of a frozen external framework;
- incremental CDC/outbox updates; immutable full generations are the accepted initial correctness model.

### Remove as unnecessary complexity

- the one-case, behaviorally unused retention-policy parameter;
- completed-brick narration, repeated focused-test counts and method-by-method source paraphrases in
  long-lived architecture docs;
- any future placeholder policy/flag that offers no executable alternative.

## Audit acceptance gate

This audit closes only when:

1. D-01, D-02, D-04, D-05, D-08 and the pre-5D part of D-09 are resolved and the implementation plan
   records the exact result;
2. the ownership map contains no decision with two executable owners;
3. every trusted aggregate in the materialization→generation→lifecycle chain has a closed construction
   path or is explicitly classified as untrusted input;
4. the technical specification labels target-only Qdrant/group/response/runtime branches honestly;
5. the Elasticsearch local communication result is accepted — **satisfied and owner-accepted**: D-05
   retains the owner's full-suite run (2,097/2,097, 0 canceled, 2026-07-16) plus two reproduced
   raw-output runs of the unchanged spec against Elasticsearch 8.14.3;
6. focused module/firewall checks pass, while full-repository verification remains coordinator/user-owned.

The next coordinator should start from this gate and the implementation plan, not repeat the Gen1 audit
or infer acceptance from focused test counts alone.
