# M-ESQ-EVAL Evidence Run Template

**Local operator template. Not a committed evidence result.**

Copy/adapt this template before collecting M-ESQ-EVAL evidence. All commands are manual. This is offline/eval-only.

## Non-goals

This template does **not** represent:

* Production readiness
* Route switch approval
* Fallback enablement
* Score fusion or reranking
* HybridServe activation
* Qdrant auto-supplement approval

## Workspace

Create an explicitly non-repo workspace:

```bash
STAMP="$(date +%Y%m%d-%H%M%S)"
LABEL="sem-broad-001"
WORK="$HOME/.beautyq-evidence-runs/${STAMP}-${LABEL}"
mkdir -p "$WORK"/{logs,artifacts,notes}
echo "$WORK"
```

**Do not use repo-local `tmp/`** unless intentionally accepting an untracked workspace that will appear in `git status` and can confuse review/commit status.
`/tmp` is reserved here for disposable review bundles or short-lived scratch output.

Recommended workspace shape:

```
$HOME/.beautyq-evidence-runs/<YYYYMMDD-HHMMSS>-<label>/
├── logs/
├── artifacts/
├── notes/
├── manifest.md
├── commands.md
└── extract.sh
```

Bundle the workspace only after a run is complete:

```bash
ZIP="$HOME/.beautyq-evidence-runs/${STAMP}-${LABEL}.zip"
rm -f "$ZIP"
(cd "$WORK/.." && zip -9 -r "$ZIP" "$(basename "$WORK")") >/dev/null
wc -c "$ZIP"
cpf "$ZIP"
echo "$ZIP"
```

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
| optional comparison inputs / output | |
| validation actually run | |
| notes / non-goals | |
```

## Lessons learned

1. **Workspace location**: Use `$HOME/.beautyq-evidence-runs/...` for the persisted run workspace, not repo-local `tmp/`. Repo-local `tmp/` appears as untracked and can confuse review/commit status.
2. **Multi-payload Qdrant extraction**: Raw Qdrant extraction may contain multiple adjacent JSON payloads. Split/select into a valid single JSON payload before feeding into `ENGINE_EVAL_QDRANT_RUN_OUTPUT_JSON`. Do not concatenate raw payloads.
