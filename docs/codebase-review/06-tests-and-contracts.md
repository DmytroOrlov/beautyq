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

Manual saved-artifact benchmark comparison:

- `QdrantEmbeddingBenchmarkSavedReportComparisonManualSpec.scala`: not resource-backed; cancels by default when `QDRANT_EMBEDDING_BENCHMARK_COMPARE_SAVED_REPORTS`, `QDRANT_EMBEDDING_BENCHMARK_LEFT_JSON`, and `QDRANT_EMBEDDING_BENCHMARK_RIGHT_JSON` are absent.

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

### M-ESQ-EVAL operator quick index

Compact navigation for the sections below:

* **Env gates & mode labels** — spec table and `ENGINE_EVAL_*_MODE` / `QDRANT_EMBEDDING_BENCHMARK_COMPARE_SAVED_REPORTS` gates (above).
* **Output markers** — `BEGIN_*` / `END_*` marker names for ES, Qdrant, aggregate, and comparison payloads (above).
* **Operational workflow** — 4-step evidence collection sequence: ES artifact emission → Qdrant benchmark run → assembly → optional comparison (step 4).
* **Evidence collection dry-run checklist** — preflight checklist before running any resource-backed or env-gated M-ESQ-EVAL specs.
* **Marker payload extraction** — read-only `extract_marker()` helper and usage examples for JSON and non-JSON markers.
* **Saved artifact naming and manifest** — recommended directory shape, filenames, and `manifest.md` template.
* **Evidence artifact sanity checklist** — verification checklist before using collected artifacts as evidence.
* **Saved comparison interpretation notes** — what `EngineEvalSavedReportComparisonManualSpec` output is useful for and what it does not prove.

All sections are offline/eval-only and operator-controlled.

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
- Saved aggregate JSON schema remains unchanged.
- `EngineEvalReportJson` remains unchanged.

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
- The helper does not wire class breakdowns into saved-report comparison yet.

Boundary:

- This is offline/eval-only helper behavior.
- It is not production readiness.
- It is not routing approval.
- It does not imply Qdrant/hybrid production readiness, route switch, fallback, score fusion, reranking, `HybridServe`, or Qdrant auto-supplement.

For a copyable local operator template (workspace setup, commands, multi-payload splitting, first-run example), see `docs/local/M_ESQ_EVAL_EVIDENCE_RUN_TEMPLATE.md`.

Output markers:

* ES eval reports JSON: `BEGIN_ENGINE_EVAL_ES_REPORTS_JSON` / `END_ENGINE_EVAL_ES_REPORTS_JSON`
* ES expected roles JSON: `BEGIN_ENGINE_EVAL_EXPECTED_ROLES_JSON` / `END_ENGINE_EVAL_EXPECTED_ROLES_JSON`
* Qdrant benchmark report JSON: `BEGIN_QDRANT_EMBEDDING_BENCHMARK_JSON` / `END_QDRANT_EMBEDDING_BENCHMARK_JSON`
* Qdrant benchmark run-output JSON: `BEGIN_QDRANT_EMBEDDING_BENCHMARK_RUN_OUTPUT_JSON` / `END_QDRANT_EMBEDDING_BENCHMARK_RUN_OUTPUT_JSON`
* EngineEval aggregate report: `BEGIN_ENGINE_EVAL_AGGREGATE_REPORT` / `END_ENGINE_EVAL_AGGREGATE_REPORT`
* EngineEval aggregate JSON: `BEGIN_ENGINE_EVAL_AGGREGATE_REPORT_JSON` / `END_ENGINE_EVAL_AGGREGATE_REPORT_JSON`
* EngineEval saved comparison: `BEGIN_ENGINE_EVAL_SAVED_REPORT_COMPARISON` / `END_ENGINE_EVAL_SAVED_REPORT_COMPARISON`

Operational workflow (M-ESQ-EVAL evidence collection):

This workflow collects concrete ES + Qdrant benchmark artifacts for EngineEval saved-report assembly and comparison. It is offline/eval-only and does not affect production serving. Endpoint/env overrides are optional resource controls, not required production gates. JSON payload extraction between markers is operator-controlled; use the marker extraction helper below to avoid copy-paste errors.

1. **ES artifact emission**: Run `BeautySearchElasticsearchIntegrationSpec` with `ENGINE_EVAL_PRINT_ES_ARTIFACTS=1`. This emits the `SemanticBroadSmoke` subset (query ids `q_broad_001`–`q_broad_006`) ES eval reports JSON and expected roles JSON between their respective markers. The expected-role JSON is first-pass offline evidence metadata for `SemanticBroadSmoke`, not a production routing policy. Current role map: `q_broad_001` → `EsShouldHandle` (nail-service nearby), `q_broad_002` → `EsShouldHandle` (lashes/brows nearby), `q_broad_003` → `QdrantMayComplement` (vague/conversational facial), `q_broad_004` → `HybridMayImprove` (broad self-care), `q_broad_005` → `EsShouldHandle` (affordable nails), `q_broad_006` → `QdrantMayComplement` (generic broad nearby).
2. **Qdrant benchmark run**: Run `QdrantEmbeddingBenchmarkExecutorIntegrationSpec` (defaults to local endpoints `http://localhost:8081` / `http://localhost:8082`); endpoint env vars are optional overrides. Capture the Qdrant run-output JSON between `BEGIN_QDRANT_EMBEDDING_BENCHMARK_RUN_OUTPUT_JSON` / `END_QDRANT_EMBEDDING_BENCHMARK_RUN_OUTPUT_JSON`.
3. **Assembly**: Run `EngineEvalSavedReportAssemblyManualSpec` with `ENGINE_EVAL_ASSEMBLE_SAVED_REPORT=1` and the four saved-artifact env vars (`ENGINE_EVAL_ES_REPORTS_JSON`, `ENGINE_EVAL_QDRANT_RUN_OUTPUT_JSON`, `ENGINE_EVAL_QDRANT_CANDIDATE_ID`, `ENGINE_EVAL_EXPECTED_ROLES_JSON`). Capture the aggregate report JSON between `BEGIN_ENGINE_EVAL_AGGREGATE_REPORT_JSON` / `END_ENGINE_EVAL_AGGREGATE_REPORT_JSON`.
4. **Comparison (optional)**: Run `EngineEvalSavedReportComparisonManualSpec` with `ENGINE_EVAL_COMPARE_SAVED_REPORTS=1` and two aggregate report JSON blobs via `ENGINE_EVAL_LEFT_JSON` / `ENGINE_EVAL_RIGHT_JSON`. Capture the comparison output between `BEGIN_ENGINE_EVAL_SAVED_REPORT_COMPARISON` / `END_ENGINE_EVAL_SAVED_REPORT_COMPARISON`.

Expected roles: `EsShouldHandle`, `QdrantMayComplement`, `QdrantShouldStaySilent`, `HybridMayImprove`.

Expected full-suite baseline when saved artifact JSON is not supplied: **963 succeeded, 0 failed, 1 canceled**. The 1 canceled spec is `QdrantEmbeddingBenchmarkSavedReportComparisonManualSpec`; the two EngineEval dual-mode specs run default fixture mode instead of canceling.

### Evidence collection dry-run checklist

Before running any resource-backed or env-gated M-ESQ-EVAL specs, complete this preflight checklist. This is preparation only; it does not claim artifacts have been collected.

- [ ] **Run label and directory**: chosen a short run label (e.g. `sem-broad-001`) and local artifact directory under `tmp/m-esq-eval/<YYYYMMDD-HHMMSS>-<label>/`.
- [ ] **Log destination**: decided where saved test logs will be written (file path for each spec run).
- [ ] **Marker pairs confirmed**: confirmed the exact `BEGIN_*` / `END_*` marker pairs needed:
  - ES eval reports JSON: `BEGIN_ENGINE_EVAL_ES_REPORTS_JSON` / `END_ENGINE_EVAL_ES_REPORTS_JSON`
  - ES expected roles JSON: `BEGIN_ENGINE_EVAL_EXPECTED_ROLES_JSON` / `END_ENGINE_EVAL_EXPECTED_ROLES_JSON`
  - Qdrant run-output JSON: `BEGIN_QDRANT_EMBEDDING_BENCHMARK_RUN_OUTPUT_JSON` / `END_QDRANT_EMBEDDING_BENCHMARK_RUN_OUTPUT_JSON`
  - Aggregate report JSON: `BEGIN_ENGINE_EVAL_AGGREGATE_REPORT_JSON` / `END_ENGINE_EVAL_AGGREGATE_REPORT_JSON`
  - Optional comparison text: `BEGIN_ENGINE_EVAL_SAVED_REPORT_COMPARISON` / `END_ENGINE_EVAL_SAVED_REPORT_COMPARISON`
- [ ] **Qdrant candidate id**: chosen or recorded the Qdrant candidate id to use for `qdrant-run-output.<candidate-id>.json` and `engine-eval-aggregate.<candidate-id>.json` naming, and for `ENGINE_EVAL_QDRANT_CANDIDATE_ID`.
- [ ] **manifest.md prepared**: prepared `manifest.md` in the target directory (use the template in "Saved artifact naming and manifest") with at minimum date/time, operator, and notes/non-goals fields filled before extraction.
- [ ] **Assembly env vars conceptually prepared**: reviewed the four assembly env values and their targets:
  - `ENGINE_EVAL_ES_REPORTS_JSON` → `es-reports.semantic-broad-smoke.json`
  - `ENGINE_EVAL_QDRANT_RUN_OUTPUT_JSON` → `qdrant-run-output.<candidate-id>.json`
  - `ENGINE_EVAL_QDRANT_CANDIDATE_ID` → the chosen candidate id string
  - `ENGINE_EVAL_EXPECTED_ROLES_JSON` → `expected-roles.semantic-broad-smoke.json`
- [ ] **Resource availability assessed**: noted whether ES and Qdrant resources are expected to be available or may cancel (Docker-backed ES via `BeautySearchElasticsearchIntegrationSpec`; Qdrant benchmark executor defaults to local endpoints and cancels when unavailable).
- [ ] **Validation and cancels recorded**: planned what validation will actually be run and whether any resource-unavailable cancels are expected; will record both in `manifest.md`.
- [ ] **Offline/eval-only confirmed**: confirmed this session is offline/eval-only — no production `/beauty-search` route changes, no hybrid serving, no routing decisions from benchmark output, and no production automation signals.

### Marker payload extraction

Saved test output logs contain JSON or text payloads delimited by `BEGIN_*` / `END_*` marker comments. Extract them with a read-only, one-shot shell helper that uses `awk` (available on macOS and Linux). The helper accepts exact begin and end marker strings so it works for both JSON and non-JSON markers without internal suffix mangling:

```bash
extract_marker() {
  local log_file="$1" begin="$2" end="$3"
  awk -v begin="$begin" -v end="$end" '
    $0 == begin { found=1; next }
    $0 == end { found=0; next }
    found { print }
  ' "$log_file"
}
```

Usage examples (replace `log.txt` with your saved test output file):

```bash
# ES eval reports JSON
extract_marker log.txt "BEGIN_ENGINE_EVAL_ES_REPORTS_JSON" "END_ENGINE_EVAL_ES_REPORTS_JSON" > es_reports.json

# ES expected roles JSON
extract_marker log.txt "BEGIN_ENGINE_EVAL_EXPECTED_ROLES_JSON" "END_ENGINE_EVAL_EXPECTED_ROLES_JSON" > expected_roles.json

# Qdrant benchmark run-output JSON
extract_marker log.txt "BEGIN_QDRANT_EMBEDDING_BENCHMARK_RUN_OUTPUT_JSON" "END_QDRANT_EMBEDDING_BENCHMARK_RUN_OUTPUT_JSON" > qdrant_run_output.json

# EngineEval aggregate report JSON
extract_marker log.txt "BEGIN_ENGINE_EVAL_AGGREGATE_REPORT_JSON" "END_ENGINE_EVAL_AGGREGATE_REPORT_JSON" > aggregate_report.json
```

Non-JSON markers use the same pattern with their exact begin/end markers:

```bash
# EngineEval aggregate report text (non-JSON)
extract_marker log.txt "BEGIN_ENGINE_EVAL_AGGREGATE_REPORT" "END_ENGINE_EVAL_AGGREGATE_REPORT" > aggregate_report.txt

# EngineEval saved comparison text
extract_marker log.txt "BEGIN_ENGINE_EVAL_SAVED_REPORT_COMPARISON" "END_ENGINE_EVAL_SAVED_REPORT_COMPARISON" > comparison.txt
```

This helper is read-only: it does not run `sbt`, Docker, or mutate any repo files. It works on any saved log file with standard shell tools. Operator-controlled extraction reduces copy-paste errors while keeping payload selection explicit.

### Saved artifact naming and manifest

Recommended directory shape for a single M-ESQ-EVAL run:

```
tmp/m-esq-eval/<YYYYMMDD-HHMMSS>-<short-label>/
```

Example: `tmp/m-esq-eval/20260614-143000-sem-broad-001/`

Recommended filenames for extracted artifacts:

* `es-reports.semantic-broad-smoke.json` — ES eval reports JSON from `BEGIN_ENGINE_EVAL_ES_REPORTS_JSON` / `END_ENGINE_EVAL_ES_REPORTS_JSON`
* `expected-roles.semantic-broad-smoke.json` — ES expected roles JSON from `BEGIN_ENGINE_EVAL_EXPECTED_ROLES_JSON` / `END_ENGINE_EVAL_EXPECTED_ROLES_JSON`
* `qdrant-run-output.<candidate-id>.json` — Qdrant run-output JSON from `BEGIN_QDRANT_EMBEDDING_BENCHMARK_RUN_OUTPUT_JSON` / `END_QDRANT_EMBEDDING_BENCHMARK_RUN_OUTPUT_JSON`
* `engine-eval-aggregate.<candidate-id>.json` — EngineEval aggregate report JSON from `BEGIN_ENGINE_EVAL_AGGREGATE_REPORT_JSON` / `END_ENGINE_EVAL_AGGREGATE_REPORT_JSON`
* `engine-eval-comparison.<left-label>--<right-label>.txt` — optional saved comparison text from `BEGIN_ENGINE_EVAL_SAVED_REPORT_COMPARISON` / `END_ENGINE_EVAL_SAVED_REPORT_COMPARISON`
* `manifest.md` — optional bookkeeping manifest (template below)

`manifest.md` template:

```markdown
# M-ESQ-EVAL Artifact Manifest

| Field | Value |
|---|---|
| date/time | |
| operator | |
| repo commit / branch / status note | |
| ES artifact source (marker / log file) | |
| Qdrant candidate id | |
| Qdrant run-output source (marker / log file) | |
| expected roles file | |
| aggregate report file | |
| optional comparison inputs / output | |
| validation actually run | |
| notes / non-goals | |
```

This manifest is for local evidence bookkeeping only. It does not indicate production readiness, routing policy, or hybrid serving approval.

### Evidence artifact sanity checklist

Before using manually collected M-ESQ-EVAL artifacts as evidence for later Qdrant shadow/hybrid decisions, verify:

- [ ] Source logs are saved and referenced in `manifest.md` (ES artifact source, Qdrant run-output source).
- [ ] All expected `BEGIN_*` / `END_*` marker pairs are present in source logs before extraction: `BEGIN_ENGINE_EVAL_ES_REPORTS_JSON`/`END_ENGINE_EVAL_ES_REPORTS_JSON`, `BEGIN_ENGINE_EVAL_EXPECTED_ROLES_JSON`/`END_ENGINE_EVAL_EXPECTED_ROLES_JSON`, `BEGIN_QDRANT_EMBEDDING_BENCHMARK_RUN_OUTPUT_JSON`/`END_QDRANT_EMBEDDING_BENCHMARK_RUN_OUTPUT_JSON`, `BEGIN_ENGINE_EVAL_AGGREGATE_REPORT_JSON`/`END_ENGINE_EVAL_AGGREGATE_REPORT_JSON`.
- [ ] Extracted files use the documented naming convention: `es-reports.semantic-broad-smoke.json`, `expected-roles.semantic-broad-smoke.json`, `qdrant-run-output.<candidate-id>.json`, `engine-eval-aggregate.<candidate-id>.json`.
- [ ] ES reports artifact corresponds to `SemanticBroadSmoke` (not another eval subset).
- [ ] ES report query ids are exactly `q_broad_001` through `q_broad_006` in order.
- [ ] Expected roles artifact contains the current six-query role map exactly: `q_broad_001` → `EsShouldHandle`, `q_broad_002` → `EsShouldHandle`, `q_broad_003` → `QdrantMayComplement`, `q_broad_004` → `HybridMayImprove`, `q_broad_005` → `EsShouldHandle`, `q_broad_006` → `QdrantMayComplement`.
- [ ] Qdrant run-output artifact candidate id matches the `manifest.md` Qdrant candidate id field and the `ENGINE_EVAL_QDRANT_CANDIDATE_ID` used for assembly.
- [ ] EngineEval assembly used matching ES reports, Qdrant run-output, candidate id, and expected roles (all four env vars set to the correct extracted files/artifacts).
- [ ] Aggregate report JSON is extracted from the `BEGIN_ENGINE_EVAL_AGGREGATE_REPORT_JSON` / `END_ENGINE_EVAL_AGGREGATE_REPORT_JSON` markers.
- [ ] Optional saved comparison records left/right labels and filenames are recorded in `manifest.md` (if comparison was run).
- [ ] Validation actually run is recorded in `manifest.md`, including cancels or resource unavailability if applicable.
- [ ] Notes/non-goals in `manifest.md` explicitly preserve offline/eval-only and non-production-routing status.

This checklist is for operator verification of locally collected artifacts. It does not constitute production approval, routing policy, or hybrid serving authorization.

### Saved comparison interpretation notes

`EngineEvalSavedReportComparisonManualSpec` runs in saved-comparison mode when `ENGINE_EVAL_COMPARE_SAVED_REPORTS=1` is set along with `ENGINE_EVAL_LEFT_JSON` and `ENGINE_EVAL_RIGHT_JSON`. Output is delimited by `BEGIN_ENGINE_EVAL_SAVED_REPORT_COMPARISON` / `END_ENGINE_EVAL_SAVED_REPORT_COMPARISON`.

**What comparison output is useful for:**

- Comparing two aggregate evidence snapshots from different runs or candidates.
- Spotting query-level or aggregate movement between candidates/runs (deltas for `queryCount`, `esRecallCount`, `qdrantRecallCount`, `qdrantComplementCount`, `qdrantNoiseCount`, `overlapCount`, `simulatedHybridGainCount`).
- Spotting role-level movement between candidates/runs when role deltas are non-zero (see roleDeltas below).
- Supporting later Qdrant shadow/hybrid decisions with recorded evidence.
- Recording evidence in `manifest.md` alongside the compared files and left/right labels.

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
- Not a substitute for manual review of artifact inputs, the sanity checklist, and the manifest.

Left/right labels for the compared files should be recorded in `manifest.md` alongside the file paths. Resource-unavailable cancels and validation actually run must be recorded separately in `manifest.md`; comparison output alone is not the validation record.

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
