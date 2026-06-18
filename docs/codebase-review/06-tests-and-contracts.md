# Tests And Contracts

## Source Of Truth Rule

For current behavior, focused tests are more authoritative than prose docs. In particular:

- HTTP route-level contract suites define HTTP behavior.
- Repository specs define dummy/Postgres persistence behavior.
- Attribute/variant specs define JSON, schema, and storage invariants.
- Search pure/eval specs define parser/DSL/search semantics.
- Integration/smoke specs prove real-resource paths only under their resource gates.

## HTTP Contract Tests

Implemented/current:

- `CategoryApiHttpContractSuite.scala`
- `ServiceApiHttpContractSuite.scala`
- `MasterApiHttpContractSuite.scala`
- `MasterLocationApiHttpContractSuite.scala`
- `MasterServiceOfferApiHttpContractSuite.scala`
- `MasterServiceOfferVariantApiHttpContractSuite.scala`
- `LadderApiHttpContractSuite.scala`
- `ProfileApiHttpContractSuite.scala`
- `LegacySingleEntityGetHttpContractSuite.scala`
- `BeautySearchApiHttpContractSuite.scala`: valid request/response pass-through, exact empty-response JSON shape, backend failure mapping, default decode failures, and exact structured semantic-invalid query/limit/coordinate `400 BadRequest` bodies without calling the fake service.
- `BeautySearchProductionRouteLimitSpec.scala`: valid positive limit remains `200 OK`; non-positive and above-carousel-maximum limits return structured `invalid_limit` JSON `400 BadRequest`.
- `BeautySearchProductionRouteCoordinateSpec.scala`: in-range coordinates remain `200 OK`; out-of-range latitude/longitude return structured `invalid_latitude` / `invalid_longitude` JSON `400 BadRequest`.
- `BeautySearchProductionRouteQuerySpec.scala`: non-blank and very-long queries retain current `200 OK` behavior; empty/whitespace-only queries return structured `invalid_query` JSON `400 BadRequest`.
- `BeautySearchProductionRouteErrorSpec.scala`: malformed JSON, empty body, wrong limit type, and missing query return Tapir default `400 BadRequest`.
- `BeautySearchElasticsearchRouteParitySpec.scala`: accepted inputs retain response-shape parity. Decode-invalid inputs retain Tapir default `400 BadRequest`; semantic-invalid inputs return exact `code` / `message` JSON `400 BadRequest`.
- `ElasticsearchSeedIndexReadinessSpec.scala`: pins pure `ElasticsearchProductionReadinessState.seedOnly` derivation, including lifecycle metadata and all current non-serving values.
- `ElasticsearchSeedSearchCompositionSpec.scala`: pins `productionReadinessState` derivation from composition lifecycle metadata.
- `ElasticsearchLifecycleStatusResponseSpec.scala`: pins exact state-to-response mapping, metadata-sourced document counts, exact Circe field names/current string values, and `productionLifecycleComplete = false`.
- `ElasticsearchStartupReadinessTransitionSpec.scala`: pins prepared-state preservation, field-by-field transition-to-status mapping, JSON equality with direct readiness-state projection, source-backed `OperationFailure` operation/message classification, explicit unsupported failure handling, absent failure metadata/status response, and `ElasticsearchStartupServingDecision.NotEnforced` for both outcomes.
- `BeautySearchProductionRouteExposureSpec.scala`: production API graph exposure remains `LeaderboardPlugin.modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`; production and targeted ES route probes root the readiness state while preserving `POST /beauty-search`.
- `BeautySearchElasticsearchRouteModuleSpec.scala`: the explicit ES seed route module exposes seed-only lifecycle metadata and the non-serving readiness state while preserving its zero-hit route response contract.
- `BeautySearchElasticsearchHttpRouteModuleSpec.scala`: the ES route with the real HTTP client module exposes the state while preserving mapping/index PUT, bulk ingestion, refresh, and search calls.
- `BeautySearchElasticsearchDefaultReadyRouteSpec.scala`: the port-configured default ES route exposes the state while preserving route behavior and ES preparation/search calls.
- BeautySearch request-boundary tests use `BeautySearchRequestContract` for public limits and semantic error descriptors. A focused mirror assertion keeps `BeautySearchRequestContract.MaxLimit` source-backed by `BeautySearchSpecV1.spec.carouselSpec.variantSize`.

They protect:

- Endpoint paths.
- Success response JSON.
- Missing-entity behavior.
- Error/exception behavior.
- Tapir/http4s default decode behavior: malformed JSON, empty bodies, missing required fields, invalid field types, and malformed path captures return `400 BadRequest` before repository/service logic.
- BeautySearch semantic validation behavior: blank query, invalid limit bounds, and out-of-range optional coordinates return structured JSON `400 BadRequest` before service/Elasticsearch logic.
- The named public request contract requires non-blank query text; limit range `1..BeautySearchRequestContract.MaxLimit` (`MinLimit` is `1`); latitude range `BeautySearchRequestContract.MinLatitude..BeautySearchRequestContract.MaxLatitude`; and longitude range `BeautySearchRequestContract.MinLongitude..BeautySearchRequestContract.MaxLongitude`. Coordinates are independently optional, and there is no maximum query-length rule.
- Default uncaught server exception behavior: `500 InternalServerError` with `Internal server error` body.
- Literal route precedence: `/category/root` remains a successful category-root route.
- Malformed UUID captures return `400 BadRequest`.

Structured bad-request boundary:

- BeautySearch semantic failures have exact structured `code` / `message` coverage and do not call the fake service or Elasticsearch-backed route logic.
- Malformed JSON, empty body, missing required fields, and invalid field types remain Tapir default `400 BadRequest`; tests do not treat the generated decode body as a domain schema.
- Global structured decode handling is not implemented.
- Non-goals remain explicit here as well: no route switch, no fallback, no score fusion, no reranking, no `HybridServe`, and no Qdrant auto-supplement.

### BeautySearch public contract freeze: regression checklist

Freeze-pinning suites:

- `BeautySearchApiHttpContractSuite.scala`: request/response contract at the API adapter seam, exact empty success JSON shape, structured semantic-invalid bodies, and proof that semantic-invalid and decode-invalid requests do not call the fake service.
- `BeautySearchProductionRouteQuerySpec.scala`: non-blank query requirement, accepted normal query, accepted very long query, and no max query-length rule.
- `BeautySearchProductionRouteLimitSpec.scala`: positive-limit success, structured invalid-limit failures, and the focused mirror assertion that `BeautySearchRequestContract.MaxLimit` stays source-backed.
- `BeautySearchProductionRouteCoordinateSpec.scala`: independently optional coordinates and structured out-of-range latitude/longitude failures.
- `BeautySearchProductionRouteErrorSpec.scala`: malformed JSON, empty body, wrong field type, and missing required field remain Tapir-default decode failures.
- `BeautySearchElasticsearchRouteParitySpec.scala`: accepted-input route parity plus the decode-invalid versus semantic-invalid split on the ES-backed route.
- `BeautySearchProductionRouteExposureSpec.scala`: production graph exposure remains ES-backed and serves `POST /beauty-search`.

Checklist for future edits:

- Keep success examples and success assertions on non-blank queries only.
- Keep semantic-invalid coverage separate from malformed-body decode coverage.
- Keep malformed decode failures undocumented as structured domain JSON unless a global decode handler is intentionally added.
- Keep `MaxLimit` references on the public contract surface via `BeautySearchRequestContract.MaxLimit`, except for the explicit mirror assertion.
- Keep route/module graph proof for production ES-backed exposure.
- Keep route-module metadata assertions pinned to the BeautyQ index name, `seed-resource-loader`, a positive document count, `EagerSeedIndexPreparation`, and `SeedOnlyNotProductionLifecycle`.
- Keep readiness-state assertions pinned to `NotEnforced`, `NotConfigured`, `NotTracked`, `EagerSeedPreparationOnly`, `NotConfigured`, and `NotExposed`.
- Treat this metadata and state as non-serving seed readiness seams, not production lifecycle completion.
- Do not imply route switch, fallback, score fusion, reranking, `HybridServe`, or Qdrant auto-supplement from these tests or docs.

### Future tests required before ES production lifecycle completion

Future/unimplemented unless matching source-backed tests are added:

- startup readiness success/failure:
  - pure success/failure transition-shape coverage now exists in `ElasticsearchStartupReadinessTransitionSpec.scala`;
  - serving path when ES preparation/readiness succeeds;
  - failure path when preparation fails;
  - explicit proof of whether serving blocks, degrades, or fails fast.
- replacement / alias behavior:
  - versioned index naming or alias movement if adopted;
  - atomic versus non-atomic replacement behavior;
  - proof that old/new index selection is explicit.
- freshness / version / status reporting:
  - operator-visible version/timestamp/count fields if introduced;
  - stale-data detection semantics if introduced;
  - distinction between seed-only metadata and production freshness state.
- refresh trigger semantics:
  - startup-only, manual, scheduled, or external trigger behavior once chosen;
  - proof that refresh happens only on the approved trigger path.
- rollback behavior:
  - return to previous known-good index/version if rollback support is introduced;
  - retained state required for rollback.
- operator-visible lifecycle status:
  - status surface and fields once introduced;
  - distinction among seed-only, preparing, ready, failed, stale, rollback, or disabled states.
- operator-facing lifecycle status design:
  - pure status model/encoder coverage now exists in `ElasticsearchLifecycleStatusResponseSpec.scala`;
  - route/status endpoint contract tests only if an endpoint/path is separately approved;
  - explicit proof that lifecycle-status values distinguish current seed-only state from any future production-ready state;
  - explicit proof that `/beauty-search` serving tests and behavior remain unaffected by any separate status surface.

Current focused pure/composition/route-module specs cover only the seed-only metadata, explicit non-serving state, status projection, and pure startup transition shape. They are not substitutes for production lifecycle tests.

## Repository Tests

Implemented/current:

- `CatalogRepositoriesSpec.scala`: categories, services, masters, locations, and offers across dummy/Postgres.
- `RankingSpec.scala`: ladder/profile/rank behavior across dummy/Postgres.
- `MasterServiceOfferVariantsSpec.scala`: variant repository invariants.
- `BeautyQSeedSpec.scala`: seed decode/insert/read behavior.

Resource requirement:

- Postgres tests use Distage managed resources through `LeaderboardTest`/`ProdTest`; Docker availability may be required.

## Domain Invariant Tests

Implemented/current:

- `AttributeDefinitionSpec.scala`: attribute registry behavior.
- `CodedEnumValueSpec.scala`: coded enum behavior.
- `VariantAttributeJsonSpec.scala`: variant attribute JSON shape.
- `VariantAttributeSchemaSpec.scala`: service variant schema validation.
- `VariantAttributeStorageSpec.scala`: variant attribute storage encode/decode.
- `MasterServiceOfferVariantsSpec.scala`: variant invariants, including relationship and schema checks.

## Search Pure / In-Memory Tests

Implemented/current/fake-only where applicable:

- `BeautySearchPureSpec.scala`: large pure contract suite for parser, DSL fields, in-memory backend, ES request/mapping/ingestion interpreters, routing, experimental service separation, and eval outcomes.
- `BeautySearchEvalInventory.scala`: eval query inventory.
- `BeautySearchEvalTestSupport.scala`: shared eval assertions.
- `ElasticsearchSearchResponseInterpreterSpec.scala`: pure ES response decoding and matched query diagnostics.

Fast/safe candidates for later passes:

- `BeautySearchPureSpec.scala` is likely the main focused pure search suite, but it is large.
- Smaller pure suites such as `ElasticsearchSearchResponseInterpreterSpec.scala`, `GenericHybridDocumentRetrievalSpec.scala`, and Qdrant pure unit specs avoid Docker/Llama based on inspected code names and lack of env gates.

## Elasticsearch Integration Tests

Integration-test-only:

- `BeautySearchElasticsearchIntegrationSpec.scala`: creates ES index, ingests one document per seeded variant, runs eval subsets, and uses `ElasticsearchTestClient.scala`.
- It adds `DIKey[ElasticsearchPortCfg]` to memoization roots and uses `ElasticsearchDockerPlugin` resource discovery.

Resource requirement:

- Docker-backed Elasticsearch through Distage managed resources.

## Generic Retrieval / Hybrid Tests

Implemented/current pure tests:

- `GenericLexicalDocumentBackendSpec.scala`: generic lexical seam, no BeautyQ/ES dependency.
- `GenericSemanticDocumentBackendSpec.scala`: generic semantic seam.
- `GenericSemanticCandidateAssemblerSpec.scala`: semantic assembly behavior.
- `GenericSemanticResponseProjectorSpec.scala`: semantic projector seam.
- `GenericHybridDocumentRetrievalSpec.scala`: generic hybrid result container.
- `HybridGenericSecondDomainProofSpec.scala`: second-domain proof using fake article ids/documents.

Hybrid/experiment tests:

- `BeautyQHybridProjectionPolicySpec.scala`
- `BeautyQHybridVariantProjectionSpec.scala`
- `BeautyQHybridProviderServiceProjectionSpec.scala`
- `BeautyQHybridResponseAdapterSpec.scala`
- `BeautyQHybridResponsePipelineSpec.scala`
- `BeautyQNonProductionHybridExperimentActivationSpec.scala`
- `BeautyQNonProductionHybridExperimentModuleGatingSpec.scala`
- `BeautyQNonProductionHybridResponseExperimentSpec.scala`
- `ExperimentalHybridRouteDiagnosticsSpec.scala`
- `QdrantNonProductionHybridExperimentSpec.scala`

What they prove:

- Pure/generic seams and non-production experiment behavior.
- They do not prove production hybrid wiring.

## Qdrant Tests

Pure/unit-style Qdrant tests:

- `QdrantCandidateAssemblerSpec.scala`
- `QdrantCandidateResponseProjectorSpec.scala`
- `QdrantCollectionCompatibilityCheckerSpec.scala`
- `QdrantCollectionCompatibilityGuardSpec.scala`
- `QdrantCollectionCompatibilityValidatorSpec.scala`
- `QdrantCollectionIdentitySpec.scala`
- `QdrantCollectionInfoDecoderSpec.scala`
- `QdrantCollectionReadinessConfigSpec.scala`
- `QdrantNonProductionExperimentActivationSpec.scala`
- `QdrantNonProductionExperimentCompositionSpec.scala`
- `QdrantSearchDocumentIndexerSpec.scala`
- `QdrantSemanticCandidateBackendSpec.scala`
- `QdrantSemanticCandidateSearchSpec.scala`
- `QdrantVariantDocumentIndexerSpec.scala`
- `QdrantVariantDocumentPointBuilderSpec.scala`
- `QdrantVariantDocumentSnapshotIndexerSpec.scala`
- `VariantSearchDocumentSnapshotProviderSpec.scala`

Docker-backed:

- `QdrantDockerSmokeSpec.scala`: uses Distage `QdrantPortCfg`, creates a collection, upserts a vector, searches, and deletes the collection.

Resource-backed auto-gated:

- `QdrantLlamaCppRetrievalSmokeSpec.scala`: defaults to local endpoint `http://localhost:8081`; cancels when Llama endpoint is unavailable.
- `QdrantExperimentalHybridServiceIntegrationSpec.scala`: defaults to local endpoint `http://localhost:8081`; cancels when Llama endpoint is unavailable.
- `QdrantSemanticCandidateEvalSpec.scala`: defaults to local endpoint `http://localhost:8081`; cancels when Llama endpoint is unavailable.
- `BeautyQManualHybridRealQdrantIndexingSmokeSpec.scala`: uses injected `QdrantPortCfg`; runs directly when Qdrant is available.
- `BeautyQManualHybridRealQdrantRetrievalSmokeSpec.scala`: uses injected `QdrantPortCfg`; runs directly when Qdrant is available.

Qdrant Docker/resource-backed integration specs:

- `QdrantCollectionCompatibilityIntegrationSpec.scala`: uses the distage-managed Qdrant Docker resource via `QdrantPortCfg`; runs automatically when that resource is available.
- `QdrantSnapshotIndexingCompatibilityIntegrationSpec.scala`: uses the distage-managed Qdrant Docker resource via `QdrantPortCfg`; runs automatically when that resource is available.

## Llama Tests

Resource-backed auto-gated:

- `LlamaCppEmbeddingSmokeSpec.scala`: defaults to local endpoint `http://localhost:8081`; cancels when endpoint is unavailable; `LLAMA_CPP_EMBEDDING_URL` is optional override.

Pure/client decode:

- `LlamaCppEmbeddingClientSpec.scala`: tests client decoding/error behavior without necessarily requiring a running server based on inspected env search.

## Benchmark Tests

Pure benchmark tests:

- `QdrantEmbeddingBenchmarkSpec.scala`
- `QdrantEmbeddingBenchmarkDecisionPolicySpec.scala`
- `QdrantEmbeddingBenchmarkExecutorSpec.scala`
- `QdrantEmbeddingBenchmarkQuerySubsetSpec.scala`
- `QdrantEmbeddingBenchmarkReportJsonSpec.scala`
- `QdrantEmbeddingBenchmarkRunnerSpec.scala`
- `QdrantEmbeddingBenchmarkSavedReportComparisonSpec.scala`

Resource-backed auto-gated benchmark tests:

- `QdrantEmbeddingBenchmarkExecutorIntegrationSpec.scala`: single endpoint defaults to `http://localhost:8081`, dual endpoints default to `http://localhost:8081` + `http://localhost:8082`; cancels when endpoints are unavailable; env vars remain as optional overrides.

Manual saved-artifact benchmark comparison:

- `QdrantEmbeddingBenchmarkSavedReportComparisonManualSpec.scala`: not resource-backed; cancels by default when `QDRANT_EMBEDDING_BENCHMARK_COMPARE_SAVED_REPORTS`, `QDRANT_EMBEDDING_BENCHMARK_LEFT_JSON`, and `QDRANT_EMBEDDING_BENCHMARK_RIGHT_JSON` are absent.

Important boundary:

- Benchmark tests validate benchmark machinery and reports. They do not prove production model switching.

## Manual saved-artifact eval specs

Three specs handle saved artifact JSON. Two are dual-mode (default fixture + real-artifacts); one cancels by default.

| Spec | Gate(s) | Default behavior |
|---|---|---|
| `EngineEvalSavedReportAssemblyManualSpec` | `ENGINE_EVAL_ASSEMBLE_SAVED_REPORT`, `ENGINE_EVAL_ES_REPORTS_JSON`, `ENGINE_EVAL_QDRANT_RUN_OUTPUT_JSON`, `ENGINE_EVAL_QDRANT_CANDIDATE_ID`, `ENGINE_EVAL_EXPECTED_ROLES_JSON` | Dual-mode: runs default fixture assembly when gate is absent; real-artifacts when gate is enabled |
| `EngineEvalSavedReportComparisonManualSpec` | `ENGINE_EVAL_COMPARE_SAVED_REPORTS`, `ENGINE_EVAL_LEFT_JSON`, `ENGINE_EVAL_RIGHT_JSON`, optional paired `ENGINE_EVAL_LEFT_QUERY_CLASSES_JSON` / `ENGINE_EVAL_RIGHT_QUERY_CLASSES_JSON` | Dual-mode: runs default fixture comparison when gate is absent; real-artifacts when gate is enabled |
| `QdrantEmbeddingBenchmarkSavedReportComparisonManualSpec` | `QDRANT_EMBEDDING_BENCHMARK_COMPARE_SAVED_REPORTS`, `QDRANT_EMBEDDING_BENCHMARK_LEFT_JSON`, `QDRANT_EMBEDDING_BENCHMARK_RIGHT_JSON` | Cancels by default when env vars are absent |

Mode labels printed to stdout:

* `ENGINE_EVAL_SAVED_REPORT_ASSEMBLY_MODE=DEFAULT_FIXTURE` — assembly spec ran default fixture path
* `ENGINE_EVAL_SAVED_REPORT_ASSEMBLY_MODE=REAL_ARTIFACTS` — assembly spec ran real-artifact path
* `ENGINE_EVAL_SAVED_REPORT_COMPARISON_MODE=DEFAULT_FIXTURE` — comparison spec ran default fixture path
* `ENGINE_EVAL_SAVED_REPORT_COMPARISON_MODE=REAL_ARTIFACTS` — comparison spec ran real-artifact path

When real-artifacts mode is enabled but required env vars are missing, the dual-mode specs fail clearly instead of canceling.

### M-ESQ-EVAL contract and evidence scope

This document is the canonical detailed contract and evidence reference for M-ESQ-EVAL. Keep the handoff compact and keep local operator mechanics in `docs/local/M_ESQ_EVAL_EVIDENCE_RUN_TEMPLATE.md`.
For milestone status and sequencing, use `docs/codebase-review/07-current-gaps-and-roadmap.md`.

Use this file for:

* saved-schema boundaries;
* query-class and sidecar contracts;
* `roleDeltas:`, `queryDeltas:`, and `classDeltas:` behavior;
* validated replay evidence summary;
* detailed evidence tables and interpretation.

For operator commands, extraction mechanics, artifact naming, manifest shape, and workspace procedure, use `docs/local/M_ESQ_EVAL_EVIDENCE_RUN_TEMPLATE.md`.

Validated replay note:

- A real offline class-sidecar saved-comparison replay was validated for `benchmark-small -> benchmark-large` using existing saved aggregate JSON artifacts copied into `.beautyq-evidence-runs/20260615T120500-saved-report-replay-sidecars/`.
- The replay generated `query-classes.benchmark-small.json` and `query-classes.benchmark-large.json` sidecars from current `beautyq_search_eval_queries_v1.json` plus current `EngineEvalQueryClass.fromQueryTypes`.
- `q_broad_005` classified to `PriceDuration` and `BroadIntent`.
- Every aggregate `queryReports` id was present in current `beautyq_search_eval_queries_v1.json`.
- `sbt "bifunctor-tagless / Test / testOnly leaderboard.search.EngineEvalSavedReportComparisonManualSpec"` passed.
- Replay output confirmed `classDeltas:`, `roleDeltas:`, and `queryDeltas:` appeared.
- The copied small/large aggregate JSON files were byte-identical to the source artifacts by `shasum`.
- Saved aggregate JSON schema remains unchanged.
- `EngineEvalAggregateReport` remains unchanged.
- `EngineEvalReportJson` remains unchanged.

### EngineEval query-class classification contract

`EngineEvalQueryClass.fromQueryTypes` maps BeautySearch eval `queryTypes` strings to `EngineEvalQueryClass` values for offline/eval metadata only. This contract is locked by `EngineEvalQueryClassSpec`.

Contract facts:

- Output class order is stable: `ExactService`, `Category`, `StructuredFilter`, `PriceDuration`, `GeoLocal`, `SemanticVague`, `BroadIntent`, `HardNegative`, `Mixed`.
- `english` and `german` are language modifier tags and are ignored.
- Unknown non-language query type tags fail with `QueryFailure.operation`; they are not silently ignored.
- Empty `queryTypes` returns no classes for existing fixture compatibility.
- Current observed query type tags are `ambiguous`, `attribute`, `attribute_heavy`, `broad`, `conversational`, `direct`, `english`, `german`, `hard_negative`, `home_visit`, `location`, `mixed_language`, `multi_intent`, `negative_attribute`, `numeric`, `price`, `synonym`, `technical_token`, and `typo`.
- Real `SemanticBroadSmoke` example: `q_broad_005` classifies to `PriceDuration` then `BroadIntent`.

Boundary:

- This is offline/eval-only metadata classification.
- It is not production readiness.
- It is not routing approval.

### Saved aggregate schema boundary

Saved aggregate report schema remains unchanged in the current offline/eval evidence path:

- Saved aggregate JSON schema remains unchanged.
- `EngineEvalAggregateReport` remains unchanged.
- `EngineEvalReportJson` remains unchanged.
- `EngineEvalSavedReportComparison.compareReportJsonStrings(leftJson, rightJson)` remains backward compatible with aggregate/role/query comparison and produces no class comparisons by itself.
- Query-class comparison remains explicit and optional via paired sidecars:
  - `ENGINE_EVAL_LEFT_QUERY_CLASSES_JSON`
  - `ENGINE_EVAL_RIGHT_QUERY_CLASSES_JSON`

### EngineEval query-class breakdown sidecar contract

`EngineEvalQueryClassBreakdown.from` aggregates `EngineEvalQueryReport` metrics by `EngineEvalQueryClass` for offline/eval use only. It requires an explicit `queryId -> List[EngineEvalQueryClass]` sidecar map and does not read `queryTypes` or `queryClasses` from saved `EngineEvalQueryReport` data.

Contract facts:

- A query may contribute to multiple class buckets.
- Repeated classes for a single query are deduplicated.
- Empty class lists contribute no bucket.
- Missing sidecar entries fail with `QueryFailure.operation` naming the missing `queryId`.
- Output buckets follow `EngineEvalQueryClass.stableOrder`.
- Saved aggregate JSON schema remains unchanged.
- `EngineEvalAggregateReport` remains unchanged.
- `EngineEvalReportJson` remains unchanged.
- The breakdown helper is consumed only by explicit sidecar comparison paths; saved aggregate reports still do not contain query classes and class breakdowns are not derivable from saved JSON alone.

Boundary:

- This is offline/eval-only helper behavior.
- It is not production readiness.
- It is not routing approval.
- It does not imply Qdrant/hybrid production readiness, route switch, fallback, score fusion, reranking, `HybridServe`, or Qdrant auto-supplement.

### EngineEval class-delta comparison contract

`EngineEvalSavedReportComparison.compareReportJsonStrings(leftJson, rightJson)` remains backward compatible with the existing aggregate/role/query comparison behavior and produces no class comparisons. `EngineEvalSavedReportComparison.compareReportJsonStringsWithQueryClasses(...)` decodes saved aggregate JSON strings and uses explicit left/right `queryId -> List[EngineEvalQueryClass]` sidecar maps for class-level reporting.

Contract facts:

- `EngineEvalSavedReportComparisonManualSpec` accepts optional paired `ENGINE_EVAL_LEFT_QUERY_CLASSES_JSON` / `ENGINE_EVAL_RIGHT_QUERY_CLASSES_JSON` env vars for manual real-artifact comparison.
- If neither class sidecar env var is present, manual real-artifact comparison keeps the previous aggregate/role/query behavior and emits no `classDeltas:`.
- If both class sidecar env vars are present, they are decoded and may produce `classDeltas:`.
- If exactly one class sidecar env var is present, the run fails clearly and names the missing counterpart.
- Sidecar JSON shape is `{ "q_broad_005": ["PriceDuration", "BroadIntent"] }`.
- Class names must be exact `EngineEvalQueryClass` names: `ExactService`, `Category`, `StructuredFilter`, `PriceDuration`, `GeoLocal`, `SemanticVague`, `BroadIntent`, `HardNegative`, `Mixed`.
- Invalid class names fail with `QueryFailure.operation` and name the invalid value.
- Missing sidecar query ids fail through `EngineEvalQueryClassBreakdown` and name the missing query id.
- A missing class bucket on one side is compared against zero metrics.
- `classDeltas:` is omitted when class comparisons are empty or when all class-level deltas are zero.
- Existing aggregate deltas, `roleDeltas:`, and `queryDeltas:` behavior is preserved.
- Saved aggregate JSON schema remains unchanged.
- `EngineEvalAggregateReport` remains unchanged.
- `EngineEvalReportJson` remains unchanged.

Boundary:

- This is offline/eval-only reporting behavior.
- It is not production readiness.
- It is not routing approval.
- It does not imply Qdrant/hybrid production readiness, route switch, fallback, score fusion, reranking, `HybridServe`, or Qdrant auto-supplement.

For local operator procedure, workspace setup, extraction mechanics, artifact naming, manifest details, and first-run command examples, see `docs/local/M_ESQ_EVAL_EVIDENCE_RUN_TEMPLATE.md`.

Output markers:

* ES eval reports JSON: `BEGIN_ENGINE_EVAL_ES_REPORTS_JSON` / `END_ENGINE_EVAL_ES_REPORTS_JSON`
* ES expected roles JSON: `BEGIN_ENGINE_EVAL_EXPECTED_ROLES_JSON` / `END_ENGINE_EVAL_EXPECTED_ROLES_JSON`
* Qdrant benchmark report JSON: `BEGIN_QDRANT_EMBEDDING_BENCHMARK_JSON` / `END_QDRANT_EMBEDDING_BENCHMARK_JSON`
* Qdrant benchmark run-output JSON: `BEGIN_QDRANT_EMBEDDING_BENCHMARK_RUN_OUTPUT_JSON` / `END_QDRANT_EMBEDDING_BENCHMARK_RUN_OUTPUT_JSON`
* EngineEval aggregate report: `BEGIN_ENGINE_EVAL_AGGREGATE_REPORT` / `END_ENGINE_EVAL_AGGREGATE_REPORT`
* EngineEval aggregate JSON: `BEGIN_ENGINE_EVAL_AGGREGATE_REPORT_JSON` / `END_ENGINE_EVAL_AGGREGATE_REPORT_JSON`
* EngineEval saved comparison: `BEGIN_ENGINE_EVAL_SAVED_REPORT_COMPARISON` / `END_ENGINE_EVAL_SAVED_REPORT_COMPARISON`

### Saved comparison interpretation notes

`EngineEvalSavedReportComparisonManualSpec` runs in saved-comparison mode when `ENGINE_EVAL_COMPARE_SAVED_REPORTS=1` is set along with `ENGINE_EVAL_LEFT_JSON` and `ENGINE_EVAL_RIGHT_JSON`. Optional paired `ENGINE_EVAL_LEFT_QUERY_CLASSES_JSON` / `ENGINE_EVAL_RIGHT_QUERY_CLASSES_JSON` sidecars enable `classDeltas:` output for manual real-artifact comparison. Output is delimited by `BEGIN_ENGINE_EVAL_SAVED_REPORT_COMPARISON` / `END_ENGINE_EVAL_SAVED_REPORT_COMPARISON`.

**What comparison output is useful for:**

- Comparing two aggregate evidence snapshots from different runs or candidates.
- Spotting query-level or aggregate movement between candidates/runs (deltas for `queryCount`, `esRecallCount`, `qdrantRecallCount`, `qdrantComplementCount`, `qdrantNoiseCount`, `overlapCount`, `simulatedHybridGainCount`).
- Spotting role-level movement between candidates/runs when role deltas are non-zero (see roleDeltas below).
- Supporting later Qdrant shadow/hybrid decisions with recorded evidence.

**Role-level deltas (roleDeltas):**

- Role breakdowns are derived from query reports, not persisted in saved aggregate JSON.
- Saved aggregate JSON schema remains unchanged.
- Text aggregate reports may include `roleAggregates`.
- Saved-report comparison output may include `roleDeltas` when role-level deltas are non-zero.
- Replayed saved comparisons confirmed: `benchmark-small` → `benchmark-large` includes `queryDeltas` and preserves `roleDeltas`; `benchmark-single` → `benchmark-small` omits both `queryDeltas` and `roleDeltas` because that comparison is zero-delta at query and role levels.
- This remains offline/eval-only and does not imply production Qdrant/hybrid readiness, routing, fallback, score fusion, reranking, HybridServe, or Qdrant auto-supplement.

**Query-level deltas (queryDeltas):**

- Query-level deltas are derived from `EngineEvalAggregateReport.queryReports`.
- Saved-report comparison output may include `queryDeltas` when query-level deltas are non-zero.
- `queryDeltas` is omitted when all query-level deltas are zero.
- Replayed saved comparisons confirmed the omission behavior for all-zero query deltas and the presence behavior for non-zero query deltas: `benchmark-small` → `benchmark-large` includes `queryDeltas`, while `benchmark-single` → `benchmark-small` omits `queryDeltas`.
- Query ids are compared in stable order: ids from the left report in left order, then ids only present in the right report in right order.
- Queries missing on either side are compared against zero metrics.
- Query-level deltas include `expectedVariantCountDelta`, `esRecallDelta`, `qdrantRecallDelta`, `qdrantComplementDelta`, `qdrantNoiseDelta`, `overlapDelta`, and `simulatedHybridGainDelta`.
- This is offline/eval-only reporting. It is not production readiness, route approval, fallback approval, score fusion, reranking, `HybridServe`, or Qdrant auto-supplement.

**What comparison output does NOT prove:**

- Not production readiness for any engine or hybrid path.
- Not a route switch decision for `/beauty-search` or any production endpoint.
- Not approval for fallback, score fusion, reranking, `HybridServe`, or Qdrant auto-supplement.
- Not a substitute for readiness/policy/kill-switch/lifecycle work.
- Not a substitute for manual review of the local evidence procedure and artifact inputs described in `docs/local/M_ESQ_EVAL_EVIDENCE_RUN_TEMPLATE.md`.

Comparison output is evidence support only. All M-ESQ-EVAL work remains offline/eval-only and non-production-routing.

### First M-ESQ-EVAL evidence summary (SemanticBroadSmoke)

First real offline evidence for the `SemanticBroadSmoke` eval subset (query ids `q_broad_001`–`q_broad_006`). All numbers below are offline/eval-only, not production readiness or routing approval.

**benchmark-single:**

| Metric | Value |
|---|---|
| qdrantRecall | 6 |
| qdrantComplement | 4 |
| qdrantNoise | 0 |
| overlap | 2 |
| simulatedHybridGain | 4 |

**benchmark-small:**

| Metric | Value |
|---|---|
| qdrantRecall | 6 |
| qdrantComplement | 4 |
| qdrantNoise | 0 |
| overlap | 2 |
| simulatedHybridGain | 4 |

**benchmark-large:**

| Metric | Value |
|---|---|
| qdrantRecall | 22 |
| qdrantComplement | 7 |
| qdrantNoise | 0 |
| overlap | 15 |
| simulatedHybridGain | 7 |

**Deltas between runs:**

| From → To | qdrantRecall | qdrantComplement | qdrantNoise | overlap | simulatedHybridGain |
|---|---|---|---|---|---|
| benchmark-single → benchmark-small | 0 | 0 | 0 | 0 | 0 |
| benchmark-small → benchmark-large | +16 | +3 | +0 | +13 | +3 |

**Offline interpretation:** `benchmark-large` is the strongest in this SemanticBroadSmoke evidence slice. All deltas are zero from `benchmark-single` to `benchmark-small`; the meaningful movement is from `benchmark-small` to `benchmark-large`.

**Boundaries:** This is offline/eval-only evidence. It is not production readiness. It is not routing approval. It does not imply Qdrant/hybrid production readiness, route switch, fallback, score fusion, reranking, `HybridServe`, or Qdrant auto-supplement.

## Ignored / Tagged / Pending Tests

Targeted search result:

- Pass 2 did not find prominent `ignore(`, `taggedAs`, `pending`, or `Assume` markers in the searched test output.
- Resource-backed specs cancel when resources are unavailable; true manual artifact specs may cancel when saved artifact env vars are absent.
- Class names containing `Smoke`, `Integration`, or `Manual` reflect the spec's role.

## Suggested Future Verification Slices

Documentation-safe future commands, subject to user approval and SBT availability:

- Focused pure search: `sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.ElasticsearchSearchResponseInterpreterSpec'`
- Pure generic retrieval: `sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.GenericHybridDocumentRetrievalSpec'`
- HTTP contract slice: choose one `*ApiHttpContractSuite`, but it may still initialize Distage resources.
- Plain `sbt test` is canonical; resource-backed tests auto-run when local resources are available and cancel with reason when unavailable.
