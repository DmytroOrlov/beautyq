# COORDINATOR_PROMPTING_REMINDER.md

This file is not AGENTS.md.

Purpose:
Remind the coordinator how to write cheap, precise prompts for different model classes.

Use this file when the coordinator starts writing broad, expensive, or ambiguous prompts.

---

# 1. Universal rules valid for all models

These rules apply to GPT, MiniMax, Qwen, MiMo, and other coding agents.

## 1.1 Role split

Coordinator does:

```text
architecture
audit
strategy
design
patch decomposition
risk decisions
```

Delegated agents do:

```text
bounded edits
focused tests
mechanical verification
small cleanup
claim verification
```

Correct workflow:

```text
user gives bundle
→ coordinator reads bundle
→ coordinator extracts exact facts/files/types
→ coordinator designs the next minimal patch
→ delegated agent receives a direct edit recipe
→ user/coordinator reviews patch
→ user runs full test when coordinator recommends it
```

Do not ask an agent to do broad architecture/audit if the coordinator can do it.

Use agents for final cheap verification only when claims are explicit:

```text
Verify these 7 claims in these 5 files.
Do not redesign.
Do not suggest architecture unless a claim is false.
Report only claim/evidence/gap.
```

---

## 1.2 Bundle usage

A bundle is primarily for the coordinator.

Bad prompt:

```text
Use the attached bundle and audit the repo.
```

Good workflow:

```text
1. Coordinator reads bundle.
2. Coordinator inlines relevant facts into prompt.
3. Agent receives exact files and exact task.
```

Prompt should not say:

```text
Use attached bundle.
Search the repo.
Find all relevant files.
Explore nearby architecture.
Plan the best approach.
```

Prompt should say:

```text
Read only:
- exact file A
- exact file B

Edit only:
- exact file C

Current facts:
- extracted fact 1
- extracted fact 2

Do exactly:
- step 1
- step 2
- step 3
```

---

## 1.3 Prompt must answer six questions

Every coding prompt should answer:

```text
1. What files to read?
2. What files to edit?
3. What exact behavior/types/tests to add?
4. What must not be touched?
5. What focused command proves done?
6. What should be reported?
```

Preferred skeleton:

```text
Task: feat(scope): exact small change

Read only:
- file 1
- file 2

Edit only:
- file 3
- file 4

Current facts:
- fact from bundle
- fact from bundle

Goal:
Add X.

Exact public API / wording / behavior:
...

Tests:
...

Forbidden:
...

Validation:
- git diff --check
- focused sbt command

Do not run full sbt test unless explicitly requested.

Report only:
- focused result
- deviations/compile fixes
```

---

## 1.4 Full test policy

Default delegated-agent mode:

```text
focused checks only
```

The user runs full `sbt test` when the coordinator recommends it.

Prompt wording:

```text
Do not run full sbt test.
```

Report wording:

```text
Focused result only. Full verification left to coordinator/user.
```

Never allow an agent to call focused checks `FULL GREEN`.

---

## 1.5 Suggested signatures are not sacred

Do not give a suggested API with unused params unless behavior uses them.

Bad:

```scala
def from(
  queryClass: EngineEvalQueryClass,
  ...
): Metrics
```

when `queryClass` is not used.

Better:

```text
Do not include queryClass in `from` unless metrics use it.
`EngineEvalQueryClass` is taxonomy metadata for future inventory classification.
```

Rule:

```text
Behavioral contract wins over prompt snippet.
If a suggested param is unused:
- use it in real behavior, or
- remove it and report the deviation.
```

Do not ask agents to preserve future-placeholder params.

---

## 1.6 Unused params / compiler flags

Do not let agents spend time investigating:

```text
- unused parameter warning or error?
- is -Wunused enabled?
- is -Werror enabled?
- Scala 3 default warnings?
- should I use val _ = x?
- should I use @nowarn?
- should I use @unused?
```

Default answer for pure code:

```text
If a param/import/local is unused and not an intentional dependency/lifecycle edge, remove it.
Do not use `val _ = x`.
Do not add `@nowarn`.
Do not add `@unused`.
```

`@unused` is only for intentional dependency/lifecycle/readiness edges:

```text
- Distage role dependencies
- seed readiness edges
- FK table creation order
- constructor deps that force graph construction
```

`@unused` is not for future placeholder params in pure functions.

---

## 1.7 Task-local facts belong in prompts

Do not make agents search for task-local facts.

Inline them.

Examples:

```text
exact package path
exact class names
exact imports
id construction helper
existing enum/case class names
exact test style
focused command
```

For BeautyQ eval/model tasks, inline if relevant:

```text
BeautySearchEval lives at:
bifunctor-tagless/src/main/scala/leaderboard/search/eval/BeautySearchEval.scala
```

If a type/import is likely to cause searching, provide it in the prompt.

---

## 1.8 Metrics semantics must be explicit

For eval/benchmark metrics, decide whether counts are:

```text
distinct entity ids
ranked slots
raw hits including duplicates
```

Default for B-lite engine comparison:

```text
counts are by distinct variant ids
```

Therefore:

```text
Recall/complement/overlap/gain/noise must not be inflated by duplicate ids.
Duplicate ids should be a separate validation failure or separate duplicate-count metric.
```

For Qdrant noise:

```text
qdrantNoiseCount counts Qdrant hits only when expected role says Qdrant should stay silent.
Otherwise qdrantNoiseCount = 0.
```

If Qdrant returns duplicates and noise is identity-based, use `qdrant.variantIds.toSet.size`, not `qdrant.variantIds.size`.

---

## 1.9 Test style defaults for pure eval/model code

For simple pure search/eval model tests:

```text
AnyWordSpec
deterministic UUID fixtures
direct assert(...)
no effects
no Distage
no runtime unsafe boilerplate
no Docker
no ES/Qdrant clients
```

Do not use:

```text
Ref
Atomic*
var
Recording*
Counting*
assert(true)
empty success branch
isInstanceOf
asInstanceOf
Option.get
null assertions
```

Use pattern matching when extracting ADTs/options.

Use direct equality for case objects.

---

## 1.10 Reports should be short

Good report:

```text
Focused result:
- command ...
- N tests passed

Deviations:
- removed unused suggested param X because behavior did not use it
```

Bad report:

```text
large architecture explanation
long confirmation checklist
full restatement of prompt
```

Coordinator checks patch/bundle. The agent report does not need to prove every negative in prose.

---

## 1.11 Commit messages

The user commits manually; delegated agents do not commit.

Non-trivial commits should use extended commit messages: subject + body.

Body should state behavior/result, verification, and explicit non-goals or unchanged production boundaries.

One-line commit messages are acceptable only for tiny mechanical cleanups.

Do not confuse short agent reports with short commit messages; reports may be short, commit messages should preserve project context.

When preparing a commit message in chat, provide the full extended message unless the user asks for short subject only.

## 1.12 What goes to AGENTS.md vs this file vs prompt

Put in `AGENTS.md` when it is stable repo behavior for all agents:

```text
no whole-plugin include in focused specs
@unused only for lifecycle/dependency edges
no Ref/Atomic/var recording spies
no assert(true)
no production hybrid wiring
benchmark is decision support, not production automation
```

Put in this coordinator reminder when it is about writing better prompts:

```text
inline bundle facts instead of "use attached bundle"
do not include model/thinking boilerplate
do not ask agents to audit broadly
include exact id construction/import hints
do not give unused suggested params
user runs full sbt test
agent reports only focused result/deviations
```

Put in the specific prompt when it is task-local:

```text
exact package path
exact class names
exact helper imports
exact UUID/id construction
whether queryClass is taxonomy-only
whether metric counts distinct ids or ranked slots
```

---

## 1.13 Bundle scripts

Bundles are for the coordinator, not delegated agents.

The user may give bundles to the coordinator so the coordinator can avoid broad repo search and write cheaper, more direct prompts.

After reading a bundle, the coordinator must inline important facts into the delegated prompt.

Do not write delegated prompts that say `use attached bundle`.

### Bundle script rules

* Bundle scripts must write to a unique `/tmp/beautyq-<topic>-<timestamp>-$RANDOM.txt`.
* Bundle scripts must include only task-relevant status, compact diff, signatures, nearby specs, docs anchors, and hazard scans.
* Bundle scripts should cap/truncate output when it may grow large.
* Bundle scripts must run `wc -c "$OUT"`.
* Bundle scripts must run `cpf "$OUT"` near the end.
* Bundle scripts must print `echo "$OUT"` last.
* `cpf "$OUT"` is required because the user relies on it to copy/upload the bundle.
* Do not include `/tmp`, full `target`, generated build output, screenshots, generic pasted files, stale numbered files, or broad `HEAD~N --patch` unless explicitly requested.

### Canonical shell shape

```bash
OUT="/tmp/beautyq-<topic>-$(date +%Y%m%d-%H%M%S)-$RANDOM.txt"

{
  echo "## status"
  git status --short
  echo

  echo "## relevant anchors"
  rg -n "PatternA|PatternB" AGENTS.md docs bifunctor-tagless/src/main bifunctor-tagless/src/test || true
} > "$OUT" 2>&1

python3 - <<'PY' "$OUT"
import pathlib, sys
p = pathlib.Path(sys.argv[1])
data = p.read_text(errors="replace")
limit = 700_000
if len(data.encode()) > limit:
    p.write_text(data[:limit] + "\n\n## TRUNCATED\n")
PY

wc -c "$OUT"
cpf "$OUT"
echo "$OUT"
```

---

# 2. GPT-specific rules

These rules apply when GPT is available and is the acting model/agent.

## 2.1 GPT can handle wider context, but should still receive a patch boundary

GPT can do architecture/audit better than MiniMax/Qwen/MiMo, but do not waste it on unbounded repo exploration when the coordinator already has a bundle.

Good GPT prompt:

```text
Here is the current design and evidence.
Check these assumptions.
Then implement this bounded patch.
```

Bad GPT prompt:

```text
Figure out the entire repo and decide what to do.
```

GPT can verify and improve design, but still needs:

```text
scope
non-goals
files or packages
validation command
```

---

## 2.2 GPT may be used for architecture/audit, but only when that is the explicit task

GPT is acceptable for:

```text
architecture review
cross-checking a design
finding contradictions in docs
choosing between two implementation strategies
summarizing tradeoffs
```

But do not mix:

```text
broad architecture audit
+ code edits
+ docs edits
+ full verification
```

in one prompt unless the user explicitly wants a large expensive run.

Preferred split:

```text
audit/design
→ patch prompt
→ cleanup prompt
→ verification
```

---

## 2.3 GPT prompts can include rationale; keep action surface small

GPT benefits from rationale, but action surface should remain narrow.

Good:

```text
Why:
- B-lite requires distinct-id metrics.
Edit:
- EngineEval.scala
- EngineEvalSpec.scala
```

Bad:

```text
Here is all roadmap context; update whatever seems necessary.
```

---

## 2.4 GPT can handle contradictions, but must be asked to separate facts from proposals

For GPT audit prompts, require:

```text
facts verified from code
inferences
recommendations
open questions
```

This prevents confident but unsupported repo claims.

---

## 2.5 GPT can run broader tests if explicitly requested

When GPT is the code agent and environment permits, it may run full tests if the coordinator asks.

Still distinguish:

```text
FOCUSED GREEN
FULL GREEN
USER-VERIFIED FULL GREEN
VERIFICATION BLOCKED
```

---

## 2.6 GPT does not need as many mechanical snippets

For GPT, snippets are still useful, but the prompt can be less step-by-step.

Still include exact constraints when risk is high:

```text
no LeaderboardPlugin
no route wiring
no Qdrant/Llama resources
no startup indexing
```

---

# 3. MiniMax / Qwen / MiMo rules

These rules apply to weaker/local/cheaper models.

## 3.1 Do not give them broad audits/designs

MiniMax/Qwen/MiMo should not receive:

```text
audit the architecture
find the best strategy
read all docs and decide
explore repo
search for all relevant files
```

unless the task is final narrow verification of explicit claims.

Coordinator should do the design from bundles, then provide an edit recipe.

---

## 3.2 Prompts must be mechanical

For MiniMax/Qwen/MiMo, prompts should be recipe-like:

```text
Read these 2 files.
Edit this 1 file.
Copy this pattern.
Add these 3 tests.
Run this focused command.
Report only deviations.
```

Avoid:

```text
if appropriate
consider
design a good abstraction
choose best place
inspect broadly
```

---

## 3.3 Inline all important facts

Do not say:

```text
Use the attached bundle.
```

Instead inline:

```text
Current file path is ...
Existing type names are ...
Existing test style is ...
Use this exact package ...
Use these UUIDs ...
```

---

## 3.4 Give exact read/edit lists

MiniMax/Qwen/MiMo prompts should include:

```text
Read only:
- ...

Edit only:
- ...
```

If source changes are not allowed, say:

```text
Do not change src/main.
```

If source changes are allowed only for compile fixes, say:

```text
Do not rewrite src/main unless compile forces a tiny import/signature fix.
```

---

## 3.5 Do not ask them to run full sbt test

Default:

```text
Do not run full sbt test.
```

They should run focused:

```bash
sbt 'project bifunctor-tagless' Test/compile 'testOnly ...'
```

The user runs full after coordinator review.

---

## 3.6 Limit reports

MiniMax/Qwen/MiMo reports should be tiny:

```text
focused result
deviations/compile fixes
```

Do not ask for long confirmation checklists.

The coordinator will verify the patch.

---

## 3.7 Avoid optionality

Bad:

```text
Add this if useful.
Use scores if appropriate.
Add diagnostics if helpful.
```

Good:

```text
Do not add scores.
Do not add diagnostics.
Do not add JSON codecs.
```

or:

```text
Add exactly this field.
```

---

## 3.8 Give compile-fix policy

If a MiniMax/Qwen/MiMo prompt includes suggested signatures, also include:

```text
If a suggested param is unused, remove it or use it in real behavior.
Do not inspect scalac flags.
Do not add @nowarn, @unused, or val _ = x.
```

This prevents expensive compiler-flag spelunking.

---

## 3.9 Give id/import hints

If a task uses domain ids, provide construction hints or the exact file to inspect.

Example:

```text
Use deterministic UUID strings.
If `MasterServiceOfferVariantId` construction is unclear, inspect only nearby existing tests or model/package.scala.
Do not broad-search the repo.
```

Better, if known:

```text
Use:
UUID.fromString("00000000-0000-0000-0000-000000000001")
as MasterServiceOfferVariantId
```

---

## 3.10 Keep them out of docs strategy unless applying exact wording

MiniMax/Qwen/MiMo can edit docs when wording is exact:

```text
Replace this sentence with this sentence.
Add this section under this heading.
Remove these stale phrases.
```

Do not ask them to:

```text
read all docs and integrate strategy
```

unless there is no GPT and the coordinator has already extracted exact required wording.

---

# 4. B-lite prompt reminder

This section applies to all models, but especially MiniMax/Qwen/MiMo.

## 4.1 B-lite current strategy

Current B-lite rules:

```text
ES and Qdrant may advance together only in eval/benchmark.
Production serving remains sequential.
Simulated hybrid is offline-only.
No Qdrant auto-supplement.
No HybridServe from benchmark alone.
InMemorySearchBackend is not an ES oracle.
```

## 4.2 First pure eval/model patch shape

For the first B-lite engine-eval patches, prefer:

```text
Read only:
- BeautySearchEval.scala
- BeautySearchEvalInventory.scala
- nearby pure test style file

Edit only:
- EngineEval.scala
- EngineEvalSpec.scala
```

Add exact types/tests.

Avoid:

```text
ES clients
Qdrant clients
Docker
route wiring
docs
benchmark executor changes
```

## 4.3 Engine eval metrics default

Default metric semantics:

```text
distinct variant ids
```

Do not count duplicate hits unless the metric explicitly says it counts ranked slots or raw hits.

Potential first-patch metric semantics:

```text
esRecallCount:
  expected ids found in ES result

qdrantRecallCount:
  expected ids found in Qdrant result

qdrantComplementCount:
  expected ids found by Qdrant but missed by ES

overlapCount:
  distinct variant ids present in both ES and Qdrant results

simulatedHybridGainCount:
  expected ids found by simulated hybrid but missed by ES

qdrantNoiseCount:
  if expectedRole == QdrantShouldStaySilent, count distinct Qdrant ids;
  otherwise 0
```

## 4.4 Query class vs expected role

`EngineEvalQueryClass` is taxonomy metadata for query inventory.

`EngineExpectedRole` drives first-pass metrics.

Do not put `queryClass` into a metrics function unless the metric actually uses it.

---

# 5. Before sending any delegated prompt, check

Ask:

```text
Did I ask the agent to audit/design when I can do it?
Did I say “use attached bundle” instead of inlining facts?
Did I include exact read/edit files?
Did I include task-specific imports/id hints?
Did I include an unused suggested param?
Did I tell it not to run full sbt test?
Did I keep report short?
Did I avoid model/thinking boilerplate?
Did I separate GPT-capable tasks from MiniMax/Qwen/MiMo mechanical tasks?
```

If any answer is bad, rewrite the prompt before sending.

---

# 6. Documentation ownership

Before adding or changing a documented fact, identify its canonical owner.

Prefer one canonical owner per fact. Other docs should use short summaries and pointers. If ownership or drift is unclear, the coordinator should request or build a focused docs bundle before writing a docs prompt.

This is coordinator work, not a default delegated-agent task. Delegated agents should not perform broad duplicate hunts unless explicitly asked. Agent docs prompts should name exact read/edit files and state which doc is the canonical owner, which copies should become pointers, and which short safety-critical guardrails must remain duplicated.

Duplicate only safety-critical guardrails that must be visible at multiple entrypoints; keep those duplicates short and free of implementation detail. Do not copy long API lists, metric semantics, roadmap state, verification counts, bundle rules, or prompt-writing rules into multiple docs.

Exact volatile verification counts belong in reports or commit messages, not long-lived docs.

Classify each docs patch as adding new truth to the canonical owner, moving truth to the canonical owner, replacing duplicate truth with a pointer, or intentionally preserving a short safety-critical guardrail.

---

# 7. Source-truth gate

Before any patch design or delegated-agent prompt, source-confirm the relevant files, types, functions, and fields.

Docs and handoff establish current state and priorities; they are not enough for exact patch APIs. If source truth is missing, ask the user for a focused bundle and stop.

Do not invent conceptual APIs, method signatures, field mappings, test recipes, or agent tasks from docs/memory. This applies before every task, not only during onboarding.

This gate is coordinator responsibility. Delegated prompts should contain exact source-confirmed facts, read/edit files, and validation commands; they should not ask agents to compensate with broad repo searches unless explicitly intended.
