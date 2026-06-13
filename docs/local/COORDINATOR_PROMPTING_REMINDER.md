# COORDINATOR_PROMPTING_REMINDER.md

Purpose: Remind the coordinator how to write cheap, precise prompts. Use this file when prompts become broad, expensive, or ambiguous.

---

# 1. Universal rules

## 1.1 Role split

Coordinator does: architecture, audit, strategy, design, patch decomposition, risk decisions.
Delegated agents do: bounded edits, focused tests, mechanical verification, small cleanup, claim verification.

Workflow:

```text
user gives bundle
→ coordinator reads bundle, extracts exact facts/files/types/imports
→ coordinator designs next minimal patch (or asks for additional focused bundle if needed)
→ delegated agent receives direct edit recipe only after source truth is sufficient
→ coordinator reviews patch → proposes commit message → applies one-step lookahead
→ user runs full test when coordinator recommends it
```

## 1.2 One-step lookahead

After closing patch N, the coordinator must include exactly one of:

```text
If N+1 is source-confirmed:
  include delegated-agent prompt for N+1.
  If N+2 is visible but not source-confirmed, also include focused source-truth bundle request for N+2.

If N+1 is not source-confirmed:
  include only focused source-truth bundle request for N+1.
  Do not write a delegated-agent prompt.
```

Never write delegated-agent prompts from guesses about APIs, fields, imports, signatures, or test seams.

After every accepted patch review / closed patch, include the extended commit message before the N+1 delegated prompt or N+1 bundle request.

## 1.3 Verification labels

* `FOCUSED GREEN`: requested focused suite passed; full repo unknown.
* `FULL GREEN`: full requested project test passed.
* `USER-VERIFIED FULL GREEN`: user ran the exact command and reported green.
* `VERIFICATION BLOCKED`: sbt/docker/local permissions blocked verification.

Focused-only is never `FULL GREEN`. Do not run full `sbt test` unless explicitly requested.

## 1.4 Full test policy

Default delegated-agent mode: focused checks only.

Prompt: `Do not run full sbt test.`
Report: `Focused result only. Full verification left to coordinator/user.`

The user runs full `sbt test` when the coordinator recommends it.

## 1.5 Delegated prompt DoD

Every delegated prompt must include:

```text
Task: feat(scope): exact small change

Read only:
- exact files

Edit only:
- exact files

Current facts (source-confirmed, inlined — never say "use attached bundle"):
- exact types, imports, package paths, constructors, helpers, aliases
- exact fixture/test style
- exact metric semantics (distinct variant-id counts unless source confirms otherwise)

Goal:
- exact behavior / tests / wording to add

Forbidden:
- exact scope boundaries

Validation:
- focused command (never full sbt test unless explicitly requested)

Report only:
- focused result
- deviations / compile fixes
```

Never say "use attached bundle" instead of inlining relevant facts.
For high-specificity edits where the target hunk is known, prefer exact replacement hunk or exact before/after snippet over prose-only instructions.

## 1.6 Unused-param invariant

If a suggested param/import/local is unused and not an intentional lifecycle/readiness edge (Distage roles, seed readiness, FK table creation order, constructor deps that force graph construction), remove it or use it in real behavior.

Do not investigate scalac flags, add `@nowarn`, `@unused`, or `val _ = x` unless source confirms an intentional edge.

## 1.7 Unsafe-extraction rule

For decoded `Map`, `List`, `Option`, `Either`, or JSON-derived structures, delegated prompts must require pattern matching with useful `fail(...)`. Do not allow `Map.apply`, `.head`, `.tail`, `.last`, `.get`, `.toOption.get`, right/left projection `.get`, or similar unsafe extraction unless the task explicitly proves the operation is total and documents why.

## 1.8 Metrics semantics

For B-lite / M-ESQ-EVAL prompts, inline metric semantics from current handoff/source. Default is distinct variant-id counts unless source-confirmed metric says otherwise. Do not re-explain metric semantics here; reference `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md`.

## 1.9 Test style defaults

For simple pure search/eval model tests: `AnyWordSpec`, deterministic UUID fixtures, direct `assert`, no effects, no Distage, no runtime, no Docker, no ES/Qdrant clients.

Do not use: `Ref`, `Atomic*`, `var`, `Recording*`, `Counting*`, `assert(true)`, empty success branch, `isInstanceOf`, `asInstanceOf`, `Option.get`, null assertions. Use pattern matching for ADTs/options; use direct equality for case objects.

## 1.10 Commit messages

Delegated agents do not commit. Non-trivial commits use extended messages: subject + body stating behavior/result, verification, and explicit non-goals. One-line messages acceptable only for tiny mechanical cleanups. Prepare the full extended message unless user asks for short subject only.

## 1.11 What goes where

`AGENTS.md`: stable repo behavior for all agents.
This file: prompt-writing guidance and coordinator workflow.
Specific prompt: task-local facts (package paths, class names, imports, helpers, metric semantics).

## 1.12 Manual/env-gated/cancel-by-default test DoD

If a patch adds env vars, BEGIN/END markers, saved-artifact flow, or manual local-service flow, close it only with docs/runbook done, queued as immediate N+1, or explicitly blocked with focused executable bundle; report that status.

---

# 2. Model prompt deltas

Source-truth gate always wins for all models. No model may infer missing APIs.

| Aspect | GPT | MiniMax / Qwen / MiMo |
|--------|-----|------------------------|
| Scope | Can handle wider context, rationale, design cross-check. Still needs bounded scope, non-goals, files, validation command. | Mechanical recipe only: read/edit files, exact facts, exact validation, short report. |
| Rationale | May include rationale; keep action surface small. | No rationale needed. Recipe-like: "Read these 2 files. Edit this 1 file. Copy this pattern. Run this command." |
| Audit | Acceptable for architecture review / cross-checking design / finding contradictions. Do not mix audit + code edits + docs + full verification in one prompt. | Do not give broad audits/designs. Coordinator does design from bundles, then provides edit recipe. |
| Optionality | — | Avoid "if useful", "consider", "choose best place". Use exact: "Do not add scores." or "Add exactly this field." |
| Reports | Can be slightly more detailed. | Tiny: focused result + deviations/compile fixes only. |
| Full tests | May run full tests if explicitly requested. Still distinguish FOCUSED GREEN / FULL GREEN / USER-VERIFIED FULL GREEN. | Do not run full sbt test. Focused only. |
| Docs strategy | Can do docs review. | Only exact wording replace/add/remove. Not "read all docs and integrate strategy". |
| Compile-fix policy | — | Include: "If a suggested param is unused, remove it or use it in real behavior. Do not inspect scalac flags. Do not add @nowarn, @unused, or val _ = x." |

---

# 3. Bundle script rules

Bundles are for the coordinator, not delegated agents. After reading a bundle, the coordinator must inline important facts into the delegated prompt.

When requesting a bundle from the user, provide an executable shell script, not a prose include-list.

Script rules:

* Write to unique `/tmp/beautyq-<topic>-<timestamp>-$RANDOM.txt`.
* Include only task-relevant status, compact diff, signatures, nearby specs, docs anchors, hazard scans.
* Cap/truncate output when large.
* After truncation, create `ZIP="$OUT.zip"`, run `zip -9 -j "$ZIP" "$OUT"`, then print `wc -c "$OUT"` and `wc -c "$ZIP"`, then `cpf "$ZIP"`, then `echo "$ZIP"`.
* Do not include `/tmp`, full `target`, generated build output, screenshots, stale numbered files, or broad `HEAD~N --patch` unless explicitly requested.

Canonical shell shape:

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

ZIP="$OUT.zip"
rm -f "$ZIP"
zip -9 -j "$ZIP" "$OUT" >/dev/null

wc -c "$OUT"
wc -c "$ZIP"
cpf "$ZIP"
echo "$ZIP"
```

---

# 4. Model recommendation block

For non-trivial delegated prompts, provide a separate coordinator note before the prompt:

```text
Task classification:
- Type:
- Source truth:
- Risk:
- Preference:
- Availability:

Run recommendation:
- Cheapest likely to work:
- Faster cloud option:
- Stronger non-GPT option:
- GPT option, only if justified:
- If source truth is missing:
```

This note is not part of the delegated prompt unless the user asks. Keep delegated prompts model-agnostic: no `Model: ...`, no `thinking-budget=...`, no `Use AGENTS.md` boilerplate.

Use exact model/tier names: `MiMo-V2.5`, `MiMo-V2.5-Pro`, `MiniMax-M3`, `GPT-5.5-medium`, `GPT-5.5-high`. Do not write vague `Qwen`, `MiMo`, or `GPT`.

If source truth is missing, recommend requesting a focused bundle first; do not recommend a stronger model to infer missing APIs.

---

# 5. Documentation ownership

Before adding or changing a documented fact, identify its canonical owner. Prefer one canonical owner per fact; other docs should use short summaries and pointers.

Duplicate only safety-critical guardrails that must be visible at multiple entrypoints; keep those duplicates short and free of implementation detail. Do not copy long API lists, metric semantics, roadmap state, bundle rules, or prompt-writing rules into multiple docs.

Exact volatile verification counts belong in reports or commit messages, not long-lived docs.

The source-truth gate is safety-critical and must not be deduplicated away; keep the canonical rule in this file and only link to it from other docs.

---

# 6. Before sending any delegated prompt, check

```text
□ Did I ask the agent to audit/design when I can do it?
□ Did I say "use attached bundle" instead of inlining facts?
□ Did I include exact read/edit files?
□ Did I include known imports, package paths, constructors, aliases, fixture/test style, validation command, boundaries?
□ Did I include an unused suggested param?
□ Did I tell it not to run full sbt test?
□ Did I keep report short?
□ Did I avoid model/thinking boilerplate?
□ Did I provide a model recommendation block for non-trivial prompts?
□ If I need a bundle, did I give an executable shell script?
```

If any answer is bad, rewrite the prompt before sending.

---

# 7. Source-truth gate

This section is a protected coordinator invariant. Do not remove, shorten, soften, or move it into `AGENTS.md`. It may only be replaced by wording that is at least as strict: missing source truth must stop patch planning, delegated-agent prompts, adjacent "safe" patches, and invented helpers/APIs.

Before any patch design or delegated-agent prompt, source-confirm the relevant files, types, functions, and fields.

Docs and handoff establish current state and priorities; they are not enough for exact patch APIs. If source truth is missing, ask the user for a focused bundle and stop.

Do not invent conceptual APIs, method signatures, field mappings, test recipes, or agent tasks from docs/memory. This applies before every task, not only during onboarding.

This gate is coordinator responsibility. Delegated prompts should contain exact source-confirmed facts, read/edit files, and validation commands; they should not ask agents to compensate with broad repo searches unless explicitly intended.

If the requested output includes a patch proposal, exact read/edit files, a test recipe, or a delegated-agent prompt, but required source truth is missing, explicitly decline that part of the output. Ask for a focused bundle and stop.

Do not salvage a source-incomplete task by inventing a source-independent helper, adapter, model API, field mapping, merge rule, or test plan unless the current bundle source-confirms that this helper/API is the next required seam.

Do not rescope a source-incomplete task into an adjacent "safe" patch. If the requested task requires missing anchors, stop at the bundle request unless the user explicitly approves a different task after seeing the missing-source report.

A partial inventory may list `SOURCE_CONFIRMED` and `DOC_LEVEL_ONLY` facts, but when required anchors are missing it must end with a bundle request, not a patch proposal. The source-truth gate has higher priority than the requested output shape.
