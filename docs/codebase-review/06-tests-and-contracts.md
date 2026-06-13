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
- `TapirHttpSupportContractSuite.scala`

They protect:

- Endpoint paths.
- Success response JSON.
- Missing-entity behavior.
- Error/exception behavior.
- Tapir compatibility behavior.

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
- `QdrantEmbeddingBenchmarkSavedReportComparisonManualSpec.scala`: gates manual comparison by `QDRANT_EMBEDDING_BENCHMARK_COMPARE_SAVED_REPORTS`, `QDRANT_EMBEDDING_BENCHMARK_LEFT_JSON`, and `QDRANT_EMBEDDING_BENCHMARK_RIGHT_JSON`.

Important boundary:

- Benchmark tests validate benchmark machinery and reports. They do not prove production model switching.

## Manual saved-artifact eval specs

Three specs handle saved artifact JSON. Two are dual-mode (default fixture + real-artifacts); one cancels by default.

| Spec | Gate(s) | Default behavior |
|---|---|---|
| `EngineEvalSavedReportAssemblyManualSpec` | `ENGINE_EVAL_ASSEMBLE_SAVED_REPORT`, `ENGINE_EVAL_ES_REPORTS_JSON`, `ENGINE_EVAL_QDRANT_RUN_OUTPUT_JSON`, `ENGINE_EVAL_QDRANT_CANDIDATE_ID`, `ENGINE_EVAL_EXPECTED_ROLES_JSON` | Dual-mode: runs default fixture assembly when gate is absent; real-artifacts when gate is enabled |
| `EngineEvalSavedReportComparisonManualSpec` | `ENGINE_EVAL_COMPARE_SAVED_REPORTS`, `ENGINE_EVAL_LEFT_JSON`, `ENGINE_EVAL_RIGHT_JSON` | Dual-mode: runs default fixture comparison when gate is absent; real-artifacts when gate is enabled |
| `QdrantEmbeddingBenchmarkSavedReportComparisonManualSpec` | `QDRANT_EMBEDDING_BENCHMARK_COMPARE_SAVED_REPORTS`, `QDRANT_EMBEDDING_BENCHMARK_LEFT_JSON`, `QDRANT_EMBEDDING_BENCHMARK_RIGHT_JSON` | Cancels by default when env vars are absent |

Mode labels printed to stdout:

* `ENGINE_EVAL_SAVED_REPORT_ASSEMBLY_MODE=DEFAULT_FIXTURE` — assembly spec ran default fixture path
* `ENGINE_EVAL_SAVED_REPORT_ASSEMBLY_MODE=REAL_ARTIFACTS` — assembly spec ran real-artifact path
* `ENGINE_EVAL_SAVED_REPORT_COMPARISON_MODE=DEFAULT_FIXTURE` — comparison spec ran default fixture path
* `ENGINE_EVAL_SAVED_REPORT_COMPARISON_MODE=REAL_ARTIFACTS` — comparison spec ran real-artifact path

When real-artifacts mode is enabled but required env vars are missing, the dual-mode specs fail clearly instead of canceling.

Output markers:

* Qdrant benchmark report JSON: `BEGIN_QDRANT_EMBEDDING_BENCHMARK_JSON` / `END_QDRANT_EMBEDDING_BENCHMARK_JSON`
* Qdrant benchmark run-output JSON: `BEGIN_QDRANT_EMBEDDING_BENCHMARK_RUN_OUTPUT_JSON` / `END_QDRANT_EMBEDDING_BENCHMARK_RUN_OUTPUT_JSON`
* EngineEval aggregate report: `BEGIN_ENGINE_EVAL_AGGREGATE_REPORT` / `END_ENGINE_EVAL_AGGREGATE_REPORT`
* EngineEval aggregate JSON: `BEGIN_ENGINE_EVAL_AGGREGATE_REPORT_JSON` / `END_ENGINE_EVAL_AGGREGATE_REPORT_JSON`
* EngineEval saved comparison: `BEGIN_ENGINE_EVAL_SAVED_REPORT_COMPARISON` / `END_ENGINE_EVAL_SAVED_REPORT_COMPARISON`

Run sequence:

1. Run `QdrantEmbeddingBenchmarkExecutorIntegrationSpec` with endpoint env vars; capture `BEGIN_QDRANT_EMBEDDING_BENCHMARK_RUN_OUTPUT_JSON` content.
2. Feed ES eval reports JSON + Qdrant run-output JSON + expected-role JSON to `EngineEvalSavedReportAssemblyManualSpec`; capture `BEGIN_ENGINE_EVAL_AGGREGATE_REPORT_JSON`.
3. Compare two EngineEval aggregate JSON reports with `EngineEvalSavedReportComparisonManualSpec`.

Expected roles JSON example: `{ "query_id_here": "QdrantMayComplement" }`. Allowed role strings: `EsShouldHandle`, `QdrantMayComplement`, `QdrantShouldStaySilent`, `HybridMayImprove`.

Expected full-suite baseline when saved artifact JSON is not supplied: **963 succeeded, 0 failed, 1 canceled**. The 1 canceled spec is `QdrantEmbeddingBenchmarkSavedReportComparisonManualSpec`; the two EngineEval dual-mode specs run default fixture mode instead of canceling.

## Ignored / Tagged / Pending Tests

Targeted search result:

- Pass 2 did not find prominent `ignore(`, `taggedAs`, `pending`, or `Assume` markers in the searched test output.
- Several tests use `cancel(...)` when env vars are absent.
- Manual/local status is therefore represented mostly by explicit env gates and class names containing `Smoke`, `Integration`, or `Manual`.

## Suggested Future Verification Slices

Documentation-safe future commands, subject to user approval and SBT availability:

- Focused pure search: `sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.ElasticsearchSearchResponseInterpreterSpec'`
- Pure generic retrieval: `sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.GenericHybridDocumentRetrievalSpec'`
- HTTP contract slice: choose one `*ApiHttpContractSuite`, but it may still initialize Distage resources.
- Avoid full `sbt test` unless Docker/local resource requirements are accepted.
