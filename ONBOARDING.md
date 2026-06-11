Ты новый координатор по BeautyQ repo.



Используй приложенный handoff bundle и документацию как source of truth.



Читай в таком порядке:



1. `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md`

2. `AGENTS.md`

3. `docs/local/COORDINATOR_PROMPTING_REMINDER.md`

4. `docs/LOCAL_LLM_MODEL_SELECTION_POLICY.md`

5. `docs/codebase-review/README.md`

6. source anchors included in the bundle



Главные правила:



* Current state и приоритеты бери из handoff doc.

* Repo/agent guardrails бери из `AGENTS.md`.

* Workflow координатора, source-truth gate, bundles, model recommendations бери из `COORDINATOR_PROMPTING_REMINDER.md`.

* Перед любой новой задачей сначала проверь source truth для нужных files/types/functions/fields.

* Если нужного source truth нет в текущем bundle/context, попроси focused bundle и остановись.

* Не придумывай patch API, method signatures, field mappings, tests или delegated-agent prompts из docs/memory.

* Не делегируй MiniMax/Qwen/MiMo broad audit/design. Сначала сам сделай дизайн по source-confirmed facts, потом дай агенту exact read/edit files.



First, summarize the current state in 10 bullets and list the next safest task. Do not propose code changes until asked.

OUT="/tmp/beautyq-old-prompt-onboarding-$(date +%Y%m%d-%H%M%S)-$RANDOM.txt"

{
echo "## status"
git status --short
echo

echo "## recent commits"
git --no-pager log -8 --oneline
echo

echo "## root README entrypoint"
sed -n '1,120p' README.md
echo

echo "## canonical handoff"
sed -n '1,340p' docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md
echo

echo "## AGENTS.md"
sed -n '1,320p' AGENTS.md
echo

echo "## coordinator prompting reminder"
sed -n '1,520p' docs/local/COORDINATOR_PROMPTING_REMINDER.md
echo

echo "## model selection policy"
sed -n '1,300p' docs/LOCAL_LLM_MODEL_SELECTION_POLICY.md
echo

echo "## codebase review README"
sed -n '1,220p' docs/codebase-review/README.md
echo

echo "## current-state doc anchors"
rg -n "production-exposed|seedCatalogInMemory|InMemorySearchBackend|not Elasticsearch|not Qdrant|not hybrid|B-lite|M-ESQ-EVAL|EngineEval|source-truth gate|focused bundle|Do not salvage|bundle request, not a patch proposal|requested output shape|Coordinator workflow|canonical owner|Model recommendation|MiniMax-M3|GPT-5.5-medium|GPT-5.5-high" \
README.md \
docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md \
AGENTS.md \
docs/local/COORDINATOR_PROMPTING_REMINDER.md \
docs/LOCAL_LLM_MODEL_SELECTION_POLICY.md \
docs/codebase-review/README.md \
docs/codebase-review/05-search-and-retrieval-architecture.md \
docs/codebase-review/07-current-gaps-and-roadmap.md \
docs/search-dsl-hybrid-v1-plan.md \
docs/beautyq-search-dsl-v1.md \
| sed -n '1,600p'
echo

echo "## source anchor: production route wiring"
for f in \
bifunctor-tagless/src/main/scala/leaderboard/plugins/LeaderboardPlugin.scala \
bifunctor-tagless/src/main/scala/leaderboard/search/BeautySearchRouteModules.scala; do
echo
echo "### $f"
if [ -f "$f" ]; then
rg -n -C 4 "modules\\.api|seedCatalogInMemory|BeautySearchRouteModules|BeautySearchCatalogBackendModules|seedResourceInMemory|InMemorySearchBackend|BeautySearchService\\.Impl|BeautySearchApi" "$f" || true
else
echo "MISSING"
fi
done
echo

echo "## source anchor: EngineEval only"
for f in \
bifunctor-tagless/src/main/scala/leaderboard/search/eval/EngineEval.scala \
bifunctor-tagless/src/test/scala/leaderboard/search/EngineEvalSpec.scala; do
echo
echo "### $f"
if [ -f "$f" ]; then
sed -n '1,260p' "$f"
else
echo "MISSING"
fi
done
echo

echo "## intentionally omitted anchors"
cat <<'EOF'
This onboarding bundle intentionally does NOT include full M-ESQ-EVAL seam source:
- BeautySearchEval.scala
- BeautySearchEvalInventory.scala
- BeautySearchEvalTestSupport.scala
- BeautySearchElasticsearchIntegrationSpec.scala
- ElasticsearchSearchRequestInterpreter / ElasticsearchSearchResponseInterpreter / BeautySearchEvalScorer full source
- LexicalDocumentHit definitions
- QdrantSemanticCandidateEvalSpec.scala
- QdrantEmbeddingBenchmark*.scala
- Qdrant candidate/result type definitions
- HybridDocumentRetrievalResult / BeautyQHybridResponsePipeline full source

A correct coordinator should request a focused bundle before designing ES/Qdrant adapters or exact patch APIs.
EOF
echo

echo "## stale contradiction scan"
rg -n "Still no production route wiring|Search HTTP exposure is not production-wired|Beauty search remains a design boundary|Production search API boundary is uncertain|Is there a production HTTP route|does not make the route production-exposed|does not wire a production search service graph|in-memory Elasticsearch|ES oracle|auto-supplement|HybridServe follows|10/10|10 tests|848 tests|859 tests" \
README.md AGENTS.md docs || true
} > "$OUT" 2>&1

python3 - <<'PY' "$OUT"
import pathlib, sys
p = pathlib.Path(sys.argv[1])
data = p.read_text(errors="replace")
limit = 700_000
if len(data.encode()) > limit:
p.write_text(data[:limit] + "\n\n## TRUNCATED TO 700KB\n")
PY

wc -c "$OUT"
cpf "$OUT"
echo "$OUT"
