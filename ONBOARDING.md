Ты новый координатор по BeautyQ repo.

Не восстанавливай контекст из догадок. Используй приложенный onboarding bundle и документацию как source of truth.

Читай в таком порядке:

1. `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md`
2. `AGENTS.md`
3. `docs/local/COORDINATOR_PROMPTING_REMINDER.md`
4. `docs/LOCAL_LLM_MODEL_SELECTION_POLICY.md`
5. `docs/codebase-review/README.md`
6. source anchors included in the bundle

Правила:

* Current state и приоритеты бери из handoff doc.
* Repo/agent guardrails бери из `AGENTS.md`.
* Workflow координатора, source-truth gate, bundles, documentation ownership и model recommendations бери из `COORDINATOR_PROMPTING_REMINDER.md`.
* Перед любой задачей проверь source truth для нужных files/types/functions/fields.
* Если source truth отсутствует, попроси focused bundle и остановись.
* Если я прошу patch proposal, exact read/edit files, test recipe или delegated-agent prompt, но source truth отсутствует, явно откажись от этой части output, попроси focused bundle и остановись.
* Не придумывай helpers, adapters, method signatures, field mappings, merge rules, tests или adjacent “safe” patches из docs/memory.
* Не делегируй MiniMax/MiMo/Qwen broad audit/design. Сначала сам делай дизайн по source-confirmed facts, потом давай агенту exact read/edit files.

Первый ответ:

1. current state summary in 8–10 bullets;
2. current priority;
3. source anchors present in this bundle;
4. source anchors that would be needed before designing the next patch;
5. whether a focused bundle is needed before proposing any patch.

В первом ответе не предлагай code/docs changes, patch API, helper API, exact read/edit files или agent prompt.

OUT="/tmp/beautyq-new-coordinator-onboarding-$(date +%Y%m%d-%H%M%S)-$RANDOM.txt"

{
  echo "## status"
  git status --short
  echo

  echo "## recent commits"
  git --no-pager log -8 --oneline
  echo

  echo "## root README entrypoint"
  sed -n '1,140p' README.md
  echo

  echo "## canonical handoff"
  sed -n '1,380p' docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md
  echo

  echo "## AGENTS.md"
  sed -n '1,340p' AGENTS.md
  echo

  echo "## coordinator prompting reminder"
  sed -n '1,1100p' docs/local/COORDINATOR_PROMPTING_REMINDER.md
  echo

  echo "## model selection policy"
  sed -n '1,360p' docs/LOCAL_LLM_MODEL_SELECTION_POLICY.md
  echo

  echo "## codebase review README"
  sed -n '1,240p' docs/codebase-review/README.md
  echo

  echo "## current-state doc anchors"
  rg -n "production-exposed|apiBase\\[IO\\]|apiElasticsearch|ElasticsearchSearchBackend|ES-backed|seedCatalogInMemory|InMemorySearchBackend|rollback|non-default|not Qdrant|not hybrid|not production-wired|B-lite|M-ESQ-EVAL|EngineEval|EngineEvalQueryReport|source-truth gate|protected coordinator invariant|focused bundle and stop|Do not salvage|bundle request, not a patch proposal|adjacent.*safe|Documentation ownership|Model recommendation|MiniMax-M3|GPT-5.5-medium|GPT-5.5-high" \
    README.md \
    docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md \
    AGENTS.md \
    docs/local/COORDINATOR_PROMPTING_REMINDER.md \
    docs/LOCAL_LLM_MODEL_SELECTION_POLICY.md \
    docs/codebase-review/README.md \
    docs/codebase-review/04-api-and-http-contracts.md \
    docs/codebase-review/05-search-and-retrieval-architecture.md \
    docs/codebase-review/07-current-gaps-and-roadmap.md \
    docs/search-dsl-hybrid-v1-plan.md \
    docs/beautyq-search-dsl-v1.md \
    docs/search-dsl-qdrant-vector-backend.md \
    | sed -n '1,800p'
  echo

  echo "## source anchor: LeaderboardPlugin route graph"
  f="bifunctor-tagless/src/main/scala/leaderboard/plugins/LeaderboardPlugin.scala"
  echo "### $f"
  sed -n '1,220p' "$f" 2>/dev/null || echo "MISSING"
  echo

  echo "## source anchors: route/backend modules discovered by rg"
  for f in $(rg -l "apiElasticsearch|seedCatalogInMemory|ElasticsearchSearchBackend|InMemorySearchBackend|BeautySearchRouteModules|BeautySearchCatalogBackendModules|apiBase\\[IO\\]" \
    bifunctor-tagless/src/main/scala/leaderboard \
    bifunctor-tagless/src/test/scala/leaderboard 2>/dev/null | sort -u); do
    echo
    echo "### $f"
    rg -n "package |trait |class |object |def |val |include\\(|apiBase\\[IO\\]|apiElasticsearch|seedCatalogInMemory|ElasticsearchSearchBackend|InMemorySearchBackend|BeautySearchRouteModules|BeautySearchCatalogBackendModules|rollback|legacy" "$f" \
      | sed -n '1,260p'
  done
  echo

  echo "## source anchor: EngineEval current shape"
  for f in \
    bifunctor-tagless/src/main/scala/leaderboard/search/eval/EngineEval.scala \
    bifunctor-tagless/src/test/scala/leaderboard/search/EngineEvalSpec.scala \
    bifunctor-tagless/src/test/scala/leaderboard/search/eval/EngineEvalSpec.scala; do
    echo
    echo "### $f"
    if [ -f "$f" ]; then
      sed -n '1,360p' "$f"
    else
      echo "MISSING"
    fi
  done
  echo

  echo "## intentionally omitted task-specific anchors"
  cat <<'EOF'
This onboarding bundle intentionally omits most task-specific source.
It is not enough to design arbitrary patches.

For any next task, the coordinator must identify required source anchors.
If they are missing, request a focused bundle and stop.

Do not infer patch APIs, helper APIs, field mappings, tests, delegated-agent prompts, or adjacent "safe" patches from this onboarding bundle alone.
EOF
  echo

  echo "## stale contradiction scan"
  rg -n "Still no production route wiring|Search HTTP exposure is not production-wired|Beauty search remains a design boundary|Production search API boundary is uncertain|Is there a production HTTP route|does not make the route production-exposed|does not wire production Elasticsearch|does not wire a production search service graph|production.*InMemorySearchBackend|Elasticsearch remains non-production|Elasticsearch, Qdrant, and hybrid remain non-production|in-memory Elasticsearch|ES oracle|auto-supplement|HybridServe follows|Characterized/current|10/10|10 tests|848 tests|859 tests|FULL GREEN" \
    README.md AGENTS.md docs || true
} > "$OUT" 2>&1

python3 - <<'PY' "$OUT"
import pathlib, sys
p = pathlib.Path(sys.argv[1])
data = p.read_text(errors="replace")
limit = 800_000
if len(data.encode()) > limit:
    p.write_text(data[:limit] + "\n\n## TRUNCATED TO 800KB\n")
PY

wc -c "$OUT"
cpf "$OUT"
echo "$OUT"
