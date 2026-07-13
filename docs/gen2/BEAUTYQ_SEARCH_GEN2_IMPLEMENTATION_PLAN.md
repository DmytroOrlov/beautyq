# BeautyQ Search Framework Gen2 — incremental implementation plan

Status: proposed  
Planning unit: one reviewable commit or small commit series per iteration  
Rule: each iteration must be independently testable and revertible.

## Delivery strategy

The migration follows vertical slices. A slice introduces one missing executable boundary, proves it against V1, and only then switches ownership. Avoid broad “rename/move everything” changes.

Each iteration below lists:

- purpose;
- allowed diff;
- forbidden diff;
- proof;
- rollback.

## Iteration 0 — Record semantic decisions and freeze the baseline

### Purpose

Turn implicit semantics into explicit decisions before new abstractions encode the wrong behavior.

### Diff

Add:

- this review;
- Gen2 technical specification;
- Gen2 plan;
- ADR covering price/range/facet/geo/filter precedence;
- characterization tests for current V1 request/response JSON and representative results.

### Forbidden

- runtime changes;
- route changes;
- alias deletion;
- payload changes.

### Proof

- current focused tests pass;
- golden V1 fixtures checked in;
- ADR has no unresolved P0 semantic question.

### Rollback

Documentation/test-only revert.

## Iteration 1 — Introduce generic `SearchPlan` and `BackendSearchResult`

### Purpose

Create the missing common execution boundary without changing serving.

### Diff

In `search-contract-core` / `search-core` add:

- explicit range-bound types;
- planned constraint algebra;
- facet/group request/result IDs;
- `SearchPlan`;
- `BackendSearchResult`;
- validation errors.

Add pure tests using a non-BeautyQ sample document.

### Forbidden

- ES/Qdrant request JSON changes;
- BeautyQ route changes;
- new builder for the whole domain.

### Proof

- generic modules contain no BeautyQ names;
- algebra can represent all current BeautyQ constraints and response sections;
- no V1 production source depends on the new types yet.

### Expected diff shape

```text
search-contract-core/src/main/.../plan/*          new
search-core/src/main/.../plan/*                   new
search-*/src/test/...                             new tests
```

### Rollback

Delete additive types/tests.

## Iteration 2 — Compile current BeautyQ input into `SearchPlan`

### Purpose

Unify parser constraints, future structured filters and backend input.

### Diff

Add BeautyQ plan compiler:

```text
UserSearchInput + ParsedSearchIntent -> SearchPlan
```

Initially structured filter list is empty. Preserve V1 behavior through parity tests.

Resolve and test:

- price semantics;
- range inclusivity;
- geo activation;
- duplicate/conflicting constraints.

### Forbidden

- ES backend switch;
- Qdrant payload change;
- API JSON change.

### Proof

For the checked-in query corpus, generated plan snapshots are deterministic and V1 constraint resolution parity is explicit.

### Rollback

Keep V1 direct constraint path; remove compiler usage.

## Iteration 3 — Decode full Elasticsearch result in shadow mode

### Purpose

Close G-01 without changing the user response yet.

### Diff

Extend `search-elasticsearch` with typed decoders for:

- total hits;
- terms facets;
- range facets;
- groups;
- diagnostics for missing/unknown aggregations.

Compile aggregation names from stable typed IDs rather than normalized path strings alone.

In the BeautyQ ES backend:

- decode `BackendSearchResult`;
- still build the V1 response with the old assembler;
- compare decoded aggregate counts with old in-memory counts in tests/log-free test hooks.

### Forbidden

- production response ownership switch;
- removal of old assembler facet/group code.

### Proof

- generic decoder tests;
- real ES integration test on boundary values;
- exact totals/facets/groups verified against fixture data;
- divergence report is understood.

### Expected diff shape

```text
search-elasticsearch/...ResponseInterpreter.scala  focused extension
search-contract-core/...BackendSearchResult.scala  additive use
beautyq-search-wiring/...Elasticsearch...           shadow adapter
focused tests only
```

### Rollback

Stop constructing/reading the shadow result.

## Iteration 4 — Switch ES facets/groups/totals to backend-owned results

### Purpose

Make Elasticsearch the actual owner of the data it already computes.

### Diff

- `SearchResponseAssembler` accepts backend facet/group/total data;
- remove ES-path in-memory facet/group recounting;
- retain in-memory backend implementation by producing the same generic result locally;
- add total/page metadata internally; V1 adapter may omit it if JSON is locked.

### Forbidden

- Qdrant serving change;
- public structured filters;
- alias cleanup.

### Proof

- route parity for result IDs and existing JSON;
- facet boundary tests;
- counts no longer change when matching hits exceed 256;
- ES request no longer contains unused aggregations.

### Rollback

Feature flag or module binding selects old ES response adapter.

## Iteration 5 — Add structured filters behind a V2 internal model

### Purpose

Complete the facet/filter round trip and make public query field names executable.

### Diff

- add `BeautySearchRequestV2` and public filter codecs;
- add V1-to-V2 adapter;
- compile UI filters + parsed text into `SearchPlan` with documented precedence;
- introduce internal `appliedFilters` with origin;
- preserve V1 `inferredFilters` JSON through adapter.

Option A: expose `/beauty-search/v2`.  
Option B: keep endpoint unchanged and add optional fields only if compatibility policy permits.  
Choose in ADR before implementation.

### Forbidden

- Qdrant activation;
- removal of V1 codecs;
- simultaneous rename of response JSON fields.

### Proof

- facet value -> request filter -> plan -> ES filter -> applied filter round trip;
- invalid field/operator/value errors are stable;
- explicit UI filter precedence tests;
- V1 requests remain byte-compatible at the JSON contract level.

### Rollback

Disable V2 endpoint/optional fields; V1 adapter remains.

## Iteration 6 — Make repository snapshot the index source

### Purpose

Ensure indexed documents reflect persisted repository state.

### Diff

- introduce `SearchSnapshotSource` and snapshot version metadata;
- wire managed-local startup order:
  1. seed insertion ready;
  2. repository/catalog snapshot load;
  3. projection;
  4. index build;
- keep direct seed projection temporarily only for parity tests;
- store source fingerprint in ES/Qdrant metadata.

### Forbidden

- CDC/outbox implementation;
- production automatic seeding;
- route behavior changes.

### Proof

- direct-seed and repository-loaded document parity on current seed;
- mutation-after-seed test proves index source observes repository data;
- missing/inconsistent repository entity fails projection before activation.

### Rollback

Module binding returns to seed-backed `BeautySearchReadyCatalogDocuments`.

## Iteration 7 — Introduce versioned ES index lifecycle

### Purpose

Replace delete-and-recreate bootstrap semantics with safe generation switching.

### Diff

- physical index name includes generation/fingerprint;
- stable alias is used by serving;
- build, validate, count-check, then atomically switch alias;
- previous generation retained for rollback policy;
- lifecycle metadata includes source version and contract fingerprint.

### Forbidden

- Qdrant lifecycle changes in the same diff;
- production CDC.

### Proof

- failed build leaves old alias active;
- successful build switches once;
- rollback switches to previous generation;
- serving never targets a partially built index.

### Rollback

Alias switch back to previous generation.

## Iteration 8 — Expand Qdrant payload and compile hard filters

### Purpose

Eliminate primary post-retrieval filtering and recall loss.

### Diff

- add typed Qdrant payload projection for filterable fields;
- create/validate payload indexes;
- compile supported `SearchPlan.hardConstraints` to Qdrant filter JSON;
- retain post-hydration validation as an assertion/defense;
- add oversampling policy.

### Forbidden

- default route change;
- supplement budget increase;
- score fusion/rerank.

### Proof

- Qdrant filter golden tests for every supported constraint;
- real-resource test where matching candidate lies outside unfiltered top-K but is retrieved with pushed-down filter;
- zero constraint violations after hydration.

### Rollback

Use old vector-only request and keep supplement disabled/previous gate.

## Iteration 9 — Run Qdrant supplement through the common plan

### Purpose

Make ES and Qdrant interpret the same request semantics.

### Diff

- semantic backend accepts `SearchPlan` rather than raw input/intent;
- remove duplicated constraint interpretation from supplement policy;
- retain top-1, append-only, prefix-preserving gate;
- provenance remains unchanged.

### Forbidden

- Qdrant-owned facets/groups;
- fallback;
- rerank;
- production/default activation change.

### Proof

- existing no-harm scorecard remains green;
- ES prefix/order and ES-owned components unchanged;
- all appended hits satisfy the same planned constraints.

### Rollback

Bind the previous semantic candidate adapter.

## Iteration 10 — Make the BeautyQ domain root executable and lossless

### Purpose

Finish the source-of-truth migration after runtime consumers exist.

### Diff

Create a canonical root that directly references executable sections:

```text
BeautyQSearchDomainV2.catalog
BeautyQSearchDomainV2.variants.document
BeautyQSearchDomainV2.variants.request
BeautyQSearchDomainV2.variants.response
BeautyQSearchDomainV2.variants.backends.elasticsearch
BeautyQSearchDomainV2.variants.backends.qdrant
BeautyQSearchDomainV2.variants.quality
```

Generate the descriptive documentation model from this root.

Deprecate, but do not yet delete:

- `BeautyQCatalogDeclaration`;
- `BeautyQVariantSearchDocumentContract`;
- top-level query-schema alias;
- manual lossy section adapters.

### Forbidden

- serving behavior change;
- alias deletion in the same diff.

### Proof

- every runtime interpreter receives sections reachable from the root;
- generated documentation contains all executable field/intent/response/backend policy facts;
- no manual projection drops semantics;
- fingerprint changes for every behavior-affecting field.

### Rollback

Existing facade aliases continue to delegate to V2 root.

## Iteration 11 — Remove compatibility facades and duplicate adapters

### Purpose

Reduce false ownership after consumers have migrated.

### Diff

- remove zero-usage aliases;
- merge duplicate ES adaptation logic into one path;
- rename `ExperimentalHybridSearchBackend` to a truthful neutral name if it remains serving code;
- rename internal `inferredFilters` to `appliedFilters`, retaining V1 codec mapping;
- update docs and boundary tests.

### Forbidden

- new search features;
- ranking changes;
- route activation changes.

### Proof

- reachability/usage scan is zero before each deletion;
- route parity and public codecs unchanged;
- generic module boundary tests remain green.

### Rollback

Reintroduce thin aliases only; do not restore duplicate implementation.

## Iteration 12 — Separate serving from evaluation/history

### Purpose

Shrink runtime ownership and make the architecture readable.

### Diff

Move offline-only code from `beautyq-search-wiring/src/main/.../eval` into:

- `search-eval`;
- test source;
- tooling module;
- or delete if superseded and unreachable.

Keep only serving-required quality interfaces in runtime modules.

### Forbidden

- evaluation metric semantic changes mixed with moves;
- route changes.

### Proof

- build DAG shows serving does not depend on report/scaffold implementations;
- eval commands still work from their new owner;
- no runtime classpath need for M8–M21 scaffolding;
- docs distinguish current policy from historical phase evidence.

### Rollback

Module dependency can temporarily include `search-eval`; source ownership remains separated.

## Iteration 13 — Production synchronization implementation

### Purpose

Move beyond bootstrap snapshots once Gen2 contracts are stable.

### Candidate implementations

- outbox + incremental indexer;
- event stream;
- scheduled versioned snapshots;
- CDC.

Select based on operational requirements. All implementations must preserve:

- source version;
- idempotency;
- ordering/conflict policy;
- replay;
- reindex fallback;
- freshness metrics;
- active-generation safety.

This iteration is intentionally last: synchronization should target stable Gen2 document and lifecycle contracts.

## Review rules for every iteration

A reviewable diff should answer:

1. What source of truth moved?
2. What behavior changed, if any?
3. Which old path remains as rollback?
4. Which tests prove equivalence or intended difference?
5. Does the commit mix declaration migration with route activation?
6. Does a generic module gain any BeautyQ name?
7. Does a descriptive DTO become authoritative without an interpreter?
8. Are fingerprints and docs updated for behavior-affecting changes?

## Suggested commit naming

```text
search-gen2: add backend result algebra
search-gen2: compile BeautyQ requests into search plans
search-es: decode typed totals facets and groups
beautyq-search: switch ES response to backend aggregates
beautyq-search-api: add structured filter request v2
beautyq-search-index: load documents from repository snapshot
search-es: add versioned alias-based reindex
search-qdrant: compile plan constraints into payload filters
beautyq-search: route supplement through search plan
beautyq-search-contract: make Gen2 domain root executable
beautyq-search: remove compatibility facades
search-eval: split offline scaffolding from serving
```

## First actionable PR

The first code PR after these docs should contain only:

- `RangeBound` / `RangeBounds`;
- `SearchPlan`;
- `BackendSearchResult`;
- typed facet/group request/result IDs;
- pure generic tests;
- no BeautyQ serving changes.

The second code PR should compile current BeautyQ requests into that plan and pin semantics. The ES decoder switch comes only after those two foundations.
