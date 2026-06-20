# M-ESQ-EVAL Evidence Run Template

**Local operator template. Not a committed evidence result.**

Copy/adapt this template before collecting M-ESQ-EVAL evidence. All commands are manual. This is offline/eval-only.

Metric naming in this template remains the current evidence-run format. Future evidence reports should align with the implemented shared M8/M9 contracts in `leaderboard.search.eval.M8M9EvalContracts`: `ServingMode`, `CandidateSource`, `QueryClass`, `CatalogSnapshotId`, `EvalDatasetId`, `MetricWindow`, and `OfflineEvalMetricName`.

The current `EngineEvalAggregateReport` and saved-report JSON format remain unchanged. `leaderboard.search.eval.M9OfflineEvalSavedReport` adds a pure saved dataset/report model and deterministic markdown renderer for the planned M9 format. `leaderboard.search.eval.M9OfflineEvalStaticRunner` can assemble that saved report and markdown artifact from static/in-memory caller-supplied inputs. `leaderboard.search.eval.M9OfflineEvalStaticFixtures` plus the checked-in `bifunctor-tagless/src/test/resources/leaderboard/search/eval/m9-static-example-report.md` show the canonical saved M9 artifact shape for future evidence. They do not add telemetry emission, ES/Qdrant backend execution, route behavior, hybrid serving, fusion, or reranking.

## Non-goals

This template does **not** represent:

* Production readiness
* Route switch approval
* Fallback enablement
* Score fusion or reranking
* HybridServe activation
* Qdrant auto-supplement approval
* Shadow-serving or production traffic-mirroring evidence

There is no real production traffic in this project context. Treat all metrics collected here as offline seed/eval evidence over curated canonical seed queries and representative seed/eval fixture queries. Include regression, edge, and negative cases when selecting query subsets so the evidence is not just a narrow happy-path fit.

## Activation evidence boundary

An assembled or decoded saved `EngineEvalAggregateReport` may be evaluated by `QdrantProductionCandidateQualityGate.fromEngineEval`. Its quality decision may then be mapped to no-regression evidence by `QdrantProductionCandidateActivationConfigApproval.noRegressionEvidenceFromQuality`.

This mapping is evidence-facing only: passed quality supplies satisfied evidence, failed quality supplies missing/unsatisfied evidence, and absent or incomplete quality remains unknown. It does not approve that evidence, enable a config gate, approve route/serving, or change production behavior.

For the explicit opt-in Qdrant route, this run can supply only the saved/offline eval and no-regression part of the prerequisite package. Before assembling `BeautySearchRouteModules.apiQdrantExplicitOptIn`, the operator still needs a ready M6 report, ready M7 activation-policy report, enabled config gate, explicitly approved no-regression evidence, observability/status evidence, rollback/disable control, focused route/module safety checks, and separate explicit route/serving approval. Full verification remains a coordinator/user step.

For a bounded opt-in assembly smoke checklist, see `docs/local/QDRANT_EXPLICIT_OPTIN_ROUTE_SMOKE_CHECKLIST.md`. That checklist is optional, resource-gated, and not a production-activation path.

## Workspace

Create a project-local workspace by default:

```bash
STAMP="$(date +%Y%m%d-%H%M%S)"
LABEL="sem-broad-001"
WORK="./.beautyq-evidence-runs/${STAMP}-${LABEL}"
mkdir -p "$WORK"/{logs,artifacts,notes}
echo "$WORK"
```

Make sure `./.beautyq-evidence-runs/` is ignored before use, either by adding it to `.gitignore` or by adding the path to `.git/info/exclude`.

**Do not use repo-local `tmp/`** unless intentionally accepting an untracked workspace that will appear in `git status` and can confuse review/commit status.
`/tmp` is reserved here for disposable review bundles or short-lived scratch output.
Use `$HOME` or another external path only when the user explicitly asks for an external workspace.

Recommended workspace shape:

```
./.beautyq-evidence-runs/<YYYYMMDD-HHMMSS>-<label>/
├── logs/
├── artifacts/
├── notes/
├── manifest.md
├── commands.md
└── extract.sh
```

Suggested local artifact filenames for saved-report comparisons:

* `left-query-classes.json`
* `right-query-classes.json`
* candidate-specific variants such as `query-classes.benchmark-small.json`
* candidate-specific variants such as `query-classes.benchmark-large.json`
* M9 saved markdown artifacts such as `m9-offline-eval.<label>.md`, when a manual/future evidence step materializes `M9OfflineEvalSavedReport` directly or through the static runner shape

Use `bifunctor-tagless/src/test/resources/leaderboard/search/eval/m9-static-example-report.md` as the canonical format example for future saved M9 markdown evidence. It is an example-only static fixture artifact with `sample_not_for_activation`; current backend/eval runs and existing `EngineEvalAggregateReport` JSON artifacts remain separate.

Bundle the workspace only after a run is complete:

```bash
ZIP="./.beautyq-evidence-runs/${STAMP}-${LABEL}.zip"
rm -f "$ZIP"
(cd "$WORK/.." && zip -9 -r "$ZIP" "$(basename "$WORK")") >/dev/null
cpf "$ZIP"
echo "$ZIP"
```

Note: `cpf "$ZIP"` is the intended user-terminal handoff step. It can be absent in agent/non-interactive shells; if that happens during an artifact run, still create the zip, print `echo "$ZIP"`, and report the `command not found` deviation without replacing `cpf "$ZIP"` with `pbcopy`.

## `extract.sh`

Single-quoted heredoc (`<<'EOF'`) prevents shell expansion inside the awk script. This avoids nested markdown-fence hazards. POSIX sh, no `set` flags.

```bash
cat > "$WORK/extract.sh" <<'EOF'
#!/bin/sh
# Usage: ./extract.sh <log-file> <begin-marker> <end-marker>
awk -v begin="$2" -v end="$3" '
  $0 == begin { found=1; next }
  $0 == end { found=0; next }
  found { print }
' "$1"
EOF
chmod +x "$WORK/extract.sh"
```

Usage:

```bash
"$WORK/extract.sh" "$WORK/logs/es-log.txt" \
  "BEGIN_ENGINE_EVAL_ES_REPORTS_JSON" "END_ENGINE_EVAL_ES_REPORTS_JSON" \
  > "$WORK/artifacts/es-reports.semantic-broad-smoke.json"
```

## Commands

All commands are manual operator steps. Run each individually and verify before proceeding.

### 1. ES artifact emission

```bash
ENGINE_EVAL_PRINT_ES_ARTIFACTS=1 \
sbt "bifunctor-tagless / Test / testOnly leaderboard.search.BeautySearchElasticsearchIntegrationSpec" \
  > "$WORK/logs/es-log.txt" 2>&1
```

Extract ES artifacts:

```bash
"$WORK/extract.sh" "$WORK/logs/es-log.txt" \
  "BEGIN_ENGINE_EVAL_ES_REPORTS_JSON" "END_ENGINE_EVAL_ES_REPORTS_JSON" \
  > "$WORK/artifacts/es-reports.semantic-broad-smoke.json"

"$WORK/extract.sh" "$WORK/logs/es-log.txt" \
  "BEGIN_ENGINE_EVAL_EXPECTED_ROLES_JSON" "END_ENGINE_EVAL_EXPECTED_ROLES_JSON" \
  > "$WORK/artifacts/expected-roles.semantic-broad-smoke.json"
```

### 2. Qdrant run-output collection

```bash
# Defaults to local endpoints http://localhost:8081 / http://localhost:8082
sbt "bifunctor-tagless / Test / testOnly leaderboard.search.QdrantEmbeddingBenchmarkExecutorIntegrationSpec" \
  > "$WORK/logs/qdrant-log.txt" 2>&1
```

Extract raw Qdrant run-output:

```bash
"$WORK/extract.sh" "$WORK/logs/qdrant-log.txt" \
  "BEGIN_QDRANT_EMBEDDING_BENCHMARK_RUN_OUTPUT_JSON" "END_QDRANT_EMBEDDING_BENCHMARK_RUN_OUTPUT_JSON" \
  > "$WORK/artifacts/qdrant-run-output.raw.json"
```

### 3. Qdrant raw payload splitting/selection

**Warning:** Raw Qdrant extraction may contain multiple adjacent JSON payloads. The first run produced two payloads: first with candidate `benchmark-single`, second with candidates `benchmark-small` and `benchmark-large`. The raw file is **not** valid as one JSON document until split/selected.

Inspect and split raw output using `json.JSONDecoder().raw_decode`, which extracts one complete JSON object at a time from an arbitrary byte stream:

```bash
python3 - <<'PY' "$WORK/artifacts/qdrant-run-output.raw.json" "$WORK/artifacts"
import json
import pathlib
import sys

raw_path = pathlib.Path(sys.argv[1])
out_dir = pathlib.Path(sys.argv[2])
raw = raw_path.read_text()

decoder = json.JSONDecoder()
idx = 0
payloads = []
while idx < len(raw):
    # skip whitespace / non-JSON text between payloads
    while idx < len(raw) and raw[idx] in " \t\n\r":
        idx += 1
    if idx >= len(raw):
        break
    try:
        obj, end_idx = decoder.raw_decode(raw, idx)
        payloads.append((obj, raw[idx:idx + end_idx - idx]))
        idx = end_idx
    except json.JSONDecodeError:
        break

print(f"Found {len(payloads)} JSON payload(s)")
for i, (obj, _) in enumerate(payloads):
    print(f"  Payload {i}: top keys = {list(obj.keys())[:5]}")

# Select payload by content -- adapt filter as needed
# Example: pick the one containing "benchmark-small"
selected_text = None
for obj, text in payloads:
    if "benchmark-small" in text:
        selected_text = text
        break

if selected_text is None:
    print("BLOCKED: no matching payload found", file=sys.stderr)
else:
    (out_dir / "qdrant-run-output.selected.json").write_text(selected_text)
    print("Selected payload written")
PY
```

Do not feed concatenated raw JSON into `ENGINE_EVAL_QDRANT_RUN_OUTPUT_JSON`. Only the selected/split payload should be used for assembly.

### 4. EngineEval aggregate assembly

```bash
export ENGINE_EVAL_ASSEMBLE_SAVED_REPORT=1
export ENGINE_EVAL_ES_REPORTS_JSON="$(cat "$WORK/artifacts/es-reports.semantic-broad-smoke.json")"
export ENGINE_EVAL_QDRANT_RUN_OUTPUT_JSON="$(cat "$WORK/artifacts/qdrant-run-output.selected.json")"
export ENGINE_EVAL_QDRANT_CANDIDATE_ID="benchmark-small"
export ENGINE_EVAL_EXPECTED_ROLES_JSON="$(cat "$WORK/artifacts/expected-roles.semantic-broad-smoke.json")"

sbt "bifunctor-tagless / Test / testOnly leaderboard.search.EngineEvalSavedReportAssemblyManualSpec" \
  > "$WORK/logs/assembly-log.txt" 2>&1
```

Extract aggregate report:

```bash
"$WORK/extract.sh" "$WORK/logs/assembly-log.txt" \
  "BEGIN_ENGINE_EVAL_AGGREGATE_REPORT_JSON" "END_ENGINE_EVAL_AGGREGATE_REPORT_JSON" \
  > "$WORK/artifacts/engine-eval-aggregate.benchmark-small.json"
```

### 5. Optional saved comparison

To compare two aggregate reports (e.g. from different candidate ids or runs):

```bash
export ENGINE_EVAL_COMPARE_SAVED_REPORTS=1
export ENGINE_EVAL_LEFT_JSON="$(cat "$WORK/artifacts/engine-eval-aggregate.benchmark-small.json")"
export ENGINE_EVAL_RIGHT_JSON="$(cat "$WORK/artifacts/engine-eval-aggregate.benchmark-large.json")"

sbt "bifunctor-tagless / Test / testOnly leaderboard.search.EngineEvalSavedReportComparisonManualSpec" \
  > "$WORK/logs/comparison-log.txt" 2>&1
```

Optional paired query-class sidecars are also supported for manual saved-report comparison:

* Required saved-comparison env vars remain:
  * `ENGINE_EVAL_COMPARE_SAVED_REPORTS`
  * `ENGINE_EVAL_LEFT_JSON`
  * `ENGINE_EVAL_RIGHT_JSON`
* Optional paired class sidecar env vars:
  * `ENGINE_EVAL_LEFT_QUERY_CLASSES_JSON`
  * `ENGINE_EVAL_RIGHT_QUERY_CLASSES_JSON`
* Class-sidecar replay for `benchmark-small -> benchmark-large` requires existing saved aggregate JSON artifacts for both sides before comparison can run:
  * `engine-eval-aggregate.benchmark-small.json`
  * `engine-eval-aggregate.benchmark-large.json`
* Restore/import those aggregate artifacts from a prior evidence archive or prior project-local evidence workspace into the current workspace, for example:
  * `./.beautyq-evidence-runs/<run-id>/artifacts/engine-eval-aggregate.benchmark-small.json`
  * `./.beautyq-evidence-runs/<run-id>/artifacts/engine-eval-aggregate.benchmark-large.json`
* If the saved aggregate JSON artifacts are missing, replay is blocked. Do not rerun the full evidence collection pipeline unless explicitly requested.
* Once the aggregate JSON artifacts are present, the sidecar files can be derived and manual saved comparison can run with the class-sidecar env vars above.
* If neither class sidecar env var is present, saved comparison keeps the previous aggregate/role/query behavior and emits no `classDeltas:`.
* If both class sidecar env vars are present, comparison may emit `classDeltas:`.
* If exactly one class sidecar env var is present, comparison fails clearly and names the missing counterpart.

Example with class sidecars:

```bash
LEFT_CLASSES="$WORK/artifacts/query-classes.benchmark-small.json"
RIGHT_CLASSES="$WORK/artifacts/query-classes.benchmark-large.json"

export ENGINE_EVAL_COMPARE_SAVED_REPORTS=1
export ENGINE_EVAL_LEFT_JSON="$(cat "$WORK/artifacts/engine-eval-aggregate.benchmark-small.json")"
export ENGINE_EVAL_RIGHT_JSON="$(cat "$WORK/artifacts/engine-eval-aggregate.benchmark-large.json")"
export ENGINE_EVAL_LEFT_QUERY_CLASSES_JSON="$(cat "$LEFT_CLASSES")"
export ENGINE_EVAL_RIGHT_QUERY_CLASSES_JSON="$(cat "$RIGHT_CLASSES")"

sbt "bifunctor-tagless / Test / testOnly leaderboard.search.EngineEvalSavedReportComparisonManualSpec" \
  > "$WORK/logs/comparison-log.with-classes.txt" 2>&1
```

Query-class sidecar JSON shape:

```json
{ "q_broad_005": ["PriceDuration", "BroadIntent"] }
```

Class names must be exact `EngineEvalQueryClass` names:

* `ExactService`
* `Category`
* `StructuredFilter`
* `PriceDuration`
* `GeoLocal`
* `SemanticVague`
* `BroadIntent`
* `HardNegative`
* `Mixed`

Failure behavior:

* Invalid class names fail with `QueryFailure.operation` and name the invalid value.
* Missing sidecar query ids fail through `EngineEvalQueryClassBreakdown` and name the missing query id.
* Saved aggregate JSON schema remains unchanged.
* `EngineEvalAggregateReport` remains unchanged.
* `EngineEvalReportJson` remains unchanged.

Extract comparison:

```bash
"$WORK/extract.sh" "$WORK/logs/comparison-log.txt" \
  "BEGIN_ENGINE_EVAL_SAVED_REPORT_COMPARISON" "END_ENGINE_EVAL_SAVED_REPORT_COMPARISON" \
  > "$WORK/artifacts/engine-eval-comparison.benchmark-small--benchmark-large.txt"
```

## First-run example outcome

Example results from one offline evidence run. These are **not** production approval.

| Subset | Qdrant recall | Complement | Noise | Overlap | Simulated hybrid gain |
|---|---|---|---|---|---|
| `benchmark-single` | 6 | 4 | 0 | 2 | 4 |
| `benchmark-small` | 6 | 4 | 0 | 2 | 4 |
| `benchmark-large` | 22 | 7 | 0 | 15 | 7 |

Saved comparisons from that run:

* `benchmark-small--benchmark-large`
* `benchmark-single--benchmark-small`

## `manifest.md` template

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
| restored benchmark-small aggregate source / import path | |
| restored benchmark-large aggregate source / import path | |
| left class sidecar path | |
| right class sidecar path | |
| sidecar source / derivation note | |
| optional comparison inputs / output | |
| class-delta comparison output path | |
| M9 report format version | |
| M9 generated_at | |
| M9 eval_dataset_id | |
| M9 catalog_snapshot_id | |
| M9 serving_mode / candidate_source | |
| M9 aggregate metrics | |
| M9 quality gate decision | |
| M9 notes / warnings | |
| validation actually run | |
| notes / non-goals | |
```

## Lessons learned

1. **Workspace location**: Use `./.beautyq-evidence-runs/...` for the persisted run workspace by default, and keep it gitignored or listed in `.git/info/exclude`. Repo-local `tmp/` appears as untracked and can confuse review/commit status. Use `$HOME` only when the user explicitly asks for an external workspace.
2. **Multi-payload Qdrant extraction**: Raw Qdrant extraction may contain multiple adjacent JSON payloads. Split/select into a valid single JSON payload before feeding into `ENGINE_EVAL_QDRANT_RUN_OUTPUT_JSON`. Do not concatenate raw payloads.
