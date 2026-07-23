# BeautyQ Search Framework Gen2 — implementation plan

Status: **implementation completed**
Planning unit: one reviewable brick, usually one commit or a small cohesive commit series
Migration: completed — the final cutover removed all Gen1 search projects and runtime owners.

Normative companion documents:

- [technical specification](BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md) for end-state types, data paths,
  backend roles and Definition of Done;
- [semantics ADR](BEAUTYQ_SEARCH_GEN2_SEMANTICS_ADR.md) for accepted business/search decisions;
- [evidence-backed Gen1 review](BEAUTYQ_SEARCH_GEN2_REVIEW.md) for the source-confirmed problems Gen2
  addresses;
- [domain authoring principles](../search/DOMAIN_AUTHORING_PRINCIPLES.md) for the repository-wide
  business-policy, reuse, and executable-source-of-truth acceptance contract.

## Current implementation state

This section is the single authoritative live status owner for Gen2 implementation. Update it in
the same commit that starts, completes, blocks, or materially re-scopes a brick. Root and docs-index
summaries must remain short and point here.

- **Overall:** all planned bricks implemented. The cutover is complete and user-verified: 380/380 tests passed across 55 suites.
- **Live status:** all bricks 0–8B implemented. Brick 9 executed the atomic cutover:
  native Gen2 request/response ownership is served at `POST /beauty-search`;
  `/beauty-search-gen2` and all Gen1 search project/runtime owners are absent.
  The `BeautyQGen2EmbeddingClient` binds the response model identity to the
  requested model; a missing, non-string or mismatched `model` field is a
  typed `InvalidResult(QdrantEmbeddingError.ModelMismatch)` and never degrades
  to a connectivity failure. The single test-only `leaderboard-app-shell
  test -> beautyq-search-gen2-eval test` build edge plus the
  `SearchGen2ModuleFirewallSpec` assertion keeps the eval module out of
  production serving. Coordinator or user verified the whole repository
  green.
- **Completed:** 5D owns exact Elasticsearch groups and BeautyQ carousel projection. 6A owns the pure
  Qdrant policy, generation artifacts, candidate request compiler and candidate-only response decoder.
  6B-A owns the neutral JSON/HTTP transport, thin Elasticsearch adapter and exact Qdrant wire client.
  7A owns generic append-only supplement selection, lifecycle-bound Elasticsearch baseline membership,
  bound baseline authorization, and construction boundaries. 7B owns BeautyQ orchestration with
  owner-private outcomes (`Ineligible`, `Evaluated`, `Failed`), typed `BeautyQSupplementStatus`, exact
  cause preservation in `PipelineFailureDisposition`, policy-owned readiness results with derived
  `supplementReady`, and the production-path proofs described below. 8A owns the native
  app-shell graph, the complete HTTP projection and the real embedding communication. 8B owns the
  readiness-aware cutover gate, the executable four-fixture runner, the typed `BeautyQGen1SearchDeletionInventory`
  and the deterministic machine-readable report artifacts.
- **Materialization boundary decision:** the canonical production source and materializer compute their
  fingerprints with the values they return. No untrusted production caller supplies those aggregates,
  so no additional defensive construction framework is justified.
- **Canonical reading path:** start at wiring-owned `BeautyQSearchGen2`. Its `contract`, `input`,
  `materialization`, `plan`, `elasticsearch`, `qdrant` and `supplement` branches are direct references
  to the executable owners; the facade owns no copied policy.

### Audit-derived acceptance gate for the completed cutover

This is the local application of the normative domain-authoring principles, not another audit ledger:

1. **Ownership:** every persisted identity, authorized target and composed result has one executable
   owner; facade, trace, fingerprint and docs remain derived views.
2. **Totality at real boundaries:** validate untrusted HTTP, JSON and persisted resource state with
   typed errors; do not wrap ordinary internal immutable values against impossible Scala callers.
3. **Semantic integrity:** Elasticsearch remains the full-result owner and Qdrant remains candidate-only;
   no transport or lifecycle path may drop a declared constraint or invent totals/facets/groups.
4. **Authoring value and reuse:** BeautyQ declares only resource names, embedding/retrieval choices and
   hydration policy. The framework owns repeated transport, lifecycle and execution mechanics, proved
   with neutral fixtures rather than a renamed BeautyQ copy.
5. **Proof and documentation honesty:** scripted fixtures use the real wire shape, communication tests
   prove destructive/resource behavior, and this plan reports live status while the technical spec owns
   exact API and supported shapes.

The post-resource review found no reason to reopen the generic plan, backend or supplement kernels.
Lifecycle-bound baseline/membership execution, owner-private backend results, one orchestrator result,
derived eval evidence and projector-owned public DTOs are implemented. Brick 8 closed four
app/evidence seams:

- compose the existing bootstrap, activation, application, readiness and runtime owners into one
  native app-shell module; the current `BeautySearchGen2PluginModules.api` binds only the route adapter;
- make the HTTP JSON encoder expose the projector's complete derived contract, including
  `totalRelation`, applied-filter constraints and suppressed-filter constraint/provenance/reason;
- bind `BeautyQGen2EmbeddingClient` from the existing `LlamaCppEmbeddingClientConfig` and prove the
  real `/v1/embeddings` response/dimension path without creating a second endpoint configuration;
- execute `BeautyQCutoverQueryFixture.required` through the real application/projector graph and derive
  all observations from those results; synthetic observations remain unit tests, not cutover evidence.

The initial application composition performs lifecycle authorization from persisted state for each
request. A process-local active-generation cache with only local activation invalidation is forbidden:
another process may switch the alias. Such an optimization requires a separately proven coherence
contract and is not part of the Gen2 delivery.

The implemented application path serves the native Gen2 request/response contract at `POST /beauty-search`;
`/beauty-search-gen2` is absent. Elasticsearch blocking
is confined to the runtime effect boundary. Baseline-only readiness executes the baseline path without
candidate mechanics, while degradable supplement failures remain successful baseline-preserving
responses. Evaluation emits a machine-readable derived gate. The
response DTO constructors are owner-private to the projector; application/http code receives only
read-only aliases. The cutover gate requires the four entries of the typed
`BeautyQCutoverQueryFixture.required` vector, each executed through the
application and projector before evidence is derived:
`q_broad_006_ready_append_probe`, `manicure_real_route_probe`,
`q_broad_001_widened_probe`, and `q_broad_003_widened_probe`; it reports typed metrics for improvement, unchanged behavior, worsening,
baseline loss/order/component changes and append-budget violations.

### Accepted initial limits, not live abstraction debt

- Materialization aggregates remain ordinary internal immutable values until a real untrusted caller is
  introduced.
- Multi-valued search fields, a second production domain, CDC, authenticated cursors and arbitrary custom
  analyzers are outside the initial Gen2 scope.
- `Fields` must keep its dependencies local or outside its enclosing root; sibling capture is an
  unsupported authoring shape, not a planned defensive wrapper project.
- An application-level rate limiter is not a Gen2 completion gate: the repository has no accepted
  caller-identity or quota policy. Deployment ingress owns rate limiting until such policy exists; the
  cutover must not invent a search-specific limiter or arbitrary numeric quota.

Every brick that adds domain policy or reusable mechanics must satisfy the
[Domain Authoring Principles](../search/DOMAIN_AUTHORING_PRINCIPLES.md). A brick is not
framework-complete merely because BeautyQ works: domain differences must remain explicit, reusable
mechanics must be reused or extracted, neutral proof must exist, and generated views must derive
from one executable declaration. If the current implementation still has an extraction gap, name its
owner and next boundary in this plan instead of claiming the gap is already generic.

## Coordinator starting model

The next coordinator must preserve this distinction:

1. `catalog` describes normalized persisted business facts and source topology;
2. `variants.document` is an explicit denormalized read model built from a consistent snapshot;
3. `variants.intent` and `SearchPlan` describe requested operations over document fields and are not
   another stored copy of the catalog.

`BeautyQSearchDeclarations` is the backend-neutral contract root with two top-level branches:

```text
BeautyQSearchDeclarations
├── catalog       normalized topology
└── variants      document-centred search contract plus executable request/intent inventories
```

The shared bricks across the boundary are stable typed values (`ServiceCode`, `CategoryCode`, IDs,
money, duration and geo values), not normalized entity case classes or paths. Explicit projection
connects snapshot entities to `VariantSearchDocumentGen2`. Stable codes connect catalog identity,
document values and intent vocabulary.

Within the search branch, `BeautyQSearchDeclarations.variants.Fields` is the semantic hub. The exact
typed handles declared there are reused by document, constraints, facets, groups, sorts, ES policy,
Qdrant payload/filter policy and response descriptions. No downstream section recreates a field or
derives a public query name from `SearchField.path`.

The 4D+4E inbound boundary is visible under the same branch: `variants.request` exposes
the authoritative public filter/sort/facet registries and `variants.intent` exposes the validated
stable-code vocabulary. `PublicFilterDeclaration`/`PublicFilterRegistry` now own standard scalar,
collection, range, interval and geo decoding in `search-gen2-contract`; BeautyQ declares only its
ordered public names, handles and intentional narrowing. The longest-match/contextual/overlay engine
is already generic.
BeautyQ declares public names, typed field policy, stable actions, aliases and domain translation/label policy. The validated
request assigns `ExplicitUi`/`FacetSelection` provenance on the server; the parser emits `ParsedHard`
constraints, canonical semantic labels and the one `GeoProximitySignal`. Noise without contextual
requirements remains in the independent matching phase; the Gen2-only NearUser rule is an explicit
semantic overlay with a hair-removal exclusion. Neither branch constructs a `SearchPlan`, resolves
geo origins, applies precedence or talks to a backend.

The contract root intentionally stops at the contract-module boundary. Wiring-owned
`BeautyQSearchGen2` is the navigation facade across request/intent, plan/candidate compilation,
materialization, Elasticsearch and Qdrant; it contains only direct references and does not reverse the DAG.

The implementation must not begin by rebuilding catalog topology as an isolated vertical. Catalog
cannot determine document joins, normalization, text/embedding composition, price-overlap semantics,
geo operations, public names or backend capabilities. The visible catalog branch is added early via
the neutral `repo-core` algebra, while the first search DSL establishes the document/field kernel.

## Delivery closeout

Bricks 0–9 are complete; the final cutover removed all Gen1 search modules.

- The [technical specification](BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md) owns current architecture and supported shapes.
- [NEW_DOMAIN_ONBOARDING.md](../search/NEW_DOMAIN_ONBOARDING.md) owns practical authoring.
- [BEAUTYQ_SEARCH_GEN2_REVIEW.md](BEAUTYQ_SEARCH_GEN2_REVIEW.md) owns historical Gen1 findings.
- Git history owns per-brick chronology and rollback history.
- There is no active migration brick or next Gen2 implementation sequence.
